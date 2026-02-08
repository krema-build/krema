package build.krema.core.dock;

import build.krema.core.ports.DockPort;

/**
 * Platform-agnostic interface for dock/taskbar operations.
 *
 * @deprecated Use {@link DockPort} instead. This interface is maintained
 *             for backward compatibility and will be removed in a future version.
 * @see DockPort
 */
@Deprecated(since = "2.0", forRemoval = true)
public interface DockEngine extends DockPort {

    /**
     * Sets the badge label on the dock icon.
     * @param text The badge text (typically a number). Empty string or null clears the badge.
     */
    void setBadge(String text);

    /**
     * Gets the current badge label.
     * @return The current badge text, or empty string if none.
     */
    String getBadge();

    /**
     * Clears the badge from the dock icon.
     */
    void clearBadge();

    /**
     * Requests user attention by bouncing the dock icon.
     * @param critical If true, bounces until user responds. If false, bounces once.
     * @return The attention request ID (can be used to cancel).
     */
    long requestAttention(boolean critical);

    /**
     * Cancels a previous attention request.
     * @param requestId The ID returned from requestAttention.
     */
    void cancelAttention(long requestId);

    /**
     * Checks if dock badge is supported on this platform.
     */
    boolean isSupported();
}
