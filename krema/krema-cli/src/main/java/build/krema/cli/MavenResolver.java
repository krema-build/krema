package build.krema.cli;

import build.krema.core.platform.PlatformDetector;

import java.io.IOException;

/**
 * Checks that Maven is available on the system.
 */
public final class MavenResolver {

    private MavenResolver() {}

    /**
     * Checks that Maven is available on PATH.
     * Prints an error and returns false if not found.
     */
    public static boolean checkAvailable() {
        String whichCmd = PlatformDetector.isWindows() ? "where" : "which";
        try {
            var pb = new ProcessBuilder(whichCmd, "mvn");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return true;
            }
        } catch (IOException | InterruptedException ignored) {}

        System.err.println("[Krema] Maven is required but was not found on PATH.");
        System.err.println("[Krema] Install it with:");
        System.err.println("[Krema]   Windows:  choco install maven  (or scoop install maven)");
        System.err.println("[Krema]   macOS:    brew install maven");
        System.err.println("[Krema]   Linux:    sudo apt install maven  (or your package manager)");
        return false;
    }
}
