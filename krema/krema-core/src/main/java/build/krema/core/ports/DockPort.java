package build.krema.core.ports;

/**
 * Port interface for dock/taskbar operations.
 * Implementations provide platform-specific dock functionality.
 *
 * @see build.krema.core.dock.DockEngine
 */
public interface DockPort {

    /**
     * Sets the badge label on the dock icon.
     *
     * @param text badge text (null or empty to clear)
     */
    void setBadge(String text);

    /**
     * Gets the current badge label.
     *
     * @return current badge text, or null if none
     */
    String getBadge();

    /**
     * Clears the badge from the dock icon.
     */
    void clearBadge();

    /**
     * Requests user attention by bouncing/flashing the dock icon.
     *
     * @param critical if true, bounces continuously until app is focused
     * @return request ID for cancellation
     */
    long requestAttention(boolean critical);

    /**
     * Cancels a previous attention request.
     *
     * @param requestId the request ID from requestAttention
     */
    void cancelAttention(long requestId);

    /**
     * Checks if dock operations are supported on this platform.
     *
     * @return true if dock badge/bounce is available
     */
    boolean isSupported();
}
