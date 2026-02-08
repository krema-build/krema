package build.krema.core.notification.windows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import build.krema.core.notification.NotificationEngine;

/**
 * Windows NotificationEngine implementation using PowerShell toast notifications.
 * Uses the Windows.UI.Notifications API via PowerShell for modern toast notifications.
 */
public final class WindowsNotificationEngine implements NotificationEngine {

    @Override
    public boolean show(String title, String body, Map<String, Object> options) {
        String appId = "KremaApp";
        if (options != null && options.containsKey("appId")) {
            appId = options.get("appId").toString();
        }

        String escapedTitle = escapeXml(title != null ? title : "");
        String escapedBody = escapeXml(body != null ? body : "");

        String script = String.format("""
            [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
            [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom, ContentType = WindowsRuntime] | Out-Null

            $template = @"
            <toast>
              <visual>
                <binding template="ToastGeneric">
                  <text>%s</text>
                  <text>%s</text>
                </binding>
              </visual>
            </toast>
            "@

            $xml = New-Object Windows.Data.Xml.Dom.XmlDocument
            $xml.LoadXml($template)
            $toast = [Windows.UI.Notifications.ToastNotification]::new($xml)
            $notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('%s')
            $notifier.Show($toast)
            """, escapedTitle, escapedBody, escapePS(appId));

        return execPowerShell(script);
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String escapePS(String s) {
        return s.replace("'", "''");
    }

    private static boolean execPowerShell(String script) {
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true)
                .start();
            try (var is = p.getInputStream()) {
                is.readAllBytes();
            }
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
