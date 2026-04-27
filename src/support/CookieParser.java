package support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CookieParser — parses the value of an HTTP {@code Cookie} request header
 * into a name→value map.
 *
 * <p>Example header: {@code Cookie: session=abc123; theme=dark}
 * produces {@code {"session": "abc123", "theme": "dark"}}.</p>
 */
public final class CookieParser {

    private CookieParser() {}

    /**
     * Parses {@code cookieHeader} and returns an unmodifiable name→value map.
     * Returns an empty map for {@code null} or blank input.
     */
    public static Map<String, String> parse(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : cookieHeader.split(";")) {
            String trimmed = pair.strip();
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                String name  = trimmed.substring(0, eq).strip();
                String value = trimmed.substring(eq + 1).strip();
                result.put(name, value);
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
