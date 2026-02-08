package build.krema.core.api.path;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import build.krema.core.KremaCommand;
import build.krema.core.platform.Platform;

/**
 * Provides paths to common system directories.
 * All methods return platform-appropriate paths.
 */
public class AppPaths {

    private final String appName;
    private final String appIdentifier;

    public AppPaths(String appName, String appIdentifier) {
        this.appName = appName;
        this.appIdentifier = appIdentifier;
    }

    public AppPaths(String appName) {
        this(appName, appName.toLowerCase().replaceAll("[^a-z0-9]", ""));
    }

    @KremaCommand("path:appData")
    public String appDataDir() {
        return getAppDataDir().toString();
    }

    @KremaCommand("path:appConfig")
    public String appConfigDir() {
        return getAppConfigDir().toString();
    }

    @KremaCommand("path:appCache")
    public String appCacheDir() {
        return getAppCacheDir().toString();
    }

    @KremaCommand("path:appLog")
    public String appLogDir() {
        return getAppLogDir().toString();
    }

    @KremaCommand("path:home")
    public String homeDir() {
        return getHomeDir().toString();
    }

    @KremaCommand("path:temp")
    public String tempDir() {
        return getTempDir().toString();
    }

    @KremaCommand("path:desktop")
    public String desktopDir() {
        return getDesktopDir().toString();
    }

    @KremaCommand("path:documents")
    public String documentsDir() {
        return getDocumentsDir().toString();
    }

    @KremaCommand("path:downloads")
    public String downloadsDir() {
        return getDownloadsDir().toString();
    }

    public Path getAppDataDir() {
        return switch (Platform.current()) {
            case MACOS -> Path.of(System.getProperty("user.home"), "Library", "Application Support", appName);
            case WINDOWS -> Path.of(System.getenv("APPDATA"), appName);
            case LINUX -> Path.of(System.getProperty("user.home"), ".local", "share", appIdentifier);
            case UNKNOWN -> Path.of(System.getProperty("user.home"), "." + appIdentifier);
        };
    }

    public Path getAppConfigDir() {
        return switch (Platform.current()) {
            case MACOS -> Path.of(System.getProperty("user.home"), "Library", "Preferences", appIdentifier);
            case WINDOWS -> Path.of(System.getenv("APPDATA"), appName);
            case LINUX -> Path.of(System.getProperty("user.home"), ".config", appIdentifier);
            case UNKNOWN -> Path.of(System.getProperty("user.home"), "." + appIdentifier);
        };
    }

    public Path getAppCacheDir() {
        return switch (Platform.current()) {
            case MACOS -> Path.of(System.getProperty("user.home"), "Library", "Caches", appIdentifier);
            case WINDOWS -> Path.of(System.getenv("LOCALAPPDATA"), appName, "Cache");
            case LINUX -> Path.of(System.getProperty("user.home"), ".cache", appIdentifier);
            case UNKNOWN -> Path.of(System.getProperty("java.io.tmpdir"), appIdentifier);
        };
    }

    public Path getAppLogDir() {
        return switch (Platform.current()) {
            case MACOS -> Path.of(System.getProperty("user.home"), "Library", "Logs", appIdentifier);
            case WINDOWS -> Path.of(System.getenv("LOCALAPPDATA"), appName, "Logs");
            case LINUX -> Path.of(System.getProperty("user.home"), ".local", "state", appIdentifier, "logs");
            case UNKNOWN -> Path.of(System.getProperty("java.io.tmpdir"), appIdentifier, "logs");
        };
    }

    public Path getHomeDir() {
        return Path.of(System.getProperty("user.home"));
    }

    public Path getTempDir() {
        return Path.of(System.getProperty("java.io.tmpdir"));
    }

    public Path getDesktopDir() {
        return Path.of(System.getProperty("user.home"), "Desktop");
    }

    public Path getDocumentsDir() {
        return Path.of(System.getProperty("user.home"), "Documents");
    }

    public Path getDownloadsDir() {
        return Path.of(System.getProperty("user.home"), "Downloads");
    }

    // Path manipulation utilities

    @KremaCommand("path:join")
    public String join(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }
        if (paths.size() == 1) {
            return paths.get(0);
        }
        Path result = Paths.get(paths.get(0));
        for (int i = 1; i < paths.size(); i++) {
            result = result.resolve(paths.get(i));
        }
        return result.toString();
    }

    @KremaCommand("path:dirname")
    public String dirname(String path) {
        Path parent = Paths.get(path).getParent();
        return parent != null ? parent.toString() : "";
    }

    @KremaCommand("path:basename")
    public String basename(String path, String ext) {
        String fileName = Paths.get(path).getFileName().toString();
        if (ext != null && !ext.isEmpty() && fileName.endsWith(ext)) {
            return fileName.substring(0, fileName.length() - ext.length());
        }
        return fileName;
    }

    @KremaCommand("path:extname")
    public String extname(String path) {
        String fileName = Paths.get(path).getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex);
        }
        return "";
    }

    @KremaCommand("path:resolve")
    public String resolve(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return Paths.get("").toAbsolutePath().toString();
        }
        Path result = Paths.get(paths.get(0));
        for (int i = 1; i < paths.size(); i++) {
            result = result.resolve(paths.get(i));
        }
        return result.toAbsolutePath().normalize().toString();
    }

    @KremaCommand("path:isAbsolute")
    public boolean isAbsolute(String path) {
        return Paths.get(path).isAbsolute();
    }

    @KremaCommand("path:normalize")
    public String normalize(String path) {
        return Paths.get(path).normalize().toString();
    }
}
