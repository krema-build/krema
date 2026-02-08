package build.krema.core.main;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import build.krema.core.CommandRegistry;
import build.krema.core.event.EventEmitter;
import build.krema.core.window.WindowManager;

/**
 * Service locator and dependency injection container for Krema applications.
 * Provides access to core services without relying on singletons.
 *
 * <p>KremaContext is created during application startup and provides a centralized
 * way to access services like WindowManager, EventEmitter, and CommandRegistry.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Get the current context
 * KremaContext ctx = KremaContext.current();
 *
 * // Access services
 * WindowManager windows = ctx.getWindowManager();
 * EventEmitter events = ctx.getEventEmitter();
 * }</pre>
 *
 * <p>For testing, create an isolated context:</p>
 * <pre>{@code
 * KremaContext testCtx = KremaContext.builder()
 *     .windowManager(mockWindowManager)
 *     .eventEmitter(mockEmitter)
 *     .build();
 * }</pre>
 */
public final class KremaContext {

    private static volatile KremaContext instance;

    // Core services
    private final WindowManager windowManager;
    private final EventEmitter eventEmitter;
    private final CommandRegistry commandRegistry;

    // Application metadata
    private final String appName;
    private final String appVersion;
    private final String appIdentifier;
    private final Path appDataDir;
    private final Path appConfigDir;

    // Configuration
    private final Map<String, Object> config;

    // Custom services registry
    private final Map<Class<?>, Object> services;

    private KremaContext(Builder builder) {
        this.windowManager = builder.windowManager;
        this.eventEmitter = builder.eventEmitter;
        this.commandRegistry = builder.commandRegistry;
        this.appName = builder.appName;
        this.appVersion = builder.appVersion;
        this.appIdentifier = builder.appIdentifier;
        this.appDataDir = builder.appDataDir;
        this.appConfigDir = builder.appConfigDir;
        this.config = new ConcurrentHashMap<>(builder.config);
        this.services = new ConcurrentHashMap<>(builder.services);
    }

    /**
     * Returns the current application context.
     *
     * @return The current context
     * @throws IllegalStateException if no context has been initialized
     */
    public static KremaContext current() {
        KremaContext ctx = instance;
        if (ctx == null) {
            throw new IllegalStateException(
                "KremaContext has not been initialized. " +
                "Ensure the application is started via Krema.app().run()");
        }
        return ctx;
    }

    /**
     * Returns the current context if available.
     *
     * @return Optional containing the context, or empty if not initialized
     */
    public static Optional<KremaContext> tryGet() {
        return Optional.ofNullable(instance);
    }

    /**
     * Checks if a context has been initialized.
     *
     * @return true if a context is available
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Creates a new context builder.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // === Core Services ===

    /**
     * Returns the window manager for multi-window operations.
     */
    public WindowManager getWindowManager() {
        return windowManager;
    }

    /**
     * Returns the event emitter for backend-to-frontend events.
     */
    public EventEmitter getEventEmitter() {
        return eventEmitter;
    }

    /**
     * Returns the command registry for IPC commands.
     */
    public CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    // === Application Metadata ===

    /**
     * Returns the application name.
     */
    public String getAppName() {
        return appName;
    }

    /**
     * Returns the application version.
     */
    public String getAppVersion() {
        return appVersion;
    }

    /**
     * Returns the application identifier (bundle ID).
     */
    public String getAppIdentifier() {
        return appIdentifier;
    }

    /**
     * Returns the application data directory.
     */
    public Path getAppDataDir() {
        return appDataDir;
    }

    /**
     * Returns the application config directory.
     */
    public Path getAppConfigDir() {
        return appConfigDir;
    }

    // === Configuration ===

    /**
     * Gets a configuration value.
     *
     * @param key The configuration key
     * @return The value, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key) {
        return (T) config.get(key);
    }

    /**
     * Gets a configuration value with a default.
     *
     * @param key The configuration key
     * @param defaultValue The default value if not found
     * @return The value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key, T defaultValue) {
        Object value = config.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Sets a configuration value.
     *
     * @param key The configuration key
     * @param value The value to set
     */
    public void setConfig(String key, Object value) {
        if (value != null) {
            config.put(key, value);
        } else {
            config.remove(key);
        }
    }

    // === Custom Services ===

    /**
     * Registers a custom service.
     *
     * @param type The service interface/class
     * @param instance The service instance
     */
    public <T> void registerService(Class<T> type, T instance) {
        services.put(type, instance);
    }

    /**
     * Gets a registered service.
     *
     * @param type The service interface/class
     * @return The service instance
     * @throws IllegalArgumentException if service is not registered
     */
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> type) {
        Object service = services.get(type);
        if (service == null) {
            throw new IllegalArgumentException("Service not registered: " + type.getName());
        }
        return (T) service;
    }

    /**
     * Gets a registered service if available.
     *
     * @param type The service interface/class
     * @return Optional containing the service, or empty if not registered
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> tryGetService(Class<T> type) {
        return Optional.ofNullable((T) services.get(type));
    }

    /**
     * Checks if a service is registered.
     *
     * @param type The service interface/class
     * @return true if the service is registered
     */
    public boolean hasService(Class<?> type) {
        return services.containsKey(type);
    }

    // === Package-private methods for framework use ===

    /**
     * Sets this context as the current instance.
     * Called by KremaApplication during startup.
     */
    void makeCurrent() {
        instance = this;
    }

    /**
     * Clears the current context.
     * Called by KremaApplication during shutdown.
     */
    static void clear() {
        instance = null;
    }

    /**
     * Builder for KremaContext.
     */
    public static final class Builder {
        private WindowManager windowManager;
        private EventEmitter eventEmitter;
        private CommandRegistry commandRegistry;
        private String appName = "Krema App";
        private String appVersion = "0.0.0";
        private String appIdentifier;
        private Path appDataDir;
        private Path appConfigDir;
        private final Map<String, Object> config = new ConcurrentHashMap<>();
        private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

        private Builder() {}

        public Builder windowManager(WindowManager windowManager) {
            this.windowManager = windowManager;
            return this;
        }

        public Builder eventEmitter(EventEmitter eventEmitter) {
            this.eventEmitter = eventEmitter;
            return this;
        }

        public Builder commandRegistry(CommandRegistry commandRegistry) {
            this.commandRegistry = commandRegistry;
            return this;
        }

        public Builder appName(String appName) {
            this.appName = appName;
            return this;
        }

        public Builder appVersion(String appVersion) {
            this.appVersion = appVersion;
            return this;
        }

        public Builder appIdentifier(String appIdentifier) {
            this.appIdentifier = appIdentifier;
            return this;
        }

        public Builder appDataDir(Path appDataDir) {
            this.appDataDir = appDataDir;
            return this;
        }

        public Builder appConfigDir(Path appConfigDir) {
            this.appConfigDir = appConfigDir;
            return this;
        }

        public Builder config(String key, Object value) {
            this.config.put(key, value);
            return this;
        }

        public Builder config(Map<String, Object> config) {
            this.config.putAll(config);
            return this;
        }

        public <T> Builder service(Class<T> type, T instance) {
            this.services.put(type, instance);
            return this;
        }

        public KremaContext build() {
            // Use defaults for any unset services
            if (windowManager == null) {
                windowManager = WindowManager.getInstance();
            }
            if (commandRegistry == null) {
                commandRegistry = new CommandRegistry();
            }
            return new KremaContext(this);
        }

        /**
         * Builds the context and sets it as the current instance.
         */
        public KremaContext buildAndMakeCurrent() {
            KremaContext ctx = build();
            ctx.makeCurrent();
            return ctx;
        }
    }
}
