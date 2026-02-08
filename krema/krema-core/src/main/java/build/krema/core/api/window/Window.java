package build.krema.core.api.window;

import java.util.HashMap;
import java.util.Map;

import build.krema.core.KremaCommand;
import build.krema.core.window.WindowEngine;
import build.krema.core.window.WindowEngineFactory;
import build.krema.core.window.WindowManager;
import build.krema.core.window.WindowOptions;
import build.krema.core.window.WindowState;

/**
 * Window management commands using platform-specific implementations.
 * Provides control over window state, size, position, and properties.
 */
public class Window {

    private final WindowEngine engine;

    public Window() {
        this.engine = WindowEngineFactory.get();
    }

    // === State Queries ===

    @KremaCommand("window:getState")
    public Map<String, Object> getState() {
        WindowState state = engine.getState();
        return Map.of(
            "x", state.x(),
            "y", state.y(),
            "width", state.width(),
            "height", state.height(),
            "minimized", state.minimized(),
            "maximized", state.maximized(),
            "fullscreen", state.fullscreen(),
            "focused", state.focused(),
            "visible", state.visible()
        );
    }

    @KremaCommand("window:getPosition")
    public Map<String, Integer> getPosition() {
        int[] pos = engine.getPosition();
        return Map.of("x", pos[0], "y", pos[1]);
    }

    @KremaCommand("window:getSize")
    public Map<String, Integer> getSize() {
        int[] size = engine.getSize();
        return Map.of("width", size[0], "height", size[1]);
    }

    @KremaCommand("window:isMinimized")
    public boolean isMinimized() {
        return engine.isMinimized();
    }

    @KremaCommand("window:isMaximized")
    public boolean isMaximized() {
        return engine.isMaximized();
    }

    @KremaCommand("window:isFullscreen")
    public boolean isFullscreen() {
        return engine.isFullscreen();
    }

    @KremaCommand("window:isFocused")
    public boolean isFocused() {
        return engine.isFocused();
    }

    @KremaCommand("window:isVisible")
    public boolean isVisible() {
        return engine.isVisible();
    }

    // === State Modifications ===

    @KremaCommand("window:minimize")
    public void minimize() {
        engine.minimize();
    }

    @KremaCommand("window:maximize")
    public void maximize() {
        engine.maximize();
    }

    @KremaCommand("window:restore")
    public void restore() {
        engine.restore();
    }

    @KremaCommand("window:toggleFullscreen")
    public void toggleFullscreen() {
        engine.toggleFullscreen();
    }

    @KremaCommand("window:setFullscreen")
    public void setFullscreen(Map<String, Object> options) {
        boolean fullscreen = options.containsKey("fullscreen")
            ? (Boolean) options.get("fullscreen")
            : true;
        engine.setFullscreen(fullscreen);
    }

    @KremaCommand("window:center")
    public void center() {
        engine.center();
    }

    @KremaCommand("window:focus")
    public void focus() {
        engine.focus();
    }

    @KremaCommand("window:show")
    public void show() {
        engine.show();
    }

    @KremaCommand("window:hide")
    public void hide() {
        engine.hide();
    }

    // === Size and Position ===

    @KremaCommand("window:setPosition")
    public void setPosition(Map<String, Object> options) {
        int x = ((Number) options.get("x")).intValue();
        int y = ((Number) options.get("y")).intValue();
        engine.setPosition(x, y);
    }

    @KremaCommand("window:setSize")
    public void setSize(Map<String, Object> options) {
        int width = ((Number) options.get("width")).intValue();
        int height = ((Number) options.get("height")).intValue();
        engine.setSize(width, height);
    }

    @KremaCommand("window:setBounds")
    public void setBounds(Map<String, Object> options) {
        int x = ((Number) options.get("x")).intValue();
        int y = ((Number) options.get("y")).intValue();
        int width = ((Number) options.get("width")).intValue();
        int height = ((Number) options.get("height")).intValue();
        engine.setBounds(x, y, width, height);
    }

    @KremaCommand("window:setMinSize")
    public void setMinSize(Map<String, Object> options) {
        int width = ((Number) options.get("width")).intValue();
        int height = ((Number) options.get("height")).intValue();
        engine.setMinSize(width, height);
    }

    @KremaCommand("window:setMaxSize")
    public void setMaxSize(Map<String, Object> options) {
        int width = ((Number) options.get("width")).intValue();
        int height = ((Number) options.get("height")).intValue();
        engine.setMaxSize(width, height);
    }

    // === Window Properties ===

    @KremaCommand("window:setTitle")
    public void setTitle(Map<String, Object> options) {
        String title = (String) options.get("title");
        engine.setTitle(title);
    }

    @KremaCommand("window:getTitle")
    public String getTitle() {
        return engine.getTitle();
    }

    @KremaCommand("window:setResizable")
    public void setResizable(Map<String, Object> options) {
        boolean resizable = options.containsKey("resizable")
            ? (Boolean) options.get("resizable")
            : true;
        engine.setResizable(resizable);
    }

    @KremaCommand("window:setAlwaysOnTop")
    public void setAlwaysOnTop(Map<String, Object> options) {
        boolean alwaysOnTop = options.containsKey("alwaysOnTop")
            ? (Boolean) options.get("alwaysOnTop")
            : true;
        engine.setAlwaysOnTop(alwaysOnTop);
    }

    @KremaCommand("window:setOpacity")
    public void setOpacity(Map<String, Object> options) {
        double opacity = ((Number) options.get("opacity")).doubleValue();
        engine.setOpacity(opacity);
    }

    @KremaCommand("window:getOpacity")
    public double getOpacity() {
        return engine.getOpacity();
    }

    // === Frameless Window Support ===

    /**
     * Sets the title bar style for frameless/custom title bar windows.
     * Options: "default", "hidden", "hiddenInset"
     */
    @KremaCommand("window:setTitleBarStyle")
    public void setTitleBarStyle(Map<String, Object> options) {
        String style = (String) options.getOrDefault("style", "default");
        engine.setTitleBarStyle(style);
    }

    /**
     * Sets the traffic light buttons (close/minimize/maximize) position.
     * Only applicable on macOS with hidden/hiddenInset title bar style.
     */
    @KremaCommand("window:setTrafficLightPosition")
    public void setTrafficLightPosition(Map<String, Object> options) {
        int x = ((Number) options.get("x")).intValue();
        int y = ((Number) options.get("y")).intValue();
        engine.setTrafficLightPosition(x, y);
    }

    /**
     * Sets whether the title bar appears transparent.
     */
    @KremaCommand("window:setTitlebarTransparent")
    public void setTitlebarTransparent(Map<String, Object> options) {
        boolean transparent = options.containsKey("transparent")
            ? (Boolean) options.get("transparent")
            : true;
        engine.setTitlebarAppearsTransparent(transparent);
    }

    /**
     * Enables content to extend into the title bar area.
     */
    @KremaCommand("window:setFullSizeContentView")
    public void setFullSizeContentView(Map<String, Object> options) {
        boolean extend = options.containsKey("extend")
            ? (Boolean) options.get("extend")
            : true;
        engine.setFullSizeContentView(extend);
    }

    // === Multi-Window Support ===

    /**
     * Creates a new window.
     * Returns the window label/ID.
     */
    @KremaCommand("window:create")
    public Map<String, Object> createWindow(Map<String, Object> options) {
        WindowOptions.Builder builder = WindowOptions.builder();

        if (options.containsKey("title")) {
            builder.title((String) options.get("title"));
        }
        if (options.containsKey("width") && options.containsKey("height")) {
            builder.size(
                ((Number) options.get("width")).intValue(),
                ((Number) options.get("height")).intValue()
            );
        }
        if (options.containsKey("minWidth") && options.containsKey("minHeight")) {
            builder.minSize(
                ((Number) options.get("minWidth")).intValue(),
                ((Number) options.get("minHeight")).intValue()
            );
        }
        if (options.containsKey("resizable")) {
            builder.resizable((Boolean) options.get("resizable"));
        }
        if (options.containsKey("center")) {
            builder.center((Boolean) options.get("center"));
        }
        if (options.containsKey("x") && options.containsKey("y")) {
            builder.position(
                ((Number) options.get("x")).intValue(),
                ((Number) options.get("y")).intValue()
            );
        }

        WindowManager manager = WindowManager.getInstance();
        String label = options.containsKey("label")
            ? (String) options.get("label")
            : null;

        WindowManager.ManagedWindow window;
        if (label != null) {
            window = manager.createWindow(label, builder.build());
        } else {
            window = manager.createWindow(builder.build());
        }

        // Navigate to URL if provided
        if (options.containsKey("url")) {
            window.navigate((String) options.get("url"));
        } else if (options.containsKey("html")) {
            window.setHtml((String) options.get("html"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("label", window.getLabel());
        return result;
    }

    /**
     * Creates a child window with a parent relationship.
     */
    @KremaCommand("window:createChild")
    public Map<String, Object> createChildWindow(Map<String, Object> options) {
        String parentLabel = (String) options.getOrDefault("parent", "main");

        WindowOptions.Builder builder = WindowOptions.builder();
        if (options.containsKey("title")) {
            builder.title((String) options.get("title"));
        }
        if (options.containsKey("width") && options.containsKey("height")) {
            builder.size(
                ((Number) options.get("width")).intValue(),
                ((Number) options.get("height")).intValue()
            );
        }

        WindowManager manager = WindowManager.getInstance();
        String label = options.containsKey("label")
            ? (String) options.get("label")
            : null;

        WindowManager.ManagedWindow window;
        if (label != null) {
            window = manager.createChildWindow(label, builder.build(), parentLabel);
        } else {
            window = manager.createChildWindow(
                "child-" + System.currentTimeMillis(),
                builder.build(),
                parentLabel
            );
        }

        if (options.containsKey("url")) {
            window.navigate((String) options.get("url"));
        } else if (options.containsKey("html")) {
            window.setHtml((String) options.get("html"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("label", window.getLabel());
        result.put("parent", parentLabel);
        return result;
    }

    /**
     * Creates a modal window that blocks its parent.
     */
    @KremaCommand("window:showModal")
    public Map<String, Object> showModal(Map<String, Object> options) {
        String parentLabel = (String) options.getOrDefault("parent", "main");

        WindowOptions.Builder builder = WindowOptions.builder();
        if (options.containsKey("title")) {
            builder.title((String) options.get("title"));
        }
        if (options.containsKey("width") && options.containsKey("height")) {
            builder.size(
                ((Number) options.get("width")).intValue(),
                ((Number) options.get("height")).intValue()
            );
        } else {
            builder.size(400, 300); // Default modal size
        }

        WindowManager manager = WindowManager.getInstance();
        String label = options.containsKey("label")
            ? (String) options.get("label")
            : "modal-" + System.currentTimeMillis();

        WindowManager.ManagedWindow window = manager.createModal(label, builder.build(), parentLabel);

        if (options.containsKey("url")) {
            window.navigate((String) options.get("url"));
        } else if (options.containsKey("html")) {
            window.setHtml((String) options.get("html"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("label", window.getLabel());
        result.put("parent", parentLabel);
        result.put("modal", true);
        return result;
    }

    /**
     * Closes a window by label.
     */
    @KremaCommand("window:close")
    public void closeWindow(Map<String, Object> options) {
        String label = (String) options.get("label");
        if (label != null) {
            WindowManager.getInstance().closeWindow(label);
        }
    }

    /**
     * Gets information about a specific window.
     */
    @KremaCommand("window:getWindow")
    public Map<String, Object> getWindowInfo(Map<String, Object> options) {
        String label = (String) options.get("label");
        WindowManager manager = WindowManager.getInstance();

        return manager.getWindow(label)
            .map(window -> {
                Map<String, Object> info = new HashMap<>();
                info.put("label", window.getLabel());
                info.put("parent", window.getParentLabel());
                info.put("modal", window.isModal());
                return info;
            })
            .orElse(Map.of("error", "Window not found"));
    }

    /**
     * Lists all open windows.
     */
    @KremaCommand("window:list")
    public Map<String, Object> listWindows() {
        WindowManager manager = WindowManager.getInstance();
        Map<String, Object> result = new HashMap<>();
        result.put("windows", manager.getWindowLabels());
        result.put("count", manager.windowCount());
        return result;
    }

    /**
     * Sends a message to a specific window.
     */
    @KremaCommand("window:sendTo")
    public void sendToWindow(Map<String, Object> options) {
        String label = (String) options.get("label");
        String event = (String) options.get("event");
        Object payload = options.get("payload");

        if (label != null && event != null) {
            WindowManager.getInstance().sendToWindow(label, event, payload);
        }
    }

    /**
     * Broadcasts a message to all windows.
     */
    @KremaCommand("window:broadcast")
    public void broadcastToWindows(Map<String, Object> options) {
        String event = (String) options.get("event");
        Object payload = options.get("payload");

        if (event != null) {
            WindowManager.getInstance().broadcast(event, payload);
        }
    }
}
