package build.krema.core.plugin.builtin;

import java.awt.Desktop;
import java.util.List;
import java.util.Map;

import build.krema.core.KremaCommand;
import build.krema.core.event.EventEmitter;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.plugin.PluginContext;

/**
 * Built-in deep link plugin.
 * Handles custom URL scheme events at runtime.
 */
public class DeepLinkPlugin implements KremaPlugin {

    private EventEmitter emitter;
    private volatile String currentUrl;

    @Override
    public String getId() {
        return "krema.deep-link";
    }

    @Override
    public String getName() {
        return "Deep Link";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Handles custom URL scheme events at runtime";
    }

    @Override
    public void initialize(PluginContext context) {
        this.emitter = context.getEventEmitter();
        captureInitialUrl();
        registerUriHandler();
    }

    @Override
    public List<Object> getCommandHandlers() {
        return List.of(new DeepLinkCommands(this));
    }

    @Override
    public List<String> getRequiredPermissions() {
        return List.of("deep-link:read");
    }

    private void captureInitialUrl() {
        String[] args = System.getProperty("sun.java.command", "").split(" ");
        for (String arg : args) {
            if (arg.contains("://")) {
                this.currentUrl = arg;
                break;
            }
        }
    }

    private void registerUriHandler() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.APP_OPEN_URI)) {
                    desktop.setOpenURIHandler(event -> {
                        String url = event.getURI().toString();
                        this.currentUrl = url;
                        if (emitter != null) {
                            emitter.emit("deep-link:received", Map.of("url", url));
                        }
                    });
                }
            }
        } catch (UnsupportedOperationException | UnsatisfiedLinkError e) {
            // AWT Desktop unavailable (e.g. native image) — URI handler not registered
        }
    }

    public static class DeepLinkCommands {

        private final DeepLinkPlugin plugin;

        DeepLinkCommands(DeepLinkPlugin plugin) {
            this.plugin = plugin;
        }

        @KremaCommand("deep-link:getCurrent")
        public Map<String, String> getCurrent() {
            String url = plugin.currentUrl;
            if (url != null) {
                return Map.of("url", url);
            }
            return Map.of();
        }
    }
}
