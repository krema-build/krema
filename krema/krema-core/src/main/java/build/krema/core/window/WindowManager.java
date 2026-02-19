package build.krema.core.window;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import build.krema.core.util.Json;
import build.krema.core.webview.WebViewEngine;
import build.krema.core.webview.WebViewEngineFactory;

/**
 * Manages multiple webview windows.
 * Provides lifecycle management, access to windows by label, and modal support.
 */
public class WindowManager implements AutoCloseable {

    private static volatile WindowManager instance;

    private final Map<String, ManagedWindow> windows = new ConcurrentHashMap<>();
    private final AtomicLong windowCounter = new AtomicLong(0);
    private String mainWindowLabel;
    private Consumer<String> onWindowClosed;

    /**
     * Gets the global WindowManager instance.
     * Uses double-checked locking to avoid synchronization on the read path.
     */
    public static WindowManager getInstance() {
        WindowManager local = instance;
        if (local == null) {
            synchronized (WindowManager.class) {
                local = instance;
                if (local == null) {
                    local = new WindowManager();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Sets the global WindowManager instance (for custom initialization).
     */
    public static synchronized void setInstance(WindowManager manager) {
        instance = manager;
    }

    /**
     * Creates a new window with the given options.
     *
     * @param label unique identifier for the window
     * @param options window configuration
     * @return the created window
     */
    public ManagedWindow createWindow(String label, WindowOptions options) {
        if (windows.containsKey(label)) {
            throw new IllegalArgumentException("Window with label already exists: " + label);
        }

        WebViewEngine engine = WebViewEngineFactory.create(options.debug());
        configureWindow(engine, options);

        ManagedWindow window = new ManagedWindow(label, engine, options);
        windows.put(label, window);

        if (mainWindowLabel == null) {
            mainWindowLabel = label;
        }

        return window;
    }

    /**
     * Creates a new window with a pre-existing engine (for testing).
     */
    ManagedWindow createWindowWithEngine(String label, WebViewEngine engine, WindowOptions options) {
        if (windows.containsKey(label)) {
            throw new IllegalArgumentException("Window with label already exists: " + label);
        }
        configureWindow(engine, options);
        ManagedWindow window = new ManagedWindow(label, engine, options);
        windows.put(label, window);
        if (mainWindowLabel == null) {
            mainWindowLabel = label;
        }
        return window;
    }

    /**
     * Creates a new window with auto-generated label.
     */
    public ManagedWindow createWindow(WindowOptions options) {
        String label = "window-" + windowCounter.incrementAndGet();
        return createWindow(label, options);
    }

    private void configureWindow(WebViewEngine engine, WindowOptions options) {
        engine.setTitle(options.title());
        engine.setSize(options.width(), options.height(), WebViewEngine.SizeHint.NONE);

        if (options.minWidth() > 0 && options.minHeight() > 0) {
            engine.setSize(options.minWidth(), options.minHeight(), WebViewEngine.SizeHint.MIN);
        }

        if (options.maxWidth() < Integer.MAX_VALUE && options.maxHeight() < Integer.MAX_VALUE) {
            engine.setSize(options.maxWidth(), options.maxHeight(), WebViewEngine.SizeHint.MAX);
        }

        if (!options.resizable()) {
            engine.setSize(options.width(), options.height(), WebViewEngine.SizeHint.FIXED);
        }
    }

    /**
     * Gets a window by its label.
     */
    public Optional<ManagedWindow> getWindow(String label) {
        return Optional.ofNullable(windows.get(label));
    }

    /**
     * Gets the main (first created) window.
     */
    public Optional<ManagedWindow> getMainWindow() {
        return mainWindowLabel != null ? getWindow(mainWindowLabel) : Optional.empty();
    }

    /**
     * Creates a child window with parent relationship.
     *
     * @param label unique identifier for the window
     * @param options window configuration
     * @param parentLabel label of the parent window
     * @return the created window
     */
    public ManagedWindow createChildWindow(String label, WindowOptions options, String parentLabel) {
        ManagedWindow window = createWindow(label, options);
        window.setParentLabel(parentLabel);
        return window;
    }

    /**
     * Creates a modal window.
     * Modal windows block interaction with their parent until closed.
     *
     * @param label unique identifier for the window
     * @param options window configuration
     * @param parentLabel label of the parent window
     * @return the created modal window
     */
    public ManagedWindow createModal(String label, WindowOptions options, String parentLabel) {
        ManagedWindow window = createChildWindow(label, options, parentLabel);
        window.setModal(true);
        return window;
    }

    /**
     * Closes and removes a window.
     */
    public void closeWindow(String label) {
        ManagedWindow window = windows.remove(label);
        if (window != null) {
            window.close();
            if (onWindowClosed != null) {
                onWindowClosed.accept(label);
            }
        }
    }

    /**
     * Returns the number of open windows.
     */
    public int windowCount() {
        return windows.size();
    }

    /**
     * Returns all window labels.
     */
    public String[] getWindowLabels() {
        return windows.keySet().toArray(new String[0]);
    }

    /**
     * Sets a callback for when windows are closed.
     */
    public void setOnWindowClosed(Consumer<String> callback) {
        this.onWindowClosed = callback;
    }

    /**
     * Sends a message/event to a specific window.
     */
    public void sendToWindow(String label, String event, Object payload) {
        getWindow(label).ifPresent(window -> {
            window.eval(buildEventJs(event, payload));
        });
    }

    /**
     * Broadcasts a message/event to all windows.
     */
    public void broadcast(String event, Object payload) {
        String js = buildEventJs(event, payload);
        windows.values().forEach(window -> window.eval(js));
    }

    private String buildEventJs(String event, Object payload) {
        try {
            String json = Json.mapper().writeValueAsString(payload);
            return new StringBuilder(64 + json.length())
                .append("window.__krema_event && window.__krema_event('")
                .append(event)
                .append("', ")
                .append(json)
                .append(");")
                .toString();
        } catch (JsonProcessingException e) {
            System.err.println("[Krema] Failed to serialize event payload: " + e.getMessage());
            return "";
        }
    }

    @Override
    public void close() {
        windows.values().forEach(ManagedWindow::close);
        windows.clear();
    }

    /**
     * A managed window with its associated engine and options.
     */
    public static class ManagedWindow implements AutoCloseable {
        private final String label;
        private final WebViewEngine engine;
        private final WindowOptions options;
        private String parentLabel;
        private boolean modal;

        ManagedWindow(String label, WebViewEngine engine, WindowOptions options) {
            this.label = label;
            this.engine = engine;
            this.options = options;
        }

        public String getLabel() {
            return label;
        }

        public WebViewEngine getEngine() {
            return engine;
        }

        public WindowOptions getOptions() {
            return options;
        }

        public String getParentLabel() {
            return parentLabel;
        }

        void setParentLabel(String parentLabel) {
            this.parentLabel = parentLabel;
        }

        public boolean isModal() {
            return modal;
        }

        void setModal(boolean modal) {
            this.modal = modal;
        }

        public void navigate(String url) {
            engine.navigate(url);
        }

        public void setHtml(String html) {
            engine.setHtml(html);
        }

        public void setTitle(String title) {
            engine.setTitle(title);
        }

        public void eval(String js) {
            engine.eval(js);
        }

        public void run() {
            engine.run();
        }

        public void terminate() {
            engine.terminate();
        }

        @Override
        public void close() {
            engine.close();
        }
    }
}
