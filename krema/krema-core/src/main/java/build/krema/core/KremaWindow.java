package build.krema.core;

import java.util.function.BiConsumer;

import build.krema.core.webview.WebViewEngine;
import build.krema.core.webview.WebViewEngineFactory;
import build.krema.core.webview.WebViewEngine.SizeHint;
import build.krema.core.window.WindowOptions;

/**
 * High-level window abstraction over native webview.
 * Provides a fluent API for creating and configuring webview windows.
 *
 * This class wraps a platform-specific WebViewEngine implementation.
 */
public class KremaWindow implements AutoCloseable {

    private final WebViewEngine engine;

    /**
     * Creates a new webview window with the specified options.
     */
    public KremaWindow(WindowOptions options) {
        this.engine = WebViewEngineFactory.create(options.debug());
        applyOptions(options);
    }

    /**
     * Creates a new webview window.
     *
     * @param debug Enable developer tools
     */
    public KremaWindow(boolean debug) {
        this.engine = WebViewEngineFactory.create(debug);
    }

    /**
     * Creates a new webview window with developer tools disabled.
     */
    public KremaWindow() {
        this(false);
    }

    /**
     * Creates a window wrapping an existing engine (for advanced use cases).
     */
    KremaWindow(WebViewEngine engine) {
        this.engine = engine;
    }

    private void applyOptions(WindowOptions options) {
        engine.setTitle(options.title());
        engine.setSize(options.width(), options.height(), SizeHint.NONE);

        if (options.minWidth() > 0 && options.minHeight() > 0) {
            engine.setSize(options.minWidth(), options.minHeight(), SizeHint.MIN);
        }

        if (options.maxWidth() < Integer.MAX_VALUE && options.maxHeight() < Integer.MAX_VALUE) {
            engine.setSize(options.maxWidth(), options.maxHeight(), SizeHint.MAX);
        }

        if (!options.resizable()) {
            engine.setSize(options.width(), options.height(), SizeHint.FIXED);
        }
    }

    /**
     * Sets the window title.
     */
    public KremaWindow title(String title) {
        engine.setTitle(title);
        return this;
    }

    /**
     * Sets the window size.
     */
    public KremaWindow size(int width, int height) {
        engine.setSize(width, height, SizeHint.NONE);
        return this;
    }

    /**
     * Sets the window size with hints.
     */
    public KremaWindow size(int width, int height, SizeHint hint) {
        engine.setSize(width, height, hint);
        return this;
    }

    /**
     * Sets minimum window size.
     */
    public KremaWindow minSize(int width, int height) {
        engine.setSize(width, height, SizeHint.MIN);
        return this;
    }

    /**
     * Sets maximum window size.
     */
    public KremaWindow maxSize(int width, int height) {
        engine.setSize(width, height, SizeHint.MAX);
        return this;
    }

    /**
     * Sets fixed window size (not resizable).
     */
    public KremaWindow fixedSize(int width, int height) {
        engine.setSize(width, height, SizeHint.FIXED);
        return this;
    }

    /**
     * Navigates to the specified URL.
     */
    public KremaWindow navigate(String url) {
        engine.navigate(url);
        return this;
    }

    /**
     * Sets the HTML content directly.
     */
    public KremaWindow html(String html) {
        engine.setHtml(html);
        return this;
    }

    /**
     * Injects JavaScript to be executed when the page loads.
     */
    public KremaWindow init(String js) {
        engine.init(js);
        return this;
    }

    /**
     * Evaluates JavaScript in the webview.
     */
    public KremaWindow eval(String js) {
        engine.eval(js);
        return this;
    }

    /**
     * Binds a JavaScript function to a Java callback.
     *
     * @param name The JavaScript function name
     * @param handler Callback receiving (seq, requestJson) - must call returnResult with seq
     */
    public KremaWindow bind(String name, BiConsumer<String, String> handler) {
        engine.bind(name, handler::accept);
        return this;
    }

    /**
     * Returns a result to a pending JavaScript Promise.
     */
    public void returnResult(String seq, boolean success, String result) {
        engine.returnResult(seq, success, result);
    }

    /**
     * Runs the webview event loop. Blocks until the window is closed.
     */
    public void run() {
        engine.run();
    }

    /**
     * Terminates the webview event loop.
     */
    public void terminate() {
        engine.terminate();
    }

    /**
     * Returns true if the event loop is running.
     */
    public boolean isRunning() {
        return engine.isRunning();
    }

    /**
     * Gets the underlying WebViewEngine for advanced operations.
     */
    public WebViewEngine getEngine() {
        return engine;
    }

    /**
     * @deprecated Use getEngine() instead
     */
    @Deprecated
    public Object getHandle() {
        return engine;
    }

    @Override
    public void close() {
        engine.close();
    }
}
