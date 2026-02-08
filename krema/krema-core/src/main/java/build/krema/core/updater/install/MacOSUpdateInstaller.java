package build.krema.core.updater.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import build.krema.core.util.Logger;

/**
 * macOS update installer.
 * Handles .tar.gz (extract and replace app bundle) and .dmg (open for manual install).
 */
public class MacOSUpdateInstaller implements UpdateInstaller {

    private static final Logger LOG = new Logger("MacOSUpdateInstaller");

    @Override
    public void install(Path updateFile) throws IOException {
        String fileName = updateFile.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
            installTarGz(updateFile);
        } else if (fileName.endsWith(".dmg")) {
            installDmg(updateFile);
        } else {
            throw new IOException("Unsupported update format for macOS: " + fileName);
        }
    }

    private void installTarGz(Path tarGz) throws IOException {
        Path appPath = getAppBundlePath();
        if (appPath == null) {
            throw new IOException("Cannot determine current .app bundle path");
        }

        Path tempDir = Files.createTempDirectory("krema-update-extract");
        LOG.info("Extracting %s to %s", tarGz, tempDir);

        ProcessBuilder extract = new ProcessBuilder("tar", "xzf", tarGz.toString(), "-C", tempDir.toString());
        extract.inheritIO();
        try {
            int exitCode = extract.start().waitFor();
            if (exitCode != 0) {
                throw new IOException("Failed to extract tar.gz (exit code " + exitCode + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Extraction interrupted", e);
        }

        // Find the .app directory in extracted contents
        Path extractedApp = findAppBundle(tempDir);
        if (extractedApp == null) {
            throw new IOException("No .app bundle found in extracted archive");
        }

        // Rename current app to .old, move new one in place
        Path oldApp = appPath.resolveSibling(appPath.getFileName() + ".old");
        LOG.info("Replacing %s (backup at %s)", appPath, oldApp);

        // Remove previous backup if it exists
        if (Files.exists(oldApp)) {
            deleteDirectory(oldApp);
        }

        Files.move(appPath, oldApp);
        try {
            Files.move(extractedApp, appPath);
        } catch (IOException e) {
            // Rollback: restore the old app
            LOG.error("Failed to move new app, rolling back", e);
            Files.move(oldApp, appPath);
            throw e;
        }

        // Clean up backup
        deleteDirectory(oldApp);
        LOG.info("Update installed successfully");
    }

    private void installDmg(Path dmg) throws IOException {
        LOG.info("Opening DMG for manual installation: %s", dmg);
        ProcessBuilder pb = new ProcessBuilder("open", dmg.toString());
        pb.start();
    }

    @Override
    public void restart() throws IOException {
        Path appPath = getAppBundlePath();
        if (appPath != null) {
            LOG.info("Restarting application: %s", appPath);
            new ProcessBuilder("open", "-n", appPath.toString()).start();
        } else {
            // Fallback: re-launch the java process
            String javaHome = System.getProperty("java.home");
            String javaBin = Path.of(javaHome, "bin", "java").toString();
            String classpath = System.getProperty("java.class.path");
            String mainClass = System.getProperty("sun.java.command", "").split(" ")[0];

            if (!mainClass.isEmpty()) {
                LOG.info("Restarting via java: %s", mainClass);
                new ProcessBuilder(javaBin, "-cp", classpath, mainClass).start();
            }
        }
        System.exit(0);
    }

    private Path getAppBundlePath() {
        // Try to find the .app bundle from the current working directory or code location
        String userDir = System.getProperty("user.dir");
        Path current = Path.of(userDir);

        // Walk up to find a .app parent
        while (current != null) {
            if (current.getFileName() != null &&
                current.getFileName().toString().endsWith(".app")) {
                return current;
            }
            current = current.getParent();
        }

        // Try from the code source location
        try {
            Path codeLocation = Path.of(
                MacOSUpdateInstaller.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            current = codeLocation;
            while (current != null) {
                if (current.getFileName() != null &&
                    current.getFileName().toString().endsWith(".app")) {
                    return current;
                }
                current = current.getParent();
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private Path findAppBundle(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream
                .filter(p -> p.getFileName().toString().endsWith(".app"))
                .findFirst()
                .orElse(null);
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("rm", "-rf", dir.toString());
        try {
            pb.start().waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Delete interrupted", e);
        }
    }
}
