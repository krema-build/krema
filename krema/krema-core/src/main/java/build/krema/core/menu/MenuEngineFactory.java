package build.krema.core.menu;

import java.util.function.Consumer;

import build.krema.core.menu.linux.LinuxMenuEngine;
import build.krema.core.menu.macos.MacOSMenuEngine;
import build.krema.core.menu.windows.WindowsMenuEngine;
import build.krema.core.platform.Platform;

/**
 * Factory for creating platform-specific MenuEngine instances.
 */
public final class MenuEngineFactory {

    private static volatile MenuEngine instance;
    private static Consumer<String> pendingCallback;

    private MenuEngineFactory() {}

    /**
     * Gets the MenuEngine for the current platform.
     * Uses lazy initialization and caches the instance.
     *
     * @return the MenuEngine instance
     * @throws UnsupportedPlatformException if the platform is not supported
     */
    public static MenuEngine get() {
        if (instance == null) {
            synchronized (MenuEngineFactory.class) {
                if (instance == null) {
                    instance = create();
                    if (pendingCallback != null) {
                        instance.setMenuClickCallback(pendingCallback);
                    }
                }
            }
        }
        return instance;
    }

    /**
     * Sets the menu click callback before the engine is created.
     * This is useful when the callback needs to be set during app initialization.
     */
    public static void setCallback(Consumer<String> callback) {
        pendingCallback = callback;
        if (instance != null) {
            instance.setMenuClickCallback(callback);
        }
    }

    private static MenuEngine create() {
        Platform platform = Platform.current();

        return switch (platform) {
            case MACOS -> new MacOSMenuEngine();
            case WINDOWS -> new WindowsMenuEngine();
            case LINUX -> new LinuxMenuEngine();
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
