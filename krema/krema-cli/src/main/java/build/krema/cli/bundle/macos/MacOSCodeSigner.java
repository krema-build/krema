package build.krema.cli.bundle.macos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import build.krema.cli.KremaConfig.MacOSBundleConfig;

/**
 * Handles macOS code signing, notarization, and stapling for production bundles.
 */
public class MacOSCodeSigner {

    private final MacOSBundleConfig config;

    public MacOSCodeSigner(MacOSBundleConfig config) {
        this.config = config;
    }

    /**
     * Returns true if a signing identity is configured.
     */
    public boolean isSigningConfigured() {
        return config != null && config.getSigningIdentity() != null;
    }

    /**
     * Returns true if notarization credentials are configured.
     * Supports Apple ID mode (apple_id + team_id) or keychain profile mode (fallback).
     */
    public boolean isNotarizationConfigured() {
        if (config == null) return false;
        // Apple ID mode: both apple_id and team_id must be set
        if (config.getNotarizationAppleId() != null && config.getNotarizationTeamId() != null) {
            return true;
        }
        // Keychain profile mode is always available as a fallback
        return false;
    }

    /**
     * Signs the .app bundle with the configured signing identity.
     * Uses hardened runtime (required for notarization).
     * Generates default JVM entitlements if none are configured.
     */
    public void signApp(Path appDir) throws IOException {
        String identity = config.getSigningIdentity();
        Path entitlementsPath = resolveEntitlements();

        List<String> command = new ArrayList<>();
        command.add("codesign");
        command.add("--force");
        command.add("--deep");
        command.add("--sign");
        command.add(identity);
        command.add("--options");
        command.add("runtime");
        if (entitlementsPath != null) {
            command.add("--entitlements");
            command.add(entitlementsPath.toAbsolutePath().toString());
        }
        command.add(appDir.toAbsolutePath().toString());

        System.out.println("[CodeSign] Signing app bundle: " + appDir.getFileName());
        runProcess(command);
        System.out.println("[CodeSign] App bundle signed successfully");
    }

    /**
     * Signs the DMG file with the configured signing identity.
     */
    public void signDMG(Path dmgPath) throws IOException {
        String identity = config.getSigningIdentity();

        List<String> command = List.of(
            "codesign", "--force", "--sign", identity,
            dmgPath.toAbsolutePath().toString()
        );

        System.out.println("[CodeSign] Signing DMG: " + dmgPath.getFileName());
        runProcess(command);
        System.out.println("[CodeSign] DMG signed successfully");
    }

    /**
     * Submits the DMG to Apple for notarization and waits for completion.
     * Supports Apple ID mode (with env var password) or keychain profile mode.
     */
    public void notarize(Path dmgPath) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("xcrun");
        command.add("notarytool");
        command.add("submit");
        command.add(dmgPath.toAbsolutePath().toString());
        command.add("--wait");

        if (config.getNotarizationAppleId() != null && config.getNotarizationTeamId() != null) {
            // Apple ID mode
            String password = getApplePassword();
            command.add("--apple-id");
            command.add(config.getNotarizationAppleId());
            command.add("--team-id");
            command.add(config.getNotarizationTeamId());
            command.add("--password");
            command.add(password);
        } else {
            // Keychain profile mode
            command.add("--keychain-profile");
            command.add("krema-notarization");
        }

        System.out.println("[CodeSign] Submitting for notarization: " + dmgPath.getFileName());
        runProcess(command);
        System.out.println("[CodeSign] Notarization completed successfully");
    }

    /**
     * Staples the notarization ticket to the DMG so offline verification works.
     */
    public void staple(Path dmgPath) throws IOException {
        List<String> command = List.of(
            "xcrun", "stapler", "staple",
            dmgPath.toAbsolutePath().toString()
        );

        System.out.println("[CodeSign] Stapling notarization ticket: " + dmgPath.getFileName());
        runProcess(command);
        System.out.println("[CodeSign] Stapling completed successfully");
    }

    /**
     * Resolves the entitlements plist path. If the user configured one, validates it exists.
     * Otherwise, generates a default entitlements file with JVM-required entries.
     */
    private Path resolveEntitlements() throws IOException {
        if (config.getEntitlements() != null) {
            Path path = Path.of(config.getEntitlements());
            if (!Files.exists(path)) {
                throw new IOException("Entitlements file not found: " + path.toAbsolutePath());
            }
            return path;
        }
        return generateDefaultEntitlements();
    }

    /**
     * Generates a temporary entitlements plist with JVM-required entries.
     */
    private Path generateDefaultEntitlements() throws IOException {
        String plist = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>com.apple.security.cs.allow-jit</key>
                <true/>
                <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
                <true/>
                <key>com.apple.security.cs.allow-dyld-environment-variables</key>
                <true/>
            </dict>
            </plist>
            """;

        Path tempFile = Files.createTempFile("krema-entitlements-", ".plist");
        Files.writeString(tempFile, plist);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    private String getApplePassword() throws IOException {
        String password = System.getenv("KREMA_APPLE_PASSWORD");
        if (password == null) {
            password = System.getenv("APPLE_PASSWORD");
        }
        if (password == null) {
            throw new IOException(
                "Apple notarization password not found. " +
                "Set KREMA_APPLE_PASSWORD or APPLE_PASSWORD environment variable."
            );
        }
        return password;
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
                    "Command failed (exit code " + exitCode + "): " +
                    String.join(" ", command) + "\n" + output
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during code signing", e);
        }
    }
}
