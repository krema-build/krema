package build.krema.plugin.autostart;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import build.krema.core.KremaCommand;
import build.krema.core.platform.Platform;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.plugin.PluginContext;

/**
 * Built-in autostart plugin.
 * Manages launch-at-login registration across platforms.
 */
public class AutostartPlugin implements KremaPlugin {

    private PluginContext context;

    @Override
    public String getId() {
        return "krema.autostart";
    }

    @Override
    public String getName() {
        return "Autostart";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Manages launch-at-login registration across platforms";
    }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;
    }

    @Override
    public List<Object> getCommandHandlers() {
        return List.of(new AutostartCommands(this));
    }

    @Override
    public List<String> getRequiredPermissions() {
        return List.of("autostart:manage");
    }

    private String resolveExecutablePath() {
        // 1. Bundled app (jpackage)
        String jpackagePath = System.getProperty("jpackage.app-path");
        if (jpackagePath != null) {
            return jpackagePath;
        }

        // 2. Config override
        String configPath = context.getConfig("executablePath", null);
        if (configPath != null) {
            return configPath;
        }

        // 3. Fallback to current process
        return ProcessHandle.current().info().command().orElse("java");
    }

    private String getAppId() {
        return context.getAppName().toLowerCase().replaceAll("[^a-z0-9.-]", "");
    }

    private Path getLaunchAgentPath() {
        return Path.of(System.getProperty("user.home"), "Library", "LaunchAgents", getAppId() + ".plist");
    }

    private Path getDesktopEntryPath() {
        return Path.of(System.getProperty("user.home"), ".config", "autostart", getAppId() + ".desktop");
    }

    public static class AutostartCommands {

        private final AutostartPlugin plugin;

        AutostartCommands(AutostartPlugin plugin) {
            this.plugin = plugin;
        }

        @KremaCommand("autostart:enable")
        public boolean enable() throws IOException {
            String execPath = plugin.resolveExecutablePath();
            Platform platform = Platform.current();

            return switch (platform) {
                case MACOS -> enableMacOS(execPath);
                case WINDOWS -> enableWindows(execPath);
                case LINUX -> enableLinux(execPath);
                default -> throw new UnsupportedOperationException(
                    "Autostart not supported on " + platform.getDisplayName());
            };
        }

        @KremaCommand("autostart:disable")
        public boolean disable() throws IOException {
            Platform platform = Platform.current();

            return switch (platform) {
                case MACOS -> disableMacOS();
                case WINDOWS -> disableWindows();
                case LINUX -> disableLinux();
                default -> throw new UnsupportedOperationException(
                    "Autostart not supported on " + platform.getDisplayName());
            };
        }

        @KremaCommand("autostart:isEnabled")
        public boolean isEnabled() throws IOException {
            Platform platform = Platform.current();

            return switch (platform) {
                case MACOS -> Files.exists(plugin.getLaunchAgentPath());
                case WINDOWS -> isEnabledWindows();
                case LINUX -> Files.exists(plugin.getDesktopEntryPath());
                default -> false;
            };
        }

        private boolean enableMacOS(String execPath) throws IOException {
            String appId = plugin.getAppId();
            String plist = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>Label</key>
                    <string>%s</string>
                    <key>ProgramArguments</key>
                    <array>
                        <string>%s</string>
                    </array>
                    <key>RunAtLoad</key>
                    <true/>
                </dict>
                </plist>
                """.formatted(appId, execPath);

            Path path = plugin.getLaunchAgentPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, plist, StandardCharsets.UTF_8);
            return true;
        }

        private boolean disableMacOS() throws IOException {
            return Files.deleteIfExists(plugin.getLaunchAgentPath());
        }

        private boolean enableWindows(String execPath) throws IOException {
            String appName = plugin.context.getAppName();
            Process process = new ProcessBuilder(
                "reg", "add",
                "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v", appName, "/t", "REG_SZ", "/d", execPath, "/f"
            ).start();
            try {
                return process.waitFor() == 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private boolean disableWindows() throws IOException {
            String appName = plugin.context.getAppName();
            Process process = new ProcessBuilder(
                "reg", "delete",
                "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v", appName, "/f"
            ).start();
            try {
                return process.waitFor() == 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private boolean isEnabledWindows() throws IOException {
            String appName = plugin.context.getAppName();
            Process process = new ProcessBuilder(
                "reg", "query",
                "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v", appName
            ).start();
            try {
                return process.waitFor() == 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private boolean enableLinux(String execPath) throws IOException {
            String appId = plugin.getAppId();
            String appName = plugin.context.getAppName();
            String desktop = """
                [Desktop Entry]
                Type=Application
                Name=%s
                Exec=%s
                X-GNOME-Autostart-enabled=true
                """.formatted(appName, execPath);

            Path path = plugin.getDesktopEntryPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, desktop, StandardCharsets.UTF_8);
            return true;
        }

        private boolean disableLinux() throws IOException {
            return Files.deleteIfExists(plugin.getDesktopEntryPath());
        }
    }
}
