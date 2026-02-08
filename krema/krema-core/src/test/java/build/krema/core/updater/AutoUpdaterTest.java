package build.krema.core.updater;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import build.krema.core.updater.AutoUpdater;
import build.krema.core.updater.AutoUpdaterConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutoUpdater")
class AutoUpdaterTest {

    private AutoUpdater updater;

    @BeforeEach
    void setUp() {
        updater = new AutoUpdater(AutoUpdaterConfig.builder()
                .currentVersion("1.2.3")
                .endpoints(List.of("https://example.com/update"))
                .build());
    }

    @Nested
    @DisplayName("isNewerVersion")
    class IsNewerVersion {

        @Test
        @DisplayName("higher major version returns true")
        void higherMajor() {
            assertTrue(updater.isNewerVersion("2.0.0"));
        }

        @Test
        @DisplayName("higher minor version returns true")
        void higherMinor() {
            assertTrue(updater.isNewerVersion("1.3.0"));
        }

        @Test
        @DisplayName("higher patch version returns true")
        void higherPatch() {
            assertTrue(updater.isNewerVersion("1.2.4"));
        }

        @Test
        @DisplayName("same version returns false")
        void sameVersion() {
            assertFalse(updater.isNewerVersion("1.2.3"));
        }

        @Test
        @DisplayName("lower version returns false")
        void lowerVersion() {
            assertFalse(updater.isNewerVersion("1.2.2"));
        }

        @Test
        @DisplayName("lower major version returns false")
        void lowerMajor() {
            assertFalse(updater.isNewerVersion("0.9.9"));
        }

        @Test
        @DisplayName("handles v prefix")
        void vPrefix() {
            assertTrue(updater.isNewerVersion("v2.0.0"));
        }

        @Test
        @DisplayName("handles different segment lengths")
        void differentLengths() {
            assertTrue(updater.isNewerVersion("1.2.3.1"));
            assertFalse(updater.isNewerVersion("1.2"));
        }
    }

    @Nested
    @DisplayName("parseVersion")
    class ParseVersion {

        @Test
        @DisplayName("parses simple version")
        void simpleVersion() {
            int[] result = updater.parseVersion("1.2.3");
            assertArrayEquals(new int[]{1, 2, 3}, result);
        }

        @Test
        @DisplayName("strips non-numeric characters")
        void stripsNonNumeric() {
            int[] result = updater.parseVersion("v1.2.3-beta");
            assertArrayEquals(new int[]{1, 2, 3}, result);
        }

        @Test
        @DisplayName("handles empty segments as zero")
        void emptySegments() {
            int[] result = updater.parseVersion("1..3");
            assertEquals(0, result[1]);
        }
    }

    @Nested
    @DisplayName("substituteVariables")
    class SubstituteVariables {

        @Test
        @DisplayName("replaces {{current_version}}")
        void replacesCurrentVersion() {
            String result = updater.substituteVariables("https://api.example.com/v{{current_version}}/check");
            assertTrue(result.contains("1.2.3"));
            assertFalse(result.contains("{{current_version}}"));
        }

        @Test
        @DisplayName("replaces {{target}}")
        void replacesTarget() {
            String result = updater.substituteVariables("https://api.example.com/{{target}}/update");
            assertFalse(result.contains("{{target}}"));
        }

        @Test
        @DisplayName("replaces {{arch}}")
        void replacesArch() {
            String result = updater.substituteVariables("https://api.example.com/{{arch}}/update");
            assertFalse(result.contains("{{arch}}"));
        }
    }

    @Nested
    @DisplayName("getFileName")
    class GetFileName {

        @Test
        @DisplayName("extracts filename from URL")
        void extractsFilename() {
            String result = updater.getFileName("https://example.com/downloads/app-1.0.0.dmg");
            assertEquals("app-1.0.0.dmg", result);
        }

        @Test
        @DisplayName("strips query parameters")
        void stripsQueryParams() {
            String result = updater.getFileName("https://example.com/downloads/app.dmg?token=abc");
            assertEquals("app.dmg", result);
        }

        @Test
        @DisplayName("trailing slash returns 'update'")
        void trailingSlash() {
            String result = updater.getFileName("https://example.com/downloads/");
            assertEquals("update", result);
        }

        @Test
        @DisplayName("no slash returns 'update'")
        void noSlash() {
            String result = updater.getFileName("nopath");
            assertEquals("update", result);
        }
    }
}
