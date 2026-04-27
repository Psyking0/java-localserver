package core;

import model.VirtualHost;
import model.LocationBlock;
import net.HttpRequest;
import net.HttpResponse;
import net.StatusCode;
import support.MediaTypes;
import support.CookieParser;
import support.SessionStore;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * RequestDispatcher — routes an {@link HttpRequest} to the correct handler:
 * static files, file uploads, CGI scripts, redirects, session demo, or metrics.
 */
public class RequestDispatcher {

    private final List<VirtualHost> virtualHosts;

    public RequestDispatcher(List<VirtualHost> virtualHosts) {
        this.virtualHosts = virtualHosts;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Main dispatch entry point called from the engine after a full request is read. */
    public HttpResponse dispatch(HttpRequest req, int port) {
        HttpEngine.totalRequests.incrementAndGet();

        // Built-in special endpoints
        if ("/api/metrics".equals(req.path())) return handleMetrics();
        if ("/session".equals(req.path()))     return handleSession(req);
        if ("/admin".equals(req.path()) || "/metrics".equals(req.path())) {
            return HttpResponse.redirect("/admin.html");
        }

        VirtualHost vh = resolveHost(req.header("host"), port);
        if (vh == null) {
            return buildError(StatusCode.NOT_FOUND, null);
        }

        // Body size enforcement
        if (req.bodyBytes() != null && req.bodyBytes().length > vh.maxBodyBytes) {
            return buildError(StatusCode.PAYLOAD_TOO_LARGE, vh);
        }

        LocationBlock loc = resolveLocation(req.path(), vh);
        if (loc == null) {
            return buildError(StatusCode.NOT_FOUND, vh);
        }

        // Method guard
        if (loc.allowedMethods != null && !loc.allowedMethods.contains(req.method())) {
            return buildError(StatusCode.METHOD_NOT_ALLOWED, vh);
        }

        // Redirect
        if (loc.redirectTarget != null) {
            return HttpResponse.redirect(loc.redirectTarget);
        }

        try {
            return handleFileOp(req, loc, vh);
        } catch (Exception ex) {
            ex.printStackTrace();
            return buildError(StatusCode.INTERNAL_SERVER_ERROR, vh);
        }
    }

    // ── Virtual-host & location resolution ───────────────────────────────────

    /** Finds the best-matching virtual host for a given Host header + port. */
    public VirtualHost resolveHost(String hostHeader, int port) {
        VirtualHost fallback = null;
        String reqHostname = hostHeader != null ? hostHeader.split(":")[0] : "";
        for (VirtualHost vh : virtualHosts) {
            if (!vh.listenPorts.contains(port)) continue;
            if (fallback == null) fallback = vh;
            if (vh.hostname != null && vh.hostname.equals(reqHostname)) return vh;
        }
        return fallback;
    }

    /** Longest-prefix match across a virtual host's location blocks. */
    private LocationBlock resolveLocation(String uri, VirtualHost vh) {
        LocationBlock best = null;
        for (LocationBlock loc : vh.locations) {
            if (uri.startsWith(loc.uriPrefix)) {
                if (best == null || loc.uriPrefix.length() > best.uriPrefix.length()) {
                    best = loc;
                }
            }
        }
        return best;
    }

    // ── File operations ───────────────────────────────────────────────────────

    private HttpResponse handleFileOp(HttpRequest req, LocationBlock loc, VirtualHost vh) throws IOException {
        String tail = req.path().substring(loc.uriPrefix.length());
        if (!tail.startsWith("/") && !tail.isEmpty()) tail = "/" + tail;

        Path target = Paths.get(loc.rootDir, tail).normalize();
        Path rootAbs = Paths.get(loc.rootDir).normalize().toAbsolutePath();

        // Path traversal guard
        if (!target.toAbsolutePath().startsWith(rootAbs)) {
            return buildError(StatusCode.FORBIDDEN, vh);
        }

        File targetFile = target.toFile();

        // CGI check (before method routing)
        if (loc.cgiSuffix != null && targetFile.getName().endsWith(loc.cgiSuffix)) {
            return ScriptRunner.execute(targetFile, req, loc, vh);
        }

        switch (req.method()) {
            case "DELETE": return handleDelete(targetFile, vh);
            case "POST":   return handlePost(target, targetFile, req, vh);
            default:       return handleGet(req, targetFile, loc, vh);
        }
    }

    private HttpResponse handleDelete(File file, VirtualHost vh) {
        if (!file.exists())     return buildError(StatusCode.NOT_FOUND, vh);
        if (file.isDirectory()) return buildError(StatusCode.FORBIDDEN, vh);
        if (file.delete()) {
            HttpResponse res = new HttpResponse();
            res.status(StatusCode.NO_CONTENT);
            return res;
        }
        return buildError(StatusCode.INTERNAL_SERVER_ERROR, vh);
    }

    private HttpResponse handlePost(Path target, File file, HttpRequest req, VirtualHost vh) throws IOException {
        if (file.isDirectory()) return buildError(StatusCode.FORBIDDEN, vh);
        byte[] payload = req.bodyBytes();
        if (payload == null || payload.length == 0) {
            return buildError(StatusCode.BAD_REQUEST, vh);
        }
        // Ensure the parent directory exists (e.g. public/uploads/ may not be created yet)
        Files.createDirectories(target.getParent());
        Files.write(target, payload);
        HttpResponse res = new HttpResponse();
        res.status(StatusCode.CREATED);
        res.body("Saved.".getBytes(StandardCharsets.UTF_8), "text/plain");
        return res;
    }

    private HttpResponse handleGet(HttpRequest req, File file, LocationBlock loc, VirtualHost vh) throws IOException {
        if (!file.exists()) return buildError(StatusCode.NOT_FOUND, vh);

        if (file.isDirectory()) {
            // Try index file
            if (loc.indexFile != null) {
                File idx = new File(file, loc.indexFile);
                if (idx.exists() && idx.isFile()) return serveFile(idx);
            }
            // Auto-directory listing
            if (Boolean.TRUE.equals(loc.enableListing)) {
                return directoryListing(file, req.path());
            }
            return buildError(StatusCode.FORBIDDEN, vh);
        }

        return serveFile(file);
    }

    // ── Static file serving ───────────────────────────────────────────────────

    private HttpResponse serveFile(File file) throws IOException {
        String ext  = extensionOf(file.getName());
        String mime = MediaTypes.forExtension(ext);
        byte[] data = Files.readAllBytes(file.toPath());
        HttpResponse res = new HttpResponse();
        res.status(StatusCode.OK);
        res.body(data, mime);
        return res;
    }

    // ── Directory listing ─────────────────────────────────────────────────────

    private HttpResponse directoryListing(File dir, String uriPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
          .append("<title>Index of ").append(escape(uriPath)).append("</title>")
          .append("<style>body{font-family:monospace;padding:1em}")
          .append("a{text-decoration:none;color:#0077cc}</style></head><body>")
          .append("<h2>&#128193; Index of ").append(escape(uriPath)).append("</h2><hr><pre>");

        File[] entries = dir.listFiles();
        if (entries != null) {
            java.util.Arrays.sort(entries);
            for (File e : entries) {
                String name = e.getName() + (e.isDirectory() ? "/" : "");
                String href = (uriPath.endsWith("/") ? uriPath : uriPath + "/") + e.getName();
                sb.append("<a href=\"").append(href).append("\">")
                  .append(name).append("</a>\n");
            }
        }
        sb.append("</pre><hr></body></html>");

        HttpResponse res = new HttpResponse();
        res.status(StatusCode.OK);
        res.body(sb.toString().getBytes(StandardCharsets.UTF_8), "text/html");
        return res;
    }

    // ── Built-in endpoints ────────────────────────────────────────────────────

    private HttpResponse handleMetrics() {
        long uptimeSec = (System.currentTimeMillis() - HttpEngine.bootTime) / 1000;
        String uptime = String.format("%02d:%02d:%02d",
                uptimeSec / 3600, (uptimeSec % 3600) / 60, uptimeSec % 60);
        Runtime rt = Runtime.getRuntime();
        long memMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);

        String json = String.format(
            "{\"uptime\":\"%s\",\"requests\":%d,\"live_connections\":%d," +
            "\"bytes_in\":%d,\"bytes_out\":%d," +
            "\"errors_404\":%d,\"errors_500\":%d,\"jvm_heap_mb\":%d}",
            uptime,
            HttpEngine.totalRequests.get(),
            HttpEngine.liveConnections.get(),
            HttpEngine.bytesIn.get(),
            HttpEngine.bytesOut.get(),
            HttpEngine.count404.get(),
            HttpEngine.count500.get(),
            memMb
        );

        HttpResponse res = new HttpResponse();
        res.status(StatusCode.OK);
        res.body(json.getBytes(StandardCharsets.UTF_8), "application/json");
        return res;
    }

    private HttpResponse handleSession(HttpRequest req) {
        Map<String, String> cookies = CookieParser.parse(req.header("cookie"));
        String sid = cookies.get("WSRV_SESSION");
        String html;

        if (sid == null || SessionStore.fetch(sid) == null) {
            sid = SessionStore.newSession();
            SessionStore.fetch(sid).put("visits", 1);
            html = "<html><body><h1>Session started</h1><p>ID: " + sid + "</p><p>Visits: 1</p></body></html>";
        } else {
            int visits = (Integer) SessionStore.fetch(sid).getOrDefault("visits", 0) + 1;
            SessionStore.fetch(sid).put("visits", visits);
            html = "<html><body><h1>Welcome back</h1><p>ID: " + sid + "</p><p>Visits: " + visits + "</p></body></html>";
        }

        HttpResponse res = new HttpResponse();
        res.status(StatusCode.OK);
        res.addHeader("Set-Cookie", "WSRV_SESSION=" + sid + "; Path=/; HttpOnly");
        res.body(html.getBytes(StandardCharsets.UTF_8), "text/html");
        return res;
    }

    // ── Error responses ───────────────────────────────────────────────────────

    public HttpResponse buildError(StatusCode code, VirtualHost vh) {
        if (code == StatusCode.NOT_FOUND)            HttpEngine.count404.incrementAndGet();
        if (code == StatusCode.INTERNAL_SERVER_ERROR) HttpEngine.count500.incrementAndGet();

        HttpResponse res = new HttpResponse();
        res.status(code);
        byte[] body = null;

        if (vh != null && vh.errorPageMap != null) {
            String pagePath = vh.errorPageMap.get(String.valueOf(code.code()));
            if (pagePath != null) {
                try { body = Files.readAllBytes(Paths.get(pagePath)); }
                catch (IOException ignored) {}
            }
        }

        if (body == null) {
            String fallback = "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
                "<title>" + code + "</title></head><body>" +
                "<h1>" + code + "</h1>" +
                "<p>" + code.phrase() + "</p><hr><em>WebServ</em></body></html>";
            body = fallback.getBytes(StandardCharsets.UTF_8);
        }

        res.body(body, "text/html");
        return res;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
