package build.krema.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import build.krema.core.security.Permission;
import build.krema.core.security.PermissionChecker;
import build.krema.core.security.RequiresPermission;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PermissionChecker")
class PermissionCheckerTest {

    static class AnnotatedMethods {
        @RequiresPermission({Permission.FS_READ, Permission.FS_WRITE})
        public void requiresBothFsReadAndWrite() {}

        @RequiresPermission(value = {Permission.CLIPBOARD_READ, Permission.CLIPBOARD_WRITE}, anyOf = true)
        public void requiresAnyClipboard() {}

        @RequiresPermission(Permission.FS_READ)
        public void requiresFsRead() {}

        public void unannotated() {}
    }

    private Method bothFsMethod;
    private Method anyClipboardMethod;
    private Method fsReadMethod;
    private Method unannotatedMethod;

    @BeforeEach
    void setUp() throws Exception {
        bothFsMethod = AnnotatedMethods.class.getMethod("requiresBothFsReadAndWrite");
        anyClipboardMethod = AnnotatedMethods.class.getMethod("requiresAnyClipboard");
        fsReadMethod = AnnotatedMethods.class.getMethod("requiresFsRead");
        unannotatedMethod = AnnotatedMethods.class.getMethod("unannotated");
    }

    @Nested
    @DisplayName("isGranted")
    class IsGranted {

        @Test
        @DisplayName("direct match returns true")
        void directMatch() {
            var checker = new PermissionChecker(Set.of("fs:read"));
            assertTrue(checker.isGranted("fs:read"));
        }

        @Test
        @DisplayName("ungranted returns false")
        void ungranted() {
            var checker = new PermissionChecker(Set.of("fs:read"));
            assertFalse(checker.isGranted("fs:write"));
        }

        @Test
        @DisplayName("wildcard grants all")
        void wildcardGrantsAll() {
            var checker = new PermissionChecker(Set.of("*"));
            assertTrue(checker.isGranted("fs:read"));
            assertTrue(checker.isGranted("clipboard:write"));
            assertTrue(checker.isGranted("anything"));
        }

        @Test
        @DisplayName("partial wildcard grants matching prefix")
        void partialWildcard() {
            var checker = new PermissionChecker(Set.of("fs:*"));
            assertTrue(checker.isGranted("fs:read"));
            assertTrue(checker.isGranted("fs:write"));
            assertFalse(checker.isGranted("clipboard:read"));
        }

        @Test
        @DisplayName("Permission enum variant delegates to key")
        void permissionEnumVariant() {
            var checker = new PermissionChecker(Set.of("fs:read"));
            assertTrue(checker.isGranted(Permission.FS_READ));
            assertFalse(checker.isGranted(Permission.FS_WRITE));
        }

        @Test
        @DisplayName("empty set returns false")
        void emptySet() {
            var checker = new PermissionChecker(Set.of());
            assertFalse(checker.isGranted("fs:read"));
        }
    }

    @Nested
    @DisplayName("grant and revoke")
    class GrantAndRevoke {

        @Test
        @DisplayName("grant adds permission")
        void grantAdds() {
            var checker = new PermissionChecker(new HashSet<>());
            assertFalse(checker.isGranted("fs:read"));
            checker.grant("fs:read");
            assertTrue(checker.isGranted("fs:read"));
        }

        @Test
        @DisplayName("revoke removes permission")
        void revokeRemoves() {
            var checker = new PermissionChecker(new HashSet<>(Set.of("fs:read")));
            assertTrue(checker.isGranted("fs:read"));
            checker.revoke("fs:read");
            assertFalse(checker.isGranted("fs:read"));
        }

        @Test
        @DisplayName("getGrantedPermissions returns unmodifiable set")
        void unmodifiableSet() {
            var checker = new PermissionChecker(Set.of("fs:read"));
            Set<String> granted = checker.getGrantedPermissions();
            assertThrows(UnsupportedOperationException.class, () -> granted.add("fs:write"));
        }
    }

    @Nested
    @DisplayName("allowAll")
    class AllowAll {

        @Test
        @DisplayName("grants any key")
        void grantsAnyKey() {
            var checker = PermissionChecker.allowAll();
            assertTrue(checker.isGranted("fs:read"));
            assertTrue(checker.isGranted("clipboard:write"));
            assertTrue(checker.isGranted("anything:at:all"));
        }

        @Test
        @DisplayName("grants any Permission enum")
        void grantsAnyPermissionEnum() {
            var checker = PermissionChecker.allowAll();
            assertTrue(checker.isGranted(Permission.FS_READ));
            assertTrue(checker.isGranted(Permission.SHELL_EXECUTE));
            assertTrue(checker.isGranted(Permission.ALL));
        }
    }

    @Nested
    @DisplayName("check allOf")
    class CheckAllOf {

        @Test
        @DisplayName("passes when all granted")
        void passesWhenAllGranted() {
            var checker = new PermissionChecker(Set.of("fs:read", "fs:write"));
            assertDoesNotThrow(() -> checker.check(bothFsMethod));
        }

        @Test
        @DisplayName("throws when some missing")
        void throwsWhenSomeMissing() {
            var checker = new PermissionChecker(Set.of("fs:read"));
            SecurityException ex = assertThrows(SecurityException.class,
                    () -> checker.check(bothFsMethod));
            assertTrue(ex.getMessage().contains("fs:write"));
        }

        @Test
        @DisplayName("no annotation does not throw")
        void noAnnotation() {
            var checker = new PermissionChecker(Set.of());
            assertDoesNotThrow(() -> checker.check(unannotatedMethod));
        }
    }

    @Nested
    @DisplayName("check anyOf")
    class CheckAnyOf {

        @Test
        @DisplayName("passes when at least one granted")
        void passesWhenOneGranted() {
            var checker = new PermissionChecker(Set.of("clipboard:read"));
            assertDoesNotThrow(() -> checker.check(anyClipboardMethod));
        }

        @Test
        @DisplayName("throws when none granted")
        void throwsWhenNoneGranted() {
            var checker = new PermissionChecker(Set.of("fs:read"));
            SecurityException ex = assertThrows(SecurityException.class,
                    () -> checker.check(anyClipboardMethod));
            assertTrue(ex.getMessage().contains("any of"));
        }
    }

    @Nested
    @DisplayName("check enforce=false")
    class CheckEnforceFalse {

        @Test
        @DisplayName("does not throw when enforce is false")
        void doesNotThrow() {
            var checker = new PermissionChecker(Set.of(), false);
            assertDoesNotThrow(() -> checker.check(fsReadMethod));
        }
    }

    @Nested
    @DisplayName("getRequiredPermissions")
    class GetRequiredPermissions {

        @Test
        @DisplayName("returns permissions from annotation")
        void returnsPermissions() {
            List<Permission> perms = PermissionChecker.getRequiredPermissions(bothFsMethod);
            assertEquals(2, perms.size());
            assertTrue(perms.contains(Permission.FS_READ));
            assertTrue(perms.contains(Permission.FS_WRITE));
        }

        @Test
        @DisplayName("returns empty list for unannotated method")
        void emptyForUnannotated() {
            List<Permission> perms = PermissionChecker.getRequiredPermissions(unannotatedMethod);
            assertTrue(perms.isEmpty());
        }
    }
}
