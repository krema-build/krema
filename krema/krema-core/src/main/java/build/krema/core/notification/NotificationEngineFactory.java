package build.krema.core.notification;

import build.krema.core.notification.linux.LinuxNotificationEngine;
import build.krema.core.notification.macos.MacOSNotificationEngine;
import build.krema.core.notification.windows.WindowsNotificationEngine;
import build.krema.core.platform.Platform;

/**
 * Factory for creating platform-specific NotificationEngine instances.
 */
public final class NotificationEngineFactory {

    private static volatile NotificationEngine instance;

    private NotificationEngineFactory() {}

    /**
     * Gets the NotificationEngine for the current platform.
     * Uses lazy initialization and caches the instance.
     *
     * @return the NotificationEngine instance, or null if not supported
     */
    public static NotificationEngine get() {
        if (instance == null) {
            synchronized (NotificationEngineFactory.class) {
                if (instance == null) {
                    instance = create();
                }
            }
        }
        return instance;
    }

    private static NotificationEngine create() {
        Platform platform = Platform.current();

        return switch (platform) {
            case MACOS -> new MacOSNotificationEngine();
            case LINUX -> new LinuxNotificationEngine();
            case WINDOWS -> new WindowsNotificationEngine();
            case UNKNOWN -> null;
        };
    }
}
