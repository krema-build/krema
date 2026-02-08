package build.krema.cli.bundle;

import build.krema.cli.bundle.linux.LinuxAppBundler;
import build.krema.cli.bundle.macos.MacOSAppBundler;
import build.krema.cli.bundle.windows.WindowsAppBundler;
import build.krema.core.platform.Platform;

/**
 * Factory for creating platform-specific AppBundler instances.
 */
public final class AppBundlerFactory {

    private AppBundlerFactory() {
    }

    /**
     * Returns the appropriate AppBundler for the current platform.
     *
     * @return Platform-specific bundler
     */
    public static AppBundler get() {
        return get(Platform.current());
    }

    /**
     * Returns the appropriate AppBundler for the specified platform.
     *
     * @param platform Target platform
     * @return Platform-specific bundler
     */
    public static AppBundler get(Platform platform) {
        return switch (platform) {
            case MACOS -> new MacOSAppBundler();
            case WINDOWS -> new WindowsAppBundler();
            case LINUX -> new LinuxAppBundler();
            case UNKNOWN -> throw new UnsupportedOperationException(
                "Cannot create bundler for unknown platform"
            );
        };
    }
}
