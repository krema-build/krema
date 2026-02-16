package build.krema.core.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import build.krema.core.util.LogContext;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ErrorInfo and ErrorContext")
class ErrorInfoTest {

    @Nested
    @DisplayName("ErrorInfo.fromJava")
    class FromJava {

        @Test
        @DisplayName("captures exception class name and message")
        void capturesExceptionMessage() {
            RuntimeException ex = new RuntimeException("something broke");
            ErrorInfo info = ErrorInfo.fromJava(Thread.currentThread(), ex, null);

            assertEquals(ErrorInfo.Source.JAVA, info.source());
            assertTrue(info.message().contains("RuntimeException"));
            assertTrue(info.message().contains("something broke"));
        }

        @Test
        @DisplayName("captures thread name")
        void capturesThreadName() {
            ErrorInfo info = ErrorInfo.fromJava(
                Thread.currentThread(), new RuntimeException("x"), null);

            assertEquals(Thread.currentThread().getName(), info.threadName());
        }

        @Test
        @DisplayName("captures stack trace as string")
        void capturesStackTrace() {
            RuntimeException ex = new RuntimeException("stack test");
            ErrorInfo info = ErrorInfo.fromJava(Thread.currentThread(), ex, null);

            assertNotNull(info.stackTrace());
            assertTrue(info.stackTrace().contains("stack test"));
            assertTrue(info.stackTrace().contains("ErrorInfoTest"));
        }

        @Test
        @DisplayName("fileName is null for Java errors")
        void fileNameIsNull() {
            ErrorInfo info = ErrorInfo.fromJava(
                Thread.currentThread(), new RuntimeException("x"), null);

            assertNull(info.fileName());
            assertEquals(0, info.lineNumber());
        }

        @Test
        @DisplayName("attaches provided context")
        void attachesContext() {
            ErrorContext ctx = new ErrorContext("app", "1.0", "macOS", "sess1", List.of("cmd1"));
            ErrorInfo info = ErrorInfo.fromJava(
                Thread.currentThread(), new RuntimeException("x"), ctx);

            assertSame(ctx, info.context());
        }
    }

    @Nested
    @DisplayName("ErrorInfo.fromWebView")
    class FromWebView {

        @Test
        @DisplayName("captures message, source file, and line number")
        void capturesWebViewFields() {
            ErrorInfo info = ErrorInfo.fromWebView(
                "TypeError: x is not a function", "app.js", 42, "at foo(app.js:42)", null);

            assertEquals(ErrorInfo.Source.WEBVIEW, info.source());
            assertEquals("TypeError: x is not a function", info.message());
            assertEquals("app.js", info.fileName());
            assertEquals(42, info.lineNumber());
            assertEquals("at foo(app.js:42)", info.stackTrace());
        }

        @Test
        @DisplayName("threadName is null for WebView errors")
        void threadNameIsNull() {
            ErrorInfo info = ErrorInfo.fromWebView("err", "file.js", 1, "", null);

            assertNull(info.threadName());
        }
    }

    @Nested
    @DisplayName("ErrorContext.from")
    class ErrorContextFrom {

        @Test
        @DisplayName("builds from LogContext and recent commands")
        void buildsFromLogContext() {
            LogContext logCtx = new LogContext("MyApp", "2.0", "linux-x64", "session-123");
            List<String> commands = List.of("cmd1", "cmd2");

            ErrorContext ctx = ErrorContext.from(logCtx, commands);

            assertEquals("MyApp", ctx.appName());
            assertEquals("2.0", ctx.appVersion());
            assertEquals("linux-x64", ctx.os());
            assertEquals("session-123", ctx.sessionId());
            assertEquals(commands, ctx.recentCommands());
        }

        @Test
        @DisplayName("uses defaults when LogContext is null")
        void usesDefaultsForNullLogContext() {
            List<String> commands = List.of("cmd1");
            ErrorContext ctx = ErrorContext.from(null, commands);

            assertEquals("unknown", ctx.appName());
            assertEquals("unknown", ctx.appVersion());
            assertEquals("unknown", ctx.os());
            assertEquals("unknown", ctx.sessionId());
            assertEquals(commands, ctx.recentCommands());
        }
    }
}
