package build.krema.core.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import build.krema.core.security.Permission;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Permission")
class PermissionTest {

    @Nested
    @DisplayName("implies")
    class Implies {

        @Test
        @DisplayName("self implies self")
        void selfImpliesSelf() {
            assertTrue(Permission.FS_READ.implies(Permission.FS_READ));
        }

        @Test
        @DisplayName("ALL implies any permission")
        void allImpliesAny() {
            assertTrue(Permission.ALL.implies(Permission.FS_READ));
            assertTrue(Permission.ALL.implies(Permission.CLIPBOARD_WRITE));
            assertTrue(Permission.ALL.implies(Permission.SHELL_EXECUTE));
        }

        @Test
        @DisplayName("FS_ALL implies FS_READ")
        void fsAllImpliesFsRead() {
            assertTrue(Permission.FS_ALL.implies(Permission.FS_READ));
        }

        @Test
        @DisplayName("FS_ALL implies FS_WRITE")
        void fsAllImpliesFsWrite() {
            assertTrue(Permission.FS_ALL.implies(Permission.FS_WRITE));
        }

        @Test
        @DisplayName("FS_ALL does not imply CLIPBOARD_READ")
        void fsAllDoesNotImplyClipboardRead() {
            assertFalse(Permission.FS_ALL.implies(Permission.CLIPBOARD_READ));
        }

        @Test
        @DisplayName("FS_READ does not imply FS_WRITE")
        void fsReadDoesNotImplyFsWrite() {
            assertFalse(Permission.FS_READ.implies(Permission.FS_WRITE));
        }
    }

    @Nested
    @DisplayName("fromKey")
    class FromKey {

        @Test
        @DisplayName("known key returns correct enum")
        void knownKey() {
            assertEquals(Permission.FS_READ, Permission.fromKey("fs:read"));
            assertEquals(Permission.CLIPBOARD_WRITE, Permission.fromKey("clipboard:write"));
            assertEquals(Permission.NOTIFICATION, Permission.fromKey("notification"));
        }

        @Test
        @DisplayName("unknown key returns null")
        void unknownKey() {
            assertNull(Permission.fromKey("unknown:permission"));
        }

        @Test
        @DisplayName("wildcard returns ALL")
        void wildcardReturnsAll() {
            assertEquals(Permission.ALL, Permission.fromKey("*"));
        }
    }

    @Nested
    @DisplayName("matches")
    class Matches {

        @Test
        @DisplayName("exact match")
        void exactMatch() {
            assertTrue(Permission.FS_READ.matches("fs:read"));
        }

        @Test
        @DisplayName("ALL matches anything")
        void allMatchesAnything() {
            assertTrue(Permission.ALL.matches("fs:read"));
            assertTrue(Permission.ALL.matches("clipboard:write"));
            assertTrue(Permission.ALL.matches("anything"));
        }

        @Test
        @DisplayName("FS_ALL matches fs:read")
        void fsAllMatchesFsRead() {
            assertTrue(Permission.FS_ALL.matches("fs:read"));
        }

        @Test
        @DisplayName("FS_ALL does not match clipboard:read")
        void fsAllDoesNotMatchClipboardRead() {
            assertFalse(Permission.FS_ALL.matches("clipboard:read"));
        }

        @Test
        @DisplayName("FS_READ does not match fs:write")
        void fsReadDoesNotMatchFsWrite() {
            assertFalse(Permission.FS_READ.matches("fs:write"));
        }
    }

    @Nested
    @DisplayName("accessors")
    class Accessors {

        @Test
        @DisplayName("getKey returns expected key")
        void getKey() {
            assertEquals("fs:read", Permission.FS_READ.getKey());
            assertEquals("*", Permission.ALL.getKey());
            assertEquals("fs:*", Permission.FS_ALL.getKey());
        }

        @Test
        @DisplayName("getDescription returns expected description")
        void getDescription() {
            assertEquals("Read files from the file system", Permission.FS_READ.getDescription());
            assertEquals("Full access to all features", Permission.ALL.getDescription());
        }
    }
}
