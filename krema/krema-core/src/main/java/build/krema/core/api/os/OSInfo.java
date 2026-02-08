package build.krema.core.api.os;

import java.net.InetAddress;
import java.util.Locale;
import java.util.Map;

import build.krema.core.KremaCommand;

/**
 * Operating system information API.
 * Provides details about the platform, architecture, and system configuration.
 */
public class OSInfo {

    @KremaCommand("os:platform")
    public String platform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return "macos";
        } else if (os.contains("win")) {
            return "windows";
        } else if (os.contains("linux")) {
            return "linux";
        } else {
            return "unknown";
        }
    }

    @KremaCommand("os:arch")
    public String arch() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "arm64";
        } else if (arch.contains("amd64") || arch.contains("x86_64")) {
            return "x64";
        } else if (arch.contains("x86") || arch.contains("i386") || arch.contains("i686")) {
            return "x86";
        } else if (arch.contains("arm")) {
            return "arm";
        } else {
            return arch;
        }
    }

    @KremaCommand("os:version")
    public String version() {
        return System.getProperty("os.version", "unknown");
    }

    @KremaCommand("os:hostname")
    public String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            // Fallback to environment variable
            String hostname = System.getenv("HOSTNAME");
            if (hostname == null || hostname.isEmpty()) {
                hostname = System.getenv("COMPUTERNAME");
            }
            return hostname != null ? hostname : "unknown";
        }
    }

    @KremaCommand("os:username")
    public String username() {
        return System.getProperty("user.name", "unknown");
    }

    @KremaCommand("os:homeDir")
    public String homeDir() {
        return System.getProperty("user.home", "");
    }

    @KremaCommand("os:tempDir")
    public String tempDir() {
        return System.getProperty("java.io.tmpdir", "");
    }

    @KremaCommand("os:locale")
    public Map<String, String> locale() {
        Locale locale = Locale.getDefault();
        return Map.of(
            "language", locale.getLanguage(),
            "country", locale.getCountry(),
            "displayName", locale.getDisplayName(),
            "tag", locale.toLanguageTag()
        );
    }

    @KremaCommand("os:cpuCount")
    public int cpuCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    @KremaCommand("os:memory")
    public Map<String, Long> memory() {
        Runtime runtime = Runtime.getRuntime();
        return Map.of(
            "total", runtime.totalMemory(),
            "free", runtime.freeMemory(),
            "max", runtime.maxMemory(),
            "used", runtime.totalMemory() - runtime.freeMemory()
        );
    }

    @KremaCommand("os:info")
    public Map<String, Object> info() {
        return Map.of(
            "platform", platform(),
            "arch", arch(),
            "version", version(),
            "hostname", hostname(),
            "username", username(),
            "homeDir", homeDir(),
            "tempDir", tempDir(),
            "locale", locale(),
            "cpuCount", cpuCount(),
            "memory", memory()
        );
    }

    @KremaCommand("os:env")
    public String env(Map<String, Object> options) {
        String name = (String) options.get("name");
        if (name == null) {
            return null;
        }
        return System.getenv(name);
    }

    @KremaCommand("os:envAll")
    public Map<String, String> envAll() {
        return System.getenv();
    }
}
