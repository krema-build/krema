package build.krema.core.ports;

import build.krema.core.window.WindowState;

/**
 * Port interface for window management operations.
 * Implementations provide native window control for each platform.
 *
 * <p>This is part of the Hexagonal Architecture (Ports &amp; Adapters) pattern.
 * The port defines the interface that adapters must implement.</p>
 *
 * @see build.krema.core.window.WindowEngine
 */
public interface WindowPort {

    // === State Queries ===

    /**
     * Gets the current window state including position, size, and flags.
     */
    WindowState getState();

    /**
     * Gets the current window position (x, y).
     */
    int[] getPosition();

    /**
     * Gets the current window size (width, height).
     */
    int[] getSize();

    /**
     * Checks if the window is minimized.
     */
    boolean isMinimized();

    /**
     * Checks if the window is maximized.
     */
    boolean isMaximized();

    /**
     * Checks if the window is in fullscreen mode.
     */
    boolean isFullscreen();

    /**
     * Checks if the window is focused (key window).
     */
    boolean isFocused();

    /**
     * Checks if the window is visible.
     */
    boolean isVisible();

    // === State Modifications ===

    /**
     * Minimizes the window to the dock/taskbar.
     */
    void minimize();

    /**
     * Maximizes the window to fill the screen.
     */
    void maximize();

    /**
     * Restores the window from minimized or maximized state.
     */
    void restore();

    /**
     * Toggles fullscreen mode.
     */
    void toggleFullscreen();

    /**
     * Sets fullscreen mode.
     */
    void setFullscreen(boolean fullscreen);

    /**
     * Centers the window on the screen.
     */
    void center();

    /**
     * Brings the window to front and focuses it.
     */
    void focus();

    /**
     * Shows the window if hidden.
     */
    void show();

    /**
     * Hides the window.
     */
    void hide();

    // === Size and Position ===

    /**
     * Sets the window position.
     */
    void setPosition(int x, int y);

    /**
     * Sets the window size.
     */
    void setSize(int width, int height);

    /**
     * Sets both position and size.
     */
    void setBounds(int x, int y, int width, int height);

    /**
     * Sets the minimum window size.
     */
    void setMinSize(int width, int height);

    /**
     * Sets the maximum window size.
     */
    void setMaxSize(int width, int height);

    // === Window Properties ===

    /**
     * Sets the window title.
     */
    void setTitle(String title);

    /**
     * Gets the window title.
     */
    String getTitle();

    /**
     * Sets whether the window is resizable.
     */
    void setResizable(boolean resizable);

    /**
     * Sets whether the window is always on top.
     */
    void setAlwaysOnTop(boolean alwaysOnTop);

    /**
     * Sets the window opacity (0.0 = transparent, 1.0 = opaque).
     */
    void setOpacity(double opacity);

    /**
     * Gets the window opacity.
     */
    double getOpacity();

    // === Frameless Window Support ===

    /**
     * Sets the title bar style for frameless/custom title bar windows.
     * @param style The title bar style: "default", "hidden", or "hiddenInset"
     */
    default void setTitleBarStyle(String style) {
        // No-op by default, implemented by platforms that support it
    }

    /**
     * Sets the position of the traffic light buttons (macOS only).
     * @param x X offset from window edge
     * @param y Y offset from window edge
     */
    default void setTrafficLightPosition(int x, int y) {
        // No-op by default, macOS-specific
    }

    /**
     * Sets whether the title bar appears transparent.
     * @param transparent true for transparent title bar
     */
    default void setTitlebarAppearsTransparent(boolean transparent) {
        // No-op by default
    }

    /**
     * Enables content to extend into the title bar area (full size content view).
     * @param extend true to extend content into title bar
     */
    default void setFullSizeContentView(boolean extend) {
        // No-op by default
    }
}
