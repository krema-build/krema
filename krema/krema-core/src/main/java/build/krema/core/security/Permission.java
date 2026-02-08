package build.krema.core.security;

/**
 * Permissions that can be granted to Krema applications.
 */
public enum Permission {

    // File system permissions
    FS_READ("fs:read", "Read files from the file system"),
    FS_WRITE("fs:write", "Write files to the file system"),
    FS_ALL("fs:*", "Full file system access"),

    // Clipboard permissions
    CLIPBOARD_READ("clipboard:read", "Read from clipboard"),
    CLIPBOARD_WRITE("clipboard:write", "Write to clipboard"),

    // Shell permissions
    SHELL_EXECUTE("shell:execute", "Execute shell commands"),
    SHELL_OPEN("shell:open", "Open files and URLs with default applications"),

    // System permissions
    NOTIFICATION("notification", "Show system notifications"),
    SYSTEM_TRAY("system-tray", "Access system tray"),
    SYSTEM_INFO("system-info", "Read system information"),

    // Network permissions
    NETWORK("network", "Access network resources"),

    // Dialog permissions
    DIALOG("dialog", "Show system dialogs"),

    // All permissions
    ALL("*", "Full access to all features");

    private final String key;
    private final String description;

    Permission(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Checks if this permission implies another permission.
     * For example, fs:* implies fs:read.
     */
    public boolean implies(Permission other) {
        if (this == other) {
            return true;
        }
        if (this == ALL) {
            return true;
        }
        if (this.key.endsWith(":*")) {
            String prefix = this.key.substring(0, this.key.length() - 1);
            return other.key.startsWith(prefix);
        }
        return false;
    }

    /**
     * Finds a permission by its key.
     */
    public static Permission fromKey(String key) {
        for (Permission p : values()) {
            if (p.key.equals(key)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Checks if a permission key matches this permission.
     */
    public boolean matches(String permissionKey) {
        if (this.key.equals(permissionKey)) {
            return true;
        }
        if (this.key.equals("*")) {
            return true;
        }
        if (this.key.endsWith(":*")) {
            String prefix = this.key.substring(0, this.key.length() - 1);
            return permissionKey.startsWith(prefix);
        }
        return false;
    }
}
