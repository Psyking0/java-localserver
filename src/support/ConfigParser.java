package support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * ConfigParser — minimal recursive-descent JSON parser.
 *
 * <p>Supports objects, arrays, strings, numbers, booleans, and null.
 * No external libraries required.</p>
 */
public class ConfigParser {

    private final String src;
    private int          cursor;

    private ConfigParser(String src) {
        this.src    = src;
        this.cursor = 0;
    }

    // ── Static entry points ───────────────────────────────────────────────────

    public static Object fromString(String json) {
        return new ConfigParser(json).readValue();
    }

    public static Object fromFile(String path) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(path)));
        return fromString(content);
    }

    // ── Top-level value dispatch ──────────────────────────────────────────────

    private Object readValue() {
        skipSpaces();
        if (cursor >= src.length()) return null;
        char ch = src.charAt(cursor);
        if (ch == '{') return readObject();
        if (ch == '[') return readArray();
        if (ch == '"') return readString();
        if (ch == 't' || ch == 'f') return readBoolean();
        if (ch == 'n') return readNull();
        if (Character.isDigit(ch) || ch == '-') return readNumber();
        throw new RuntimeException("Unexpected character '" + ch + "' at position " + cursor);
    }

    // ── Object ────────────────────────────────────────────────────────────────

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        cursor++; // consume '{'
        skipSpaces();
        if (peek() == '}') { cursor++; return map; }

        while (true) {
            skipSpaces();
            String key = readString();
            skipSpaces();
            expect(':');
            Object val = readValue();
            map.put(key, val);
            skipSpaces();
            char sep = src.charAt(cursor);
            if (sep == '}') { cursor++; break; }
            else if (sep == ',') cursor++;
            else throw new RuntimeException("Expected ',' or '}' at " + cursor);
        }
        return map;
    }

    // ── Array ─────────────────────────────────────────────────────────────────

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        cursor++; // consume '['
        skipSpaces();
        if (peek() == ']') { cursor++; return list; }

        while (true) {
            list.add(readValue());
            skipSpaces();
            char sep = src.charAt(cursor);
            if (sep == ']') { cursor++; break; }
            else if (sep == ',') cursor++;
            else throw new RuntimeException("Expected ',' or ']' at " + cursor);
        }
        return list;
    }

    // ── String ────────────────────────────────────────────────────────────────

    private String readString() {
        cursor++; // consume opening '"'
        StringBuilder sb = new StringBuilder();
        while (cursor < src.length()) {
            char ch = src.charAt(cursor);
            if (ch == '"') { cursor++; return sb.toString(); }
            if (ch == '\\') {
                cursor++;
                char esc = src.charAt(cursor);
                switch (esc) {
                    case 'n':  sb.append('\n'); break;
                    case 't':  sb.append('\t'); break;
                    case 'r':  sb.append('\r'); break;
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    default:   sb.append(esc);  break;
                }
            } else {
                sb.append(ch);
            }
            cursor++;
        }
        throw new RuntimeException("Unterminated string at position " + cursor);
    }

    // ── Number ────────────────────────────────────────────────────────────────

    private Number readNumber() {
        int start = cursor;
        while (cursor < src.length()) {
            char ch = src.charAt(cursor);
            if (Character.isDigit(ch) || ch == '-' || ch == '.' || ch == 'e' || ch == 'E' || ch == '+') {
                cursor++;
            } else break;
        }
        String numStr = src.substring(start, cursor);
        if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
            return Double.parseDouble(numStr);
        }
        long l = Long.parseLong(numStr);
        return (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) ? (int) l : l;
    }

    // ── Boolean ───────────────────────────────────────────────────────────────

    private Boolean readBoolean() {
        if (src.startsWith("true",  cursor)) { cursor += 4; return Boolean.TRUE; }
        if (src.startsWith("false", cursor)) { cursor += 5; return Boolean.FALSE; }
        throw new RuntimeException("Invalid boolean at " + cursor);
    }

    // ── Null ──────────────────────────────────────────────────────────────────

    private Object readNull() {
        if (src.startsWith("null", cursor)) { cursor += 4; return null; }
        throw new RuntimeException("Invalid null at " + cursor);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void skipSpaces() {
        while (cursor < src.length() && Character.isWhitespace(src.charAt(cursor))) cursor++;
    }

    private char peek() {
        return cursor < src.length() ? src.charAt(cursor) : '\0';
    }

    private void expect(char ch) {
        if (cursor >= src.length() || src.charAt(cursor) != ch) {
            throw new RuntimeException("Expected '" + ch + "' at " + cursor);
        }
        cursor++;
    }
}
