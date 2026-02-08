package build.krema.core.updater.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import build.krema.core.util.Logger;

/**
 * Linux update installer.
 * Handles .AppImage (replace and chmod +x) and .tar.gz (extract and replace).
 */
public class LinuxUpdateInstaller implements UpdateInstaller {

    private static final Logger LOG = new Logger("LinuxUpdateInstaller");

    @Override
    public void install(Path updateFile) throws IOException {
        String fileName = updateFile.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".appimage")) {
            installAppImage(updateFile);
        } else if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
            installTarGz(updateFile);
        } else {
            throw new IOException("Unsupported update format for Linux: " + fileName);
        }
    }

    private void installAppImage(Path appImage) throws IOException {
        Path currentAppImage = getCurrentAppImagePath();
        if (currentAppImage == null) {
            throw new IOException("Cannot determine current AppImage path. " +
                "Set the APPIMAGE environment variable or run from an AppImage.");
        }

        LOG.info("Replacing AppImage: %s", currentAppImage);

        // Backup current
        Path backup = currentAppImage.resolveSibling(currentAppImage.getFileName() + ".old");
        if (Files.exists(backup)) {
            Files.delete(backup);
        }
        Files.move(currentAppImage, backup);

        try {
            Files.copy(appImage, currentAppImage);
            Files.setPosixFilePermissions(currentAppImage, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_EXECUTE
            ));
        } catch (IOException e) {
            LOG.error("Failed to install AppImage, rolling back", e);
            Files.move(backup, currentAppImage);
            throw e;
        }

        Files.deleteIfExists(backup);
        LOG.info("AppImage updated successfully");
    }

    private void installTarGz(Path tarGz) throws IOException {
        Path installDir = getInstallDirectory();
        if (installDir == null) {
            throw new IOException("Cannot determine installation directory");
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

        // Copy extracted contents to install directory
        ProcessBuilder cp = new ProcessBuilder("cp", "-rf", tempDir.toString() + "/.", installDir.toString());
        cp.inheritIO();
        try {
            int exitCode = cp.start().waitFor();
            if (exitCode != 0) {
                throw new IOException("Failed to copy updated files (exit code " + exitCode + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Copy interrupted", e);
        }

        LOG.info("Update installed to %s", installDir);
    }

    @Override
    public void restart() throws IOException {
        Path appImage = getCurrentAppImagePath();
        if (appImage != null) {
            LOG.info("Restarting AppImage: %s", appImage);
            new ProcessBuilder(appImage.toString()).start();
        } else {
            String javaHome = System.getProperty("java.home");
            String javaBin = Path.of(javaHome, "bin", "java").toString();
            String classpath = System.getProperty("java.class.path");
            String mainClass = System.getProperty("sun.java.command", "").split(" ")[0];

            if (!mainClass.isEmpty()) {
                LOG.info("Restarting: %s", mainClass);
                new ProcessBuilder(javaBin, "-cp", classpath, mainClass).start();
            }
        }
        System.exit(0);
    }

    private Path getCurrentAppImagePath() {
        String appImageEnv = System.getenv("APPIMAGE");
        if (appImageEnv != null && !appImageEnv.isBlank()) {
            return Path.of(appImageEnv);
        }
        return null;
    }

    private Path getInstallDirectory() {
        // Try code source location
        try {
            Path codeLocation = Path.of(
                LinuxUpdateInstaller.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return codeLocation.getParent();
        } catch (Exception e) {
            return null;
        }
    }
}
