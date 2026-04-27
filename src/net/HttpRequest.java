package net;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * HttpRequest — accumulates raw bytes from the NIO read loop and incrementally
 * parses an HTTP/1.1 request (request line, headers, body).
 *
 * <p>Supports both fixed-length bodies ({@code Content-Length}) and
 * chunked transfer encoding ({@code Transfer-Encoding: chunked}).</p>
 *
 * <p>Call {@link #ingest(ByteBuffer)} with each incoming buffer slice.
 * Check {@link #phase()} to know when parsing is complete or has failed.</p>
 */
public class HttpRequest {

    // ── Parsing state machine ─────────────────────────────────────────────────
    public enum Phase {
        READ_REQUEST_LINE,
        READ_HEADERS,
        READ_BODY,
        READ_CHUNK_SIZE,
        READ_CHUNK_DATA,
        READ_CHUNK_TAIL,
        COMPLETE,
        BROKEN
    }

    private Phase phase = Phase.READ_REQUEST_LINE;

    // ── Parsed fields ─────────────────────────────────────────────────────────
    private String              method;
    private String              rawUri;        // includes query string
    private String              httpVersion;
    private Map<String, String> headerMap   = new HashMap<>();
    private ByteArrayOutputStream bodyAccum = new ByteArrayOutputStream();

    // ── Parser internals ──────────────────────────────────────────────────────
    private final StringBuilder lineBuf   = new StringBuilder();
    private int                 bodyLimit = 0;    // from Content-Length
    private int                 chunkRem  = -1;   // bytes remaining in current chunk

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** Feed raw bytes from the socket into the parser. */
    public void ingest(ByteBuffer buf) {
        while (buf.hasRemaining() && phase != Phase.COMPLETE && phase != Phase.BROKEN) {
            byte b = buf.get();

            switch (phase) {
                case READ_REQUEST_LINE:
                case READ_HEADERS:
                case READ_CHUNK_SIZE:
                case READ_CHUNK_TAIL:
                    if (b == '\r') break;          // skip CR
                    if (b == '\n') {
                        processTextLine(lineBuf.toString());
                        lineBuf.setLength(0);
                    } else {
                        lineBuf.append((char) b);
                    }
                    break;

                case READ_BODY:
                    bodyAccum.write(b);
                    if (bodyAccum.size() >= bodyLimit) phase = Phase.COMPLETE;
                    break;

                case READ_CHUNK_DATA:
                    bodyAccum.write(b);
                    if (--chunkRem == 0) phase = Phase.READ_CHUNK_TAIL;
                    break;

                default:
                    break;
            }
        }
    }

    /** Force the request into the BROKEN phase (e.g. body too large). */
    public void forceError() {
        phase = Phase.BROKEN;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accessors
    // ─────────────────────────────────────────────────────────────────────────

    public Phase  phase()      { return phase; }
    public String method()     { return method; }
    public String rawUri()     { return rawUri; }

    /** URI path without query string. */
    public String path() {
        if (rawUri == null) return "/";
        int q = rawUri.indexOf('?');
        return q >= 0 ? rawUri.substring(0, q) : rawUri;
    }

    public String httpVersion()          { return httpVersion; }
    public Map<String, String> headers() { return Collections.unmodifiableMap(headerMap); }

    public String header(String name) {
        return headerMap.get(name == null ? null : name.toLowerCase());
    }

    public byte[] bodyBytes() { return bodyAccum.toByteArray(); }

    public int bodyLength() {
        return bodyAccum.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal parsing
    // ─────────────────────────────────────────────────────────────────────────

    private void processTextLine(String line) {
        switch (phase) {

            case READ_REQUEST_LINE: {
                if (line.isEmpty()) break;   // ignore leading blank lines
                String[] tokens = line.split(" ", 3);
                if (tokens.length < 3) { phase = Phase.BROKEN; break; }
                method      = tokens[0].toUpperCase();
                rawUri      = tokens[1];
                httpVersion = tokens[2];
                phase       = Phase.READ_HEADERS;
                break;
            }

            case READ_HEADERS: {
                if (line.isEmpty()) {
                    // All headers received — decide what comes next
                    transitionAfterHeaders();
                    break;
                }
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key   = line.substring(0, colon).trim().toLowerCase();
                    String value = line.substring(colon + 1).trim();
                    headerMap.put(key, value);
                }
                break;
            }

            case READ_CHUNK_SIZE: {
                if (line.isEmpty()) break;
                try {
                    String hex = line.split(";", 2)[0].trim();
                    chunkRem = Integer.parseInt(hex, 16);
                    if (chunkRem == 0) {
                        phase = Phase.COMPLETE;
                    } else {
                        phase = Phase.READ_CHUNK_DATA;
                    }
                } catch (NumberFormatException ex) {
                    phase = Phase.BROKEN;
                }
                break;
            }

            case READ_CHUNK_TAIL: {
                // Expect an empty CRLF line after each chunk body
                if (line.isEmpty()) phase = Phase.READ_CHUNK_SIZE;
                break;
            }

            default:
                break;
        }
    }

    private void transitionAfterHeaders() {
        String te = headerMap.get("transfer-encoding");
        String cl = headerMap.get("content-length");

        if (te != null && te.contains("chunked")) {
            phase = Phase.READ_CHUNK_SIZE;
        } else if (cl != null) {
            try {
                bodyLimit = Integer.parseInt(cl.trim());
                phase = bodyLimit > 0 ? Phase.READ_BODY : Phase.COMPLETE;
            } catch (NumberFormatException ex) {
                phase = Phase.BROKEN;
            }
        } else {
            phase = Phase.COMPLETE;
        }
    }
}
