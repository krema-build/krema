package build.krema.plugin.upload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import build.krema.core.CommandRegistry;
import build.krema.core.event.EventEmitter;
import build.krema.core.plugin.PluginContext;
import build.krema.core.util.Logger;
import build.krema.core.webview.WebViewEngine;
import build.krema.core.window.WindowManager;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Upload Plugin")
class UploadPluginTest {

    private UploadPlugin plugin;
    private UploadPlugin.UploadCommands commands;

    @BeforeEach
    void setUp() {
        plugin = new UploadPlugin();
        StubWebViewEngine stubEngine = new StubWebViewEngine();
        EventEmitter emitter = new EventEmitter(stubEngine);
        plugin.initialize(new StubPluginContext(emitter));
        commands = (UploadPlugin.UploadCommands) plugin.getCommandHandlers().get(0);
    }

    @Nested
    @DisplayName("Plugin metadata")
    class Metadata {

        @Test
        @DisplayName("returns correct ID")
        void returnsCorrectId() {
            assertEquals("krema.upload", plugin.getId());
        }

        @Test
        @DisplayName("returns correct name")
        void returnsCorrectName() {
            assertEquals("Upload", plugin.getName());
        }

        @Test
        @DisplayName("returns required permissions")
        void returnsRequiredPermissions() {
            assertEquals(List.of("upload:send"), plugin.getRequiredPermissions());
        }

        @Test
        @DisplayName("returns command handlers")
        void returnsCommandHandlers() {
            assertFalse(plugin.getCommandHandlers().isEmpty());
        }
    }

    @Nested
    @DisplayName("upload:upload")
    class Upload {

        @Test
        @DisplayName("throws for nonexistent file")
        void throwsForNonexistentFile(@TempDir Path tempDir) {
            String fakePath = tempDir.resolve("nonexistent.txt").toString();
            UploadPlugin.UploadRequest request = new UploadPlugin.UploadRequest(
                "http://localhost:1/upload",
                List.of(fakePath),
                "POST", null, null, 5, "test-upload"
            );

            assertThrows(IOException.class, () -> commands.upload(request));
        }

        @Test
        @DisplayName("throws for invalid URL")
        void throwsForInvalidUrl(@TempDir Path tempDir) throws IOException {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "hello");

            UploadPlugin.UploadRequest request = new UploadPlugin.UploadRequest(
                "not-a-valid-url",
                List.of(testFile.toString()),
                "POST", null, null, 5, "test-upload"
            );

            assertThrows(Exception.class, () -> commands.upload(request));
        }
    }

    @Nested
    @DisplayName("UploadRequest record")
    class RequestRecord {

        @Test
        @DisplayName("stores all fields correctly")
        void storesFields() {
            UploadPlugin.UploadRequest req = new UploadPlugin.UploadRequest(
                "http://example.com/upload",
                List.of("/path/to/file.txt"),
                "PUT",
                Map.of("Authorization", "Bearer token"),
                Map.of("field1", "value1"),
                60,
                "upload-123"
            );

            assertEquals("http://example.com/upload", req.url());
            assertEquals(List.of("/path/to/file.txt"), req.files());
            assertEquals("PUT", req.method());
            assertEquals("Bearer token", req.headers().get("Authorization"));
            assertEquals("value1", req.formFields().get("field1"));
            assertEquals(60, req.timeout());
            assertEquals("upload-123", req.id());
        }
    }

    @Nested
    @DisplayName("UploadResult record")
    class ResultRecord {

        @Test
        @DisplayName("stores all fields correctly")
        void storesFields() {
            UploadPlugin.UploadResult result = new UploadPlugin.UploadResult(
                "upload-123", 200, "{\"ok\":true}", Map.of("Content-Type", List.of("application/json")));

            assertEquals("upload-123", result.id());
            assertEquals(200, result.status());
            assertEquals("{\"ok\":true}", result.body());
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
