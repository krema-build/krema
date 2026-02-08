package build.krema.core.util;

import java.util.UUID;

/**
 * Immutable session-level metadata attached to every structured log entry.
 */
public record LogContext(
    String appName,
    String appVersion,
    String os,
    String sessionId
) {
    public static LogContext create(String appName, String appVersion, String os) {
        return new LogContext(appName, appVersion, os, UUID.randomUUID().toString());
    }
}
