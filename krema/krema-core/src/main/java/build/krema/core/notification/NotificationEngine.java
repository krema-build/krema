package build.krema.core.notification;

import java.util.Map;

import build.krema.core.ports.NotificationPort;

/**
 * Platform-agnostic interface for native notifications.
 *
 * @deprecated Use {@link NotificationPort} instead. This interface is maintained
 *             for backward compatibility and will be removed in a future version.
 * @see NotificationPort
 */
@Deprecated(since = "2.0", forRemoval = true)
public interface NotificationEngine {

    /**
     * Shows a notification with title, body, and optional settings.
     *
     * @param title   the notification title
     * @param body    the notification body text
     * @param options optional settings (sound, icon, etc.)
     * @return true if the notification was shown successfully
     */
    boolean show(String title, String body, Map<String, Object> options);

    /**
     * Shows a simple notification with just title and body.
     */
    default boolean show(String title, String body) {
        return show(title, body, null);
    }

    /**
     * Checks if notifications are supported on this platform.
     */
    boolean isSupported();
}
