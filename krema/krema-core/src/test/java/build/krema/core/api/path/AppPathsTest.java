package build.krema.core.api.path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AppPaths")
class AppPathsTest {

    private AppPaths paths;

    @BeforeEach
    void setUp() {
        paths = new AppPaths("My App", "myapp");
    }

    @Nested
    @DisplayName("system directories")
    class SystemDirs {

        @Test
        @DisplayName("homeDir returns user.home")
        void homeDir() {
            assertEquals(System.getProperty("user.home"), paths.homeDir());
        }

        @Test
        @DisplayName("tempDir returns java.io.tmpdir")
        void tempDir() {
            // Normalize both to avoid trailing slash differences across platforms
            Path expected = Path.of(System.getProperty("java.io.tmpdir"));
            assertEquals(expected.toString(), paths.tempDir());
        }

        @Test
        @DisplayName("desktopDir is under home")
        void desktopDir() {
            String expected = Path.of(System.getProperty("user.home"), "Desktop").toString();
            assertEquals(expected, paths.desktopDir());
        }

        @Test
        @DisplayName("documentsDir is under home")
        void documentsDir() {
            String expected = Path.of(System.getProperty("user.home"), "Documents").toString();
            assertEquals(expected, paths.documentsDir());
        }

        @Test
        @DisplayName("downloadsDir is under home")
        void downloadsDir() {
            String expected = Path.of(System.getProperty("user.home"), "Downloads").toString();
            assertEquals(expected, paths.downloadsDir());
        }
    }

    @Nested
    @DisplayName("app directories")
    class AppDirs {

        @Test
        @DisplayName("appDataDir returns a non-empty path")
        void appDataDirNotEmpty() {
            assertFalse(paths.appDataDir().isEmpty());
        }

        @Test
        @DisplayName("appConfigDir returns a non-empty path")
        void appConfigDirNotEmpty() {
            assertFalse(paths.appConfigDir().isEmpty());
        }

        @Test
        @DisplayName("appCacheDir returns a non-empty path")
        void appCacheDirNotEmpty() {
            assertFalse(paths.appCacheDir().isEmpty());
        }

        @Test
        @DisplayName("appLogDir returns a non-empty path")
        void appLogDirNotEmpty() {
            assertFalse(paths.appLogDir().isEmpty());
        }
    }

    @Nested
    @DisplayName("constructor with auto-generated identifier")
    class AutoIdentifier {

        @Test
        @DisplayName("strips non-alphanumeric characters for identifier")
        void stripsSpecialChars() {
            AppPaths p = new AppPaths("My Cool App!");
            // The identifier is used internally; verify the paths are still valid
            assertFalse(p.appDataDir().isEmpty());
        }
    }

    @Nested
    @DisplayName("path:join")
    class Join {

        @Test
        @DisplayName("joins multiple path segments")
        void joinsMultiple() {
            String result = paths.join(List.of("a", "b", "c"));
            assertEquals(Paths.get("a", "b", "c").toString(), result);
        }

        @Test
        @DisplayName("returns single segment unchanged")
        void singleSegment() {
            assertEquals("foo", paths.join(List.of("foo")));
        }

        @Test
        @DisplayName("returns empty string for empty list")
        void emptyList() {
            assertEquals("", paths.join(List.of()));
        }

        @Test
        @DisplayName("returns empty string for null list")
        void nullList() {
            assertEquals("", paths.join(null));
        }
    }

    @Nested
    @DisplayName("path:dirname")
    class Dirname {

        @Test
        @DisplayName("returns parent directory")
        void returnsParent() {
            String result = paths.dirname("/foo/bar/baz.txt");
            assertEquals(Paths.get("/foo/bar").toString(), result);
        }

        @Test
        @DisplayName("returns empty string for root-level name")
        void emptyForRootName() {
            assertEquals("", paths.dirname("file.txt"));
        }
    }

    @Nested
    @DisplayName("path:basename")
    class Basename {

        @Test
        @DisplayName("returns file name without path")
        void returnsFileName() {
            assertEquals("file.txt", paths.basename("/foo/bar/file.txt", null));
        }

        @Test
        @DisplayName("strips extension when provided")
        void stripsExtension() {
            assertEquals("file", paths.basename("/foo/file.txt", ".txt"));
        }

        @Test
        @DisplayName("keeps name when extension does not match")
        void keepsNameWhenExtNoMatch() {
            assertEquals("file.txt", paths.basename("/foo/file.txt", ".csv"));
        }
    }

    @Nested
    @DisplayName("path:extname")
    class Extname {

        @Test
        @DisplayName("returns extension including dot")
        void returnsExtension() {
            assertEquals(".txt", paths.extname("file.txt"));
        }

        @Test
        @DisplayName("returns last extension for multiple dots")
        void returnsLastExtension() {
            assertEquals(".gz", paths.extname("archive.tar.gz"));
        }

        @Test
        @DisplayName("returns empty string when no extension")
        void emptyWhenNoExtension() {
            assertEquals("", paths.extname("Makefile"));
        }

        @Test
        @DisplayName("returns empty string for dotfile")
        void emptyForDotfile() {
            assertEquals("", paths.extname(".gitignore"));
        }
    }

    @Nested
    @DisplayName("path:isAbsolute")
    class IsAbsolute {

        @Test
        @DisplayName("returns true for absolute path")
        void trueForAbsolute() {
            assertTrue(paths.isAbsolute("/foo/bar"));
        }

        @Test
        @DisplayName("returns false for relative path")
        void falseForRelative() {
            assertFalse(paths.isAbsolute("foo/bar"));
        }
    }

    @Nested
    @DisplayName("path:normalize")
    class Normalize {

        @Test
        @DisplayName("resolves .. and . segments")
        void resolvesDotSegments() {
            String result = paths.normalize("/foo/bar/../baz/./file.txt");
            assertEquals(Paths.get("/foo/baz/file.txt").toString(), result);
        }
    }

    @Nested
    @DisplayName("path:resolve")
    class Resolve {

        @Test
        @DisplayName("resolves to absolute path")
        void resolvesToAbsolute() {
            String result = paths.resolve(List.of("a", "b"));
            assertTrue(Paths.get(result).isAbsolute());
        }

        @Test
        @DisplayName("returns cwd for empty list")
        void returnsCwdForEmpty() {
            String result = paths.resolve(List.of());
            assertTrue(Paths.get(result).isAbsolute());
        }

        @Test
        @DisplayName("returns cwd for null list")
        void returnsCwdForNull() {
            String result = paths.resolve(null);
            assertTrue(Paths.get(result).isAbsolute());
        }
    }
}
