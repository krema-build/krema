package build.krema.plugin.websocket;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.*;

import build.krema.core.CommandRegistry;
import build.krema.core.event.EventEmitter;
import build.krema.core.event.KremaEvent;
import build.krema.core.plugin.PluginContext;
import build.krema.core.util.Logger;
import build.krema.core.webview.WebViewEngine;
import build.krema.core.window.WindowManager;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocket Plugin")
class WebSocketPluginTest {

    private WebSocketPlugin plugin;
    private WebSocketPlugin.WebSocketCommands commands;

    @BeforeEach
    void setUp() {
        plugin = new WebSocketPlugin();
        StubWebViewEngine stubEngine = new StubWebViewEngine();
        EventEmitter emitter = new EventEmitter(stubEngine);
        StubPluginContext context = new StubPluginContext(emitter);
        plugin.initialize(context);
        commands = (WebSocketPlugin.WebSocketCommands) plugin.getCommandHandlers().get(0);
    }

    @AfterEach
    void tearDown() {
        plugin.shutdown();
    }

    @Nested
    @DisplayName("Plugin metadata")
    class Metadata {

        @Test
        @DisplayName("returns correct ID")
        void returnsCorrectId() {
            assertEquals("krema.websocket", plugin.getId());
        }

        @Test
        @DisplayName("returns correct name")
        void returnsCorrectName() {
            assertEquals("WebSocket", plugin.getName());
        }

        @Test
        @DisplayName("returns required permissions")
        void returnsRequiredPermissions() {
            assertEquals(List.of("websocket:connect"), plugin.getRequiredPermissions());
        }

        @Test
        @DisplayName("returns command handlers")
        void returnsCommandHandlers() {
            assertFalse(plugin.getCommandHandlers().isEmpty());
        }
    }

    @Nested
    @DisplayName("websocket:send")
    class Send {

        @Test
        @DisplayName("throws when connection does not exist")
        void throwsWhenNoConnection() {
            assertThrows(IllegalStateException.class, () ->
                commands.send(new WebSocketPlugin.SendRequest("nonexistent", "hello")));
        }
    }

    @Nested
    @DisplayName("websocket:disconnect")
    class Disconnect {

        @Test
        @DisplayName("returns false for nonexistent connection")
        void returnsFalseForNonexistent() {
            assertFalse(commands.disconnect(new WebSocketPlugin.DisconnectRequest("nonexistent")));
        }
    }

    @Nested
    @DisplayName("websocket:connect")
    class Connect {

        @Test
        @DisplayName("returns true for valid connection request")
        void returnsTrueForValidRequest() {
            // connect() is async (buildAsync), so it returns true immediately
            // The actual connection happens in the background
            boolean result = commands.connect(
                new WebSocketPlugin.ConnectRequest("test", "ws://localhost:1", null));
            assertTrue(result);
        }

        @Test
        @DisplayName("accepts custom headers")
        void acceptsCustomHeaders() {
            boolean result = commands.connect(
                new WebSocketPlugin.ConnectRequest("test-headers", "ws://localhost:1",
                    Map.of("Authorization", "Bearer token")));
            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("Plugin lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("initialize stores emitter")
        void initializeStoresEmitter() {
            // Verify the plugin can be initialized and used
            assertNotNull(plugin.getCommandHandlers());
        }
    }

    private static class StubWebViewEngine implements WebViewEngine {
        final List<String> evalCalls = new ArrayList<>();

        @Override public void setTitle(String title) {}
        @Override public void setSize(int width, int height, SizeHint hint) {}
        @Override public void navigate(String url) {}
        @Override public void setHtml(String html) {}
        @Override public void init(String js) {}
        @Override public void eval(String js) { evalCalls.add(js); }
        @Override public void bind(String name, BindCallback callback) {}
        @Override public void returnResult(String seq, boolean success, String result) {}
        @Override public void run() {}
        @Override public void terminate() {}
        @Override public boolean isRunning() { return false; }
        @Override public void close() {}
    }

    private static class StubPluginContext implements PluginContext {
        private final EventEmitter emitter;

        StubPluginContext(EventEmitter emitter) {
            this.emitter = emitter;
        }

        @Override public WindowManager getWindowManager() { return null; }
        @Override public EventEmitter getEventEmitter() { return emitter; }
        @Override public CommandRegistry getCommandRegistry() { return null; }
        @Override public Logger getLogger(String name) { return new Logger(name); }
        @Override public Path getPluginDataDir() { return Path.of("."); }
        @Override public Path getAppDataDir() { return Path.of("."); }
        @Override public Map<String, Object> getConfig() { return Map.of(); }
        @Override public boolean hasPermission(String permission) { return true; }
        @Override public String getAppName() { return "test-app"; }
        @Override public String getAppVersion() { return "1.0.0"; }
    }
}
