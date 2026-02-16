package build.krema.core.api.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Store")
class StoreTest {

    @TempDir
    Path tempDir;

    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store(tempDir);
    }

    private Map<String, Object> opts(String key) {
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        return m;
    }

    private Map<String, Object> opts(String key, Object value) {
        Map<String, Object> m = opts(key);
        m.put("value", value);
        return m;
    }

    @Nested
    @DisplayName("get/set")
    class GetSet {

        @Test
        @DisplayName("get returns default when key does not exist")
        void getReturnsDefault() {
            Map<String, Object> options = opts("missing");
            options.put("default", "fallback");

            assertEquals("fallback", store.get(options));
        }

        @Test
        @DisplayName("get returns default when key is null")
        void getReturnsDefaultForNullKey() {
            Map<String, Object> options = new HashMap<>();
            options.put("default", "fallback");

            assertEquals("fallback", store.get(options));
        }

        @Test
        @DisplayName("set then get returns stored value")
        void setThenGet() {
            store.set(opts("name", "Alice"));

            assertEquals("Alice", store.get(opts("name")));
        }

        @Test
        @DisplayName("set with null value removes the key")
        void setNullRemovesKey() {
            store.set(opts("name", "Alice"));
            store.set(opts("name", null));

            assertFalse(store.has(opts("name")));
        }

        @Test
        @DisplayName("set throws when key is null")
        void setThrowsForNullKey() {
            Map<String, Object> options = new HashMap<>();
            options.put("value", "x");

            assertThrows(IllegalArgumentException.class, () -> store.set(options));
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("delete existing key returns true")
        void deleteExistingReturnsTrue() {
            store.set(opts("key1", "val1"));

            assertTrue(store.delete(opts("key1")));
        }

        @Test
        @DisplayName("delete missing key returns false")
        void deleteMissingReturnsFalse() {
            assertFalse(store.delete(opts("nope")));
        }

        @Test
        @DisplayName("delete with null key returns false")
        void deleteNullKeyReturnsFalse() {
            assertFalse(store.delete(new HashMap<>()));
        }
    }

    @Nested
    @DisplayName("has")
    class Has {

        @Test
        @DisplayName("has returns true for existing key")
        void hasTrueForExisting() {
            store.set(opts("exists", "yes"));

            assertTrue(store.has(opts("exists")));
        }

        @Test
        @DisplayName("has returns false for missing key")
        void hasFalseForMissing() {
            assertFalse(store.has(opts("missing")));
        }

        @Test
        @DisplayName("has returns false for null key")
        void hasFalseForNullKey() {
            assertFalse(store.has(new HashMap<>()));
        }
    }

    @Nested
    @DisplayName("keys/entries/size/clear")
    class Collections {

        @Test
        @DisplayName("keys returns all stored keys")
        void keysReturnsAll() {
            store.set(opts("a", 1));
            store.set(opts("b", 2));

            List<String> keys = store.keys(new HashMap<>());
            assertTrue(keys.containsAll(List.of("a", "b")));
            assertEquals(2, keys.size());
        }

        @Test
        @DisplayName("entries returns all key-value pairs")
        void entriesReturnsAll() {
            store.set(opts("x", 10));
            store.set(opts("y", 20));

            Map<String, Object> entries = store.entries(new HashMap<>());
            assertEquals(10, entries.get("x"));
            assertEquals(20, entries.get("y"));
        }

        @Test
        @DisplayName("size returns count of stored keys")
        void sizeReturnsCount() {
            assertEquals(0, store.size(new HashMap<>()));

            store.set(opts("a", 1));
            store.set(opts("b", 2));

            assertEquals(2, store.size(new HashMap<>()));
        }

        @Test
        @DisplayName("clear removes all entries")
        void clearRemovesAll() {
            store.set(opts("a", 1));
            store.set(opts("b", 2));
            store.clear(new HashMap<>());

            assertEquals(0, store.size(new HashMap<>()));
        }
    }

    @Nested
    @DisplayName("batch operations")
    class Batch {

        @Test
        @DisplayName("setMultiple stores all entries at once")
        void setMultipleStoresAll() {
            Map<String, Object> options = new HashMap<>();
            options.put("entries", Map.of("a", 1, "b", 2, "c", 3));

            store.setMultiple(options);

            assertEquals(3, store.size(new HashMap<>()));
            assertEquals(1, store.get(opts("a")));
        }

        @Test
        @DisplayName("setMultiple with null entries does nothing")
        void setMultipleNullEntries() {
            store.setMultiple(new HashMap<>());
            assertEquals(0, store.size(new HashMap<>()));
        }

        @Test
        @DisplayName("getMultiple returns matching entries")
        void getMultipleReturnsMatching() {
            store.set(opts("a", 1));
            store.set(opts("b", 2));
            store.set(opts("c", 3));

            Map<String, Object> options = new HashMap<>();
            options.put("keys", List.of("a", "c", "missing"));

            Map<String, Object> result = store.getMultiple(options);
            assertEquals(2, result.size());
            assertEquals(1, result.get("a"));
            assertEquals(3, result.get("c"));
        }

        @Test
        @DisplayName("getMultiple with null keys returns empty map")
        void getMultipleNullKeys() {
            Map<String, Object> result = store.getMultiple(new HashMap<>());
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("persistence")
    class Persistence {

        @Test
        @DisplayName("data persists to disk after set")
        void persistsToDisk() {
            store.set(opts("key1", "value1"));

            Path storeFile = tempDir.resolve("stores").resolve("store.json");
            assertTrue(Files.exists(storeFile));
        }

        @Test
        @DisplayName("reload restores data from disk")
        void reloadRestoresData() {
            store.set(opts("key1", "value1"));

            // Create a new Store pointing at the same directory
            Store store2 = new Store(tempDir);
            assertEquals("value1", store2.get(opts("key1")));
        }

        @Test
        @DisplayName("reload command reloads from disk")
        void reloadCommand() throws IOException {
            store.set(opts("key1", "original"));

            // Manually overwrite the file
            Path storeFile = tempDir.resolve("stores").resolve("store.json");
            Files.writeString(storeFile, "{\"key1\":\"modified\"}");

            store.reload(new HashMap<>());

            assertEquals("modified", store.get(opts("key1")));
        }

        @Test
        @DisplayName("save command persists current state")
        void saveCommand() {
            store.set(opts("key1", "value1"));
            store.save(new HashMap<>());

            Path storeFile = tempDir.resolve("stores").resolve("store.json");
            assertTrue(Files.exists(storeFile));
        }
    }

    @Nested
    @DisplayName("named stores")
    class NamedStores {

        @Test
        @DisplayName("different store names are independent")
        void differentStoresAreIndependent() {
            Map<String, Object> setOpts = new HashMap<>();
            setOpts.put("key", "k");
            setOpts.put("value", "in-custom");
            setOpts.put("store", "custom");
            store.set(setOpts);

            store.set(opts("k", "in-default"));

            Map<String, Object> getDefault = opts("k");
            assertEquals("in-default", store.get(getDefault));

            Map<String, Object> getCustom = opts("k");
            getCustom.put("store", "custom");
            assertEquals("in-custom", store.get(getCustom));
        }

        @Test
        @DisplayName("setDefaultStore changes default store name")
        void setDefaultStoreChangesDefault() {
            store.setDefaultStore("mystore");
            store.set(opts("k", "v"));

            Path storeFile = tempDir.resolve("stores").resolve("mystore.json");
            assertTrue(Files.exists(storeFile));
        }
    }
}
