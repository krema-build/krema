package build.krema.core.dialog;

import build.krema.core.dialog.linux.LinuxDialogEngine;
import build.krema.core.dialog.macos.MacOSDialogEngine;
import build.krema.core.dialog.windows.WindowsDialogEngine;
import build.krema.core.platform.Platform;

/**
 * Factory for creating platform-specific DialogEngine instances.
 */
public final class DialogEngineFactory {

    private static volatile DialogEngine instance;

    private DialogEngineFactory() {}

    /**
     * Gets the DialogEngine for the current platform.
     * Uses lazy initialization and caches the instance.
     *
     * @return the DialogEngine instance
     * @throws UnsupportedPlatformException if the platform is not supported
     */
    public static DialogEngine get() {
        if (instance == null) {
            synchronized (DialogEngineFactory.class) {
                if (instance == null) {
                    instance = create();
                }
            }
        }
        return instance;
    }

    private static DialogEngine create() {
        Platform platform = Platform.current();

        return switch (platform) {
            case MACOS -> new MacOSDialogEngine();
            case WINDOWS -> new WindowsDialogEngine();
            case LINUX -> new LinuxDialogEngine();
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
