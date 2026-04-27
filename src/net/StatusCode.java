package net;

/**
 * StatusCode — HTTP/1.1 status codes used by WebServ.
 * Each constant carries a numeric {@link #code()} and a human-readable {@link #phrase()}.
 */
public enum StatusCode {

    // 2xx Success
    OK                    (200, "OK"),
    CREATED               (201, "Created"),
    NO_CONTENT            (204, "No Content"),

    // 3xx Redirection
    MOVED_PERMANENTLY     (301, "Moved Permanently"),
    FOUND                 (302, "Found"),

    // 4xx Client Errors
    BAD_REQUEST           (400, "Bad Request"),
    FORBIDDEN             (403, "Forbidden"),
    NOT_FOUND             (404, "Not Found"),
    METHOD_NOT_ALLOWED    (405, "Method Not Allowed"),
    PAYLOAD_TOO_LARGE     (413, "Payload Too Large"),

    // 5xx Server Errors
    INTERNAL_SERVER_ERROR (500, "Internal Server Error"),
    NOT_IMPLEMENTED       (501, "Not Implemented");

    // ─────────────────────────────────────────────────────────────────────────

    private final int    numericCode;
    private final String reasonPhrase;

    StatusCode(int numericCode, String reasonPhrase) {
        this.numericCode  = numericCode;
        this.reasonPhrase = reasonPhrase;
    }

    public int    code()   { return numericCode; }
    public String phrase() { return reasonPhrase; }

    @Override
    public String toString() {
        return numericCode + " " + reasonPhrase;
    }
}
