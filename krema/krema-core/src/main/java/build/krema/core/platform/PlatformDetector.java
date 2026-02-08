package build.krema.core.platform;

/**
 * Detects the current operating system platform and architecture.
 */
public final class PlatformDetector {

    private static Platform cachedPlatform;
    private static String cachedArch;

    private PlatformDetector() {}

    public static Platform detect() {
        if (cachedPlatform != null) {
            return cachedPlatform;
        }

        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("mac") || os.contains("darwin")) {
            cachedPlatform = Platform.MACOS;
        } else if (os.contains("win")) {
            cachedPlatform = Platform.WINDOWS;
        } else if (os.contains("nux") || os.contains("nix") || os.contains("bsd")) {
            cachedPlatform = Platform.LINUX;
        } else {
            cachedPlatform = Platform.UNKNOWN;
        }

        return cachedPlatform;
    }

    public static String getArch() {
        if (cachedArch != null) {
            return cachedArch;
        }

        String arch = System.getProperty("os.arch").toLowerCase();

        if (arch.contains("aarch64") || arch.contains("arm64")) {
            cachedArch = "aarch64";
        } else if (arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64")) {
            cachedArch = "x86_64";
        } else if (arch.contains("x86") || arch.contains("i386") || arch.contains("i686")) {
            cachedArch = "x86";
        } else {
            cachedArch = arch;
        }

        return cachedArch;
    }

    public static boolean isMacOS() {
        return detect() == Platform.MACOS;
    }

    public static boolean isWindows() {
        return detect() == Platform.WINDOWS;
    }

    public static boolean isLinux() {
        return detect() == Platform.LINUX;
    }

    public static boolean is64Bit() {
        String arch = getArch();
        return arch.equals("x86_64") || arch.equals("aarch64");
    }
}
