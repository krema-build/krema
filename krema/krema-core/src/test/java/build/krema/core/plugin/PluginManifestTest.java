package build.krema.core.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import build.krema.core.plugin.PluginException;
import build.krema.core.plugin.PluginManifest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PluginManifest")
class PluginManifestTest {

    @Test
    @DisplayName("load parses valid JSON with all fields")
    void loadValidJson() throws Exception {
        String json = """
                {
                  "id": "com.example.myplugin",
                  "name": "My Plugin",
                  "version": "1.2.3",
                  "description": "A test plugin",
                  "main": "com.example.MyPluginImpl",
                  "permissions": ["fs:read", "fs:write"],
                  "dependencies": ["com.example.base"]
                }
                """;
        InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        PluginManifest manifest = PluginManifest.load(is);

        assertEquals("com.example.myplugin", manifest.getId());
        assertEquals("My Plugin", manifest.getName());
        assertEquals("1.2.3", manifest.getVersion());
        assertEquals("A test plugin", manifest.getDescription());
        assertEquals("com.example.MyPluginImpl", manifest.getMainClass());
        assertEquals(2, manifest.getPermissions().size());
        assertEquals("fs:read", manifest.getPermissions().get(0));
        assertEquals(1, manifest.getDependencies().size());
        assertEquals("com.example.base", manifest.getDependencies().get(0));
    }

    @Test
    @DisplayName("validate passes for complete manifest")
    void validateComplete() throws Exception {
        PluginManifest manifest = createManifest("id", "name", "1.0.0", "com.Main");
        assertDoesNotThrow(manifest::validate);
    }

    @Test
    @DisplayName("validate throws PluginException for missing id")
    void validateMissingId() throws Exception {
        PluginManifest manifest = createManifest(null, "name", "1.0.0", "com.Main");
        PluginException ex = assertThrows(PluginException.class, manifest::validate);
        assertTrue(ex.getMessage().contains("id"));
    }

    @Test
    @DisplayName("validate throws PluginException for missing name")
    void validateMissingName() throws Exception {
        PluginManifest manifest = createManifest("id", null, "1.0.0", "com.Main");
        PluginException ex = assertThrows(PluginException.class, manifest::validate);
        assertTrue(ex.getMessage().contains("name"));
    }

    @Test
    @DisplayName("validate throws PluginException for missing version")
    void validateMissingVersion() throws Exception {
        PluginManifest manifest = createManifest("id", "name", null, "com.Main");
        PluginException ex = assertThrows(PluginException.class, manifest::validate);
        assertTrue(ex.getMessage().contains("version"));
    }

    @Test
    @DisplayName("validate throws PluginException for missing main")
    void validateMissingMain() throws Exception {
        PluginManifest manifest = createManifest("id", "name", "1.0.0", null);
        PluginException ex = assertThrows(PluginException.class, manifest::validate);
        assertTrue(ex.getMessage().contains("main"));
    }

    @Test
    @DisplayName("getPermissions returns empty list when null")
    void permissionsNullReturnsEmpty() {
        PluginManifest manifest = new PluginManifest();
        assertTrue(manifest.getPermissions().isEmpty());
    }

    @Test
    @DisplayName("getDependencies returns empty list when null")
    void dependenciesNullReturnsEmpty() {
        PluginManifest manifest = new PluginManifest();
        assertTrue(manifest.getDependencies().isEmpty());
    }

    private PluginManifest createManifest(String id, String name, String version, String main) {
        PluginManifest manifest = new PluginManifest();
        manifest.setId(id);
        manifest.setName(name);
        manifest.setVersion(version);
        manifest.setMainClass(main);
        return manifest;
    }
}
