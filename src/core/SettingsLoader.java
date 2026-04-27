package core;

import model.VirtualHost;
import support.ConfigParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SettingsLoader — reads {@code settings.json} and produces a list of
 * {@link VirtualHost} objects that describe every server block.
 */
public class SettingsLoader {

    @SuppressWarnings("unchecked")
    public static List<VirtualHost> load(String filePath) throws IOException {
        Object parsed = ConfigParser.fromFile(filePath);
        List<VirtualHost> hosts = new ArrayList<>();

        if (parsed instanceof List) {
            for (Object item : (List<Object>) parsed) {
                hosts.add(new VirtualHost((Map<String, Object>) item));
            }
        } else if (parsed instanceof Map) {
            // Allow a single object at the root level
            hosts.add(new VirtualHost((Map<String, Object>) parsed));
        }

        return hosts;
    }
}
