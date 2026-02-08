package build.krema.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import build.krema.core.util.LogContext;
import build.krema.core.util.LogEntry;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LogEntry")
class LogEntryTest {

    private final LogContext ctx = LogContext.create("TestApp", "1.0.0", "darwin-aarch64");

    @Test
    @DisplayName("of() creates entry with null error fields")
    void ofCreatesEntryWithNullErrorFields() {
        LogEntry entry = LogEntry.of("INFO", "TestLogger", "hello", ctx);

        assertEquals("INFO", entry.level());
        assertEquals("TestLogger", entry.logger());
        assertEquals("hello", entry.message());
        assertNull(entry.errorMessage());
        assertNull(entry.stackTrace());
        assertSame(ctx, entry.context());
    }

    @Test
    @DisplayName("ofError() creates entry with error message and stack trace")
    void ofErrorCreatesEntryWithErrorFields() {
        LogEntry entry = LogEntry.ofError("ERROR", "TestLogger", "failed",
            "NullPointerException", "at com.example.Main.run(Main.java:10)", ctx);

        assertEquals("ERROR", entry.level());
        assertEquals("failed", entry.message());
        assertEquals("NullPointerException", entry.errorMessage());
        assertEquals("at com.example.Main.run(Main.java:10)", entry.stackTrace());
    }

    @Test
    @DisplayName("timestamp is close to Instant.now()")
    void timestampIsRecent() {
        Instant before = Instant.now();
        LogEntry entry = LogEntry.of("DEBUG", "Test", "msg", ctx);
        Instant after = Instant.now();

        assertFalse(entry.timestamp().isBefore(before));
        assertFalse(entry.timestamp().isAfter(after));
    }
}
