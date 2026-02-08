package build.krema.core.plugin;

import java.nio.file.Path;
import java.util.Map;

import build.krema.core.CommandRegistry;
import build.krema.core.event.EventEmitter;
import build.krema.core.util.Logger;
import build.krema.core.window.WindowManager;

/**
 * Context provided to plugins during initialization.
 * Gives plugins access to Krema APIs and configuration.
 */
public interface PluginContext {

    /**
     * Returns the window manager for multi-window support.
     */
    WindowManager getWindowManager();

    /**
     * Returns the event emitter for sending events to the frontend.
     */
    EventEmitter getEventEmitter();

    /**
     * Returns the command registry for registering additional commands.
     */
    CommandRegistry getCommandRegistry();

    /**
     * Returns a logger for the plugin.
     */
    Logger getLogger(String name);

    /**
     * Returns the plugin's data directory.
     * This is a dedicated directory where the plugin can store data.
     */
    Path getPluginDataDir();

    /**
     * Returns the application's data directory.
     */
    Path getAppDataDir();

    /**
     * Returns the plugin's configuration.
     * Configuration is loaded from the plugin section in krema.toml.
     */
    Map<String, Object> getConfig();

    /**
     * Returns a specific configuration value.
     */
    default <T> T getConfig(String key, T defaultValue) {
        @SuppressWarnings("unchecked")
        T value = (T) getConfig().get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Checks if a permission is granted to the plugin.
     */
    boolean hasPermission(String permission);

    /**
     * Returns the application name.
     */
    String getAppName();

    /**
     * Returns the application version.
     */
    String getAppVersion();
}
