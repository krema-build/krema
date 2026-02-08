package build.krema.core.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import build.krema.core.event.EventEmitter;
import build.krema.core.event.KremaEvent;
import build.krema.core.util.LogContext;
import build.krema.core.util.Logger;
import build.krema.core.webview.WebViewEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ErrorHandler")
class ErrorHandlerTest {

    private StubWebViewEngine engine;
    private EventEmitter eventEmitter;
    private List<ErrorInfo> userErrors;
    private List<String> recentCommands;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        engine = new StubWebViewEngine();
        eventEmitter = new EventEmitter(engine);
        userErrors = new ArrayList<>();
        recentCommands = List.of("http:get", "store:set");
        Logger.setLogContext(LogContext.create("TestApp", "1.0.0", "darwin-aarch64"));
    }

    private ErrorHandler createHandler() {
        return new ErrorHandler(eventEmitter, tempDir, userErrors::add, () -> recentCommands);
    }

    @Nested
    @DisplayName("Java errors")
    class JavaErrors {

        @Test
        @DisplayName("handleJavaError logs, emits event, calls user handler, and writes crash report")
        void handleJavaErrorFullPipeline() {
            ErrorHandler handler = createHandler();
            RuntimeException error = new RuntimeException("test failure");

            handler.handleJavaError(Thread.currentThread(), error);

            // User handler received the error
            assertEquals(1, userErrors.size());
            ErrorInfo info = userErrors.get(0);
            assertEquals(ErrorInfo.Source.JAVA, info.source());
            assertTrue(info.message().contains("test failure"));
            assertNotNull(info.stackTrace());
            assertEquals(Thread.currentThread().getName(), info.threadName());
            assertNull(info.fileName());
            assertEquals(0, info.lineNumber());

            // Context is populated
            ErrorContext ctx = info.context();
            assertEquals("TestApp", ctx.appName());
            assertEquals("1.0.0", ctx.appVersion());
            assertEquals(List.of("http:get", "store:set"), ctx.recentCommands());

            // app:error event was emitted to frontend
            assertFalse(engine.evalCalls.isEmpty());
            assertTrue(engine.evalCalls.stream().anyMatch(js -> js.contains("app:error")));
        }

        @Test
        @DisplayName("writes crash report JSON file")
        void writesCrashReport() {
            ErrorHandler handler = createHandler();
            handler.handleJavaError(Thread.currentThread(), new RuntimeException("crash"));

            // Verify crash report file exists
            assertTrue(Files.exists(tempDir));
            String[] files = tempDir.toFile().list((dir, name) -> name.startsWith("crash-") && name.endsWith(".json"));
            assertNotNull(files);
            assertEquals(1, files.length);
        }

        @Test
        @DisplayName("uncaughtException delegates to handleJavaError")
        void uncaughtExceptionDelegates() {
            ErrorHandler handler = createHandler();
            handler.uncaughtException(Thread.currentThread(), new RuntimeException("uncaught"));

            assertEquals(1, userErrors.size());
            assertEquals(ErrorInfo.Source.JAVA, userErrors.get(0).source());
        }
    }

    @Nested
    @DisplayName("WebView errors")
    class WebViewErrors {

        @Test
        @DisplayName("handleWebViewError creates correct ErrorInfo")
        void handleWebViewError() {
            ErrorHandler handler = createHandler();

            handler.handleWebViewError("TypeError: null is not an object", "app.js", 42, "at onClick (app.js:42)");

            assertEquals(1, userErrors.size());
            ErrorInfo info = userErrors.get(0);
            assertEquals(ErrorInfo.Source.WEBVIEW, info.source());
            assertEquals("TypeError: null is not an object", info.message());
            assertEquals("at onClick (app.js:42)", info.stackTrace());
            assertNull(info.threadName());
            assertEquals("app.js", info.fileName());
            assertEquals(42, info.lineNumber());
        }
    }

    @Nested
    @DisplayName("install/uninstall")
    class InstallUninstall {

        @Test
        @DisplayName("install sets global handler, uninstall restores previous")
        void installAndUninstall() {
            Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
            ErrorHandler handler = createHandler();

            handler.install();
            assertSame(handler, Thread.getDefaultUncaughtExceptionHandler());

            handler.uninstall();
            assertSame(original, Thread.getDefaultUncaughtExceptionHandler());
        }
    }

    @Nested
    @DisplayName("event emission")
    class EventEmission {

        @Test
        @DisplayName("emits app:error to backend listeners")
        void emitsToBackendListeners() {
            ErrorHandler handler = createHandler();
            AtomicReference<KremaEvent> received = new AtomicReference<>();
            eventEmitter.on("app:error", received::set);

            handler.handleJavaError(Thread.currentThread(), new RuntimeException("boom"));

            assertNotNull(received.get());
            assertEquals("app:error", received.get().name());
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) received.get().payload();
            assertEquals("JAVA", payload.get("source"));
            assertTrue(((String) payload.get("message")).contains("boom"));
        }
    }

    @Nested
    @DisplayName("resilience")
    class Resilience {

        @Test
        @DisplayName("works without event emitter")
        void worksWithoutEventEmitter() {
            ErrorHandler handler = new ErrorHandler(null, tempDir, userErrors::add, () -> recentCommands);
            handler.handleJavaError(Thread.currentThread(), new RuntimeException("no emitter"));

            assertEquals(1, userErrors.size());
        }

        @Test
        @DisplayName("works without user handler")
        void worksWithoutUserHandler() {
            ErrorHandler handler = new ErrorHandler(eventEmitter, tempDir, null, () -> recentCommands);
            handler.handleJavaError(Thread.currentThread(), new RuntimeException("no user handler"));

            // Should not throw — just log and write crash report
            assertFalse(engine.evalCalls.isEmpty());
        }

        @Test
        @DisplayName("works without crash report dir")
        void worksWithoutCrashReportDir() {
            ErrorHandler handler = new ErrorHandler(eventEmitter, null, userErrors::add, () -> recentCommands);
            handler.handleJavaError(Thread.currentThread(), new RuntimeException("no crash dir"));

            assertEquals(1, userErrors.size());
        }

        @Test
        @DisplayName("works without recent commands supplier")
        void worksWithoutRecentCommandsSupplier() {
            ErrorHandler handler = new ErrorHandler(eventEmitter, tempDir, userErrors::add, null);
            handler.handleJavaError(Thread.currentThread(), new RuntimeException("no commands"));

            assertEquals(1, userErrors.size());
            assertEquals(List.of(), userErrors.get(0).context().recentCommands());
        }

        @Test
        @DisplayName("throwing user handler does not prevent crash report")
        void throwingUserHandlerDoesNotPreventCrashReport() {
            ErrorHandler handler = new ErrorHandler(eventEmitter, tempDir, info -> {
                throw new RuntimeException("user handler blew up");
            }, () -> recentCommands);

            handler.handleJavaError(Thread.currentThread(), new RuntimeException("original error"));

            // Crash report should still be written
            String[] files = tempDir.toFile().list((dir, name) -> name.startsWith("crash-"));
            assertNotNull(files);
            assertEquals(1, files.length);
        }
    }

    /**
     * Stub implementation of WebViewEngine that captures eval() and init() calls.
     */
    private static class StubWebViewEngine implements WebViewEngine {
        final List<String> evalCalls = new ArrayList<>();
        final List<String> initCalls = new ArrayList<>();

        @Override public void eval(String js) { evalCalls.add(js); }
        @Override public void init(String js) { initCalls.add(js); }
        @Override public void setTitle(String title) {}
        @Override public void setSize(int width, int height, SizeHint hint) {}
        @Override public void navigate(String url) {}
        @Override public void setHtml(String html) {}
        @Override public void bind(String name, BindCallback callback) {}
        @Override public void returnResult(String seq, boolean success, String result) {}
        @Override public void run() {}
        @Override public void terminate() {}
        @Override public boolean isRunning() { return false; }
        @Override public void close() {}
    }
}
