package build.krema.core.shortcut;

import build.krema.core.platform.Platform;
import build.krema.core.shortcut.linux.LinuxGlobalShortcutEngine;
import build.krema.core.shortcut.macos.MacOSGlobalShortcutEngine;
import build.krema.core.shortcut.windows.WindowsGlobalShortcutEngine;

/**
 * Factory for creating platform-specific GlobalShortcutEngine instances.
 */
public final class GlobalShortcutEngineFactory {

    private static volatile GlobalShortcutEngine instance;

    private GlobalShortcutEngineFactory() {}

    public static GlobalShortcutEngine get() {
        if (instance == null) {
            synchronized (GlobalShortcutEngineFactory.class) {
                if (instance == null) {
                    instance = create();
                }
            }
        }
        return instance;
    }

    private static GlobalShortcutEngine create() {
        Platform platform = Platform.current();

        return switch (platform) {
            case MACOS -> new MacOSGlobalShortcutEngine();
            case WINDOWS -> new WindowsGlobalShortcutEngine();
            case LINUX -> new LinuxGlobalShortcutEngine();
            case UNKNOWN -> throw new UnsupportedPlatformException(
                "Unknown platform: " + System.getProperty("os.name")
            );
        };
    }

    public static class UnsupportedPlatformException extends RuntimeException {
        public UnsupportedPlatformException(String message) {
            super(message);
        }
    }
}
