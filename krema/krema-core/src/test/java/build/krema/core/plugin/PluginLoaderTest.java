package build.krema.core.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import build.krema.core.CommandRegistry;
import build.krema.core.event.EventEmitter;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.plugin.PluginContext;
import build.krema.core.plugin.PluginLoader;
import build.krema.core.util.Logger;
import build.krema.core.window.WindowManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PluginLoader")
class PluginLoaderTest {

    private PluginLoader loader;

    @BeforeEach
    void setUp() {
        loader = new PluginLoader();
    }

    @Test
    @DisplayName("registerPlugin adds plugin and size/getPlugin work")
    void registerAndQuery() {
        TestPlugin plugin = new TestPlugin("test-plugin", "Test Plugin", "1.0.0");
        loader.registerPlugin(plugin);

        assertEquals(1, loader.size());
        assertTrue(loader.getPlugin("test-plugin").isPresent());
        assertEquals(plugin, loader.getPlugin("test-plugin").get());
    }

    @Test
    @DisplayName("getPlugin returns empty for unknown ID")
    void getPluginUnknownId() {
        assertTrue(loader.getPlugin("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("initializeAll calls initialize on all plugins")
    void initializeAll() {
        TestPlugin plugin1 = new TestPlugin("p1", "Plugin 1", "1.0.0");
        TestPlugin plugin2 = new TestPlugin("p2", "Plugin 2", "1.0.0");
        loader.registerPlugin(plugin1);
        loader.registerPlugin(plugin2);

        loader.initializeAll(new StubPluginContext());

        assertTrue(plugin1.initialized);
        assertTrue(plugin2.initialized);
    }

    @Test
    @DisplayName("shutdownAll calls shutdown on initialized plugins and clears list")
    void shutdownAll() {
        TestPlugin plugin1 = new TestPlugin("p1", "Plugin 1", "1.0.0");
        TestPlugin plugin2 = new TestPlugin("p2", "Plugin 2", "1.0.0");
        loader.registerPlugin(plugin1);
        loader.registerPlugin(plugin2);

        loader.initializeAll(new StubPluginContext());
        loader.shutdownAll();

        assertTrue(plugin1.shutDown);
        assertTrue(plugin2.shutDown);
        assertEquals(0, loader.size());
    }

    @Test
    @DisplayName("collectCommandHandlers aggregates from all plugins")
    void collectCommandHandlers() {
        Object handler1 = new Object();
        Object handler2 = new Object();

        TestPlugin plugin1 = new TestPlugin("p1", "Plugin 1", "1.0.0");
        plugin1.commandHandlers.add(handler1);

        TestPlugin plugin2 = new TestPlugin("p2", "Plugin 2", "1.0.0");
        plugin2.commandHandlers.add(handler2);

        loader.registerPlugin(plugin1);
        loader.registerPlugin(plugin2);

        List<Object> handlers = loader.collectCommandHandlers();
        assertEquals(2, handlers.size());
        assertTrue(handlers.contains(handler1));
        assertTrue(handlers.contains(handler2));
    }

    // --- Test plugin ---

    private static class TestPlugin implements KremaPlugin {
        private final String id;
        private final String name;
        private final String version;
        boolean initialized = false;
        boolean shutDown = false;
        final List<Object> commandHandlers = new ArrayList<>();

        TestPlugin(String id, String name, String version) {
            this.id = id;
            this.name = name;
            this.version = version;
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return name; }
        @Override public String getVersion() { return version; }

        @Override
        public void initialize(PluginContext context) {
            initialized = true;
        }

        @Override
        public void shutdown() {
            shutDown = true;
        }

        @Override
        public List<Object> getCommandHandlers() {
            return commandHandlers;
        }
    }

    // --- Stub PluginContext ---

    private static class StubPluginContext implements PluginContext {
        @Override public WindowManager getWindowManager() { return null; }
        @Override public EventEmitter getEventEmitter() { return null; }
        @Override public CommandRegistry getCommandRegistry() { return null; }
        @Override public Logger getLogger(String name) { return new Logger(name); }
        @Override public Path getPluginDataDir() { return Path.of("."); }
        @Override public Path getAppDataDir() { return Path.of("."); }
        @Override public Map<String, Object> getConfig() { return Map.of(); }
        @Override public boolean hasPermission(String permission) { return true; }
        @Override public String getAppName() { return "test-app"; }
        @Override public String getAppVersion() { return "1.0.0"; }
    }
}
