package build.krema.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import build.krema.core.util.LogContext;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LogContext")
class LogContextTest {

    @Test
    @DisplayName("create() populates all fields")
    void createPopulatesAllFields() {
        LogContext ctx = LogContext.create("MyApp", "1.2.0", "darwin-aarch64");

        assertEquals("MyApp", ctx.appName());
        assertEquals("1.2.0", ctx.appVersion());
        assertEquals("darwin-aarch64", ctx.os());
        assertNotNull(ctx.sessionId());
        assertFalse(ctx.sessionId().isBlank());
    }

    @Test
    @DisplayName("sessionId is a valid UUID format")
    void sessionIdIsUuidFormat() {
        LogContext ctx = LogContext.create("App", "1.0", "linux-x86_64");

        // UUID format: 8-4-4-4-12 hex digits
        assertTrue(ctx.sessionId().matches(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    @DisplayName("two calls produce different session IDs")
    void differentSessionIds() {
        LogContext ctx1 = LogContext.create("App", "1.0", "macOS");
        LogContext ctx2 = LogContext.create("App", "1.0", "macOS");

        assertNotEquals(ctx1.sessionId(), ctx2.sessionId());
    }
}
