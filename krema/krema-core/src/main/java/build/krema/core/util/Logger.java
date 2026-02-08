package build.krema.core.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple logging utility for Krema applications.
 * Supports console output and optional JSON Lines file output.
 */
public class Logger {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static Level globalLevel = Level.INFO;
    private static boolean colorEnabled = true;
    private static volatile LogContext logContext;
    private static volatile JsonFileLogWriter fileWriter;

    private final String name;
    private Level level;

    public Logger(String name) {
        this.name = name;
        this.level = null; // Use global level
    }

    public Logger(Class<?> clazz) {
        this(clazz.getSimpleName());
    }

    public static Logger getLogger(String name) {
        return new Logger(name);
    }

    public static Logger getLogger(Class<?> clazz) {
        return new Logger(clazz);
    }

    public static void setGlobalLevel(Level level) {
        globalLevel = level;
    }

    public static void setColorEnabled(boolean enabled) {
        colorEnabled = enabled;
    }

    public static void setLogContext(LogContext context) {
        logContext = context;
    }

    public static LogContext getLogContext() {
        return logContext;
    }

    public static void enableFileLogging(Path logDir, long maxFileSize, int maxFiles) throws IOException {
        Files.createDirectories(logDir);
        fileWriter = new JsonFileLogWriter(logDir, maxFileSize, maxFiles);
    }

    public static void disableFileLogging() {
        JsonFileLogWriter writer = fileWriter;
        if (writer != null) {
            fileWriter = null;
            writer.close();
        }
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public void trace(String message, Object... args) {
        log(Level.TRACE, message, args);
    }

    public void debug(String message, Object... args) {
        log(Level.DEBUG, message, args);
    }

    public void info(String message, Object... args) {
        log(Level.INFO, message, args);
    }

    public void warn(String message, Object... args) {
        log(Level.WARN, message, args);
    }

    public void error(String message, Object... args) {
        log(Level.ERROR, message, args);
    }

    public void error(String message, Throwable throwable) {
        Level effectiveLevel = level != null ? level : globalLevel;
        if (Level.ERROR.ordinal() < effectiveLevel.ordinal()) {
            return;
        }

        String stackTrace = getStackTrace(throwable);
        logToConsole(Level.ERROR, message + "\n" + stackTrace);
        logToFile(Level.ERROR, message, throwable.getMessage(), stackTrace);
    }

    private void log(Level msgLevel, String message, Object... args) {
        Level effectiveLevel = level != null ? level : globalLevel;
        if (msgLevel.ordinal() < effectiveLevel.ordinal()) {
            return;
        }

        String formattedMessage = args.length > 0 ? String.format(message, args) : message;
        logToConsole(msgLevel, formattedMessage);
        logToFile(msgLevel, formattedMessage, null, null);
    }

    private void logToConsole(Level msgLevel, String formattedMessage) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);

        String output;
        if (colorEnabled) {
            output = String.format("%s%s %s[%s]%s %s[%s]%s %s",
                Colors.GRAY, timestamp,
                msgLevel.color, msgLevel.label, Colors.RESET,
                Colors.CYAN, name, Colors.RESET,
                formattedMessage
            );
        } else {
            output = String.format("%s [%s] [%s] %s",
                timestamp, msgLevel.label, name, formattedMessage
            );
        }

        if (msgLevel.ordinal() >= Level.WARN.ordinal()) {
            System.err.println(output);
        } else {
            System.out.println(output);
        }
    }

    private void logToFile(Level msgLevel, String message, String errorMsg, String stackTrace) {
        JsonFileLogWriter writer = fileWriter;
        if (writer == null) {
            return;
        }

        LogContext ctx = logContext;
        LogEntry entry;
        if (errorMsg != null || stackTrace != null) {
            entry = LogEntry.ofError(msgLevel.name(), name, message, errorMsg, stackTrace, ctx);
        } else {
            entry = LogEntry.of(msgLevel.name(), name, message, ctx);
        }
        writer.write(entry);
    }

    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    public enum Level {
        TRACE("TRACE", Colors.GRAY),
        DEBUG("DEBUG", Colors.BLUE),
        INFO("INFO ", Colors.GREEN),
        WARN("WARN ", Colors.YELLOW),
        ERROR("ERROR", Colors.RED);

        final String label;
        final String color;

        Level(String label, String color) {
            this.label = label;
            this.color = color;
        }
    }

    private static class Colors {
        static final String RESET = "\u001B[0m";
        static final String RED = "\u001B[31m";
        static final String GREEN = "\u001B[32m";
        static final String YELLOW = "\u001B[33m";
        static final String BLUE = "\u001B[34m";
        static final String CYAN = "\u001B[36m";
        static final String GRAY = "\u001B[90m";
    }
}
