package build.krema.core.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import build.krema.core.platform.Platform;
import build.krema.core.platform.PlatformDetector;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Platform and PlatformDetector")
class PlatformTest {

    @Test
    @DisplayName("MACOS formats library name as lib{name}.dylib")
    void macosLibraryName() {
        assertEquals("libkrema.dylib", Platform.MACOS.formatLibraryName("krema"));
    }

    @Test
    @DisplayName("WINDOWS formats library name as {name}.dll")
    void windowsLibraryName() {
        assertEquals("krema.dll", Platform.WINDOWS.formatLibraryName("krema"));
    }

    @Test
    @DisplayName("LINUX formats library name as lib{name}.so")
    void linuxLibraryName() {
        assertEquals("libkrema.so", Platform.LINUX.formatLibraryName("krema"));
    }

    @Test
    @DisplayName("UNKNOWN.formatLibraryName throws UnsupportedOperationException")
    void unknownLibraryNameThrows() {
        assertThrows(UnsupportedOperationException.class,
                () -> Platform.UNKNOWN.formatLibraryName("krema"));
    }

    @Test
    @DisplayName("getTarget returns os-arch format")
    void getTargetFormat() {
        String target = Platform.MACOS.getTarget();
        assertTrue(target.startsWith("darwin-"));
        assertTrue(target.contains("-"));
    }

    @Test
    @DisplayName("getDisplayName returns human-readable names")
    void getDisplayName() {
        assertEquals("macOS", Platform.MACOS.getDisplayName());
        assertEquals("Windows", Platform.WINDOWS.getDisplayName());
        assertEquals("Linux", Platform.LINUX.getDisplayName());
        assertEquals("Unknown", Platform.UNKNOWN.getDisplayName());
    }

    @Test
    @DisplayName("current() returns non-null platform")
    void currentIsNonNull() {
        assertNotNull(Platform.current());
    }

    @Test
    @DisplayName("PlatformDetector.getArch returns a known value")
    void getArchReturnsKnownValue() {
        String arch = PlatformDetector.getArch();
        assertNotNull(arch);
        assertFalse(arch.isBlank());
    }

    @Test
    @DisplayName("is64Bit is consistent with getArch")
    void is64BitConsistentWithArch() {
        String arch = PlatformDetector.getArch();
        boolean is64 = PlatformDetector.is64Bit();

        if (arch.equals("x86_64") || arch.equals("aarch64")) {
            assertTrue(is64);
        } else if (arch.equals("x86")) {
            assertFalse(is64);
        }
    }
}
