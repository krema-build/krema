package build.krema.cli;

import build.krema.core.platform.PlatformDetector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Discovers a Java 25 installation on the system.
 * <p>
 * Search order:
 * <ol>
 *   <li>{@code KREMA_JAVA_HOME} environment variable</li>
 *   <li>{@code JAVA_HOME} environment variable (if version matches)</li>
 *   <li>macOS: {@code /usr/libexec/java_home -v 25}</li>
 *   <li>Linux: {@code update-alternatives --query java} (if version matches)</li>
 *   <li>{@code java} on PATH (if version matches)</li>
 *   <li>{@code ~/.krema/jdk/temurin-25/} (auto-installed by npm shim)</li>
 * </ol>
 */
public final class JdkResolver {

    private static final String REQUIRED_VERSION = "25";
    private static final Path KREMA_JDK_DIR = Path.of(
            System.getProperty("user.home"), ".krema", "jdk", "temurin-" + REQUIRED_VERSION
    );

    private JdkResolver() {}

    /**
     * Resolves the JAVA_HOME directory for a Java 25 installation.
     * Returns null if no suitable JDK is found.
     */
    public static Path resolve() {
        // 1. KREMA_JAVA_HOME (explicit override)
        Path found = fromEnv("KREMA_JAVA_HOME");
        if (found != null) return found;

        // 2. JAVA_HOME (if version matches)
        found = fromJavaHomeEnv();
        if (found != null) return found;

        // 3. Platform-specific discovery
        if (PlatformDetector.isMacOS()) {
            found = fromMacOSJavaHome();
            if (found != null) return found;
        }
        if (PlatformDetector.isLinux()) {
            found = fromLinuxAlternatives();
            if (found != null) return found;
        }

        // 4. java on PATH (derive JAVA_HOME from it)
        found = fromPathJava();
        if (found != null) return found;

        // 5. Auto-installed Temurin JDK
        found = fromTemurinInstall();
        if (found != null) return found;

        // 6. Current JVM (we're already running on it)
        found = fromCurrentJvm();
        if (found != null) return found;

        return null;
    }

    /**
     * Resolves JAVA_HOME or prints an error and returns null.
     */
    public static Path resolveOrWarn() {
        Path javaHome = resolve();
        if (javaHome == null) {
            System.err.println("[Krema] Java " + REQUIRED_VERSION + " is required but was not found.");
            System.err.println("[Krema] Set KREMA_JAVA_HOME or JAVA_HOME to a JDK " + REQUIRED_VERSION + " installation,");
            System.err.println("[Krema] or install one from: https://adoptium.net/temurin/releases/");
        }
        return javaHome;
    }

    private static Path fromEnv(String envVar) {
        String value = System.getenv(envVar);
        if (value == null || value.isBlank()) return null;
        Path home = Path.of(value);
        if (hasJavaBinary(home)) return home;
        return null;
    }

    private static Path fromJavaHomeEnv() {
        String value = System.getenv("JAVA_HOME");
        if (value == null || value.isBlank()) return null;
        Path home = Path.of(value);
        if (hasJavaBinary(home) && checkVersion(home.resolve(javaBinaryName()))) {
            return home;
        }
        return null;
    }

    private static Path fromMacOSJavaHome() {
        try {
            var pb = new ProcessBuilder("/usr/libexec/java_home", "-v", REQUIRED_VERSION);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (process.waitFor() == 0 && line != null && !line.isBlank()) {
                    Path home = Path.of(line.trim());
                    if (hasJavaBinary(home)) return home;
                }
            }
        } catch (IOException | InterruptedException ignored) {}
        return null;
    }

    private static Path fromLinuxAlternatives() {
        String javaPath = readAlternativesJavaPath();
        if (javaPath == null) return null;

        // /usr/lib/jvm/graalvm-25/bin/java -> /usr/lib/jvm/graalvm-25
        Path binDir = Path.of(javaPath).getParent();
        if (binDir == null) return null;

        Path home = binDir.getParent();
        if (home != null && hasJavaBinary(home) && checkVersion(Path.of(javaPath))) {
            return home;
        }
        return null;
    }

    private static String readAlternativesJavaPath() {
        try {
            var pb = new ProcessBuilder("update-alternatives", "--query", "java");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Value:")) {
                        process.waitFor();
                        return line.substring("Value:".length()).trim();
                    }
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException ignored) {}
        return null;
    }

    private static Path fromPathJava() {
        String whichCmd = PlatformDetector.isWindows() ? "where" : "which";
        try {
            var pb = new ProcessBuilder(whichCmd, "java");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (process.waitFor() == 0 && line != null && !line.isBlank()) {
                    Path javaBin = Path.of(line.trim()).toRealPath();
                    if (checkVersion(javaBin)) {
                        // Try deriving JAVA_HOME from binary path: bin/java -> bin -> JAVA_HOME
                        Path home = javaBin.getParent().getParent();
                        if (hasJavaBinary(home)) return home;

                        // Binary may be in a shim directory (e.g. Oracle's javapath on Windows).
                        // Ask the binary itself for its java.home.
                        home = queryJavaHome(javaBin);
                        if (home != null && hasJavaBinary(home)) return home;
                    }
                }
            }
        } catch (IOException | InterruptedException ignored) {}
        return null;
    }

    private static Path queryJavaHome(Path javaBin) {
        // Java 25+ uses "properties", older versions use "property"
        Path home = queryJavaHomeWith(javaBin, "-XshowSettings:properties");
        if (home != null) return home;
        return queryJavaHomeWith(javaBin, "-XshowSettings:property");
    }

    private static Path queryJavaHomeWith(Path javaBin, String showSettingsFlag) {
        try {
            var pb = new ProcessBuilder(javaBin.toString(), showSettingsFlag, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("java.home")) {
                        int eq = trimmed.indexOf('=');
                        if (eq >= 0) {
                            Path home = Path.of(trimmed.substring(eq + 1).trim());
                            process.destroyForcibly();
                            return home;
                        }
                    }
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException ignored) {}
        return null;
    }

    private static Path fromCurrentJvm() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) return null;
        Path home = Path.of(javaHome);
        if (hasJavaBinary(home) && checkVersion(home.resolve(javaBinaryName()))) {
            return home;
        }
        return null;
    }

    private static Path fromTemurinInstall() {
        if (hasJavaBinary(KREMA_JDK_DIR)) return KREMA_JDK_DIR;

        // On macOS, Temurin extracts with a Contents/Home structure
        Path macosHome = KREMA_JDK_DIR.resolve("Contents/Home");
        if (hasJavaBinary(macosHome)) return macosHome;

        return null;
    }

    private static boolean hasJavaBinary(Path javaHome) {
        if (javaHome == null) return false;
        return Files.isExecutable(javaHome.resolve(javaBinaryName()));
    }

    private static String javaBinaryName() {
        return PlatformDetector.isWindows() ? "bin/java.exe" : "bin/java";
    }

    private static boolean checkVersion(Path javaBin) {
        try {
            var pb = new ProcessBuilder(javaBin.toString(), "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
                process.waitFor();
                return parseVersion(output.toString());
            }
        } catch (IOException | InterruptedException ignored) {}
        return false;
    }

    private static boolean parseVersion(String text) {
        // java -version output: openjdk version "25" or "25.0.1" etc.
        var matcher = java.util.regex.Pattern.compile("\"(\\d+)").matcher(text);
        return matcher.find() && REQUIRED_VERSION.equals(matcher.group(1));
    }
}
