package support;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionStore — in-memory, thread-safe session registry.
 *
 * <p>Sessions are identified by a random UUID. Each session holds an arbitrary
 * key-value map that persists for the lifetime of the server process.</p>
 *
 * <p>Expired sessions (older than {@link #SESSION_TTL_MS}) are pruned lazily
 * on {@link #newSession()} calls to prevent unbounded memory growth.</p>
 */
public final class SessionStore {

    /** Time-to-live for idle sessions: 30 minutes. */
    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;

    private static final Map<String, Entry> store = new ConcurrentHashMap<>();

    private SessionStore() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** Creates a new empty session and returns its ID. */
    public static String newSession() {
        evictStale();
        String id = UUID.randomUUID().toString();
        store.put(id, new Entry());
        return id;
    }

    /**
     * Returns the data map for {@code sessionId}, or {@code null} if the
     * session does not exist or has expired.
     */
    public static Map<String, Object> fetch(String sessionId) {
        Entry entry = store.get(sessionId);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.createdAt > SESSION_TTL_MS) {
            store.remove(sessionId);
            return null;
        }
        return entry.data;
    }

    /** Explicitly removes a session (e.g. on logout). */
    public static void invalidate(String sessionId) {
        store.remove(sessionId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private static void evictStale() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now - e.getValue().createdAt > SESSION_TTL_MS);
    }

    private static final class Entry {
        final long                createdAt = System.currentTimeMillis();
        final Map<String, Object> data      = new HashMap<>();
    }
}
