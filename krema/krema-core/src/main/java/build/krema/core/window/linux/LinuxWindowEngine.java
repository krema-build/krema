package build.krema.core.window.linux;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import build.krema.core.platform.linux.GtkBindings;
import build.krema.core.webview.WebViewCLibEngine;
import build.krema.core.window.WindowEngine;
import build.krema.core.window.WindowState;

/**
 * Linux WindowEngine implementation using GTK3 via FFM.
 * Gets the GtkWindow* from the active WebViewCLibEngine instance.
 *
 * <p>Note: {@code gtk_window_move()} is ignored on Wayland by design.
 * Window positioning works on X11 only.</p>
 */
public final class LinuxWindowEngine implements WindowEngine {

    private final Arena arena = Arena.ofAuto();

    private MemorySegment getWindow() {
        WebViewCLibEngine engine = WebViewCLibEngine.getActive();
        if (engine == null) {
            throw new IllegalStateException("No active WebViewCLibEngine");
        }
        return engine.getNativeWindow();
    }

    @Override
    public WindowState getState() {
        int[] pos = getPosition();
        int[] size = getSize();
        return new WindowState(
            pos[0], pos[1], size[0], size[1],
            isMinimized(), isMaximized(), isFullscreen(),
            isFocused(), isVisible()
        );
    }

    @Override
    public int[] getPosition() {
        try {
            MemorySegment window = getWindow();
            MemorySegment xOut = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment yOut = arena.allocate(ValueLayout.JAVA_INT);
            GtkBindings.GTK_WINDOW_GET_POSITION.invokeExact(window, xOut, yOut);
            return new int[]{
                xOut.get(ValueLayout.JAVA_INT, 0),
                yOut.get(ValueLayout.JAVA_INT, 0)
            };
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get window position", t);
        }
    }

    @Override
    public int[] getSize() {
        try {
            MemorySegment window = getWindow();
            MemorySegment wOut = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment hOut = arena.allocate(ValueLayout.JAVA_INT);
            GtkBindings.GTK_WINDOW_GET_SIZE.invokeExact(window, wOut, hOut);
            return new int[]{
                wOut.get(ValueLayout.JAVA_INT, 0),
                hOut.get(ValueLayout.JAVA_INT, 0)
            };
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get window size", t);
        }
    }

    @Override
    public boolean isMinimized() {
        try {
            MemorySegment window = getWindow();
            MemorySegment gdkWindow = (MemorySegment) GtkBindings.GTK_WIDGET_GET_WINDOW.invokeExact(window);
            if (gdkWindow.address() == 0) return false;
            int state = (int) GtkBindings.GDK_WINDOW_GET_STATE.invokeExact(gdkWindow);
            return (state & GtkBindings.GDK_WINDOW_STATE_ICONIFIED) != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to check minimized state", t);
        }
    }

    @Override
    public boolean isMaximized() {
        try {
            MemorySegment window = getWindow();
            return ((int) GtkBindings.GTK_WINDOW_IS_MAXIMIZED.invokeExact(window)) != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to check maximized state", t);
        }
    }

    @Override
    public boolean isFullscreen() {
        try {
            MemorySegment window = getWindow();
            MemorySegment gdkWindow = (MemorySegment) GtkBindings.GTK_WIDGET_GET_WINDOW.invokeExact(window);
            if (gdkWindow.address() == 0) return false;
            int state = (int) GtkBindings.GDK_WINDOW_GET_STATE.invokeExact(gdkWindow);
            return (state & GtkBindings.GDK_WINDOW_STATE_FULLSCREEN) != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to check fullscreen state", t);
        }
    }

    @Override
    public boolean isFocused() {
        try {
            MemorySegment window = getWindow();
            return ((int) GtkBindings.GTK_WINDOW_IS_ACTIVE.invokeExact(window)) != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to check focused state", t);
        }
    }

    @Override
    public boolean isVisible() {
        try {
            MemorySegment window = getWindow();
            return ((int) GtkBindings.GTK_WIDGET_IS_VISIBLE.invokeExact(window)) != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to check visible state", t);
        }
    }

    @Override
    public void minimize() {
        try {
            GtkBindings.GTK_WINDOW_ICONIFY.invokeExact(getWindow());
        } catch (Throwable t) {
            throw new RuntimeException("Failed to minimize window", t);
        }
    }

    @Override
    public void maximize() {
        try {
            GtkBindings.GTK_WINDOW_MAXIMIZE.invokeExact(getWindow());
        } catch (Throwable t) {
            throw new RuntimeException("Failed to maximize window", t);
        }
    }

    @Override
    public void restore() {
        try {
            MemorySegment window = getWindow();
            if (isMinimized()) {
                GtkBindings.GTK_WINDOW_DEICONIFY.invokeExact(window);
            }
            if (isMaximized()) {
                GtkBindings.GTK_WINDOW_UNMAXIMIZE.invokeExact(window);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to restore window", t);
        }
    }

    @Override
    public void toggleFullscreen() {
        if (isFullscreen()) {
            setFullscreen(false);
        } else {
            setFullscreen(true);
        }
    }

    @Override
    public void setFullscreen(boolean fullscreen) {
        try {
            MemorySegment window = getWindow();
            if (fullscreen) {
                GtkBindings.GTK_WINDOW_FULLSCREEN.invokeExact(window);
            } else {
                GtkBindings.GTK_WINDOW_UNFULLSCREEN.invokeExact(window);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set fullscreen", t);
        }
    }

    @Override
    public void center() {
        // GTK doesn't have a built-in center call; compute from screen size
        try {
            MemorySegment window = getWindow();
            int[] size = getSize();

            MemorySegment display = (MemorySegment) GtkBindings.GDK_DISPLAY_GET_DEFAULT.invokeExact();
            MemorySegment monitor = (MemorySegment) GtkBindings.GDK_DISPLAY_GET_PRIMARY_MONITOR.invokeExact(display);
            if (monitor.address() == 0) return;

            MemorySegment rect = arena.allocate(GtkBindings.GDK_RECTANGLE);
            GtkBindings.GDK_MONITOR_GET_WORKAREA.invokeExact(monitor, rect);

            int screenW = rect.get(ValueLayout.JAVA_INT, 8);  // width offset
            int screenH = rect.get(ValueLayout.JAVA_INT, 12); // height offset
            int screenX = rect.get(ValueLayout.JAVA_INT, 0);  // x offset
            int screenY = rect.get(ValueLayout.JAVA_INT, 4);  // y offset

            int x = screenX + (screenW - size[0]) / 2;
            int y = screenY + (screenH - size[1]) / 2;

            GtkBindings.GTK_WINDOW_MOVE.invokeExact(window, x, y);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to center window", t);
        }
    }

    @Override
    public void focus() {
        try {
            GtkBindings.GTK_WINDOW_PRESENT.invokeExact(getWindow());
        } catch (Throwable t) {
            throw new RuntimeException("Failed to focus window", t);
        }
    }

    @Override
    public void show() {
        try {
            GtkBindings.GTK_WIDGET_SHOW.invokeExact(getWindow());
        } catch (Throwable t) {
            throw new RuntimeException("Failed to show window", t);
        }
    }

    @Override
    public void hide() {
        try {
            GtkBindings.GTK_WIDGET_HIDE.invokeExact(getWindow());
        } catch (Throwable t) {
            throw new RuntimeException("Failed to hide window", t);
        }
    }

    @Override
    public void setPosition(int x, int y) {
        try {
            GtkBindings.GTK_WINDOW_MOVE.invokeExact(getWindow(), x, y);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set window position", t);
        }
    }

    @Override
    public void setSize(int width, int height) {
        try {
            GtkBindings.GTK_WINDOW_RESIZE.invokeExact(getWindow(), width, height);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set window size", t);
        }
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        setPosition(x, y);
        setSize(width, height);
    }

    @Override
    public void setMinSize(int width, int height) {
        try {
            MemorySegment geometry = arena.allocate(GtkBindings.GDK_GEOMETRY);
            geometry.set(ValueLayout.JAVA_INT, 0, width);   // min_width
            geometry.set(ValueLayout.JAVA_INT, 4, height);  // min_height
            GtkBindings.GTK_WINDOW_SET_GEOMETRY_HINTS.invokeExact(
                getWindow(), MemorySegment.NULL, geometry, GtkBindings.GDK_HINT_MIN_SIZE
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set minimum size", t);
        }
    }

    @Override
    public void setMaxSize(int width, int height) {
        try {
            MemorySegment geometry = arena.allocate(GtkBindings.GDK_GEOMETRY);
            geometry.set(ValueLayout.JAVA_INT, 8, width);   // max_width
            geometry.set(ValueLayout.JAVA_INT, 12, height); // max_height
            GtkBindings.GTK_WINDOW_SET_GEOMETRY_HINTS.invokeExact(
                getWindow(), MemorySegment.NULL, geometry, GtkBindings.GDK_HINT_MAX_SIZE
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set maximum size", t);
        }
    }

    @Override
    public void setTitle(String title) {
        try {
            MemorySegment titlePtr = arena.allocateFrom(title);
            GtkBindings.GTK_WINDOW_SET_TITLE.invokeExact(getWindow(), titlePtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set title", t);
        }
    }

    @Override
    public String getTitle() {
        try {
            MemorySegment result = (MemorySegment) GtkBindings.GTK_WINDOW_GET_TITLE.invokeExact(getWindow());
            if (result.address() == 0) return "";
            return result.reinterpret(Integer.MAX_VALUE).getString(0);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get title", t);
        }
    }

    @Override
    public void setResizable(boolean resizable) {
        try {
            GtkBindings.GTK_WINDOW_SET_RESIZABLE.invokeExact(getWindow(), resizable ? 1 : 0);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set resizable", t);
        }
    }

    @Override
    public void setAlwaysOnTop(boolean alwaysOnTop) {
        try {
            GtkBindings.GTK_WINDOW_SET_KEEP_ABOVE.invokeExact(getWindow(), alwaysOnTop ? 1 : 0);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set always on top", t);
        }
    }

    @Override
    public void setOpacity(double opacity) {
        try {
            double clamped = Math.max(0.0, Math.min(1.0, opacity));
            GtkBindings.GTK_WIDGET_SET_OPACITY.invokeExact(getWindow(), clamped);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set opacity", t);
        }
    }

    @Override
    public double getOpacity() {
        try {
            return (double) GtkBindings.GTK_WIDGET_GET_OPACITY.invokeExact(getWindow());
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get opacity", t);
        }
    }

    @Override
    public void setTitleBarStyle(String style) {
        try {
            MemorySegment window = getWindow();
            switch (style.toLowerCase()) {
                case "hidden", "hiddeninset" ->
                    GtkBindings.GTK_WINDOW_SET_DECORATED.invokeExact(window, 0);
                case "default" ->
                    GtkBindings.GTK_WINDOW_SET_DECORATED.invokeExact(window, 1);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set title bar style", t);
        }
    }
}
