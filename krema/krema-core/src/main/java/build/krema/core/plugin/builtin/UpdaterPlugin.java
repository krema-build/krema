package build.krema.core.plugin.builtin;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import build.krema.core.KremaCommand;
import build.krema.core.event.EventEmitter;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.plugin.PluginContext;
import build.krema.core.updater.AutoUpdater;
import build.krema.core.updater.AutoUpdaterConfig;
import build.krema.core.updater.UpdateInfo;
import build.krema.core.util.Logger;

/**
 * Built-in updater plugin providing frontend API for the auto-updater.
 * Commands: updater:check, updater:download, updater:install, updater:installAndRestart.
 * Events: updater:update-available, updater:download-progress, updater:update-ready, updater:error.
 */
public class UpdaterPlugin implements KremaPlugin {

    private static final Logger LOG = new Logger("UpdaterPlugin");

    private AutoUpdater autoUpdater;
    private EventEmitter eventEmitter;
    private boolean configured;

    @Override
    public String getId() {
        return "krema.updater";
    }

    @Override
    public String getName() {
        return "Updater";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Auto-update support for Krema applications";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initialize(PluginContext context) {
        this.eventEmitter = context.getEventEmitter();

        Map<String, Object> config = context.getConfig();
        List<String> endpoints = (List<String>) config.get("endpoints");

        if (endpoints == null || endpoints.isEmpty()) {
            LOG.info("No updater endpoints configured — updater plugin inactive");
            this.configured = false;
            return;
        }

        String pubkey = (String) config.get("pubkey");
        int timeout = config.containsKey("timeout")
            ? ((Number) config.get("timeout")).intValue()
            : 30;

        AutoUpdaterConfig updaterConfig = AutoUpdaterConfig.builder()
            .endpoints(endpoints)
            .currentVersion(context.getAppVersion())
            .publicKey(pubkey)
            .timeout(Duration.ofSeconds(timeout))
            .build();

        this.autoUpdater = new AutoUpdater(updaterConfig);
        this.configured = true;

        // Wire callbacks to events
        autoUpdater.onUpdateAvailable(info -> eventEmitter.emit("updater:update-available", Map.of(
            "version", info.getVersion(),
            "notes", info.getReleaseNotes() != null ? info.getReleaseNotes() : "",
            "date", info.getReleaseDate() != null ? info.getReleaseDate() : "",
            "mandatory", info.isMandatory(),
            "size", info.getSize()
        )));

        autoUpdater.onDownloadProgress(progress ->
            eventEmitter.emit("updater:download-progress", Map.of("progress", progress)));

        autoUpdater.onUpdateReady(path ->
            eventEmitter.emit("updater:update-ready", Map.of("path", path.toString())));

        autoUpdater.onError(error ->
            eventEmitter.emit("updater:error", Map.of("message", error.getMessage())));

        LOG.info("Updater plugin initialized with %d endpoint(s)", endpoints.size());

        // Check on startup if configured
        boolean checkOnStartup = config.containsKey("checkOnStartup")
            ? (Boolean) config.get("checkOnStartup")
            : true;
        if (checkOnStartup) {
            autoUpdater.checkForUpdates().exceptionally(ex -> {
                LOG.debug("Startup update check failed: %s", ex.getMessage());
                return null;
            });
        }
    }

    @Override
    public List<Object> getCommandHandlers() {
        return List.of(new UpdaterCommands());
    }

    public class UpdaterCommands {

        @KremaCommand("updater:check")
        public CheckResult check() {
            if (!configured) {
                return new CheckResult(false, null, null, null, false, 0);
            }
            try {
                UpdateInfo info = autoUpdater.checkForUpdates().join();
                if (info == null) {
                    return new CheckResult(false, null, null, null, false, 0);
                }
                return new CheckResult(
                    true,
                    info.getVersion(),
                    info.getReleaseNotes(),
                    info.getReleaseDate(),
                    info.isMandatory(),
                    info.getSize()
                );
            } catch (Exception e) {
                throw new RuntimeException("Update check failed: " + e.getMessage(), e);
            }
        }

        @KremaCommand("updater:download")
        public DownloadResult download() {
            if (!configured) {
                throw new RuntimeException("Updater not configured");
            }
            try {
                // First check for the update
                UpdateInfo info = autoUpdater.checkForUpdates().join();
                if (info == null) {
                    return new DownloadResult(false, null);
                }
                Path path = autoUpdater.downloadUpdate(info).join();
                return new DownloadResult(true, path.toString());
            } catch (Exception e) {
                throw new RuntimeException("Download failed: " + e.getMessage(), e);
            }
        }

        @KremaCommand("updater:install")
        public boolean install() {
            if (!configured) {
                throw new RuntimeException("Updater not configured");
            }
            Path updatePath = autoUpdater.getLastDownloadedUpdate();
            if (updatePath == null) {
                throw new RuntimeException("No update has been downloaded yet");
            }
            try {
                autoUpdater.installUpdate(updatePath);
                return true;
            } catch (Exception e) {
                throw new RuntimeException("Installation failed: " + e.getMessage(), e);
            }
        }

        @KremaCommand("updater:installAndRestart")
        public boolean installAndRestart() {
            if (!configured) {
                throw new RuntimeException("Updater not configured");
            }
            Path updatePath = autoUpdater.getLastDownloadedUpdate();
            if (updatePath == null) {
                throw new RuntimeException("No update has been downloaded yet");
            }
            try {
                if (eventEmitter != null) {
                    eventEmitter.emit("updater:before-restart");
                }
                autoUpdater.installAndRestart(updatePath);
                return true;
            } catch (Exception e) {
                throw new RuntimeException("Install and restart failed: " + e.getMessage(), e);
            }
        }
    }

    public record CheckResult(
        boolean updateAvailable,
        String version,
        String notes,
        String date,
        boolean mandatory,
        long size
    ) {}

    public record DownloadResult(
        boolean downloaded,
        String path
    ) {}
}
