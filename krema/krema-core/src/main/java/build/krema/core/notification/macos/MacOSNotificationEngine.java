package build.krema.core.notification.macos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import build.krema.core.notification.NotificationEngine;

/**
 * macOS NotificationEngine implementation.
 *
 * Uses a pre-compiled Swift helper binary for native notifications.
 * The helper is extracted from resources on first use and cached in ~/.cache/krema.
 */
public final class MacOSNotificationEngine implements NotificationEngine {

    private static final String HELPER_RESOURCE = "/krema-notify";
    private static final String HELPER_VERSION = "1.0";
    private Path helperPath;
    private boolean helperAvailable = false;
    private boolean helperChecked = false;

    @Override
    public boolean show(String title, String body, Map<String, Object> options) {
        // Try native helper first
        if (ensureHelper()) {
            try {
                return showWithHelper(title, body, options);
            } catch (Exception e) {
                System.err.println("[MacOSNotificationEngine] Helper failed: " + e.getMessage());
            }
        }

        // Fall back to osascript
        return showWithOsascript(title, body, options);
    }

    private synchronized boolean ensureHelper() {
        if (helperChecked) {
            return helperAvailable;
        }
        helperChecked = true;

        try {
            // Create helper as a proper .app bundle (required for UNUserNotificationCenter)
            Path cacheDir = Path.of(System.getProperty("user.home"), ".cache", "krema");
            Files.createDirectories(cacheDir);

            Path appBundle = cacheDir.resolve("KremaNotify.app");
            Path contentsDir = appBundle.resolve("Contents");
            Path macOSDir = contentsDir.resolve("MacOS");
            helperPath = macOSDir.resolve("KremaNotify");

            // Version file to track when to update the helper
            Path versionFile = contentsDir.resolve(".version");

            // Check if helper app already exists with correct version
            if (Files.exists(helperPath) && Files.isExecutable(helperPath) &&
                Files.exists(versionFile) && HELPER_VERSION.equals(Files.readString(versionFile).trim())) {
                helperAvailable = true;
                return true;
            }

            System.out.println("[MacOSNotificationEngine] Extracting notification helper...");

            // Create bundle structure
            Files.createDirectories(macOSDir);

            // Create Info.plist
            String infoPlist = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>CFBundleIdentifier</key>
                    <string>build.krema.notify</string>
                    <key>CFBundleName</key>
                    <string>KremaNotify</string>
                    <key>CFBundleExecutable</key>
                    <string>KremaNotify</string>
                    <key>CFBundlePackageType</key>
                    <string>APPL</string>
                    <key>CFBundleVersion</key>
                    <string>1.0</string>
                    <key>LSBackgroundOnly</key>
                    <true/>
                    <key>LSUIElement</key>
                    <true/>
                </dict>
                </plist>
                """;
            Files.writeString(contentsDir.resolve("Info.plist"), infoPlist);

            // Extract pre-compiled universal binary from resources
            try (InputStream is = getClass().getResourceAsStream(HELPER_RESOURCE)) {
                if (is == null) {
                    System.err.println("[MacOSNotificationEngine] krema-notify binary not found in resources");
                    return false;
                }
                Files.copy(is, helperPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Make executable
            helperPath.toFile().setExecutable(true, false);

            // Sign the app bundle (required for Gatekeeper)
            ProcessBuilder signPb = new ProcessBuilder(
                "codesign", "--force", "--deep", "--sign", "-", appBundle.toString()
            );
            signPb.redirectErrorStream(true);
            Process signProcess = signPb.start();
            signProcess.waitFor();

            // Write version file
            Files.writeString(versionFile, HELPER_VERSION);

            System.out.println("[MacOSNotificationEngine] Helper ready");
            helperAvailable = true;
            return true;

        } catch (Exception e) {
            System.err.println("[MacOSNotificationEngine] Failed to setup helper: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean showWithHelper(String title, String body, Map<String, Object> options)
            throws IOException, InterruptedException {

        String subtitle = options != null && options.containsKey("subtitle")
            ? options.get("subtitle").toString() : "";
        String sound = options != null && options.containsKey("sound") ? "true" : "false";

        // Get the app bundle path (parent of Contents/MacOS/KremaNotify)
        Path appBundle = helperPath.getParent().getParent().getParent();

        System.out.println("[MacOSNotificationEngine] Launching helper: " + appBundle);
        System.out.println("[MacOSNotificationEngine] Args: title=" + title + ", body=" + body);

        // Use 'open' to launch the helper app with arguments
        // This ensures proper bundle context for UNUserNotificationCenter
        ProcessBuilder pb = new ProcessBuilder(
            "open", "-a", appBundle.toString(),
            "--args",
            title != null ? title : "",
            body != null ? body : "",
            subtitle,
            sound
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        // 'open' returns immediately, give the helper time to run
        Thread.sleep(500);

        if (exitCode == 0) {
            System.out.println("[MacOSNotificationEngine] Notification sent via native helper");
            return true;
        } else {
            System.err.println("[MacOSNotificationEngine] open command failed (exit=" + exitCode + "): " + output);
            return false;
        }
    }

    private boolean showWithOsascript(String title, String body, Map<String, Object> options) {
        try {
            StringBuilder script = new StringBuilder();
            script.append("display notification ");
            script.append("\"").append(escapeAppleScript(body != null ? body : "")).append("\"");
            script.append(" with title ");
            script.append("\"").append(escapeAppleScript(title != null ? title : "")).append("\"");

            if (options != null && options.containsKey("sound")) {
                String sound = options.get("sound").toString();
                if ("default".equals(sound)) {
                    script.append(" sound name \"default\"");
                } else {
                    script.append(" sound name \"").append(escapeAppleScript(sound)).append("\"");
                }
            }

            if (options != null && options.containsKey("subtitle")) {
                String subtitle = options.get("subtitle").toString();
                script.append(" subtitle \"").append(escapeAppleScript(subtitle)).append("\"");
            }

            System.out.println("[MacOSNotificationEngine] Showing notification via osascript");

            ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                System.err.println("[MacOSNotificationEngine] osascript failed: " + output);
            }

            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            System.err.println("[MacOSNotificationEngine] Failed to show notification: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    private String escapeAppleScript(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
