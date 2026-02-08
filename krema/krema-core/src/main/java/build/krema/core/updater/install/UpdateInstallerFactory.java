package build.krema.core.updater.install;

import build.krema.core.platform.Platform;

/**
 * Factory for creating platform-specific {@link UpdateInstaller} instances.
 */
public final class UpdateInstallerFactory {

    private UpdateInstallerFactory() {}

    /**
     * Returns the appropriate UpdateInstaller for the current platform.
     *
     * @throws UnsupportedOperationException if the platform is not supported
     */
    public static UpdateInstaller get() {
        return get(Platform.current());
    }

    /**
     * Returns the appropriate UpdateInstaller for the specified platform.
     *
     * @throws UnsupportedOperationException if the platform is not supported
     */
    public static UpdateInstaller get(Platform platform) {
        return switch (platform) {
            case MACOS -> new MacOSUpdateInstaller();
            case WINDOWS -> new WindowsUpdateInstaller();
            case LINUX -> new LinuxUpdateInstaller();
            case UNKNOWN -> throw new UnsupportedOperationException(
                "Update installation is not supported on this platform");
        };
    }
}
