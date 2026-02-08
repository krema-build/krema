package build.krema.core.ports;

/**
 * Port interface for desktop notifications.
 * Implementations provide platform-specific notification functionality.
 *
 * @see build.krema.core.notification.NotificationEngine
 */
public interface NotificationPort {

    /**
     * Shows a desktop notification.
     *
     * @param title notification title
     * @param body notification body text
     * @param icon optional icon path or base64 data
     * @return notification ID for tracking
     */
    String show(String title, String body, String icon);

    /**
     * Checks if notifications are supported and permitted.
     *
     * @return true if notifications can be shown
     */
    boolean isSupported();

    /**
     * Requests notification permission from the user.
     *
     * @return true if permission was granted
     */
    default boolean requestPermission() {
        return isSupported();
    }
}
