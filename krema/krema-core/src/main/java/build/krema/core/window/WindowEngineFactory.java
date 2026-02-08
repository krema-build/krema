package build.krema.core.window;

import build.krema.core.platform.Platform;
import build.krema.core.window.linux.LinuxWindowEngine;
import build.krema.core.window.macos.MacOSWindowEngine;
import build.krema.core.window.windows.WindowsWindowEngine;

/**
 * Factory for creating platform-specific WindowEngine instances.
 */
public final class WindowEngineFactory {

    private static volatile WindowEngine instance;

    private WindowEngineFactory() {}

    /**
     * Gets the WindowEngine for the current platform.
     * Uses lazy initialization and caches the instance.
     *
     * @return the WindowEngine instance
     * @throws UnsupportedPlatformException if the platform is not supported
     */
    public static WindowEngine get() {
        if (instance == null) {
            synchronized (WindowEngineFactory.class) {
                if (instance == null) {
                    instance = create();
                }
            }
        }
        return instance;
    }

    private static WindowEngine create() {
        Platform platform = Platform.current();

        return switch (platform) {
            case MACOS -> new MacOSWindowEngine();
            case WINDOWS -> new WindowsWindowEngine();
            case LINUX -> new LinuxWindowEngine();
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
