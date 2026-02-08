package build.krema.core.screen;

import build.krema.core.platform.Platform;
import build.krema.core.screen.linux.LinuxScreenEngine;
import build.krema.core.screen.macos.MacOSScreenEngine;
import build.krema.core.screen.windows.WindowsScreenEngine;

/**
 * Factory for creating platform-specific ScreenEngine instances.
 */
public final class ScreenEngineFactory {

    private static volatile ScreenEngine instance;

    private ScreenEngineFactory() {}

    /**
     * Gets the ScreenEngine for the current platform.
     * Uses lazy initialization and caches the instance.
     *
     * @return the ScreenEngine instance
     * @throws UnsupportedPlatformException if the platform is not supported
     */
    public static ScreenEngine get() {
        if (instance == null) {
            synchronized (ScreenEngineFactory.class) {
                if (instance == null) {
                    instance = create();
                }
            }
        }
        return instance;
    }

    private static ScreenEngine create() {
        Platform platform = Platform.current();

        return switch (platform) {
            case MACOS -> new MacOSScreenEngine();
            case WINDOWS -> new WindowsScreenEngine();
            case LINUX -> new LinuxScreenEngine();
            case UNKNOWN -> throw new UnsupportedPlatformException(
                "Unknown platform: " + System.getProperty("os.name")
            );
        };
    }

    /**
     * Exception thrown when the platform is not supported.
     */
    public static class UnsupportedPlatformException extends RuntimeException {
        public UnsupportedPlatformException(String message) {
            super(message);
        }
    }
}
