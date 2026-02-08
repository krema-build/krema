package build.krema.core.api.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import build.krema.core.KremaCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent key-value storage API.
 * Stores data as JSON files in the app data directory.
 * Supports multiple named stores.
 */
public class Store {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path storeDir;
    private final Map<String, Map<String, Object>> stores = new ConcurrentHashMap<>();
    private String defaultStoreName = "store";

    public Store(Path appDataDir) {
        this.storeDir = appDataDir.resolve("stores");
        try {
            Files.createDirectories(storeDir);
        } catch (IOException e) {
            System.err.println("[Store] Failed to create store directory: " + e.getMessage());
        }
    }

    /**
     * Sets the default store name used when no store is specified.
     */
    public void setDefaultStore(String name) {
        this.defaultStoreName = name;
    }

    private String getStoreName(Map<String, Object> options) {
        if (options != null && options.containsKey("store")) {
            return (String) options.get("store");
        }
        return defaultStoreName;
    }

    private Map<String, Object> getStore(String name) {
        return stores.computeIfAbsent(name, this::loadStore);
    }

    private Map<String, Object> loadStore(String name) {
        Path file = storeDir.resolve(name + ".json");
        if (Files.exists(file)) {
            try {
                String content = Files.readString(file);
                return MAPPER.readValue(content, new TypeReference<ConcurrentHashMap<String, Object>>() {});
            } catch (IOException e) {
                System.err.println("[Store] Failed to load store '" + name + "': " + e.getMessage());
            }
        }
        return new ConcurrentHashMap<>();
    }

    private void saveStore(String name) {
        Map<String, Object> store = stores.get(name);
        if (store == null) return;

        Path file = storeDir.resolve(name + ".json");
        try {
            String content = MAPPER.writeValueAsString(store);
            Files.writeString(file, content);
        } catch (IOException e) {
            System.err.println("[Store] Failed to save store '" + name + "': " + e.getMessage());
        }
    }

    @KremaCommand("store:get")
    public Object get(Map<String, Object> options) {
        String storeName = getStoreName(options);
        String key = (String) options.get("key");
        Object defaultValue = options.get("default");

        if (key == null) {
            return defaultValue;
        }

        Map<String, Object> store = getStore(storeName);
        return store.getOrDefault(key, defaultValue);
    }

    @KremaCommand("store:set")
    public void set(Map<String, Object> options) {
        String storeName = getStoreName(options);
        String key = (String) options.get("key");
        Object value = options.get("value");

        if (key == null) {
            throw new IllegalArgumentException("Key is required");
        }

        Map<String, Object> store = getStore(storeName);
        if (value == null) {
            store.remove(key);
        } else {
            store.put(key, value);
        }
        saveStore(storeName);
    }

    @KremaCommand("store:delete")
    public boolean delete(Map<String, Object> options) {
        String storeName = getStoreName(options);
        String key = (String) options.get("key");

        if (key == null) {
            return false;
        }

        Map<String, Object> store = getStore(storeName);
        boolean existed = store.containsKey(key);
        store.remove(key);
        saveStore(storeName);
        return existed;
    }

    @KremaCommand("store:has")
    public boolean has(Map<String, Object> options) {
        String storeName = getStoreName(options);
        String key = (String) options.get("key");

        if (key == null) {
            return false;
        }

        Map<String, Object> store = getStore(storeName);
        return store.containsKey(key);
    }

    @KremaCommand("store:keys")
    public List<String> keys(Map<String, Object> options) {
        String storeName = getStoreName(options);
        Map<String, Object> store = getStore(storeName);
        return List.copyOf(store.keySet());
    }

    @KremaCommand("store:entries")
    public Map<String, Object> entries(Map<String, Object> options) {
        String storeName = getStoreName(options);
        Map<String, Object> store = getStore(storeName);
        return Map.copyOf(store);
    }

    @KremaCommand("store:clear")
    public void clear(Map<String, Object> options) {
        String storeName = getStoreName(options);
        Map<String, Object> store = getStore(storeName);
        store.clear();
        saveStore(storeName);
    }

    @KremaCommand("store:size")
    public int size(Map<String, Object> options) {
        String storeName = getStoreName(options);
        Map<String, Object> store = getStore(storeName);
        return store.size();
    }

    @KremaCommand("store:save")
    public void save(Map<String, Object> options) {
        String storeName = getStoreName(options);
        saveStore(storeName);
    }

    @KremaCommand("store:reload")
    public void reload(Map<String, Object> options) {
        String storeName = getStoreName(options);
        stores.put(storeName, loadStore(storeName));
    }

    @KremaCommand("store:setMultiple")
    public void setMultiple(Map<String, Object> options) {
        String storeName = getStoreName(options);
        @SuppressWarnings("unchecked")
        Map<String, Object> entries = (Map<String, Object>) options.get("entries");

        if (entries == null) {
            return;
        }

        Map<String, Object> store = getStore(storeName);
        store.putAll(entries);
        saveStore(storeName);
    }

    @KremaCommand("store:getMultiple")
    public Map<String, Object> getMultiple(Map<String, Object> options) {
        String storeName = getStoreName(options);
        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) options.get("keys");

        if (keys == null) {
            return Map.of();
        }

        Map<String, Object> store = getStore(storeName);
        Map<String, Object> result = new ConcurrentHashMap<>();
        for (String key : keys) {
            if (store.containsKey(key)) {
                result.put(key, store.get(key));
            }
        }
        return result;
    }
}
