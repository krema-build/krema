package build.krema.cli.bundle.windows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import build.krema.cli.bundle.windows.WindowsRegistryHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WindowsRegistryHelper")
class WindowsRegistryHelperTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("generateRegFile creates valid .reg file with single scheme")
    void generateRegFileSingleScheme() throws IOException {
        Path regFile = tempDir.resolve("test.reg");
        WindowsRegistryHelper.generateRegFile(
            List.of("myapp"),
            "C:\\Program Files\\MyApp\\MyApp.exe",
            "MyApp",
            regFile
        );

        assertTrue(Files.exists(regFile));
        String content = Files.readString(regFile);

        // Check header
        assertTrue(content.startsWith("Windows Registry Editor Version 5.00"));

        // Check protocol registration
        assertTrue(content.contains("[HKEY_CURRENT_USER\\Software\\Classes\\myapp]"));
        assertTrue(content.contains("@=\"URL:MyApp Protocol\""));
        assertTrue(content.contains("\"URL Protocol\"=\"\""));

        // Check command registration (backslashes doubled for .reg format)
        assertTrue(content.contains("[HKEY_CURRENT_USER\\Software\\Classes\\myapp\\shell\\open\\command]"));
        assertTrue(content.contains("C:\\\\Program Files\\\\MyApp\\\\MyApp.exe"));
    }

    @Test
    @DisplayName("generateRegFile creates valid .reg file with multiple schemes")
    void generateRegFileMultipleSchemes() throws IOException {
        Path regFile = tempDir.resolve("test.reg");
        WindowsRegistryHelper.generateRegFile(
            List.of("myapp", "myapp-dev", "myapp-staging"),
            "C:\\Apps\\Test.exe",
            "Test App",
            regFile
        );

        String content = Files.readString(regFile);

        // Check all schemes are present
        assertTrue(content.contains("[HKEY_CURRENT_USER\\Software\\Classes\\myapp]"));
        assertTrue(content.contains("[HKEY_CURRENT_USER\\Software\\Classes\\myapp-dev]"));
        assertTrue(content.contains("[HKEY_CURRENT_USER\\Software\\Classes\\myapp-staging]"));

        // Check all have proper URL Protocol marker
        assertEquals(3, content.split("\"URL Protocol\"=\"\"").length - 1);
    }

    @Test
    @DisplayName("generateRegFile includes DefaultIcon entry")
    void generateRegFileIncludesIcon() throws IOException {
        Path regFile = tempDir.resolve("test.reg");
        WindowsRegistryHelper.generateRegFile(
            List.of("myapp"),
            "C:\\MyApp.exe",
            "MyApp",
            regFile
        );

        String content = Files.readString(regFile);
        assertTrue(content.contains("[HKEY_CURRENT_USER\\Software\\Classes\\myapp\\DefaultIcon]"));
        assertTrue(content.contains("C:\\\\MyApp.exe,0"));
    }

    @Test
    @DisplayName("generatePowerShellScript creates valid registration script")
    void generatePowerShellScript() throws IOException {
        Path script = tempDir.resolve("register.ps1");
        WindowsRegistryHelper.generatePowerShellScript(
            List.of("myapp"),
            "C:\\Program Files\\MyApp\\MyApp.exe",
            "MyApp",
            script
        );

        assertTrue(Files.exists(script));
        String content = Files.readString(script);

        // Check PowerShell registry commands
        assertTrue(content.contains("$scheme = \"myapp\""));
        assertTrue(content.contains("HKCU:\\Software\\Classes\\$scheme"));
        assertTrue(content.contains("New-Item -Path $basePath -Force"));
        assertTrue(content.contains("Set-ItemProperty"));
        assertTrue(content.contains("URL:MyApp Protocol"));
        assertTrue(content.contains("URL Protocol"));
        assertTrue(content.contains("shell\\open\\command"));
    }

    @Test
    @DisplayName("generatePowerShellScript handles multiple schemes")
    void generatePowerShellScriptMultipleSchemes() throws IOException {
        Path script = tempDir.resolve("register.ps1");
        WindowsRegistryHelper.generatePowerShellScript(
            List.of("app1", "app2"),
            "C:\\Test.exe",
            "Test",
            script
        );

        String content = Files.readString(script);
        assertTrue(content.contains("$scheme = \"app1\""));
        assertTrue(content.contains("$scheme = \"app2\""));
        assertEquals(2, content.split("Registered .+:// protocol handler").length - 1);
    }

    @Test
    @DisplayName("generateUnregisterScript creates valid unregistration script")
    void generateUnregisterScript() throws IOException {
        Path script = tempDir.resolve("unregister.ps1");
        WindowsRegistryHelper.generateUnregisterScript(
            List.of("myapp", "myapp-dev"),
            script
        );

        assertTrue(Files.exists(script));
        String content = Files.readString(script);

        // Check unregistration logic
        assertTrue(content.contains("HKCU:\\Software\\Classes\\myapp"));
        assertTrue(content.contains("HKCU:\\Software\\Classes\\myapp-dev"));
        assertTrue(content.contains("Test-Path"));
        assertTrue(content.contains("Remove-Item"));
        assertTrue(content.contains("-Recurse -Force"));
    }

    @Test
    @DisplayName("generateRegFile escapes special characters in paths")
    void generateRegFileEscapesSpecialCharacters() throws IOException {
        Path regFile = tempDir.resolve("test.reg");
        WindowsRegistryHelper.generateRegFile(
            List.of("myapp"),
            "C:\\Program Files (x86)\\My App\\app.exe",
            "My App",
            regFile
        );

        String content = Files.readString(regFile);
        // Backslashes should be doubled in .reg files
        assertTrue(content.contains("C:\\\\Program Files (x86)\\\\My App\\\\app.exe"));
    }

    @Test
    @DisplayName("isAvailable returns false on non-Windows platforms")
    void isAvailableOnNonWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("windows")) {
            assertFalse(WindowsRegistryHelper.isAvailable());
        }
        // On Windows, this would return true - can't easily test both cases
    }

    @Test
    @DisplayName("registerUrlProtocol returns false when not available")
    void registerUrlProtocolReturnsfalseWhenNotAvailable() {
        if (!WindowsRegistryHelper.isAvailable()) {
            boolean result = WindowsRegistryHelper.registerUrlProtocol(
                "test-scheme",
                "C:\\test.exe",
                "Test"
            );
            assertFalse(result);
        }
    }

    @Test
    @DisplayName("unregisterUrlProtocol returns false when not available")
    void unregisterUrlProtocolReturnsFalseWhenNotAvailable() {
        if (!WindowsRegistryHelper.isAvailable()) {
            boolean result = WindowsRegistryHelper.unregisterUrlProtocol("test-scheme");
            assertFalse(result);
        }
    }
}
