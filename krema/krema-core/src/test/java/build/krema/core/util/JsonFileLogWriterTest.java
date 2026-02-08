package build.krema.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import build.krema.core.util.JsonFileLogWriter;
import build.krema.core.util.LogContext;
import build.krema.core.util.LogEntry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonFileLogWriter")
class JsonFileLogWriterTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private final LogContext ctx = LogContext.create("TestApp", "2.0.0", "darwin-aarch64");

    @Test
    @DisplayName("write entry produces valid JSON with all fields")
    void writeEntryProducesValidJson() throws Exception {
        try (var writer = createWriter(1024 * 1024, 3)) {
            LogEntry entry = LogEntry.of("INFO", "Frontend", "Page loaded", ctx);
            writer.write(entry);
        }

        List<String> lines = Files.readAllLines(tempDir.resolve("app.jsonl"));
        assertEquals(1, lines.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> json = mapper.readValue(lines.getFirst(), Map.class);
        assertNotNull(json.get("timestamp"));
        assertEquals("INFO", json.get("level"));
        assertEquals("Frontend", json.get("logger"));
        assertEquals("Page loaded", json.get("message"));
        assertEquals("TestApp", json.get("appName"));
        assertEquals("2.0.0", json.get("appVersion"));
        assertEquals("darwin-aarch64", json.get("os"));
        assertNotNull(json.get("sessionId"));
    }

    @Test
    @DisplayName("multiple entries produce one JSON object per line")
    void multipleEntriesOnePerLine() throws Exception {
        try (var writer = createWriter(1024 * 1024, 3)) {
            writer.write(LogEntry.of("INFO", "A", "first", ctx));
            writer.write(LogEntry.of("DEBUG", "B", "second", ctx));
            writer.write(LogEntry.of("WARN", "C", "third", ctx));
        }

        List<String> lines = Files.readAllLines(tempDir.resolve("app.jsonl"));
        assertEquals(3, lines.size());

        for (String line : lines) {
            assertDoesNotThrow(() -> mapper.readTree(line));
        }
    }

    @Test
    @DisplayName("null error fields are omitted from output")
    void nullFieldsOmitted() throws Exception {
        try (var writer = createWriter(1024 * 1024, 3)) {
            writer.write(LogEntry.of("INFO", "Test", "msg", ctx));
        }

        String line = Files.readAllLines(tempDir.resolve("app.jsonl")).getFirst();
        assertFalse(line.contains("errorMessage"));
        assertFalse(line.contains("stackTrace"));
    }

    @Test
    @DisplayName("error entry includes errorMessage and stackTrace")
    void errorEntryIncludesErrorFields() throws Exception {
        try (var writer = createWriter(1024 * 1024, 3)) {
            writer.write(LogEntry.ofError("ERROR", "Test", "failed",
                "NPE", "at Main.run(Main.java:10)", ctx));
        }

        String line = Files.readAllLines(tempDir.resolve("app.jsonl")).getFirst();
        @SuppressWarnings("unchecked")
        Map<String, Object> json = mapper.readValue(line, Map.class);
        assertEquals("NPE", json.get("errorMessage"));
        assertEquals("at Main.run(Main.java:10)", json.get("stackTrace"));
    }

    @Test
    @DisplayName("context fields are flattened to top level")
    void contextFieldsFlattenedToTopLevel() throws Exception {
        try (var writer = createWriter(1024 * 1024, 3)) {
            writer.write(LogEntry.of("INFO", "Test", "msg", ctx));
        }

        String line = Files.readAllLines(tempDir.resolve("app.jsonl")).getFirst();
        @SuppressWarnings("unchecked")
        Map<String, Object> json = mapper.readValue(line, Map.class);

        // Context fields should be top-level, not nested under "context"
        assertFalse(json.containsKey("context"));
        assertEquals("TestApp", json.get("appName"));
        assertEquals("2.0.0", json.get("appVersion"));
    }

    @Test
    @DisplayName("rotation triggers at maxFileSize")
    void rotationTriggersAtMaxFileSize() throws Exception {
        // Use a tiny max size to force rotation
        try (var writer = createWriter(50, 3)) {
            writer.write(LogEntry.of("INFO", "T", "first message that is long enough", ctx));
            writer.write(LogEntry.of("INFO", "T", "second after rotation", ctx));
        }

        assertTrue(Files.exists(tempDir.resolve("app.1.jsonl")));
        assertTrue(Files.exists(tempDir.resolve("app.jsonl")));
    }

    @Test
    @DisplayName("max files limit respected (oldest deleted)")
    void maxFilesLimitRespected() throws Exception {
        try (var writer = createWriter(10, 2)) {
            // Write enough entries to trigger multiple rotations
            for (int i = 0; i < 10; i++) {
                writer.write(LogEntry.of("INFO", "T", "message " + i, ctx));
            }
        }

        // Should have app.jsonl, app.1.jsonl, app.2.jsonl at most
        assertTrue(Files.exists(tempDir.resolve("app.jsonl")));
        // Oldest beyond maxFiles should be deleted
        assertFalse(Files.exists(tempDir.resolve("app.3.jsonl")));
    }

    @Test
    @DisplayName("close() is idempotent")
    void closeIsIdempotent() throws Exception {
        var writer = createWriter(1024, 3);
        writer.close();
        assertDoesNotThrow(writer::close);
    }

    private JsonFileLogWriter createWriter(long maxFileSize, int maxFiles) throws IOException {
        return new JsonFileLogWriter(tempDir, maxFileSize, maxFiles);
    }
}
