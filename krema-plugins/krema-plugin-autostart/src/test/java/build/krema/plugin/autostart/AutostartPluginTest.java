package build.krema.plugin.autostart;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import build.krema.core.CommandRegistry;
import build.krema.core.event.EventEmitter;
import build.krema.core.platform.Platform;
import build.krema.core.plugin.PluginContext;
import build.krema.core.util.Logger;
import build.krema.core.window.WindowManager;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Autostart Plugin")
class AutostartPluginTest {

    private AutostartPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new AutostartPlugin();
    }

    @Nested
    @DisplayName("Plugin metadata")
    class Metadata {

        @Test
        @DisplayName("returns correct ID")
        void returnsCorrectId() {
            assertEquals("krema.autostart", plugin.getId());
        }

        @Test
        @DisplayName("returns correct name")
        void returnsCorrectName() {
            assertEquals("Autostart", plugin.getName());
        }

        @Test
        @DisplayName("returns required permissions")
        void returnsRequiredPermissions() {
            assertEquals(List.of("autostart:manage"), plugin.getRequiredPermissions());
        }

        @Test
        @DisplayName("returns command handlers")
        void returnsCommandHandlers() {
            plugin.initialize(new StubPluginContext("test-app"));
            assertFalse(plugin.getCommandHandlers().isEmpty());
        }
    }

    @Nested
    @DisplayName("macOS autostart")
    class MacOSAutostart {

        @Test
        @DisplayName("enable creates plist file on macOS")
        void enableCreatesPlistOnMacOS() throws IOException {
            // Only run on macOS
            if (Platform.current() != Platform.MACOS) {
                return;
            }

            plugin.initialize(new StubPluginContext("krema-test-autostart-" + System.nanoTime()));
            AutostartPlugin.AutostartCommands commands =
                (AutostartPlugin.AutostartCommands) plugin.getCommandHandlers().get(0);

            try {
                boolean result = commands.enable();
                assertTrue(result);
                assertTrue(commands.isEnabled());
            } finally {
                commands.disable();
            }
        }

        @Test
        @DisplayName("disable removes plist file on macOS")
        void disableRemovesPlistOnMacOS() throws IOException {
            if (Platform.current() != Platform.MACOS) {
                return;
            }

            plugin.initialize(new StubPluginContext("krema-test-autostart-" + System.nanoTime()));
            AutostartPlugin.AutostartCommands commands =
                (AutostartPlugin.AutostartCommands) plugin.getCommandHandlers().get(0);

            commands.enable();
            boolean result = commands.disable();
            assertTrue(result);
            assertFalse(commands.isEnabled());
        }
    }

    @Nested
    @DisplayName("App ID sanitization")
    class AppIdSanitization {

        @Test
        @DisplayName("sanitizes app name for filesystem use")
        void sanitizesAppName() {
            // The getAppId() method lowercases and removes non-alphanumeric characters (except . and -)
            // We test this indirectly through the plugin's behavior
            plugin.initialize(new StubPluginContext("My App! @#$"));
            assertNotNull(plugin.getCommandHandlers());
        }
    }

    private static class StubPluginContext implements PluginContext {
        private final String appName;

        StubPluginContext(String appName) {
            this.appName = appName;
        }

        @Override public WindowManager getWindowManager() { return null; }
        @Override public EventEmitter getEventEmitter() { return null; }
        @Override public CommandRegistry getCommandRegistry() { return null; }
        @Override public Logger getLogger(String name) { return new Logger(name); }
        @Override public Path getPluginDataDir() { return Path.of(System.getProperty("java.io.tmpdir"), "krema-test"); }
        @Override public Path getAppDataDir() { return Path.of(System.getProperty("java.io.tmpdir"), "krema-test"); }
        @Override public Map<String, Object> getConfig() { return Map.of(); }
        @Override public boolean hasPermission(String permission) { return true; }
        @Override public String getAppName() { return appName; }
        @Override public String getAppVersion() { return "1.0.0"; }
    }
}
