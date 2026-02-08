package build.krema.core.plugin.builtin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import build.krema.core.KremaCommand;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.plugin.PluginContext;
import build.krema.core.util.Logger;

/**
 * Built-in logging plugin.
 * Forwards frontend log calls to the backend logger.
 * File logging is handled by {@link Logger} via JSON Lines output.
 */
public class LogPlugin implements KremaPlugin {

    private PluginContext context;
    private Logger logger;

    @Override
    public String getId() {
        return "krema.log";
    }

    @Override
    public String getName() {
        return "Log";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Forwards frontend log calls to the backend logger and rotating log files";
    }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;
        this.logger = context.getLogger("Frontend");

        boolean fileLogging = context.getConfig("fileLogging", true);
        if (fileLogging) {
            try {
                Path logDir = resolveLogDir();
                long maxFileSize = context.getConfig("maxFileSize", 5L * 1024 * 1024);
                int maxFiles = context.getConfig("maxFiles", 5);
                Logger.enableFileLogging(logDir, maxFileSize, maxFiles);
            } catch (IOException e) {
                logger.warn("Failed to initialize file logging: %s", e.getMessage());
            }
        }
    }

    @Override
    public void shutdown() {
        Logger.disableFileLogging();
    }

    @Override
    public List<Object> getCommandHandlers() {
        return List.of(new LogCommands(this));
    }

    @Override
    public List<String> getRequiredPermissions() {
        return List.of("log:write");
    }

    private Path resolveLogDir() {
        Path appDataDir = context.getAppDataDir();
        return appDataDir.getParent() != null
            ? appDataDir.resolveSibling("Logs").resolve(context.getAppName())
            : appDataDir.resolve("logs");
    }

    private void log(String level, String message) {
        switch (level) {
            case "trace" -> logger.trace(message);
            case "debug" -> logger.debug(message);
            case "info" -> logger.info(message);
            case "warn" -> logger.warn(message);
            case "error" -> logger.error(message);
        }
    }

    public static class LogCommands {

        private final LogPlugin plugin;

        LogCommands(LogPlugin plugin) {
            this.plugin = plugin;
        }

        @KremaCommand("log:trace")
        public void trace(String message) {
            plugin.log("trace", message);
        }

        @KremaCommand("log:debug")
        public void debug(String message) {
            plugin.log("debug", message);
        }

        @KremaCommand("log:info")
        public void info(String message) {
            plugin.log("info", message);
        }

        @KremaCommand("log:warn")
        public void warn(String message) {
            plugin.log("warn", message);
        }

        @KremaCommand("log:error")
        public void error(String message) {
            plugin.log("error", message);
        }
    }
}
