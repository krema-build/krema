package build.krema.core.api.notification;

import java.io.IOException;
import java.util.Map;

import build.krema.core.KremaCommand;
import build.krema.core.notification.NotificationEngine;
import build.krema.core.notification.NotificationEngineFactory;
import build.krema.core.platform.Platform;

/**
 * System notification utilities.
 * On macOS: Uses native NSUserNotificationCenter via FFM.
 */
public class Notification {

    private final NotificationEngine engine = NotificationEngineFactory.get();

    @KremaCommand("notification:show")
    public boolean show(String title, String body, Map<String, Object> options) {
        // Use native engine if available
        if (engine != null) {
            return engine.show(title, body, options);
        }

        // Fallback to script-based approach
        return switch (Platform.current()) {
            case MACOS -> showMacOSFallback(title, body, options);
            case WINDOWS -> showWindows(title, body, options);
            case LINUX -> showLinux(title, body, options);
            case UNKNOWN -> false;
        };
    }

    @KremaCommand("notification:showSimple")
    public boolean showSimple(String title, String body) {
        return show(title, body, null);
    }

    private boolean showMacOSFallback(String title, String body, Map<String, Object> options) {
        try {
            // Fallback: Use osascript for macOS notifications
            String script = String.format(
                "display notification \"%s\" with title \"%s\"",
                escapeAppleScript(body),
                escapeAppleScript(title)
            );

            if (options != null && options.containsKey("sound")) {
                String sound = options.get("sound").toString();
                script += " sound name \"" + escapeAppleScript(sound) + "\"";
            }

            ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private boolean showWindows(String title, String body, Map<String, Object> options) {
        try {
            // Use PowerShell for Windows 10+ notifications
            String script = String.format(
                "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null; " +
                "[Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null; " +
                "$template = @\"<toast><visual><binding template=\"ToastText02\"><text id=\"1\">%s</text><text id=\"2\">%s</text></binding></visual></toast>\"@; " +
                "$xml = New-Object Windows.Data.Xml.Dom.XmlDocument; " +
                "$xml.LoadXml($template); " +
                "$toast = [Windows.UI.Notifications.ToastNotification]::new($xml); " +
                "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('Krema').Show($toast)",
                escapeForPowerShell(title),
                escapeForPowerShell(body)
            );

            ProcessBuilder pb = new ProcessBuilder("powershell", "-Command", script);
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private boolean showLinux(String title, String body, Map<String, Object> options) {
        try {
            // Use notify-send for Linux
            ProcessBuilder pb;
            if (options != null && options.containsKey("icon")) {
                pb = new ProcessBuilder("notify-send", "-i", options.get("icon").toString(), title, body);
            } else {
                pb = new ProcessBuilder("notify-send", title, body);
            }
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private String escapeAppleScript(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeForPowerShell(String text) {
        if (text == null) return "";
        return text.replace("`", "``").replace("\"", "`\"").replace("$", "`$");
    }

    @KremaCommand("notification:isSupported")
    public boolean isSupported() {
        if (engine != null) {
            return engine.isSupported();
        }
        return Platform.current() != Platform.UNKNOWN;
    }
}
