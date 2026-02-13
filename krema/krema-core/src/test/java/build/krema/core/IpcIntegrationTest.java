package build.krema.core;

import static org.junit.jupiter.api.Assertions.*;

import build.krema.core.ipc.IpcHandler;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.plugin.PluginContext;
import build.krema.core.plugin.PluginLoader;
import build.krema.core.event.EventEmitter;
import build.krema.core.util.Logger;
import build.krema.core.webview.WebViewEngine;
import build.krema.core.window.WindowManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration tests that exercise the full IPC pipeline:
 * PluginLoader → CommandRegistry → IpcHandler → WebViewEngine (stub).
 *
 * <p>Uses a {@link CapturingWebViewEngine} that records {@code bind()} callbacks
 * and {@code returnResult()} calls, so no native library or GUI is required.
 */
@DisplayName("IPC Integration")
class IpcIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CapturingWebViewEngine engine;
    private KremaWindow window;
    private CommandRegistry registry;
    private IpcHandler ipcHandler;

    @BeforeEach
    void setUp() {
        engine = new CapturingWebViewEngine();
        window = new KremaWindow(engine);

        registry = new CommandRegistry();

        PluginLoader pluginLoader = new PluginLoader();
        pluginLoader.loadBuiltinPlugins();
        pluginLoader.initializeAll(new StubPluginContext());

        List<Object> handlers = pluginLoader.collectCommandHandlers();
        registry.register(handlers.toArray());

        ipcHandler = new IpcHandler(window);
        ipcHandler.setCommandHandler(request -> {
            try {
                return registry.invoke(request);
            } catch (CommandRegistry.CommandException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
        ipcHandler.initialize();
    }

    // -- helpers ----------------------------------------------------------

    private CapturedResult invokeCommand(String command, Map<String, Object> args)
            throws JsonProcessingException {

        ObjectNode request = MAPPER.createObjectNode();
        request.put("cmd", command);
        if (args != null) {
            request.set("args", MAPPER.valueToTree(args));
        }

        // Wire format: JSON array with one stringified request
        String requestJson = MAPPER.writeValueAsString(request);
        String argsJson = MAPPER.writeValueAsString(List.of(requestJson));

        WebViewEngine.BindCallback callback = engine.bindings.get("__krema_invoke");
        assertNotNull(callback, "__krema_invoke binding must exist");

        String seq = String.valueOf(engine.seqCounter.getAndIncrement());
        int before = engine.results.size();
        callback.invoke(seq, argsJson);

        assertTrue(engine.results.size() > before,
                "Expected a result for command: " + command);
        return engine.results.getLast();
    }

    private CapturedResult invokeCommand(String command) throws JsonProcessingException {
        return invokeCommand(command, null);
    }

    // -- Pipeline wiring --------------------------------------------------

    @Nested
    @DisplayName("Pipeline wiring")
    class PipelineWiring {

        @Test
        @DisplayName("__krema_invoke binding exists after initialize()")
        void invokeBindingExists() {
            assertTrue(engine.bindings.containsKey("__krema_invoke"));
        }

        @Test
        @DisplayName("__krema_report_error binding exists after initialize()")
        void errorBindingExists() {
            assertTrue(engine.bindings.containsKey("__krema_report_error"));
        }

        @Test
        @DisplayName("bridge JS scripts are injected")
        void bridgeJsInjected() {
            assertTrue(engine.initCalls.size() >= 3,
                    "Expected at least 3 init calls (bridge, dragdrop, errors)");
        }
    }

    // -- Builtin plugin discovery -----------------------------------------

    @Nested
    @DisplayName("Builtin plugin discovery")
    class BuiltinPluginDiscovery {

        @Test
        @DisplayName("fs plugin commands are registered")
        void fsCommandsRegistered() {
            assertAll(
                () -> assertTrue(registry.hasCommand("fs:exists")),
                () -> assertTrue(registry.hasCommand("fs:readTextFile")),
                () -> assertTrue(registry.hasCommand("fs:writeTextFile")),
                () -> assertTrue(registry.hasCommand("fs:stat")),
                () -> assertTrue(registry.hasCommand("fs:readDir")),
                () -> assertTrue(registry.hasCommand("fs:isFile")),
                () -> assertTrue(registry.hasCommand("fs:isDirectory")),
                () -> assertTrue(registry.hasCommand("fs:createDir")),
                () -> assertTrue(registry.hasCommand("fs:remove")),
                () -> assertTrue(registry.hasCommand("fs:rename")),
                () -> assertTrue(registry.hasCommand("fs:copy"))
            );
        }

        @Test
        @DisplayName("log plugin commands are registered")
        void logCommandsRegistered() {
            assertAll(
                () -> assertTrue(registry.hasCommand("log:trace")),
                () -> assertTrue(registry.hasCommand("log:debug")),
                () -> assertTrue(registry.hasCommand("log:info")),
                () -> assertTrue(registry.hasCommand("log:warn")),
                () -> assertTrue(registry.hasCommand("log:error"))
            );
        }
    }

    // -- Fs plugin round-trip ---------------------------------------------

    @Nested
    @DisplayName("Fs plugin round-trip")
    class FsRoundTrip {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("fs:exists returns true for existing file")
        void existsTrue() throws Exception {
            Path file = tempDir.resolve("test.txt");
            Files.writeString(file, "hello");

            CapturedResult result = invokeCommand("fs:exists",
                    Map.of("path", file.toString()));

            assertTrue(result.success());
            assertEquals("true", result.json());
        }

        @Test
        @DisplayName("fs:exists returns false for nonexistent path")
        void existsFalse() throws Exception {
            CapturedResult result = invokeCommand("fs:exists",
                    Map.of("path", tempDir.resolve("nope").toString()));

            assertTrue(result.success());
            assertEquals("false", result.json());
        }

        @Test
        @DisplayName("fs:writeTextFile + fs:readTextFile round-trip")
        void writeReadRoundTrip() throws Exception {
            Path file = tempDir.resolve("roundtrip.txt");
            String content = "Hello, Krema!";

            CapturedResult writeResult = invokeCommand("fs:writeTextFile",
                    Map.of("path", file.toString(), "content", content));
            assertTrue(writeResult.success());
            assertEquals("true", writeResult.json());

            CapturedResult readResult = invokeCommand("fs:readTextFile",
                    Map.of("path", file.toString()));
            assertTrue(readResult.success());
            assertEquals(MAPPER.writeValueAsString(content), readResult.json());
        }

        @Test
        @DisplayName("fs:stat returns FileInfo structure")
        void statReturnsFileInfo() throws Exception {
            Path file = tempDir.resolve("stat-test.txt");
            Files.writeString(file, "test content");

            CapturedResult result = invokeCommand("fs:stat",
                    Map.of("path", file.toString()));
            assertTrue(result.success());

            JsonNode info = MAPPER.readTree(result.json());
            assertEquals("stat-test.txt", info.get("name").asText());
            assertEquals(file.toString(), info.get("path").asText());
            assertFalse(info.get("isDirectory").asBoolean());
            assertTrue(info.get("size").asLong() > 0);
            assertTrue(info.get("modifiedTime").asLong() > 0);
        }

        @Test
        @DisplayName("fs:readDir lists files in directory")
        void readDirListsFiles() throws Exception {
            Files.writeString(tempDir.resolve("a.txt"), "a");
            Files.writeString(tempDir.resolve("b.txt"), "b");

            CapturedResult result = invokeCommand("fs:readDir",
                    Map.of("path", tempDir.toString()));
            assertTrue(result.success());

            JsonNode entries = MAPPER.readTree(result.json());
            assertTrue(entries.isArray());
            assertEquals(2, entries.size());

            Set<String> names = new HashSet<>();
            entries.forEach(e -> names.add(e.get("name").asText()));
            assertEquals(Set.of("a.txt", "b.txt"), names);
        }

        @Test
        @DisplayName("fs:isDirectory returns true for directory")
        void isDirectoryTrue() throws Exception {
            CapturedResult result = invokeCommand("fs:isDirectory",
                    Map.of("path", tempDir.toString()));
            assertTrue(result.success());
            assertEquals("true", result.json());
        }

        @Test
        @DisplayName("fs:createDir + fs:exists round-trip")
        void createDirRoundTrip() throws Exception {
            Path dir = tempDir.resolve("newdir");

            CapturedResult createResult = invokeCommand("fs:createDir",
                    Map.of("path", dir.toString()));
            assertTrue(createResult.success());

            CapturedResult existsResult = invokeCommand("fs:exists",
                    Map.of("path", dir.toString()));
            assertTrue(existsResult.success());
            assertEquals("true", existsResult.json());
        }
    }

    // -- Log plugin -------------------------------------------------------

    @Nested
    @DisplayName("Log plugin")
    class LogPluginTests {

        @Test
        @DisplayName("log:info succeeds and returns null")
        void logInfoSucceeds() throws Exception {
            CapturedResult result = invokeCommand("log:info",
                    Map.of("message", "test message"));
            assertTrue(result.success());
            assertEquals("null", result.json());
        }

        @Test
        @DisplayName("log:error succeeds")
        void logErrorSucceeds() throws Exception {
            CapturedResult result = invokeCommand("log:error",
                    Map.of("message", "error message"));
            assertTrue(result.success());
        }
    }

    // -- User commands ----------------------------------------------------

    @Nested
    @DisplayName("User commands")
    class UserCommandTests {

        @Test
        @DisplayName("custom command registered and invokable through IPC")
        void customCommand() throws Exception {
            registry.register(new CustomCommands());

            CapturedResult result = invokeCommand("custom:greet",
                    Map.of("name", "World"));
            assertTrue(result.success());
            assertEquals(MAPPER.writeValueAsString("Hello, World!"), result.json());
        }
    }

    // -- Error handling ---------------------------------------------------

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("unknown command returns error response")
        void unknownCommand() throws Exception {
            CapturedResult result = invokeCommand("nonexistent:command");
            assertFalse(result.success());

            JsonNode error = MAPPER.readTree(result.json());
            assertTrue(error.get("message").asText().contains("Unknown command"));
        }

        @Test
        @DisplayName("error response uses 'message' key, not 'error'")
        void errorResponseFormat() throws Exception {
            CapturedResult result = invokeCommand("nonexistent:command");
            assertFalse(result.success());

            JsonNode error = MAPPER.readTree(result.json());
            assertTrue(error.has("message"),
                    "Error response must contain 'message' key");
            assertFalse(error.has("error"),
                    "Error response must not contain 'error' key");
        }

        @Test
        @DisplayName("exception message propagates to error response")
        void exceptionMessagePropagates() throws Exception {
            registry.register(new FailingCommands());

            CapturedResult result = invokeCommand("fail:always");
            assertFalse(result.success());

            JsonNode error = MAPPER.readTree(result.json());
            assertTrue(error.get("message").asText().contains("Something went wrong"));
        }

        @Test
        @DisplayName("malformed JSON returns error, not crash")
        void malformedJsonReturnsError() {
            WebViewEngine.BindCallback callback = engine.bindings.get("__krema_invoke");
            assertNotNull(callback);

            int before = engine.results.size();
            callback.invoke("99", "not valid json {{{");

            assertTrue(engine.results.size() > before);
            CapturedResult result = engine.results.getLast();
            assertEquals("99", result.seq());
            assertFalse(result.success());
        }

        @Test
        @DisplayName("empty args array returns error")
        void emptyArgsArray() {
            WebViewEngine.BindCallback callback = engine.bindings.get("__krema_invoke");
            int before = engine.results.size();
            callback.invoke("100", "[]");

            assertTrue(engine.results.size() > before);
            assertFalse(engine.results.getLast().success());
        }

        @Test
        @DisplayName("missing cmd field returns error")
        void missingCmdField() throws Exception {
            String argsJson = MAPPER.writeValueAsString(
                    List.of(MAPPER.writeValueAsString(Map.of("args", Map.of()))));

            WebViewEngine.BindCallback callback = engine.bindings.get("__krema_invoke");
            int before = engine.results.size();
            callback.invoke("101", argsJson);

            assertTrue(engine.results.size() > before);
            assertFalse(engine.results.getLast().success());
        }
    }

    // -- Argument handling ------------------------------------------------

    @Nested
    @DisplayName("Argument handling")
    class ArgumentHandling {

        @Test
        @DisplayName("command with no parameters is invokable")
        void noArgCommand() throws Exception {
            registry.register(new NoArgCommands());

            CapturedResult result = invokeCommand("test:noargs");
            assertTrue(result.success());
            assertEquals(MAPPER.writeValueAsString("ok"), result.json());
        }

        @Test
        @DisplayName("command with multiple params receives all values")
        void multiParamCommand() throws Exception {
            registry.register(new MultiParamCommands());

            CapturedResult result = invokeCommand("test:multi",
                    Map.of("a", "hello", "b", 42));
            assertTrue(result.success());
            assertEquals(MAPPER.writeValueAsString("hello-42"), result.json());
        }

        @Test
        @DisplayName("command with missing optional args gets defaults")
        void missingOptionalArgs() throws Exception {
            registry.register(new DefaultArgCommands());

            CapturedResult result = invokeCommand("test:defaults", Map.of());
            assertTrue(result.success());
            assertEquals(MAPPER.writeValueAsString("null-0-false"), result.json());
        }
    }

    // -- Result serialization ---------------------------------------------

    @Nested
    @DisplayName("Result serialization")
    class ResultSerialization {

        @Test
        @DisplayName("boolean result serialized correctly")
        void booleanResult() throws Exception {
            CapturedResult result = invokeCommand("fs:exists",
                    Map.of("path", System.getProperty("java.home")));
            assertTrue(result.success());
            assertEquals("true", result.json());
        }

        @Test
        @DisplayName("POJO result serialized as JSON object")
        void pojoResult() throws Exception {
            registry.register(new PojoResultCommands());

            CapturedResult result = invokeCommand("test:pojo");
            assertTrue(result.success());

            JsonNode node = MAPPER.readTree(result.json());
            assertEquals("Alice", node.get("name").asText());
            assertEquals(30, node.get("age").asInt());
        }

        @Test
        @DisplayName("void command returns null")
        void voidCommandReturnsNull() throws Exception {
            CapturedResult result = invokeCommand("log:info",
                    Map.of("message", "test"));
            assertTrue(result.success());
            assertEquals("null", result.json());
        }

        @Test
        @DisplayName("null result serialized as null")
        void nullResult() throws Exception {
            registry.register(new NullResultCommands());

            CapturedResult result = invokeCommand("test:null");
            assertTrue(result.success());
            assertEquals("null", result.json());
        }
    }

    // -- test command handler classes -------------------------------------

    public static class CustomCommands {
        @KremaCommand("custom:greet")
        public String greet(String name) {
            return "Hello, " + name + "!";
        }
    }

    public static class FailingCommands {
        @KremaCommand("fail:always")
        public String fail() {
            throw new RuntimeException("Something went wrong");
        }
    }

    public static class NoArgCommands {
        @KremaCommand("test:noargs")
        public String noargs() {
            return "ok";
        }
    }

    public static class MultiParamCommands {
        @KremaCommand("test:multi")
        public String multi(String a, int b) {
            return a + "-" + b;
        }
    }

    public static class DefaultArgCommands {
        @KremaCommand("test:defaults")
        public String defaults(String name, int count, boolean flag) {
            return name + "-" + count + "-" + flag;
        }
    }

    public static class PojoResultCommands {
        @KremaCommand("test:pojo")
        public PersonResult pojo() {
            return new PersonResult("Alice", 30);
        }
    }

    public record PersonResult(String name, int age) {}

    public static class NullResultCommands {
        @KremaCommand("test:null")
        public String nullResult() {
            return null;
        }
    }

    // -- stubs ------------------------------------------------------------

    record CapturedResult(String seq, boolean success, String json) {}

    static class CapturingWebViewEngine implements WebViewEngine {
        final Map<String, BindCallback> bindings = new LinkedHashMap<>();
        final List<CapturedResult> results = new ArrayList<>();
        final List<String> initCalls = new ArrayList<>();
        final List<String> evalCalls = new ArrayList<>();
        final AtomicInteger seqCounter = new AtomicInteger(0);

        @Override
        public void bind(String name, BindCallback callback) {
            bindings.put(name, callback);
        }

        @Override
        public void returnResult(String seq, boolean success, String result) {
            results.add(new CapturedResult(seq, success, result));
        }

        @Override public void init(String js) { initCalls.add(js); }
        @Override public void eval(String js) { evalCalls.add(js); }
        @Override public void setTitle(String title) {}
        @Override public void setSize(int w, int h, SizeHint hint) {}
        @Override public void navigate(String url) {}
        @Override public void setHtml(String html) {}
        @Override public void run() {}
        @Override public void terminate() {}
        @Override public boolean isRunning() { return false; }
        @Override public void close() {}
    }

    static class StubPluginContext implements PluginContext {
        @Override public WindowManager getWindowManager() { return null; }
        @Override public EventEmitter getEventEmitter() { return null; }
        @Override public CommandRegistry getCommandRegistry() { return null; }
        @Override public Logger getLogger(String name) { return new Logger(name); }
        @Override public Path getPluginDataDir() { return Path.of(System.getProperty("java.io.tmpdir")); }
        @Override public Path getAppDataDir() { return Path.of(System.getProperty("java.io.tmpdir")); }
        @Override public Map<String, Object> getConfig() { return Map.of("fileLogging", false); }
        @Override public boolean hasPermission(String permission) { return true; }
        @Override public String getAppName() { return "test-app"; }
        @Override public String getAppVersion() { return "1.0.0"; }
    }
}
