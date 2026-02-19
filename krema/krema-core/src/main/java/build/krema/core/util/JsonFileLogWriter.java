package build.krema.core.util;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes structured log entries as JSON Lines to rotating files.
 * Produces {@code app.jsonl}, rotating to {@code app.1.jsonl}, {@code app.2.jsonl}, etc.
 */
public class JsonFileLogWriter implements AutoCloseable {

    private final Path logDir;
    private final long maxFileSize;
    private final int maxFiles;
    private final Path logFile;
    private Writer writer;
    private boolean closed;

    public JsonFileLogWriter(Path logDir, long maxFileSize, int maxFiles) throws IOException {
        this.logDir = logDir;
        this.maxFileSize = maxFileSize;
        this.maxFiles = maxFiles;
        this.logFile = logDir.resolve("app.jsonl");
        this.writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public synchronized void write(LogEntry entry) {
        if (closed) {
            return;
        }
        try {
            if (Files.exists(logFile) && Files.size(logFile) >= maxFileSize) {
                rotate();
            }
            Map<String, Object> map = toFlatMap(entry);
            String json = Json.mapper().writeValueAsString(map);
            writer.write(json);
            writer.write(System.lineSeparator());
            writer.flush();
        } catch (IOException e) {
            System.err.println("[Krema] Failed to write JSON log: " + e.getMessage());
        }
    }

    private Map<String, Object> toFlatMap(LogEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", entry.timestamp().toString());
        map.put("level", entry.level());
        map.put("logger", entry.logger());
        map.put("message", entry.message());
        if (entry.errorMessage() != null) {
            map.put("errorMessage", entry.errorMessage());
        }
        if (entry.stackTrace() != null) {
            map.put("stackTrace", entry.stackTrace());
        }
        LogContext ctx = entry.context();
        if (ctx != null) {
            map.put("appName", ctx.appName());
            map.put("appVersion", ctx.appVersion());
            map.put("os", ctx.os());
            map.put("sessionId", ctx.sessionId());
        }
        return map;
    }

    private void rotate() throws IOException {
        writer.close();

        Path oldest = logDir.resolve("app." + maxFiles + ".jsonl");
        Files.deleteIfExists(oldest);

        for (int i = maxFiles - 1; i >= 1; i--) {
            Path from = logDir.resolve("app." + i + ".jsonl");
            Path to = logDir.resolve("app." + (i + 1) + ".jsonl");
            if (Files.exists(from)) {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        Files.move(logFile, logDir.resolve("app.1.jsonl"), StandardCopyOption.REPLACE_EXISTING);

        writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            writer.close();
        } catch (IOException e) {
            System.err.println("[Krema] Failed to close JSON log writer: " + e.getMessage());
        }
    }
}
