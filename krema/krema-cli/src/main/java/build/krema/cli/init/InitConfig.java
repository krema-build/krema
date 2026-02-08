package build.krema.cli.init;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for project initialization.
 * Holds all options from quick, guided, and wizard modes.
 */
public class InitConfig {

    /**
     * Available Krema plugins.
     */
    public enum Plugin {
        // Built-in plugins (included in krema-core)
        FS("krema.fs", "File System", "File system operations", true, true,
                List.of("fs:read", "fs:write"), null),
        LOG("krema.log", "Logging", "Frontend logging with file rotation", true, true,
                List.of("log:write"), null),
        DEEP_LINK("krema.deeplink", "Deep Linking", "Handle custom URL schemes", true, false,
                List.of(), null),
        UPDATER("krema.updater", "Auto-updater", "Application update functionality", true, false,
                List.of("network"), null),

        // External plugins (separate Maven dependencies)
        UPLOAD("krema-plugin-upload", "File Upload", "Multipart file uploads with progress", false, false,
                List.of("network", "upload:send"), "krema-plugin-upload"),
        WEBSOCKET("krema-plugin-websocket", "WebSocket", "WebSocket connection support", false, false,
                List.of("network"), "krema-plugin-websocket"),
        SQL("krema-plugin-sql", "SQLite Database", "SQLite database operations", false, false,
                List.of("fs:read", "fs:write"), "krema-plugin-sql"),
        AUTOSTART("krema-plugin-autostart", "Autostart", "Launch app on system startup", false, false,
                List.of(), "krema-plugin-autostart"),
        POSITIONER("krema-plugin-positioner", "Window Positioner", "Window positioning utilities", false, false,
                List.of(), "krema-plugin-positioner");

        private final String id;
        private final String displayName;
        private final String description;
        private final boolean builtIn;
        private final boolean defaultEnabled;
        private final List<String> requiredPermissions;
        private final String artifactId; // null for built-in plugins

        Plugin(String id, String displayName, String description, boolean builtIn,
               boolean defaultEnabled, List<String> requiredPermissions, String artifactId) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.builtIn = builtIn;
            this.defaultEnabled = defaultEnabled;
            this.requiredPermissions = requiredPermissions;
            this.artifactId = artifactId;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public boolean isBuiltIn() { return builtIn; }
        public boolean isDefaultEnabled() { return defaultEnabled; }
        public List<String> getRequiredPermissions() { return requiredPermissions; }
        public String getArtifactId() { return artifactId; }

        public String getLabel() {
            String suffix = builtIn ? " (built-in)" : "";
            return displayName + suffix;
        }
    }

    // Essential (Quick mode)
    private String appName;
    private String template = "vanilla";

    // Guided mode additions
    private String windowTitle;
    private String identifier;
    private boolean typescript = true;

    // Wizard mode additions
    private String description;
    private String packageManager = "npm";
    private int windowWidth = 1024;
    private int windowHeight = 768;
    private boolean resizable = true;
    private List<String> permissions = List.of("clipboard:read", "clipboard:write");
    private String deepLinkScheme;
    private String javaPackage = "com.example.app";
    private List<Plugin> plugins = List.of(Plugin.FS, Plugin.LOG); // Default plugins

    public InitConfig() {
    }

    public InitConfig(String appName) {
        this.appName = appName;
    }

    // Computed getters with defaults
    public String getWindowTitle() {
        return windowTitle != null ? windowTitle : toTitleCase(appName);
    }

    public String getIdentifier() {
        if (identifier != null) {
            return identifier;
        }
        return "com.example." + appName.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public String getDescription() {
        return description != null ? description : "A Krema desktop application";
    }

    // Utility method
    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                result.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            } else {
                if (result.length() > 0) {
                    result.append(' ');
                }
                capitalizeNext = true;
            }
        }
        return result.toString();
    }

    // Standard getters and setters
    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public void setWindowTitle(String windowTitle) {
        this.windowTitle = windowTitle;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public boolean isTypescript() {
        return typescript;
    }

    public void setTypescript(boolean typescript) {
        this.typescript = typescript;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPackageManager() {
        return packageManager;
    }

    public void setPackageManager(String packageManager) {
        this.packageManager = packageManager;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(int windowWidth) {
        this.windowWidth = windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(int windowHeight) {
        this.windowHeight = windowHeight;
    }

    public boolean isResizable() {
        return resizable;
    }

    public void setResizable(boolean resizable) {
        this.resizable = resizable;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public String getDeepLinkScheme() {
        return deepLinkScheme;
    }

    public void setDeepLinkScheme(String deepLinkScheme) {
        this.deepLinkScheme = deepLinkScheme;
    }

    public String getJavaPackage() {
        return javaPackage;
    }

    public void setJavaPackage(String javaPackage) {
        this.javaPackage = javaPackage;
    }

    /**
     * Get the Java package path (e.g., "com/example/app" from "com.example.app")
     */
    public String getJavaPackagePath() {
        return javaPackage.replace('.', '/');
    }

    /**
     * Get artifact ID suitable for pom.xml (lowercase, hyphens only)
     */
    public String getArtifactId() {
        return appName.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }

    /**
     * Get package name suitable for package.json (lowercase, hyphens only)
     */
    public String getPackageName() {
        return appName.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }

    public List<Plugin> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<Plugin> plugins) {
        this.plugins = plugins;
    }

    /**
     * Get external plugins that need Maven dependencies.
     */
    public List<Plugin> getExternalPlugins() {
        return plugins.stream()
                .filter(p -> !p.isBuiltIn())
                .toList();
    }

    /**
     * Compute all permissions needed based on base permissions and selected plugins.
     */
    public List<String> getAllPermissions() {
        List<String> allPerms = new ArrayList<>(permissions);
        for (Plugin plugin : plugins) {
            for (String perm : plugin.getRequiredPermissions()) {
                if (!allPerms.contains(perm)) {
                    allPerms.add(perm);
                }
            }
        }
        return allPerms;
    }

    /**
     * Check if a specific plugin is enabled.
     */
    public boolean hasPlugin(Plugin plugin) {
        return plugins.contains(plugin);
    }
}
