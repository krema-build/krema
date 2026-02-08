package build.krema.cli.bundle.macos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import build.krema.cli.bundle.AppBundleConfig;
import build.krema.cli.bundle.macos.MacOSAppBundler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MacOSAppBundler")
class MacOSAppBundlerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("dev bundle includes CFBundleURLTypes when schemes are configured")
    void devBundleIncludesUrlTypes() throws IOException {
        AppBundleConfig config = AppBundleConfig.builder()
            .appName("TestApp")
            .identifier("com.example.testapp")
            .version("1.0.0")
            .mainClass("com.example.Main")
            .outputDir(tempDir)
            .deepLinkSchemes(List.of("myapp", "myapp-dev"))
            .build();

        MacOSAppBundler bundler = new MacOSAppBundler();
        Path appDir = bundler.createDevBundle(config);

        Path plistPath = appDir.resolve("Contents/Info.plist");
        assertTrue(Files.exists(plistPath));

        String plist = Files.readString(plistPath);
        assertTrue(plist.contains("<key>CFBundleURLTypes</key>"), "plist should contain CFBundleURLTypes");
        assertTrue(plist.contains("<key>CFBundleURLSchemes</key>"), "plist should contain CFBundleURLSchemes");
        assertTrue(plist.contains("<string>myapp</string>"), "plist should contain myapp scheme");
        assertTrue(plist.contains("<string>myapp-dev</string>"), "plist should contain myapp-dev scheme");
        assertTrue(plist.contains("<string>com.example.testapp.myapp</string>"), "plist should contain URL name");
        assertTrue(plist.contains("<string>com.example.testapp.myapp-dev</string>"), "plist should contain URL name");
    }

    @Test
    @DisplayName("dev bundle omits CFBundleURLTypes when no schemes are configured")
    void devBundleOmitsUrlTypesWhenNoSchemes() throws IOException {
        AppBundleConfig config = AppBundleConfig.builder()
            .appName("TestApp")
            .identifier("com.example.testapp")
            .version("1.0.0")
            .mainClass("com.example.Main")
            .outputDir(tempDir)
            .build();

        MacOSAppBundler bundler = new MacOSAppBundler();
        Path appDir = bundler.createDevBundle(config);

        Path plistPath = appDir.resolve("Contents/Info.plist");
        assertTrue(Files.exists(plistPath));

        String plist = Files.readString(plistPath);
        assertFalse(plist.contains("CFBundleURLTypes"), "plist should not contain CFBundleURLTypes");
    }

    @Test
    @DisplayName("production bundle includes CFBundleURLTypes when schemes are configured")
    void productionBundleIncludesUrlTypes() throws IOException {
        AppBundleConfig config = AppBundleConfig.builder()
            .appName("TestApp")
            .identifier("com.example.testapp")
            .version("1.0.0")
            .mainClass("com.example.Main")
            .outputDir(tempDir)
            .deepLinkSchemes(List.of("myapp"))
            .build();

        MacOSAppBundler bundler = new MacOSAppBundler();
        Path appDir = bundler.createProductionBundle(config);

        Path plistPath = appDir.resolve("Contents/Info.plist");
        assertTrue(Files.exists(plistPath));

        String plist = Files.readString(plistPath);
        assertTrue(plist.contains("<key>CFBundleURLTypes</key>"), "plist should contain CFBundleURLTypes");
        assertTrue(plist.contains("<string>myapp</string>"), "plist should contain myapp scheme");
    }

    @Test
    @DisplayName("production bundle omits CFBundleURLTypes when no schemes")
    void productionBundleOmitsUrlTypesWhenNoSchemes() throws IOException {
        AppBundleConfig config = AppBundleConfig.builder()
            .appName("TestApp")
            .identifier("com.example.testapp")
            .version("1.0.0")
            .mainClass("com.example.Main")
            .outputDir(tempDir)
            .build();

        MacOSAppBundler bundler = new MacOSAppBundler();
        Path appDir = bundler.createProductionBundle(config);

        Path plistPath = appDir.resolve("Contents/Info.plist");
        assertTrue(Files.exists(plistPath));

        String plist = Files.readString(plistPath);
        assertFalse(plist.contains("CFBundleURLTypes"), "plist should not contain CFBundleURLTypes");
    }
}
