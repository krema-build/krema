package build.krema.core.webview;

/**
 * Platform-agnostic interface for webview engines.
 * Implementations provide platform-specific webview functionality.
 */
public interface WebViewEngine extends AutoCloseable {

    /**
     * Sets the window title.
     */
    void setTitle(String title);

    /**
     * Sets the window size.
     */
    void setSize(int width, int height, SizeHint hint);

    /**
     * Navigates to the specified URL.
     */
    void navigate(String url);

    /**
     * Sets the HTML content directly.
     */
    void setHtml(String html);

    /**
     * Injects JavaScript to be executed when any page loads.
     */
    void init(String js);

    /**
     * Evaluates JavaScript in the current page context.
     */
    void eval(String js);

    /**
     * Binds a JavaScript function to a native callback.
     */
    void bind(String name, BindCallback callback);

    /**
     * Returns a result to a pending JavaScript Promise.
     */
    void returnResult(String seq, boolean success, String result);

    /**
     * Runs the event loop. Blocks until the window is closed.
     */
    void run();

    /**
     * Terminates the event loop.
     */
    void terminate();

    /**
     * Returns true if the event loop is running.
     */
    boolean isRunning();

    /**
     * Closes and releases all resources.
     */
    @Override
    void close();

    /**
     * Callback interface for bound JavaScript functions.
     */
    @FunctionalInterface
    interface BindCallback {
        void invoke(String seq, String request);
    }

    /**
     * Size hints for window sizing.
     */
    enum SizeHint {
        NONE(0),
        MIN(1),
        MAX(2),
        FIXED(3);

        private final int value;

        SizeHint(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
