package build.krema.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import build.krema.core.util.LogContext;
import build.krema.core.util.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Logger file output")
class LoggerFileOutputTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void cleanup() {
        Logger.disableFileLogging();
        Logger.setLogContext(null);
        Logger.setGlobalLevel(Logger.Level.INFO);
    }

    @Test
    @DisplayName("after enableFileLogging, log calls produce JSON Lines in file")
    void logCallsProduceJsonLines() throws Exception {
        Logger.enableFileLogging(tempDir, 1024 * 1024, 3);
        Logger logger = new Logger("TestLogger");
        logger.info("hello world");

        Logger.disableFileLogging();

        Path logFile = tempDir.resolve("app.jsonl");
        assertTrue(Files.exists(logFile));

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> json = mapper.readValue(lines.getFirst(), Map.class);
        assertEquals("INFO", json.get("level"));
        assertEquals("TestLogger", json.get("logger"));
        assertEquals("hello world", json.get("message"));
    }

    @Test
    @DisplayName("setLogContext metadata appears in entries")
    void logContextMetadataAppearsInEntries() throws Exception {
        LogContext ctx = LogContext.create("MyApp", "3.0.0", "linux-x86_64");
        Logger.setLogContext(ctx);
        Logger.enableFileLogging(tempDir, 1024 * 1024, 3);

        Logger logger = new Logger("Backend");
        logger.info("started");

        Logger.disableFileLogging();

        String line = Files.readAllLines(tempDir.resolve("app.jsonl")).getFirst();
        @SuppressWarnings("unchecked")
        Map<String, Object> json = mapper.readValue(line, Map.class);
        assertEquals("MyApp", json.get("appName"));
        assertEquals("3.0.0", json.get("appVersion"));
        assertEquals("linux-x86_64", json.get("os"));
        assertEquals(ctx.sessionId(), json.get("sessionId"));
    }

    @Test
    @DisplayName("error(msg, throwable) produces structured error with errorMessage and stackTrace")
    void errorWithThrowableProducesStructuredError() throws Exception {
        Logger.enableFileLogging(tempDir, 1024 * 1024, 3);
        Logger logger = new Logger("ErrLogger");

        Exception ex = new RuntimeException("something broke");
        logger.error("operation failed", ex);

        Logger.disableFileLogging();

        String line = Files.readAllLines(tempDir.resolve("app.jsonl")).getFirst();
        @SuppressWarnings("unchecked")
        Map<String, Object> json = mapper.readValue(line, Map.class);
        assertEquals("ERROR", json.get("level"));
        assertEquals("operation failed", json.get("message"));
        assertEquals("something broke", json.get("errorMessage"));
        assertNotNull(json.get("stackTrace"));
        assertTrue(((String) json.get("stackTrace")).contains("RuntimeException"));
    }

    @Test
    @DisplayName("disableFileLogging stops file output")
    void disableFileLoggingStopsOutput() throws Exception {
        Logger.enableFileLogging(tempDir, 1024 * 1024, 3);
        Logger logger = new Logger("Test");
        logger.info("before");

        Logger.disableFileLogging();
        logger.info("after");

        List<String> lines = Files.readAllLines(tempDir.resolve("app.jsonl"));
        assertEquals(1, lines.size());
    }

    @Test
    @DisplayName("without enableFileLogging, no file created")
    void withoutEnableNoFileCreated() {
        Logger logger = new Logger("Test");
        logger.info("should not create file");

        assertFalse(Files.exists(tempDir.resolve("app.jsonl")));
    }
}
