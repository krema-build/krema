package build.krema.core.error;

import java.util.List;

import build.krema.core.util.LogContext;

/**
 * Contextual information attached to an error for diagnostics.
 */
public record ErrorContext(
    String appName,
    String appVersion,
    String os,
    String sessionId,
    List<String> recentCommands
) {

    /**
     * Builds an ErrorContext from the current LogContext and recent IPC commands.
     */
    public static ErrorContext from(LogContext logContext, List<String> recentCommands) {
        if (logContext == null) {
            return new ErrorContext("unknown", "unknown", "unknown", "unknown", recentCommands);
        }
        return new ErrorContext(
            logContext.appName(),
            logContext.appVersion(),
            logContext.os(),
            logContext.sessionId(),
            recentCommands
        );
    }
}
