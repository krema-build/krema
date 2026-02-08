package build.krema.core.plugin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Plugin manifest parsed from plugin.json.
 */
public class PluginManifest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String id;
    private String name;
    private String version;
    private String description;

    @JsonProperty("main")
    private String mainClass;

    private List<String> permissions;
    private List<String> dependencies;

    public static PluginManifest load(Path manifestPath) throws IOException {
        return MAPPER.readValue(Files.newInputStream(manifestPath), PluginManifest.class);
    }

    public static PluginManifest load(InputStream inputStream) throws IOException {
        return MAPPER.readValue(inputStream, PluginManifest.class);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMainClass() {
        return mainClass;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public List<String> getPermissions() {
        return permissions != null ? permissions : List.of();
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public List<String> getDependencies() {
        return dependencies != null ? dependencies : List.of();
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public void validate() throws PluginException {
        if (id == null || id.isBlank()) {
            throw new PluginException("Plugin manifest missing required field: id");
        }
        if (name == null || name.isBlank()) {
            throw new PluginException("Plugin manifest missing required field: name");
        }
        if (version == null || version.isBlank()) {
            throw new PluginException("Plugin manifest missing required field: version");
        }
        if (mainClass == null || mainClass.isBlank()) {
            throw new PluginException("Plugin manifest missing required field: main");
        }
    }

    @Override
    public String toString() {
        return String.format("PluginManifest{id='%s', name='%s', version='%s'}", id, name, version);
    }
}
