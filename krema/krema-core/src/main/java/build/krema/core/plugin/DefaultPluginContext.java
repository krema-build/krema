package build.krema.core.plugin;

import java.nio.file.Path;
import java.util.Map;

import build.krema.core.CommandRegistry;
import build.krema.core.event.EventEmitter;
import build.krema.core.util.Logger;
import build.krema.core.window.WindowManager;

/**
 * Default implementation of {@link PluginContext}.
 * Provides plugins with access to Krema APIs and configuration.
 */
public class DefaultPluginContext implements PluginContext {

    private final WindowManager windowManager;
    private final EventEmitter eventEmitter;
    private final CommandRegistry commandRegistry;
    private final Path appDataDir;
    private final String appName;
    private final String appVersion;
    private final Map<String, Object> config;

    public DefaultPluginContext(WindowManager windowManager, EventEmitter eventEmitter,
                                CommandRegistry commandRegistry, Path appDataDir,
                                String appName, String appVersion,
                                Map<String, Object> config) {
        this.windowManager = windowManager;
        this.eventEmitter = eventEmitter;
        this.commandRegistry = commandRegistry;
        this.appDataDir = appDataDir;
        this.appName = appName;
        this.appVersion = appVersion;
        this.config = config;
    }

    @Override
    public WindowManager getWindowManager() {
        return windowManager;
    }

    @Override
    public EventEmitter getEventEmitter() {
        return eventEmitter;
    }

    @Override
    public CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    @Override
    public Logger getLogger(String name) {
        return new Logger("plugin:" + name);
    }

    @Override
    public Path getPluginDataDir() {
        return appDataDir.resolve("plugins");
    }

    @Override
    public Path getAppDataDir() {
        return appDataDir;
    }

    @Override
    public Map<String, Object> getConfig() {
        return config;
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }

    @Override
    public String getAppName() {
        return appName;
    }

    @Override
    public String getAppVersion() {
        return appVersion;
    }
}
