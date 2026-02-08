package build.krema.core.updater.install;

import java.io.IOException;
import java.nio.file.Path;

import build.krema.core.util.Logger;

/**
 * Windows update installer.
 * Handles .exe (silent install) and .msi (passive install).
 */
public class WindowsUpdateInstaller implements UpdateInstaller {

    private static final Logger LOG = new Logger("WindowsUpdateInstaller");

    @Override
    public void install(Path updateFile) throws IOException {
        String fileName = updateFile.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".exe")) {
            installExe(updateFile);
        } else if (fileName.endsWith(".msi")) {
            installMsi(updateFile);
        } else {
            throw new IOException("Unsupported update format for Windows: " + fileName);
        }
    }

    private void installExe(Path exe) throws IOException {
        LOG.info("Running installer with /SILENT: %s", exe);
        ProcessBuilder pb = new ProcessBuilder(exe.toString(), "/SILENT", "/NORESTART");
        pb.inheritIO();
        try {
            int exitCode = pb.start().waitFor();
            if (exitCode != 0) {
                throw new IOException("Installer exited with code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Installation interrupted", e);
        }
    }

    private void installMsi(Path msi) throws IOException {
        LOG.info("Running msiexec /passive: %s", msi);
        ProcessBuilder pb = new ProcessBuilder(
            "msiexec", "/i", msi.toString(), "/passive", "/norestart");
        pb.inheritIO();
        try {
            int exitCode = pb.start().waitFor();
            if (exitCode != 0) {
                throw new IOException("msiexec exited with code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Installation interrupted", e);
        }
    }

    @Override
    public void restart() throws IOException {
        String javaHome = System.getProperty("java.home");
        String javaBin = Path.of(javaHome, "bin", "java.exe").toString();
        String classpath = System.getProperty("java.class.path");
        String mainClass = System.getProperty("sun.java.command", "").split(" ")[0];

        if (!mainClass.isEmpty()) {
            LOG.info("Restarting: %s", mainClass);
            new ProcessBuilder(javaBin, "-cp", classpath, mainClass).start();
        }
        System.exit(0);
    }
}
