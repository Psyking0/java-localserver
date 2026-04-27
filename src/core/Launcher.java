package core;

import model.VirtualHost;
import model.LocationBlock;

import java.io.File;
import java.util.List;

/**
 * Launcher — entry point for the WebServ HTTP/1.1 server.
 * Loads configuration, validates it, ensures required directories exist,
 * and starts the event-driven engine.
 */
public class Launcher {

    public static void main(String[] args) {
        String settingsPath = "settings.json";
        if (args.length > 0) {
            settingsPath = args[0];
        }

        File settingsFile = new File(settingsPath);
        if (!settingsFile.exists() || !settingsFile.canRead()) {
            System.err.println("[FATAL] Cannot open settings file: " + settingsPath);
            System.exit(1);
        }

        try {
            List<VirtualHost> hosts = SettingsLoader.load(settingsPath);
            if (hosts.isEmpty()) {
                System.err.println("[FATAL] No virtual hosts defined in: " + settingsPath);
                System.exit(1);
            }
            System.out.println("[INFO] Loaded " + hosts.size() + " virtual host(s).");

            // Ensure all root directories exist so GET on them never 404
            ensureDirectories(hosts);

            HttpEngine engine = new HttpEngine(hosts);
            engine.run();
        } catch (Exception ex) {
            System.err.println("[FATAL] Startup failed: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        }
    }

    /** Creates all root_dir directories declared in the config if they don't exist yet. */
    private static void ensureDirectories(List<VirtualHost> hosts) {
        for (VirtualHost vh : hosts) {
            for (LocationBlock loc : vh.locations) {
                if (loc.rootDir != null) {
                    File dir = new File(loc.rootDir);
                    if (!dir.exists()) {
                        if (dir.mkdirs()) {
                            System.out.println("[INFO] Created missing directory: " + loc.rootDir);
                        } else {
                            System.err.println("[WARN] Could not create directory: " + loc.rootDir);
                        }
                    }
                }
            }
        }
    }
}
