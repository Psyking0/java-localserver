package support;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * MediaTypes — maps file extensions to their MIME / media type strings.
 * Used when serving static files so the browser renders them correctly.
 */
public final class MediaTypes {

    private static final Map<String, String> TABLE;

    static {
        Map<String, String> m = new HashMap<>();
        // Text
        m.put("html", "text/html; charset=utf-8");
        m.put("htm",  "text/html; charset=utf-8");
        m.put("css",  "text/css");
        m.put("txt",  "text/plain; charset=utf-8");
        m.put("csv",  "text/csv");
        m.put("xml",  "application/xml");
        // Scripts
        m.put("js",   "application/javascript");
        m.put("mjs",  "application/javascript");
        m.put("json", "application/json");
        // Images
        m.put("png",  "image/png");
        m.put("jpg",  "image/jpeg");
        m.put("jpeg", "image/jpeg");
        m.put("gif",  "image/gif");
        m.put("webp", "image/webp");
        m.put("svg",  "image/svg+xml");
        m.put("ico",  "image/x-icon");
        // Fonts
        m.put("woff",  "font/woff");
        m.put("woff2", "font/woff2");
        // Archives / binary
        m.put("zip",  "application/zip");
        m.put("pdf",  "application/pdf");
        // Server-side scripts rendered as plain text when not executed as CGI
        m.put("py",   "text/plain");
        m.put("sh",   "text/plain");
        m.put("php",  "text/plain");
        TABLE = Collections.unmodifiableMap(m);
    }

    private MediaTypes() {}

    /**
     * Returns the MIME type for the given file extension (case-insensitive).
     * Falls back to {@code application/octet-stream} for unknown types.
     */
    public static String forExtension(String ext) {
        if (ext == null) return "application/octet-stream";
        return TABLE.getOrDefault(ext.toLowerCase(), "application/octet-stream");
    }
}
