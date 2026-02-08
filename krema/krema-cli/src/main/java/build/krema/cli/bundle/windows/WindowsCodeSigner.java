package build.krema.cli.bundle.windows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import build.krema.cli.KremaConfig.WindowsBundleConfig;

/**
 * Handles Windows code signing using signtool for production bundles.
 */
public class WindowsCodeSigner {

    private final WindowsBundleConfig config;

    public WindowsCodeSigner(WindowsBundleConfig config) {
        this.config = config;
    }

    /**
     * Returns true if a signing certificate is configured.
     */
    public boolean isSigningConfigured() {
        return config != null && config.getSigningCertificate() != null;
    }

    /**
     * Signs the executable with the configured certificate.
     */
    public void sign(Path exePath) throws IOException {
        String signtool = findSigntool();
        if (signtool == null) {
            throw new IOException(
                "signtool.exe not found. Install the Windows SDK or ensure signtool is on your PATH. " +
                "Download from: https://developer.microsoft.com/en-us/windows/downloads/windows-sdk/"
            );
        }

        Path certPath = Path.of(config.getSigningCertificate());
        if (!Files.exists(certPath)) {
            throw new IOException("Signing certificate not found: " + certPath.toAbsolutePath());
        }

        String password = System.getenv("KREMA_WINDOWS_SIGN_PASSWORD");
        if (password == null) {
            throw new IOException(
                "Signing password not found. Set KREMA_WINDOWS_SIGN_PASSWORD environment variable."
            );
        }

        List<String> command = List.of(
            signtool, "sign",
            "/f", certPath.toAbsolutePath().toString(),
            "/p", password,
            "/tr", config.getTimestampUrl(),
            "/td", "SHA256",
            "/fd", "SHA256",
            exePath.toAbsolutePath().toString()
        );

        System.out.println("[CodeSign] Signing executable: " + exePath.getFileName());
        runProcess(command);
        System.out.println("[CodeSign] Executable signed successfully");
    }

    /**
     * Locates signtool.exe by searching PATH and common Windows SDK locations.
     */
    private String findSigntool() {
        // Check PATH
        try {
            Process p = new ProcessBuilder("where", "signtool").start();
            String output = new String(p.getInputStream().readAllBytes()).strip();
            if (p.waitFor() == 0 && !output.isEmpty()) {
                return output.lines().findFirst().orElse(null);
            }
        } catch (Exception ignored) {
        }

        // Check common Windows SDK locations
        String[] candidates = {
            "C:\\Program Files (x86)\\Windows Kits\\10\\bin\\x64\\signtool.exe",
            "C:\\Program Files (x86)\\Windows Kits\\10\\App Certification Kit\\signtool.exe"
        };
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isExecutable(path)) {
                return path.toString();
            }
        }

        return null;
    }

    private void runProcess(List<String> command) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new IOException(
                    "Signing failed (exit code " + exitCode + "): " + output
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during code signing", e);
        }
    }
}
