package core;

import model.LocationBlock;
import model.VirtualHost;
import net.HttpRequest;
import net.HttpResponse;
import net.StatusCode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ScriptRunner — executes CGI scripts via {@link ProcessBuilder}.
 *
 * <p>Supports multiple interpreters resolved by file extension:
 * <ul>
 *   <li>{@code .py}  → python3</li>
 *   <li>{@code .sh}  → /bin/bash</li>
 *   <li>{@code .pl}  → perl</li>
 *   <li>executable   → run directly (shebang line)</li>
 * </ul>
 * </p>
 */
public class ScriptRunner {

    private static final int CGI_TIMEOUT_SEC = 10;

    public static HttpResponse execute(File scriptFile,
                                       HttpRequest req,
                                       LocationBlock loc,
                                       VirtualHost vh) {
        try {
            ProcessBuilder pb = buildProcess(scriptFile, req);
            Process proc = pb.start();

            boolean finished = proc.waitFor(CGI_TIMEOUT_SEC, TimeUnit.SECONDS);

            // Feed request body to CGI stdin
            byte[] body = req.bodyBytes();
            try (OutputStream stdin = proc.getOutputStream()) {
                if (!finished){
                    proc.destroyForcibly();
                    System.err.println("[WARN] CGI script timed out: " + scriptFile.getName());
                    return errorResponse(StatusCode.INTERNAL_SERVER_ERROR);
                } 
                if (body != null && body.length > 0) {
                    stdin.write(body);
                }
            }

            // Read stdout within timeout
            byte[] rawOutput;
            try (InputStream stdout = proc.getInputStream()) {
                rawOutput = stdout.readAllBytes();
            }

            finished = proc.waitFor(CGI_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                System.err.println("[WARN] CGI script timed out: " + scriptFile.getName());
                return errorResponse(StatusCode.INTERNAL_SERVER_ERROR);
            }

            return parseCgiOutput(rawOutput);

        } catch (Exception ex) {
            ex.printStackTrace();
            return errorResponse(StatusCode.INTERNAL_SERVER_ERROR);
        }
    }

    // ── Process building ──────────────────────────────────────────────────────

    private static ProcessBuilder buildProcess(File scriptFile, HttpRequest req) {
        List<String> cmd = resolveCommand(scriptFile);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(scriptFile.getParentFile());
        pb.redirectErrorStream(true);

        Map<String, String> env = pb.environment();

        String rawUri   = req.rawUri();
        String pathInfo = rawUri.contains("?") ? rawUri.substring(0, rawUri.indexOf('?')) : rawUri;
        String query    = rawUri.contains("?") ? rawUri.substring(rawUri.indexOf('?') + 1) : "";

        env.put("GATEWAY_INTERFACE", "CGI/1.1");
        env.put("SERVER_SOFTWARE",   "WebServ/1.0");
        env.put("SERVER_PROTOCOL",   "HTTP/1.1");
        env.put("REQUEST_METHOD",    req.method());
        env.put("PATH_INFO",         pathInfo);
        env.put("QUERY_STRING",      query);
        env.put("SCRIPT_FILENAME",   scriptFile.getAbsolutePath());
        env.put("SCRIPT_NAME",       pathInfo);

        byte[] body = req.bodyBytes();
        if (body != null && body.length > 0) {
            env.put("CONTENT_LENGTH", String.valueOf(body.length));
            String ct = req.header("content-type");
            if (ct != null) env.put("CONTENT_TYPE", ct);
        } else {
            env.put("CONTENT_LENGTH", "0");
        }

        // Forward HTTP request headers as HTTP_* env vars
        for (Map.Entry<String, String> hdr : req.headers().entrySet()) {
            String envKey = "HTTP_" + hdr.getKey().toUpperCase().replace('-', '_');
            env.put(envKey, hdr.getValue());
        }

        return pb;
    }

    /**
     * Resolves the command list for the given script by extension.
     * Falls back to direct execution (requires executable bit + shebang).
     */
    private static List<String> resolveCommand(File script) {
        String name = script.getName().toLowerCase();
        List<String> cmd = new ArrayList<>();
        if (name.endsWith(".py")) {
            cmd.add("python3");
            cmd.add(script.getAbsolutePath());
        } else if (name.endsWith(".sh")) {
            cmd.add("/bin/bash");
            cmd.add(script.getAbsolutePath());
        } else if (name.endsWith(".pl")) {
            cmd.add("perl");
            cmd.add(script.getAbsolutePath());
        } else {
            // Direct execution — script must have executable bit and a shebang
            cmd.add(script.getAbsolutePath());
        }
        return cmd;
    }

    // ── Output parsing ────────────────────────────────────────────────────────

    private static HttpResponse parseCgiOutput(byte[] raw) {
        String text    = new String(raw, StandardCharsets.UTF_8);
        String[] parts = text.split("\\r?\\n\\r?\\n", 2);

        HttpResponse res = new HttpResponse();
        res.status(StatusCode.OK);

        if (parts.length == 2) {
            for (String line : parts[0].split("\\r?\\n")) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    res.addHeader(line.substring(0, colon).trim(),
                                  line.substring(colon + 1).trim());
                }
            }
            res.body(parts[1].getBytes(StandardCharsets.UTF_8), null);
        } else {
            res.body(raw, "text/plain");
        }
        return res;
    }

    private static HttpResponse errorResponse(StatusCode code) {
        HttpResponse res = new HttpResponse();
        res.status(code);
        res.body("CGI execution error.".getBytes(StandardCharsets.UTF_8), "text/plain");
        return res;
    }
}
