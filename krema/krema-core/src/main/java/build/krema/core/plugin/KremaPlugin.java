package build.krema.core.plugin;

import java.util.List;

/**
 * Interface for Krema plugins.
 * Plugins extend the functionality of Krema applications.
 */
public interface KremaPlugin {

    /**
     * Returns the unique identifier for this plugin.
     * Should be in reverse domain notation (e.g., "com.example.myplugin").
     */
    String getId();

    /**
     * Returns the display name of the plugin.
     */
    String getName();

    /**
     * Returns the semantic version of the plugin (e.g., "1.0.0").
     */
    String getVersion();

    /**
     * Returns a brief description of the plugin.
     */
    default String getDescription() {
        return "";
    }

    /**
     * Called when the plugin is loaded.
     * Use this to perform initialization.
     *
     * @param context The plugin context providing access to Krema APIs
     */
    default void initialize(PluginContext context) {}

    /**
     * Called when the plugin is being unloaded.
     * Use this to clean up resources.
     */
    default void shutdown() {}

    /**
     * Returns command handler objects that will be registered.
     * Methods annotated with @KremaCommand will be available to the frontend.
     */
    default List<Object> getCommandHandlers() {
        return List.of();
    }

    /**
     * Returns the permissions required by this plugin.
     */
    default List<String> getRequiredPermissions() {
        return List.of();
    }

    /**
     * Called when a new window is created.
     */
    default void onWindowCreated(String windowLabel) {}

    /**
     * Called when a window is closed.
     */
    default void onWindowClosed(String windowLabel) {}
}
