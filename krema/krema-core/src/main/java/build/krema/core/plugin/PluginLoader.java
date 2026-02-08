package build.krema.core.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import build.krema.core.util.Logger;

/**
 * Loads and manages Krema plugins.
 * Supports built-in plugins via ServiceLoader and external plugins from JAR files.
 */
public class PluginLoader {

    private static final Logger LOG = new Logger("PluginLoader");
    private static final String MANIFEST_NAME = "plugin.json";

    private final Map<String, LoadedPlugin> plugins = new LinkedHashMap<>();
    private final List<ClassLoader> pluginClassLoaders = new ArrayList<>();

    /**
     * Loads all built-in plugins discovered via ServiceLoader.
     */
    public void loadBuiltinPlugins() {
        ServiceLoader<KremaPlugin> loader = ServiceLoader.load(KremaPlugin.class);
        for (KremaPlugin plugin : loader) {
            registerPlugin(plugin, null);
        }
    }

    /**
     * Loads a plugin from a JAR file.
     */
    public void loadFromJar(Path jarPath) throws PluginException {
        LOG.info("Loading plugin from: %s", jarPath);

        try {
            // Read manifest from JAR
            PluginManifest manifest = readManifestFromJar(jarPath);
            manifest.validate();

            // Check for duplicates
            if (plugins.containsKey(manifest.getId())) {
                throw new PluginException("Plugin already loaded: " + manifest.getId());
            }

            // Create classloader for the plugin
            URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                getClass().getClassLoader()
            );
            pluginClassLoaders.add(classLoader);

            // Load and instantiate the plugin class
            Class<?> pluginClass = classLoader.loadClass(manifest.getMainClass());
            if (!KremaPlugin.class.isAssignableFrom(pluginClass)) {
                throw new PluginException(
                    "Plugin class does not implement KremaPlugin: " + manifest.getMainClass()
                );
            }

            KremaPlugin plugin = (KremaPlugin) pluginClass.getDeclaredConstructor().newInstance();
            registerPlugin(plugin, manifest);

            LOG.info("Loaded plugin: %s v%s", manifest.getName(), manifest.getVersion());

        } catch (PluginException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginException("Failed to load plugin from: " + jarPath, e);
        }
    }

    /**
     * Loads all plugins from a directory.
     */
    public void loadFromDirectory(Path pluginDir) throws PluginException {
        if (!Files.isDirectory(pluginDir)) {
            LOG.debug("Plugin directory does not exist: %s", pluginDir);
            return;
        }

        try (var stream = Files.list(pluginDir)) {
            stream.filter(p -> p.toString().endsWith(".jar"))
                .forEach(jar -> {
                    try {
                        loadFromJar(jar);
                    } catch (PluginException e) {
                        LOG.error("Failed to load plugin: %s - %s", jar.getFileName(), e.getMessage());
                    }
                });
        } catch (IOException e) {
            throw new PluginException("Failed to list plugins in: " + pluginDir, e);
        }
    }

    private PluginManifest readManifestFromJar(Path jarPath) throws PluginException {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            JarEntry manifestEntry = jarFile.getJarEntry(MANIFEST_NAME);
            if (manifestEntry == null) {
                throw new PluginException("Plugin JAR missing " + MANIFEST_NAME + ": " + jarPath);
            }

            try (InputStream is = jarFile.getInputStream(manifestEntry)) {
                return PluginManifest.load(is);
            }
        } catch (IOException e) {
            throw new PluginException("Failed to read plugin manifest: " + jarPath, e);
        }
    }

    public void registerPlugin(KremaPlugin plugin, PluginManifest manifest) {
        String id = plugin.getId();
        plugins.put(id, new LoadedPlugin(plugin, manifest));
        LOG.debug("Registered plugin: %s", id);
    }

    /**
     * Registers a plugin programmatically without a manifest.
     */
    public void registerPlugin(KremaPlugin plugin) {
        registerPlugin(plugin, null);
    }

    /**
     * Initializes all loaded plugins.
     */
    public void initializeAll(PluginContext context) {
        for (LoadedPlugin loaded : plugins.values()) {
            try {
                LOG.debug("Initializing plugin: %s", loaded.plugin.getId());
                loaded.plugin.initialize(context);
                loaded.initialized = true;
            } catch (Exception e) {
                LOG.error("Failed to initialize plugin: %s", loaded.plugin.getId());
                LOG.error("Error", e);
            }
        }
    }

    /**
     * Shuts down all loaded plugins.
     */
    public void shutdownAll() {
        // Shutdown in reverse order
        List<LoadedPlugin> reversed = new ArrayList<>(plugins.values());
        Collections.reverse(reversed);

        for (LoadedPlugin loaded : reversed) {
            if (loaded.initialized) {
                try {
                    LOG.debug("Shutting down plugin: %s", loaded.plugin.getId());
                    loaded.plugin.shutdown();
                } catch (Exception e) {
                    LOG.error("Error shutting down plugin: %s - %s",
                        loaded.plugin.getId(), e.getMessage());
                }
            }
        }

        // Close classloaders
        for (ClassLoader classLoader : pluginClassLoaders) {
            if (classLoader instanceof URLClassLoader urlClassLoader) {
                try {
                    urlClassLoader.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        plugins.clear();
        pluginClassLoaders.clear();
    }

    /**
     * Returns a loaded plugin by ID.
     */
    public Optional<KremaPlugin> getPlugin(String id) {
        LoadedPlugin loaded = plugins.get(id);
        return loaded != null ? Optional.of(loaded.plugin) : Optional.empty();
    }

    /**
     * Returns all loaded plugins.
     */
    public List<KremaPlugin> getPlugins() {
        return plugins.values().stream()
            .map(lp -> lp.plugin)
            .toList();
    }

    /**
     * Returns the number of loaded plugins.
     */
    public int size() {
        return plugins.size();
    }

    /**
     * Collects all command handlers from all plugins.
     */
    public List<Object> collectCommandHandlers() {
        List<Object> handlers = new ArrayList<>();
        for (LoadedPlugin loaded : plugins.values()) {
            handlers.addAll(loaded.plugin.getCommandHandlers());
        }
        return handlers;
    }

    private static class LoadedPlugin {
        final KremaPlugin plugin;
        final PluginManifest manifest;
        boolean initialized = false;

        LoadedPlugin(KremaPlugin plugin, PluginManifest manifest) {
            this.plugin = plugin;
            this.manifest = manifest;
        }
    }
}
