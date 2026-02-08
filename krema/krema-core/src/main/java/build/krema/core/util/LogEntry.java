package build.krema.core.util;

import java.time.Instant;

/**
 * Structured log record for JSON Lines file output.
 */
public record LogEntry(
    Instant timestamp,
    String level,
    String logger,
    String message,
    String errorMessage,
    String stackTrace,
    LogContext context
) {
    public static LogEntry of(String level, String logger, String message, LogContext context) {
        return new LogEntry(Instant.now(), level, logger, message, null, null, context);
    }

    public static LogEntry ofError(String level, String logger, String message,
                                    String errorMessage, String stackTrace, LogContext context) {
        return new LogEntry(Instant.now(), level, logger, message, errorMessage, stackTrace, context);
    }
}
