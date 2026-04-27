package model;

import java.util.*;

/**
 * VirtualHost — one server block from {@code settings.json}.
 *
 * <p>A virtual host listens on one or more ports and matches requests
 * by {@code Host} header to {@link #hostname}. It owns a list of
 * {@link LocationBlock} entries that define how URIs are served.</p>
 *
 * JSON keys:
 * <pre>
 * {
 *   "bind":              "127.0.0.1",
 *   "ports":             [8080, 8081],
 *   "hostname":          "myserver.local",
 *   "max_body_bytes":    1048576,
 *   "error_pages":       { "404": "error_pages/404.html" },
 *   "locations":         [ ... ]
 * }
 * </pre>
 */
public class VirtualHost {

    /** IP address to bind (e.g. {@code "0.0.0.0"} or {@code "127.0.0.1"}). */
    public final String          bindAddress;

    /** Ports this virtual host accepts connections on. */
    public final List<Integer>   listenPorts;

    /** Value of the {@code Host} header used for virtual-host selection. */
    public final String          hostname;

    /** Maximum allowed request body size in bytes. Defaults to 1 MiB. */
    public final long            maxBodyBytes;

    /** Map from HTTP status code string to file path of the custom error page. */
    public final Map<String, String> errorPageMap;

    /** Ordered list of location blocks (URI prefix → handler config). */
    public final List<LocationBlock> locations;

    @SuppressWarnings("unchecked")
    public VirtualHost(Map<String, Object> raw) {
        this.bindAddress = (String) raw.getOrDefault("bind", "127.0.0.1");

        List<Object> rawPorts = (List<Object>) raw.get("ports");
        List<Integer> ports = new ArrayList<>();
        if (rawPorts != null) {
            for (Object p : rawPorts) ports.add(((Number) p).intValue());
        }
        this.listenPorts = Collections.unmodifiableList(ports);

        this.hostname     = (String) raw.get("hostname");
        Number limit      = (Number) raw.get("max_body_bytes");
        this.maxBodyBytes = limit != null ? limit.longValue() : 1_048_576L;

        Map<String, String> epMap = new LinkedHashMap<>();
        Map<String, Object> rawEp = (Map<String, Object>) raw.get("error_pages");
        if (rawEp != null) {
            for (Map.Entry<String, Object> e : rawEp.entrySet()) {
                epMap.put(e.getKey(), (String) e.getValue());
            }
        }
        this.errorPageMap = Collections.unmodifiableMap(epMap);

        List<LocationBlock> locs = new ArrayList<>();
        List<Object> rawLocs = (List<Object>) raw.get("locations");
        if (rawLocs != null) {
            for (Object item : rawLocs) {
                locs.add(new LocationBlock((Map<String, Object>) item));
            }
        }
        this.locations = Collections.unmodifiableList(locs);
    }
}
