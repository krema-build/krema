package build.krema.core.main;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import build.krema.core.error.ErrorInfo;
import build.krema.core.event.EventEmitter;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.splash.SplashScreen;
import build.krema.core.splash.SplashScreenOptions;
import build.krema.core.window.WindowOptions.TitleBarStyle;

/**
 * Builder for Krema applications.
 * Provides a fluent API for configuring desktop applications with system webviews.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * KremaBuilder.create()
 *     .title("My App")
 *     .size(1200, 800)
 *     .devUrl("http://localhost:5173")
 *     .commands(new MyCommands())
 *     .run();
 * }</pre>
 */
public final class KremaBuilder {

    // Window configuration
    String title = "Krema App";
    String appIdentifier;
    int width = 1024;
    int height = 768;
    int minWidth = 0;
    int minHeight = 0;

    // Content configuration
    String devUrl;
    String prodAssets;
    String html;

    // Window appearance
    boolean debug = false;
    TitleBarStyle titleBarStyle = TitleBarStyle.DEFAULT;
    Integer trafficLightX;
    Integer trafficLightY;
    boolean titlebarAppearsTransparent = false;

    // Commands and plugins
    boolean registerBuiltinApis = true;
    final List<Object> commandObjects = new ArrayList<>();
    final List<KremaPlugin> explicitPlugins = new ArrayList<>();
    boolean loadBuiltinPlugins = true;
    Path pluginDirectory;

    // Application metadata
    String appVersion = "0.0.0";
    Map<String, Object> updaterConfig = Collections.emptyMap();
    String environmentName;
    Map<String, String> environmentVars = Collections.emptyMap();

    // Callbacks
    Consumer<EventEmitter> eventSetup;
    Consumer<ErrorInfo> errorHandler;
    SplashScreenOptions splashOptions;
    Consumer<SplashScreen> splashSetup;

    public KremaBuilder() {}

    /**
     * Creates a new Krema application builder.
     */
    public static KremaBuilder create() {
        return new KremaBuilder();
    }

    /**
     * Sets the window title.
     */
    public KremaBuilder title(String title) {
        this.title = title;
        return this;
    }

    /**
     * Sets the application identifier (used for app directories).
     * If not set, defaults to a sanitized version of the title.
     */
    public KremaBuilder identifier(String identifier) {
        this.appIdentifier = identifier;
        return this;
    }

    /**
     * Disables automatic registration of built-in APIs.
     */
    public KremaBuilder noBuiltinApis() {
        this.registerBuiltinApis = false;
        return this;
    }

    /**
     * Sets the window size.
     */
    public KremaBuilder size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Sets the minimum window size.
     */
    public KremaBuilder minSize(int minWidth, int minHeight) {
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        return this;
    }

    /**
     * Sets the development server URL.
     */
    public KremaBuilder devUrl(String url) {
        this.devUrl = url;
        return this;
    }

    /**
     * Sets the production assets path (classpath resource).
     */
    public KremaBuilder prodAssets(String path) {
        this.prodAssets = path;
        return this;
    }

    /**
     * Sets the HTML content directly.
     */
    public KremaBuilder html(String html) {
        this.html = html;
        return this;
    }

    /**
     * Enables developer tools in the webview.
     */
    public KremaBuilder debug(boolean debug) {
        this.debug = debug;
        return this;
    }

    /**
     * Enables developer tools in the webview.
     */
    public KremaBuilder debug() {
        return debug(true);
    }

    /**
     * Sets the title bar style for frameless/custom title bar windows.
     */
    public KremaBuilder titleBarStyle(TitleBarStyle style) {
        this.titleBarStyle = style;
        return this;
    }

    /**
     * Configures a hidden inset title bar (macOS native frameless look).
     */
    public KremaBuilder hiddenInset() {
        this.titleBarStyle = TitleBarStyle.HIDDEN_INSET;
        this.titlebarAppearsTransparent = true;
        return this;
    }

    /**
     * Sets the traffic light (close/minimize/maximize buttons) position.
     * Only applicable on macOS with hidden/hiddenInset title bar style.
     */
    public KremaBuilder trafficLightPosition(int x, int y) {
        this.trafficLightX = x;
        this.trafficLightY = y;
        return this;
    }

    /**
     * Makes the title bar appear transparent.
     */
    public KremaBuilder titlebarAppearsTransparent(boolean transparent) {
        this.titlebarAppearsTransparent = transparent;
        return this;
    }

    /**
     * Registers command objects containing @KremaCommand methods.
     */
    public KremaBuilder commands(Object... commandObjects) {
        for (Object obj : commandObjects) {
            this.commandObjects.add(obj);
        }
        return this;
    }

    /**
     * Registers plugins to be loaded when the application starts.
     */
    public KremaBuilder plugin(KremaPlugin... plugins) {
        for (KremaPlugin p : plugins) {
            this.explicitPlugins.add(p);
        }
        return this;
    }

    /**
     * Sets a directory to scan for plugin JAR files.
     */
    public KremaBuilder pluginDirectory(String path) {
        this.pluginDirectory = Path.of(path);
        return this;
    }

    /**
     * Disables automatic loading of built-in plugins via ServiceLoader.
     */
    public KremaBuilder noBuiltinPlugins() {
        this.loadBuiltinPlugins = false;
        return this;
    }

    /**
     * Sets the application version.
     */
    public KremaBuilder version(String version) {
        this.appVersion = version;
        return this;
    }

    /**
     * Sets the updater configuration map.
     */
    public KremaBuilder updaterConfig(Map<String, Object> config) {
        this.updaterConfig = config != null ? config : Collections.emptyMap();
        return this;
    }

    /**
     * Sets the environment profile name and custom variables.
     */
    public KremaBuilder environment(String name, Map<String, String> vars) {
        this.environmentName = name;
        this.environmentVars = vars != null ? vars : Collections.emptyMap();
        return this;
    }

    /**
     * Configures the event emitter for backend to frontend events.
     */
    public KremaBuilder events(Consumer<EventEmitter> setup) {
        this.eventSetup = setup;
        return this;
    }

    /**
     * Registers an error handler that is called for unhandled Java and WebView errors.
     */
    public KremaBuilder onError(Consumer<ErrorInfo> handler) {
        this.errorHandler = handler;
        return this;
    }

    /**
     * Enables the splash screen with default options.
     */
    public KremaBuilder splash() {
        this.splashOptions = SplashScreenOptions.builder().build();
        return this;
    }

    /**
     * Enables the splash screen with the specified options.
     */
    public KremaBuilder splash(SplashScreenOptions options) {
        this.splashOptions = options;
        return this;
    }

    /**
     * Enables splash screen and provides access to it during startup.
     */
    public KremaBuilder splash(SplashScreenOptions options, Consumer<SplashScreen> setup) {
        this.splashOptions = options;
        this.splashSetup = setup;
        return this;
    }

    /**
     * Builds and runs the application.
     * This method blocks until the window is closed.
     */
    public void run() {
        new KremaApplication(this).run();
    }
}
