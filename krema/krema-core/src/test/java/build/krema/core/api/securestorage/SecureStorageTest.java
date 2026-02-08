package build.krema.core.api.securestorage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import build.krema.core.api.securestorage.SecureStorage;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecureStorage")
class SecureStorageTest {

    private static final String TEST_SERVICE =
        "build.krema.core.test." + System.currentTimeMillis();

    private final SecureStorage storage = new SecureStorage(TEST_SERVICE);

    private static final String[] TEST_KEYS = {
        "test-key", "test-key-upsert", "test-key-delete",
        "test-key-has", "test-key-utf8"
    };

    @AfterEach
    void cleanup() {
        for (String key : TEST_KEYS) {
            try {
                storage.delete(Map.of("key", key));
            } catch (Exception ignored) {}
        }
    }

    @Nested
    @DisplayName("macOS")
    @EnabledOnOs(OS.MAC)
    class MacOSTests {

        @Test
        @DisplayName("set and get round-trip")
        void setAndGet() {
            assertTrue(storage.set(Map.of("key", "test-key", "value", "secret123")));
            assertEquals("secret123", storage.get(Map.of("key", "test-key")));
        }

        @Test
        @DisplayName("set upsert overwrites existing value")
        void setUpsert() {
            storage.set(Map.of("key", "test-key-upsert", "value", "first"));
            storage.set(Map.of("key", "test-key-upsert", "value", "second"));
            assertEquals("second", storage.get(Map.of("key", "test-key-upsert")));
        }

        @Test
        @DisplayName("get returns null for nonexistent key")
        void getMissing() {
            assertNull(storage.get(Map.of("key", "nonexistent-key-" + System.nanoTime())));
        }

        @Test
        @DisplayName("delete removes key")
        void deleteKey() {
            storage.set(Map.of("key", "test-key-delete", "value", "to-delete"));
            assertTrue(storage.delete(Map.of("key", "test-key-delete")));
            assertNull(storage.get(Map.of("key", "test-key-delete")));
        }

        @Test
        @DisplayName("delete returns false for nonexistent key")
        void deleteMissing() {
            assertFalse(storage.delete(Map.of("key", "nonexistent-key-" + System.nanoTime())));
        }

        @Test
        @DisplayName("has returns true for existing key")
        void hasExisting() {
            storage.set(Map.of("key", "test-key-has", "value", "exists"));
            assertTrue(storage.has(Map.of("key", "test-key-has")));
        }

        @Test
        @DisplayName("has returns false for nonexistent key")
        void hasMissing() {
            assertFalse(storage.has(Map.of("key", "nonexistent-key-" + System.nanoTime())));
        }

        @Test
        @DisplayName("handles UTF-8 values")
        void utf8Value() {
            String utf8 = "caf\u00e9 \ud83d\ude80 \u00fc\u00f1\u00ee\u00e7\u00f6d\u00e9";
            storage.set(Map.of("key", "test-key-utf8", "value", utf8));
            assertEquals(utf8, storage.get(Map.of("key", "test-key-utf8")));
        }
    }

    @Nested
    @DisplayName("Windows")
    @EnabledOnOs(OS.WINDOWS)
    class WindowsTests {

        @Test
        @DisplayName("set and get round-trip")
        void setAndGet() {
            assertTrue(storage.set(Map.of("key", "test-key", "value", "secret123")));
            assertEquals("secret123", storage.get(Map.of("key", "test-key")));
        }

        @Test
        @DisplayName("set upsert overwrites existing value")
        void setUpsert() {
            storage.set(Map.of("key", "test-key-upsert", "value", "first"));
            storage.set(Map.of("key", "test-key-upsert", "value", "second"));
            assertEquals("second", storage.get(Map.of("key", "test-key-upsert")));
        }

        @Test
        @DisplayName("get returns null for nonexistent key")
        void getMissing() {
            assertNull(storage.get(Map.of("key", "nonexistent-key-" + System.nanoTime())));
        }

        @Test
        @DisplayName("delete removes key")
        void deleteKey() {
            storage.set(Map.of("key", "test-key-delete", "value", "to-delete"));
            assertTrue(storage.delete(Map.of("key", "test-key-delete")));
            assertNull(storage.get(Map.of("key", "test-key-delete")));
        }

        @Test
        @DisplayName("delete returns false for nonexistent key")
        void deleteMissing() {
            assertFalse(storage.delete(Map.of("key", "nonexistent-key-" + System.nanoTime())));
        }

        @Test
        @DisplayName("has returns true for existing key")
        void hasExisting() {
            storage.set(Map.of("key", "test-key-has", "value", "exists"));
            assertTrue(storage.has(Map.of("key", "test-key-has")));
        }

        @Test
        @DisplayName("has returns false for nonexistent key")
        void hasMissing() {
            assertFalse(storage.has(Map.of("key", "nonexistent-key-" + System.nanoTime())));
        }

        @Test
        @DisplayName("handles UTF-8 values")
        void utf8Value() {
            String utf8 = "caf\u00e9 \ud83d\ude80 \u00fc\u00f1\u00ee\u00e7\u00f6d\u00e9";
            storage.set(Map.of("key", "test-key-utf8", "value", utf8));
            assertEquals(utf8, storage.get(Map.of("key", "test-key-utf8")));
        }
    }

    @Nested
    @DisplayName("Linux")
    @EnabledOnOs(OS.LINUX)
    class LinuxTests {

        @Test
        @DisplayName("set and get round-trip")
        void setAndGet() {
            assertTrue(storage.set(Map.of("key", "test-key", "value", "secret123")));
            assertEquals("secret123", storage.get(Map.of("key", "test-key")));
        }

        @Test
        @DisplayName("set upsert overwrites existing value")
        void setUpsert() {
            storage.set(Map.of("key", "test-key-upsert", "value", "first"));
            storage.set(Map.of("key", "test-key-upsert", "value", "second"));
            assertEquals("second", storage.get(Map.of("key", "test-key-upsert")));
        }

        @Test
        @DisplayName("get returns null for nonexistent key")
        void getMissing() {
            assertNull(storage.get(Map.of("key", "nonexistent-key-" + System.nanoTime())));
        }

        @Test
        @DisplayName("delete removes key")
        void deleteKey() {
            storage.set(Map.of("key", "test-key-delete", "value", "to-delete"));
            assertTrue(storage.delete(Map.of("key", "test-key-delete")));
            assertNull(storage.get(Map.of("key", "test-key-delete")));
        }

        @Test
        @DisplayName("delete returns false for nonexistent key")
        void deleteMissing() {
            assertFalse(storage.delete(Map.of("key", "nonexistent-key-" + System.nanoTime())));
        }

        @Test
        @DisplayName("has returns true for existing key")
        void hasExisting() {
            storage.set(Map.of("key", "test-key-has", "value", "exists"));
            assertTrue(storage.has(Map.of("key", "test-key-has")));
        }

        @Test
        @DisplayName("has returns false for nonexistent key")
        void hasMissing() {
            assertFalse(storage.has(Map.of("key", "nonexistent-key-" + System.nanoTime())));
        }

        @Test
        @DisplayName("handles UTF-8 values")
        void utf8Value() {
            String utf8 = "caf\u00e9 \ud83d\ude80 \u00fc\u00f1\u00ee\u00e7\u00f6d\u00e9";
            storage.set(Map.of("key", "test-key-utf8", "value", utf8));
            assertEquals(utf8, storage.get(Map.of("key", "test-key-utf8")));
        }
    }
}
