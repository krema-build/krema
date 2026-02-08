package build.krema.core;

import java.util.Map;
import java.util.function.Consumer;

import build.krema.core.event.EventEmitter;
import build.krema.core.main.KremaBuilder;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.splash.SplashScreen;
import build.krema.core.splash.SplashScreenOptions;
import build.krema.core.window.WindowOptions.TitleBarStyle;

/**
 * Main entry point for Krema applications.
 * Provides a fluent builder API for creating desktop applications with system webviews.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * Krema.app()
 *     .title("My App")
 *     .size(1200, 800)
 *     .devUrl("http://localhost:5173")
 *     .commands(new MyCommands())
 *     .run();
 * }</pre>
 *
 * <p>This class delegates to {@link KremaBuilder} for configuration and
 * {@link build.krema.core.main.KremaApplication} for execution.</p>
 *
 * @see KremaBuilder
 */
public final class Krema {

    private final KremaBuilder builder;

    private Krema() {
        this.builder = new KremaBuilder();
    }

    /**
     * Creates a new Krema application builder.
     */
    public static Krema app() {
        return new Krema();
    }

    /**
     * Sets the window title.
     */
    public Krema title(String title) {
        builder.title(title);
        return this;
    }

    /**
     * Sets the application identifier (used for app directories).
     * If not set, defaults to a sanitized version of the title.
     */
    public Krema identifier(String identifier) {
        builder.identifier(identifier);
        return this;
    }

    /**
     * Disables automatic registration of built-in APIs.
     */
    public Krema noBuiltinApis() {
        builder.noBuiltinApis();
        return this;
    }

    /**
     * Sets the window size.
     */
    public Krema size(int width, int height) {
        builder.size(width, height);
        return this;
    }

    /**
     * Sets the minimum window size.
     */
    public Krema minSize(int minWidth, int minHeight) {
        builder.minSize(minWidth, minHeight);
        return this;
    }

    /**
     * Sets the development server URL.
     */
    public Krema devUrl(String url) {
        builder.devUrl(url);
        return this;
    }

    /**
     * Sets the production assets path (classpath resource).
     */
    public Krema prodAssets(String path) {
        builder.prodAssets(path);
        return this;
    }

    /**
     * Sets the HTML content directly.
     */
    public Krema html(String html) {
        builder.html(html);
        return this;
    }

    /**
     * Enables developer tools in the webview.
     */
    public Krema debug(boolean debug) {
        builder.debug(debug);
        return this;
    }

    /**
     * Enables developer tools in the webview.
     */
    public Krema debug() {
        builder.debug();
        return this;
    }

    /**
     * Sets the title bar style for frameless/custom title bar windows.
     */
    public Krema titleBarStyle(TitleBarStyle style) {
        builder.titleBarStyle(style);
        return this;
    }

    /**
     * Configures a hidden inset title bar (macOS native frameless look).
     */
    public Krema hiddenInset() {
        builder.hiddenInset();
        return this;
    }

    /**
     * Sets the traffic light (close/minimize/maximize buttons) position.
     * Only applicable on macOS with hidden/hiddenInset title bar style.
     */
    public Krema trafficLightPosition(int x, int y) {
        builder.trafficLightPosition(x, y);
        return this;
    }

    /**
     * Makes the title bar appear transparent.
     */
    public Krema titlebarAppearsTransparent(boolean transparent) {
        builder.titlebarAppearsTransparent(transparent);
        return this;
    }

    /**
     * Registers command objects containing @KremaCommand methods.
     */
    public Krema commands(Object... commandObjects) {
        builder.commands(commandObjects);
        return this;
    }

    /**
     * Registers plugins to be loaded when the application starts.
     */
    public Krema plugin(KremaPlugin... plugins) {
        builder.plugin(plugins);
        return this;
    }

    /**
     * Sets a directory to scan for plugin JAR files.
     */
    public Krema pluginDirectory(String path) {
        builder.pluginDirectory(path);
        return this;
    }

    /**
     * Disables automatic loading of built-in plugins via ServiceLoader.
     */
    public Krema noBuiltinPlugins() {
        builder.noBuiltinPlugins();
        return this;
    }

    /**
     * Sets the application version.
     */
    public Krema version(String version) {
        builder.version(version);
        return this;
    }

    /**
     * Sets the updater configuration map.
     */
    public Krema updaterConfig(Map<String, Object> config) {
        builder.updaterConfig(config);
        return this;
    }

    /**
     * Sets the environment profile name and custom variables.
     */
    public Krema environment(String name, Map<String, String> vars) {
        builder.environment(name, vars);
        return this;
    }

    /**
     * Configures the event emitter for backend to frontend events.
     */
    public Krema events(Consumer<EventEmitter> setup) {
        builder.events(setup);
        return this;
    }

    /**
     * Enables the splash screen with default options.
     */
    public Krema splash() {
        builder.splash();
        return this;
    }

    /**
     * Enables the splash screen with the specified options.
     */
    public Krema splash(SplashScreenOptions options) {
        builder.splash(options);
        return this;
    }

    /**
     * Enables splash screen and provides access to it during startup.
     */
    public Krema splash(SplashScreenOptions options, Consumer<SplashScreen> setup) {
        builder.splash(options, setup);
        return this;
    }

    /**
     * Builds and runs the application.
     * This method blocks until the window is closed.
     */
    public void run() {
        builder.run();
    }

    /**
     * Returns the underlying builder for advanced configuration.
     * Prefer using the Krema facade methods when possible.
     *
     * @return the KremaBuilder instance
     */
    public KremaBuilder getBuilder() {
        return builder;
    }
}
