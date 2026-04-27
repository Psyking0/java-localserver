package model;

import java.util.*;

/**
 * LocationBlock — one URI prefix rule inside a {@link VirtualHost}.
 *
 * <p>JSON keys:
 * <pre>
 * {
 *   "prefix":          "/",
 *   "methods":         ["GET", "POST", "DELETE"],
 *   "root_dir":        "public/html",
 *   "index_file":      "index.html",
 *   "enable_listing":  true,
 *   "cgi_suffix":      ".py",
 *   "redirect_to":     "/other"
 * }
 * </pre>
 * </p>
 */
public class LocationBlock {

    /** URI prefix matched by longest-prefix rule (e.g. {@code "/"}). */
    public final String        uriPrefix;

    /** HTTP methods allowed for this location, or {@code null} for any. */
    public final List<String>  allowedMethods;

    /** Filesystem root directory for file serving. */
    public final String        rootDir;

    /** Default file to serve when the URI resolves to a directory. */
    public final String        indexFile;

    /** Whether to generate an auto-index listing for directories. */
    public final Boolean       enableListing;

    /** If set, files ending with this suffix are executed as CGI scripts. */
    public final String        cgiSuffix;

    /** If set, requests to this location are redirected here (302). */
    public final String        redirectTarget;

    @SuppressWarnings("unchecked")
    public LocationBlock(Map<String, Object> raw) {
        this.uriPrefix      = (String) raw.get("prefix");
        this.rootDir        = (String) raw.get("root_dir");
        this.indexFile      = (String) raw.get("index_file");
        this.enableListing  = (Boolean) raw.get("enable_listing");
        this.cgiSuffix      = (String) raw.get("cgi_suffix");
        this.redirectTarget = (String) raw.get("redirect_to");

        List<Object> rawMethods = (List<Object>) raw.get("methods");
        if (rawMethods != null) {
            List<String> methods = new ArrayList<>();
            for (Object m : rawMethods) methods.add((String) m);
            this.allowedMethods = Collections.unmodifiableList(methods);
        } else {
            this.allowedMethods = null;
        }
    }
}
