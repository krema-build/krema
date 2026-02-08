package build.krema.cli;

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Configuration model for krema.toml.
 */
public class KremaConfig {
    private PackageConfig packageConfig;
    private WindowConfig window;
    private BuildConfig build;
    private BundleConfig bundle;
    private PermissionsConfig permissions;
    private UpdaterConfig updater;
    private DeepLinkConfig deepLink;

    public static KremaConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Configuration file not found: " + path);
        }
        Toml toml = new Toml().read(path.toFile());
        return fromToml(toml);
    }

    public static KremaConfig load(Path path, String profile) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Configuration file not found: " + path);
        }
        Toml toml = new Toml().read(path.toFile());
        KremaConfig config = fromToml(toml);
        if (profile != null) {
            applyEnvironmentOverrides(toml, config, profile);
        }
        return config;
    }

    public static KremaConfig loadOrDefault(Path path) {
        try {
            return load(path);
        } catch (IOException e) {
            return defaults();
        }
    }

    public static KremaConfig loadOrDefault(Path path, String profile) {
        try {
            return load(path, profile);
        } catch (IOException e) {
            return defaults();
        }
    }

    public static KremaConfig defaults() {
        KremaConfig config = new KremaConfig();
        config.packageConfig = new PackageConfig();
        config.window = new WindowConfig();
        config.build = new BuildConfig();
        config.bundle = new BundleConfig();
        config.permissions = new PermissionsConfig();
        config.updater = new UpdaterConfig();
        config.deepLink = new DeepLinkConfig();
        return config;
    }

    private static KremaConfig fromToml(Toml toml) {
        KremaConfig config = new KremaConfig();

        Toml pkg = toml.getTable("package");
        if (pkg != null) {
            config.packageConfig = new PackageConfig();
            config.packageConfig.name = pkg.getString("name", "krema-app");
            config.packageConfig.version = pkg.getString("version", "0.1.0");
            config.packageConfig.identifier = pkg.getString("identifier", "com.example.app");
            config.packageConfig.description = pkg.getString("description", "");
        } else {
            config.packageConfig = new PackageConfig();
        }

        Toml win = toml.getTable("window");
        if (win != null) {
            config.window = new WindowConfig();
            config.window.title = win.getString("title", "Krema App");
            config.window.width = win.getLong("width", 1024L).intValue();
            config.window.height = win.getLong("height", 768L).intValue();
            config.window.minWidth = win.getLong("min_width", 0L).intValue();
            config.window.minHeight = win.getLong("min_height", 0L).intValue();
            config.window.resizable = win.getBoolean("resizable", true);
            config.window.fullscreen = win.getBoolean("fullscreen", false);
            config.window.decorations = win.getBoolean("decorations", true);
        } else {
            config.window = new WindowConfig();
        }

        Toml bld = toml.getTable("build");
        if (bld != null) {
            config.build = new BuildConfig();
            config.build.frontendCommand = bld.getString("frontend_command", "npm run build");
            config.build.frontendDevCommand = bld.getString("frontend_dev_command", "npm run dev");
            config.build.frontendDevUrl = bld.getString("frontend_dev_url", "http://localhost:5173");
            config.build.outDir = bld.getString("out_dir", "dist");
            config.build.javaSourceDir = bld.getString("java_source_dir", "src-java");
            config.build.mainClass = bld.getString("main_class");
            config.build.assetsPath = bld.getString("assets_path", "assets");
        } else {
            config.build = new BuildConfig();
        }

        Toml bdl = toml.getTable("bundle");
        if (bdl != null) {
            config.bundle = new BundleConfig();
            config.bundle.icon = bdl.getString("icon");
            config.bundle.identifier = bdl.getString("identifier");
            config.bundle.copyright = bdl.getString("copyright");

            Toml macos = bdl.getTable("macos");
            if (macos != null) {
                config.bundle.macos = new MacOSBundleConfig();
                config.bundle.macos.signingIdentity = macos.getString("signing_identity");
                config.bundle.macos.entitlements = macos.getString("entitlements");
                config.bundle.macos.notarizationAppleId = macos.getString("notarization_apple_id");
                config.bundle.macos.notarizationTeamId = macos.getString("notarization_team_id");
            }

            Toml winBundle = bdl.getTable("windows");
            if (winBundle != null) {
                config.bundle.windows = new WindowsBundleConfig();
                config.bundle.windows.signingCertificate = winBundle.getString("signing_certificate");
                String tsUrl = winBundle.getString("timestamp_url");
                if (tsUrl != null) {
                    config.bundle.windows.timestampUrl = tsUrl;
                }
            }
        } else {
            config.bundle = new BundleConfig();
        }

        Toml perms = toml.getTable("permissions");
        if (perms != null) {
            config.permissions = new PermissionsConfig();
            config.permissions.allow = perms.getList("allow");
        } else {
            config.permissions = new PermissionsConfig();
        }

        Toml upd = toml.getTable("updater");
        if (upd != null) {
            config.updater = new UpdaterConfig();
            config.updater.pubkey = upd.getString("pubkey");
            config.updater.endpoints = upd.getList("endpoints");
            config.updater.checkOnStartup = upd.getBoolean("check_on_startup", true);
            config.updater.timeout = upd.getLong("timeout", 30L).intValue();
        } else {
            config.updater = new UpdaterConfig();
        }

        Toml dl = toml.getTable("deep-link");
        if (dl != null) {
            config.deepLink = new DeepLinkConfig();
            config.deepLink.schemes = dl.getList("schemes");
        } else {
            config.deepLink = new DeepLinkConfig();
        }

        return config;
    }

    private static void applyEnvironmentOverrides(Toml toml, KremaConfig config, String profile) {
        Toml envTable = toml.getTable("env");
        if (envTable == null) return;
        Toml profileTable = envTable.getTable(profile);
        if (profileTable == null) return;

        applyBuildOverrides(profileTable, config);
        applyWindowOverrides(profileTable, config);
        applyPackageOverrides(profileTable, config);
        applyUpdaterOverrides(profileTable, config);
    }

    private static void applyBuildOverrides(Toml profileTable, KremaConfig config) {
        Toml build = profileTable.getTable("build");
        if (build == null) return;

        String frontendCommand = build.getString("frontend_command");
        if (frontendCommand != null) config.build.frontendCommand = frontendCommand;

        String frontendDevCommand = build.getString("frontend_dev_command");
        if (frontendDevCommand != null) config.build.frontendDevCommand = frontendDevCommand;

        String frontendDevUrl = build.getString("frontend_dev_url");
        if (frontendDevUrl != null) config.build.frontendDevUrl = frontendDevUrl;

        String outDir = build.getString("out_dir");
        if (outDir != null) config.build.outDir = outDir;

        String javaSourceDir = build.getString("java_source_dir");
        if (javaSourceDir != null) config.build.javaSourceDir = javaSourceDir;

        String mainClass = build.getString("main_class");
        if (mainClass != null) config.build.mainClass = mainClass;

        String assetsPath = build.getString("assets_path");
        if (assetsPath != null) config.build.assetsPath = assetsPath;
    }

    private static void applyWindowOverrides(Toml profileTable, KremaConfig config) {
        Toml window = profileTable.getTable("window");
        if (window == null) return;

        String title = window.getString("title");
        if (title != null) config.window.title = title;

        Long width = window.getLong("width");
        if (width != null) config.window.width = width.intValue();

        Long height = window.getLong("height");
        if (height != null) config.window.height = height.intValue();

        Long minWidth = window.getLong("min_width");
        if (minWidth != null) config.window.minWidth = minWidth.intValue();

        Long minHeight = window.getLong("min_height");
        if (minHeight != null) config.window.minHeight = minHeight.intValue();

        Boolean resizable = window.getBoolean("resizable");
        if (resizable != null) config.window.resizable = resizable;

        Boolean fullscreen = window.getBoolean("fullscreen");
        if (fullscreen != null) config.window.fullscreen = fullscreen;

        Boolean decorations = window.getBoolean("decorations");
        if (decorations != null) config.window.decorations = decorations;
    }

    private static void applyPackageOverrides(Toml profileTable, KremaConfig config) {
        Toml pkg = profileTable.getTable("package");
        if (pkg == null) return;

        String name = pkg.getString("name");
        if (name != null) config.packageConfig.name = name;

        String version = pkg.getString("version");
        if (version != null) config.packageConfig.version = version;

        String identifier = pkg.getString("identifier");
        if (identifier != null) config.packageConfig.identifier = identifier;

        String description = pkg.getString("description");
        if (description != null) config.packageConfig.description = description;
    }

    private static void applyUpdaterOverrides(Toml profileTable, KremaConfig config) {
        Toml updater = profileTable.getTable("updater");
        if (updater == null) return;

        String pubkey = updater.getString("pubkey");
        if (pubkey != null) config.updater.pubkey = pubkey;

        List<String> endpoints = updater.getList("endpoints");
        if (endpoints != null) config.updater.endpoints = endpoints;

        Boolean checkOnStartup = updater.getBoolean("check_on_startup");
        if (checkOnStartup != null) config.updater.checkOnStartup = checkOnStartup;

        Long timeout = updater.getLong("timeout");
        if (timeout != null) config.updater.timeout = timeout.intValue();
    }

    public PackageConfig getPackageConfig() { return packageConfig; }
    public WindowConfig getWindow() { return window; }
    public BuildConfig getBuild() { return build; }
    public BundleConfig getBundle() { return bundle; }
    public PermissionsConfig getPermissions() { return permissions; }
    public UpdaterConfig getUpdater() { return updater; }
    public DeepLinkConfig getDeepLink() { return deepLink; }

    public static class PackageConfig {
        private String name = "krema-app";
        private String version = "0.1.0";
        private String identifier = "com.example.app";
        private String description = "";

        public String getName() { return name; }
        public String getVersion() { return version; }
        public String getIdentifier() { return identifier; }
        public String getDescription() { return description; }
    }

    public static class WindowConfig {
        private String title = "Krema App";
        private int width = 1024;
        private int height = 768;
        private int minWidth = 0;
        private int minHeight = 0;
        private boolean resizable = true;
        private boolean fullscreen = false;
        private boolean decorations = true;

        public String getTitle() { return title; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getMinWidth() { return minWidth; }
        public int getMinHeight() { return minHeight; }
        public boolean isResizable() { return resizable; }
        public boolean isFullscreen() { return fullscreen; }
        public boolean isDecorations() { return decorations; }
    }

    public static class BuildConfig {
        private String frontendCommand = "npm run build";
        private String frontendDevCommand = "npm run dev";
        private String frontendDevUrl = "http://localhost:5173";
        private String outDir = "dist";
        private String javaSourceDir = "src-java";
        private String mainClass = null;
        private String assetsPath = "assets";

        public String getFrontendCommand() { return frontendCommand; }
        public String getFrontendDevCommand() { return frontendDevCommand; }
        public String getFrontendDevUrl() { return frontendDevUrl; }
        public String getOutDir() { return outDir; }
        public String getJavaSourceDir() { return javaSourceDir; }
        public String getMainClass() { return mainClass; }
        public String getAssetsPath() { return assetsPath; }
    }

    public static class BundleConfig {
        private String icon;
        private String identifier;
        private String copyright;
        private MacOSBundleConfig macos;
        private WindowsBundleConfig windows;

        public String getIcon() { return icon; }
        public String getIdentifier() { return identifier; }
        public String getCopyright() { return copyright; }
        public MacOSBundleConfig getMacos() { return macos; }
        public WindowsBundleConfig getWindows() { return windows; }
    }

    public static class MacOSBundleConfig {
        private String signingIdentity;
        private String entitlements;
        private String notarizationAppleId;
        private String notarizationTeamId;

        public String getSigningIdentity() { return signingIdentity; }
        public String getEntitlements() { return entitlements; }
        public String getNotarizationAppleId() { return notarizationAppleId; }
        public String getNotarizationTeamId() { return notarizationTeamId; }
    }

    public static class WindowsBundleConfig {
        private String signingCertificate;
        private String timestampUrl = "http://timestamp.digicert.com";

        public String getSigningCertificate() { return signingCertificate; }
        public String getTimestampUrl() { return timestampUrl; }
    }

    public static class PermissionsConfig {
        private List<String> allow;

        public List<String> getAllow() { return allow; }
    }

    public static class UpdaterConfig {
        private String pubkey;
        private List<String> endpoints;
        private boolean checkOnStartup = true;
        private int timeout = 30;

        public String getPubkey() { return pubkey; }
        public List<String> getEndpoints() { return endpoints; }
        public boolean isCheckOnStartup() { return checkOnStartup; }
        public int getTimeout() { return timeout; }
    }

    public static class DeepLinkConfig {
        private List<String> schemes;

        public List<String> getSchemes() { return schemes; }
    }
}
