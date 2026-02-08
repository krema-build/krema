package build.krema.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import build.krema.cli.KremaConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KremaConfig")
class KremaConfigTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("defaults returns correct default values for all sections")
    void defaults() {
        KremaConfig config = KremaConfig.defaults();

        assertNotNull(config.getPackageConfig());
        assertEquals("krema-app", config.getPackageConfig().getName());
        assertEquals("0.1.0", config.getPackageConfig().getVersion());
        assertEquals("com.example.app", config.getPackageConfig().getIdentifier());

        assertNotNull(config.getWindow());
        assertEquals("Krema App", config.getWindow().getTitle());
        assertEquals(1024, config.getWindow().getWidth());
        assertEquals(768, config.getWindow().getHeight());
        assertTrue(config.getWindow().isResizable());

        assertNotNull(config.getBuild());
        assertEquals("npm run build", config.getBuild().getFrontendCommand());
        assertEquals("dist", config.getBuild().getOutDir());

        assertNotNull(config.getBundle());
        assertNotNull(config.getPermissions());

        assertNotNull(config.getUpdater());
        assertTrue(config.getUpdater().isCheckOnStartup());
        assertEquals(30, config.getUpdater().getTimeout());

        assertNotNull(config.getDeepLink());
        assertNull(config.getDeepLink().getSchemes());
    }

    @Test
    @DisplayName("load parses full TOML with all sections")
    void loadFullToml() throws IOException {
        String toml = """
                [package]
                name = "my-app"
                version = "2.0.0"
                identifier = "com.mycompany.myapp"
                description = "My application"

                [window]
                title = "My App"
                width = 1280
                height = 720
                min_width = 800
                min_height = 600
                resizable = false
                fullscreen = true
                decorations = false

                [build]
                frontend_command = "yarn build"
                frontend_dev_command = "yarn dev"
                frontend_dev_url = "http://localhost:3000"
                out_dir = "build"
                java_source_dir = "src/java"
                main_class = "com.mycompany.Main"
                assets_path = "resources"

                [bundle]
                icon = "icon.png"
                identifier = "com.mycompany.myapp.bundle"
                copyright = "Copyright 2024"

                [bundle.macos]
                signing_identity = "Developer ID"
                entitlements = "entitlements.plist"
                notarization_apple_id = "dev@example.com"
                notarization_team_id = "TEAMID"

                [bundle.windows]
                signing_certificate = "cert.pfx"
                timestamp_url = "http://ts.example.com"

                [permissions]
                allow = ["fs:read", "fs:write"]

                [updater]
                pubkey = "base64pubkey=="
                endpoints = ["https://api.example.com/update/{{target}}/{{current_version}}"]
                check_on_startup = false
                timeout = 60
                """;

        Path configFile = tempDir.resolve("krema.toml");
        Files.writeString(configFile, toml);

        KremaConfig config = KremaConfig.load(configFile);

        // Package
        assertEquals("my-app", config.getPackageConfig().getName());
        assertEquals("2.0.0", config.getPackageConfig().getVersion());
        assertEquals("com.mycompany.myapp", config.getPackageConfig().getIdentifier());
        assertEquals("My application", config.getPackageConfig().getDescription());

        // Window
        assertEquals("My App", config.getWindow().getTitle());
        assertEquals(1280, config.getWindow().getWidth());
        assertEquals(720, config.getWindow().getHeight());
        assertEquals(800, config.getWindow().getMinWidth());
        assertEquals(600, config.getWindow().getMinHeight());
        assertFalse(config.getWindow().isResizable());
        assertTrue(config.getWindow().isFullscreen());
        assertFalse(config.getWindow().isDecorations());

        // Build
        assertEquals("yarn build", config.getBuild().getFrontendCommand());
        assertEquals("yarn dev", config.getBuild().getFrontendDevCommand());
        assertEquals("http://localhost:3000", config.getBuild().getFrontendDevUrl());
        assertEquals("build", config.getBuild().getOutDir());
        assertEquals("src/java", config.getBuild().getJavaSourceDir());
        assertEquals("com.mycompany.Main", config.getBuild().getMainClass());
        assertEquals("resources", config.getBuild().getAssetsPath());

        // Bundle
        assertEquals("icon.png", config.getBundle().getIcon());
        assertEquals("com.mycompany.myapp.bundle", config.getBundle().getIdentifier());
        assertEquals("Copyright 2024", config.getBundle().getCopyright());

        // Bundle macOS
        assertNotNull(config.getBundle().getMacos());
        assertEquals("Developer ID", config.getBundle().getMacos().getSigningIdentity());
        assertEquals("entitlements.plist", config.getBundle().getMacos().getEntitlements());
        assertEquals("dev@example.com", config.getBundle().getMacos().getNotarizationAppleId());
        assertEquals("TEAMID", config.getBundle().getMacos().getNotarizationTeamId());

        // Bundle Windows
        assertNotNull(config.getBundle().getWindows());
        assertEquals("cert.pfx", config.getBundle().getWindows().getSigningCertificate());
        assertEquals("http://ts.example.com", config.getBundle().getWindows().getTimestampUrl());

        // Permissions
        assertNotNull(config.getPermissions().getAllow());
        assertEquals(2, config.getPermissions().getAllow().size());

        // Updater
        assertEquals("base64pubkey==", config.getUpdater().getPubkey());
        assertEquals(1, config.getUpdater().getEndpoints().size());
        assertFalse(config.getUpdater().isCheckOnStartup());
        assertEquals(60, config.getUpdater().getTimeout());
    }

    @Test
    @DisplayName("load with missing file throws IOException")
    void loadMissingFile() {
        Path missing = tempDir.resolve("nonexistent.toml");
        assertThrows(IOException.class, () -> KremaConfig.load(missing));
    }

    @Test
    @DisplayName("loadOrDefault with missing file returns defaults")
    void loadOrDefaultMissingFile() {
        Path missing = tempDir.resolve("nonexistent.toml");
        KremaConfig config = KremaConfig.loadOrDefault(missing);

        assertNotNull(config);
        assertEquals("krema-app", config.getPackageConfig().getName());
    }

    @Test
    @DisplayName("env overrides build section")
    void envOverridesBuildSection() throws IOException {
        String toml = """
                [build]
                frontend_dev_url = "http://localhost:5173"

                [env.development.build]
                frontend_dev_url = "http://localhost:4200"
                """;

        Path configFile = tempDir.resolve("krema.toml");
        Files.writeString(configFile, toml);

        KremaConfig config = KremaConfig.load(configFile, "development");

        assertEquals("http://localhost:4200", config.getBuild().getFrontendDevUrl());
    }

    @Test
    @DisplayName("env overrides window title")
    void envOverridesWindowTitle() throws IOException {
        String toml = """
                [window]
                title = "My App"

                [env.staging.window]
                title = "My App (Staging)"
                """;

        Path configFile = tempDir.resolve("krema.toml");
        Files.writeString(configFile, toml);

        KremaConfig config = KremaConfig.load(configFile, "staging");

        assertEquals("My App (Staging)", config.getWindow().getTitle());
    }

    @Test
    @DisplayName("env overrides multiple sections")
    void envOverridesMultipleSections() throws IOException {
        String toml = """
                [build]
                frontend_dev_url = "http://localhost:5173"

                [window]
                title = "My App"

                [env.development.build]
                frontend_dev_url = "http://localhost:4200"

                [env.development.window]
                title = "My App (Dev)"
                """;

        Path configFile = tempDir.resolve("krema.toml");
        Files.writeString(configFile, toml);

        KremaConfig config = KremaConfig.load(configFile, "development");

        assertEquals("http://localhost:4200", config.getBuild().getFrontendDevUrl());
        assertEquals("My App (Dev)", config.getWindow().getTitle());
    }

    @Test
    @DisplayName("unknown profile uses base config")
    void unknownProfileUsesBaseConfig() throws IOException {
        String toml = """
                [window]
                title = "My App"

                [env.development.window]
                title = "My App (Dev)"
                """;

        Path configFile = tempDir.resolve("krema.toml");
        Files.writeString(configFile, toml);

        KremaConfig config = KremaConfig.load(configFile, "production");

        assertEquals("My App", config.getWindow().getTitle());
    }

    @Test
    @DisplayName("missing env section uses base config")
    void missingEnvSectionUsesBaseConfig() throws IOException {
        String toml = """
                [window]
                title = "My App"
                """;

        Path configFile = tempDir.resolve("krema.toml");
        Files.writeString(configFile, toml);

        KremaConfig config = KremaConfig.load(configFile, "development");

        assertEquals("My App", config.getWindow().getTitle());
    }

    @Test
    @DisplayName("null profile uses base config")
    void nullProfileUsesBaseConfig() throws IOException {
        String toml = """
                [window]
                title = "My App"

                [env.development.window]
                title = "My App (Dev)"
                """;

        Path configFile = tempDir.resolve("krema.toml");
        Files.writeString(configFile, toml);

        KremaConfig config = KremaConfig.load(configFile, null);

        assertEquals("My App", config.getWindow().getTitle());
    }

    @Test
    @DisplayName("missing sections fall back to defaults")
    void missingSectionsFallback() throws IOException {
        String toml = """
                [package]
                name = "partial-app"
                """;

        Path configFile = tempDir.resolve("krema.toml");
        Files.writeString(configFile, toml);

        KremaConfig config = KremaConfig.load(configFile);

        assertEquals("partial-app", config.getPackageConfig().getName());

        // Missing sections should have defaults
        assertNotNull(config.getWindow());
        assertEquals("Krema App", config.getWindow().getTitle());
        assertNotNull(config.getBuild());
        assertNotNull(config.getBundle());
        assertNotNull(config.getPermissions());
        assertNotNull(config.getUpdater());
        assertTrue(config.getUpdater().isCheckOnStartup());
        assertEquals(30, config.getUpdater().getTimeout());
        assertNotNull(config.getDeepLink());
        assertNull(config.getDeepLink().getSchemes());
    }

    @Test
    @DisplayName("deep-link section parses schemes list")
    void deepLinkSchemes() throws IOException {
        String toml = """
                [deep-link]
                schemes = ["myapp", "myapp-dev"]
                """;

        Path configFile = tempDir.resolve("krema.toml");
        Files.writeString(configFile, toml);

        KremaConfig config = KremaConfig.load(configFile);

        assertNotNull(config.getDeepLink());
        assertEquals(List.of("myapp", "myapp-dev"), config.getDeepLink().getSchemes());
    }

    @Test
    @DisplayName("missing deep-link section returns empty config")
    void missingDeepLinkSection() {
        KremaConfig config = KremaConfig.defaults();

        assertNotNull(config.getDeepLink());
        assertNull(config.getDeepLink().getSchemes());
    }
}
