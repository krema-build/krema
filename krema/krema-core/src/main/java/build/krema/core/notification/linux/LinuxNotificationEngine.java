package build.krema.core.notification.linux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import build.krema.core.notification.NotificationEngine;

/**
 * Linux NotificationEngine implementation using notify-send.
 */
public final class LinuxNotificationEngine implements NotificationEngine {

    private final boolean notifySendAvailable;

    public LinuxNotificationEngine() {
        notifySendAvailable = isCommandAvailable("notify-send");
    }

    @Override
    public boolean show(String title, String body, Map<String, Object> options) {
        if (!notifySendAvailable) return false;

        List<String> cmd = new ArrayList<>();
        cmd.add("notify-send");
        cmd.add(title != null ? title : "");
        cmd.add(body != null ? body : "");

        if (options != null) {
            Object icon = options.get("icon");
            if (icon instanceof String iconPath && !iconPath.isEmpty()) {
                cmd.add("-i");
                cmd.add(iconPath);
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    @Override
    public boolean isSupported() {
        return notifySendAvailable;
    }

    private static boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
