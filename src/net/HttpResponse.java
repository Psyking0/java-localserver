package net;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HttpResponse — builds and serializes an HTTP/1.1 response.
 *
 * <p>Call {@link #status(StatusCode)}, {@link #addHeader(String, String)},
 * and {@link #body(byte[], String)} to configure the response, then
 * {@link #serialize()} to get the final {@link ByteBuffer} to write to the socket.</p>
 */
public class HttpResponse {

    private StatusCode              statusCode = StatusCode.OK;
    private final Map<String, String> headerMap = new LinkedHashMap<>();
    private byte[]                  bodyData;

    public HttpResponse() {
        headerMap.put("Server",     "WebServ/1.0");
        headerMap.put("Connection", "keep-alive");
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    public void status(StatusCode code) {
        this.statusCode = code;
    }

    public void addHeader(String name, String value) {
        headerMap.put(name, value);
    }

    public void body(byte[] data, String contentType) {
        this.bodyData = data;
        if (contentType != null && !headerMap.containsKey("Content-Type")) {
            headerMap.put("Content-Type", contentType);
        }
        headerMap.put("Content-Length", String.valueOf(data != null ? data.length : 0));
    }

    public void body(String text) {
        body(text.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");
    }

    // ── Factory helpers ───────────────────────────────────────────────────────

    /** Convenience: build a plain-text error response. */
    public static HttpResponse plainError(StatusCode code) {
        HttpResponse res = new HttpResponse();
        res.status(code);
        String msg = code.code() + " " + code.phrase();
        res.body(msg.getBytes(StandardCharsets.UTF_8), "text/plain");
        return res;
    }

    /** Convenience: build a 302 redirect. */
    public static HttpResponse redirect(String location) {
        HttpResponse res = new HttpResponse();
        res.status(StatusCode.FOUND);
        res.addHeader("Location", location);
        res.body(new byte[0], null);
        return res;
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    /**
     * Assembles the full HTTP response (status line + headers + body) into
     * a single {@link ByteBuffer} ready for writing to a {@link java.nio.channels.SocketChannel}.
     */
    public ByteBuffer serialize() {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(statusCode.code())
            .append(' ').append(statusCode.phrase()).append("\r\n");

        for (Map.Entry<String, String> e : headerMap.entrySet()) {
            head.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
        }
        head.append("\r\n");

        byte[] headBytes = head.toString().getBytes(StandardCharsets.UTF_8);
        int    total     = headBytes.length + (bodyData != null ? bodyData.length : 0);

        ByteBuffer buf = ByteBuffer.allocate(total);
        buf.put(headBytes);
        if (bodyData != null) buf.put(bodyData);
        buf.flip();
        return buf;
    }
}
