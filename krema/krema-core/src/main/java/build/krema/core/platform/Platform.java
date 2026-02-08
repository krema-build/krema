package build.krema.core.platform;

/**
 * Supported operating system platforms.
 */
public enum Platform {
    MACOS("macOS", "dylib", "lib%s.dylib"),
    WINDOWS("Windows", "dll", "%s.dll"),
    LINUX("Linux", "so", "lib%s.so"),
    UNKNOWN("Unknown", null, null);

    private final String displayName;
    private final String libraryExtension;
    private final String libraryPattern;

    Platform(String displayName, String libraryExtension, String libraryPattern) {
        this.displayName = displayName;
        this.libraryExtension = libraryExtension;
        this.libraryPattern = libraryPattern;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLibraryExtension() {
        return libraryExtension;
    }

    public String formatLibraryName(String baseName) {
        if (libraryPattern == null) {
            throw new UnsupportedOperationException("Unknown platform");
        }
        return String.format(libraryPattern, baseName);
    }

    /**
     * Returns the target string for the current platform and architecture.
     * Format: "{os}-{arch}" (e.g. "darwin-aarch64", "windows-x86_64", "linux-x86_64").
     */
    public String getTarget() {
        String os = switch (this) {
            case MACOS -> "darwin";
            case WINDOWS -> "windows";
            case LINUX -> "linux";
            case UNKNOWN -> "unknown";
        };
        return os + "-" + PlatformDetector.getArch();
    }

    public static Platform current() {
        return PlatformDetector.detect();
    }
}
