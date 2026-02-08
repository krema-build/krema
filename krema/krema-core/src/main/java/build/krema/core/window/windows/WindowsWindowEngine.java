package build.krema.core.window.windows;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import build.krema.core.platform.windows.Win32Bindings;
import build.krema.core.webview.WebViewCLibEngine;
import build.krema.core.window.WindowEngine;
import build.krema.core.window.WindowState;

/**
 * Windows WindowEngine implementation using Win32 API via FFM.
 * Gets the HWND from the active WebViewCLibEngine instance.
 */
public final class WindowsWindowEngine implements WindowEngine {

    private final Arena arena = Arena.ofAuto();

    private MemorySegment getHwnd() {
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
            MemorySegment rect = arena.allocate(Win32Bindings.RECT);
            Win32Bindings.GET_WINDOW_RECT.invokeExact(getHwnd(), rect);
            return new int[]{
                rect.get(ValueLayout.JAVA_INT, 0),  // left
                rect.get(ValueLayout.JAVA_INT, 4)   // top
            };
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get window position", t);
        }
    }

    @Override
    public int[] getSize() {
        try {
            MemorySegment rect = arena.allocate(Win32Bindings.RECT);
            Win32Bindings.GET_WINDOW_RECT.invokeExact(getHwnd(), rect);
            int left = rect.get(ValueLayout.JAVA_INT, 0);
            int top = rect.get(ValueLayout.JAVA_INT, 4);
            int right = rect.get(ValueLayout.JAVA_INT, 8);
            int bottom = rect.get(ValueLayout.JAVA_INT, 12);
            return new int[]{ right - left, bottom - top };
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get window size", t);
        }
    }

    @Override
    public boolean isMinimized() {
        try {
            return ((int) Win32Bindings.IS_ICONIC.invokeExact(getHwnd())) != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to check minimized state", t);
        }
    }

    @Override
    public boolean isMaximized() {
        try {
            return ((int) Win32Bindings.IS_ZOOMED.invokeExact(getHwnd())) != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to check maximized state", t);
        }
    }

    @Override
    public boolean isFullscreen() {
        return fullscreenState;
    }

    @Override
    public boolean isFocused() {
        try {
            MemorySegment foreground = (MemorySegment) Win32Bindings.GET_FOREGROUND_WINDOW.invokeExact();
            return foreground.address() == getHwnd().address();
        } catch (Throwable t) {
            throw new RuntimeException("Failed to check focused state", t);
        }
    }

    @Override
    public boolean isVisible() {
        try {
            return ((int) Win32Bindings.IS_WINDOW_VISIBLE.invokeExact(getHwnd())) != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to check visible state", t);
        }
    }

    @Override
    public void minimize() {
        try {
            Win32Bindings.SHOW_WINDOW.invokeExact(getHwnd(), Win32Bindings.SW_MINIMIZE);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to minimize window", t);
        }
    }

    @Override
    public void maximize() {
        try {
            Win32Bindings.SHOW_WINDOW.invokeExact(getHwnd(), Win32Bindings.SW_SHOWMAXIMIZED);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to maximize window", t);
        }
    }

    @Override
    public void restore() {
        try {
            Win32Bindings.SHOW_WINDOW.invokeExact(getHwnd(), Win32Bindings.SW_RESTORE);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to restore window", t);
        }
    }

    // Fullscreen state tracking
    private boolean fullscreenState;
    private long savedStyle;
    private int savedX, savedY, savedW, savedH;

    @Override
    public void toggleFullscreen() {
        setFullscreen(!isFullscreen());
    }

    @Override
    public void setFullscreen(boolean fullscreen) {
        try {
            MemorySegment hwnd = getHwnd();
            fullscreenState = fullscreen;
            if (fullscreen) {
                // Save current style and position
                savedStyle = (long) Win32Bindings.GET_WINDOW_LONG_W.invokeExact(hwnd, Win32Bindings.GWL_STYLE);
                MemorySegment rect = arena.allocate(Win32Bindings.RECT);
                Win32Bindings.GET_WINDOW_RECT.invokeExact(hwnd, rect);
                savedX = rect.get(ValueLayout.JAVA_INT, 0);
                savedY = rect.get(ValueLayout.JAVA_INT, 4);
                savedW = rect.get(ValueLayout.JAVA_INT, 8) - savedX;
                savedH = rect.get(ValueLayout.JAVA_INT, 12) - savedY;

                // Remove borders
                long newStyle = savedStyle & ~(Win32Bindings.WS_CAPTION | Win32Bindings.WS_THICKFRAME
                    | Win32Bindings.WS_MINIMIZEBOX | Win32Bindings.WS_MAXIMIZEBOX);
                Win32Bindings.SET_WINDOW_LONG_W.invokeExact(hwnd, Win32Bindings.GWL_STYLE, newStyle);

                // Get screen dimensions
                int screenW = (int) Win32Bindings.GET_SYSTEM_METRICS.invokeExact(Win32Bindings.SM_CXSCREEN);
                int screenH = (int) Win32Bindings.GET_SYSTEM_METRICS.invokeExact(Win32Bindings.SM_CYSCREEN);

                Win32Bindings.SET_WINDOW_POS.invokeExact(
                    hwnd, MemorySegment.NULL,
                    0, 0, screenW, screenH,
                    Win32Bindings.SWP_NOZORDER | Win32Bindings.SWP_SHOWWINDOW
                );
            } else {
                // Restore style and position
                Win32Bindings.SET_WINDOW_LONG_W.invokeExact(hwnd, Win32Bindings.GWL_STYLE, savedStyle);
                Win32Bindings.SET_WINDOW_POS.invokeExact(
                    hwnd, MemorySegment.NULL,
                    savedX, savedY, savedW, savedH,
                    Win32Bindings.SWP_NOZORDER | Win32Bindings.SWP_SHOWWINDOW
                );
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set fullscreen", t);
        }
    }

    @Override
    public void center() {
        try {
            MemorySegment hwnd = getHwnd();
            int[] size = getSize();

            // Get work area of the monitor containing this window
            MemorySegment monitor = (MemorySegment) Win32Bindings.MONITOR_FROM_WINDOW.invokeExact(
                hwnd, Win32Bindings.MONITOR_DEFAULTTOPRIMARY
            );
            MemorySegment monitorInfo = arena.allocate(Win32Bindings.MONITORINFO);
            monitorInfo.set(ValueLayout.JAVA_INT, 0, (int) Win32Bindings.MONITORINFO.byteSize());
            Win32Bindings.GET_MONITOR_INFO_W.invokeExact(monitor, monitorInfo);

            // rcWork starts at offset 20 (cbSize=4 + rcMonitor=16)
            int workLeft = monitorInfo.get(ValueLayout.JAVA_INT, 20);
            int workTop = monitorInfo.get(ValueLayout.JAVA_INT, 24);
            int workRight = monitorInfo.get(ValueLayout.JAVA_INT, 28);
            int workBottom = monitorInfo.get(ValueLayout.JAVA_INT, 32);

            int x = workLeft + (workRight - workLeft - size[0]) / 2;
            int y = workTop + (workBottom - workTop - size[1]) / 2;

            Win32Bindings.SET_WINDOW_POS.invokeExact(
                hwnd, MemorySegment.NULL,
                x, y, 0, 0,
                Win32Bindings.SWP_NOSIZE | Win32Bindings.SWP_NOZORDER
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to center window", t);
        }
    }

    @Override
    public void focus() {
        try {
            MemorySegment hwnd = getHwnd();
            if (isMinimized()) {
                Win32Bindings.SHOW_WINDOW.invokeExact(hwnd, Win32Bindings.SW_RESTORE);
            }
            Win32Bindings.SET_FOREGROUND_WINDOW.invokeExact(hwnd);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to focus window", t);
        }
    }

    @Override
    public void show() {
        try {
            Win32Bindings.SHOW_WINDOW.invokeExact(getHwnd(), Win32Bindings.SW_SHOW);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to show window", t);
        }
    }

    @Override
    public void hide() {
        try {
            Win32Bindings.SHOW_WINDOW.invokeExact(getHwnd(), Win32Bindings.SW_HIDE);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to hide window", t);
        }
    }

    @Override
    public void setPosition(int x, int y) {
        try {
            Win32Bindings.SET_WINDOW_POS.invokeExact(
                getHwnd(), MemorySegment.NULL,
                x, y, 0, 0,
                Win32Bindings.SWP_NOSIZE | Win32Bindings.SWP_NOZORDER
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set window position", t);
        }
    }

    @Override
    public void setSize(int width, int height) {
        try {
            Win32Bindings.SET_WINDOW_POS.invokeExact(
                getHwnd(), MemorySegment.NULL,
                0, 0, width, height,
                Win32Bindings.SWP_NOMOVE | Win32Bindings.SWP_NOZORDER
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set window size", t);
        }
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        try {
            Win32Bindings.SET_WINDOW_POS.invokeExact(
                getHwnd(), MemorySegment.NULL,
                x, y, width, height,
                Win32Bindings.SWP_NOZORDER
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set window bounds", t);
        }
    }

    // Min/max size enforcement via WM_GETMINMAXINFO subclassing
    private int minWidth, minHeight, maxWidth, maxHeight;

    // WndProc subclassing state
    private static final Linker LINKER = Linker.nativeLinker();
    private long originalWndProc;
    private MemorySegment wndProcStub;
    private boolean subclassed;
    private static WindowsWindowEngine activeEngine;

    private void ensureSubclassed() {
        if (subclassed) return;
        try {
            MemorySegment hwnd = getHwnd();
            activeEngine = this;

            MethodHandle callback = MethodHandles.lookup().findStatic(
                WindowsWindowEngine.class,
                "customWindowProc",
                MethodType.methodType(long.class,
                    MemorySegment.class, int.class, long.class, long.class)
            );

            FunctionDescriptor wndProcDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG
            );

            wndProcStub = LINKER.upcallStub(callback, wndProcDesc, Arena.ofAuto());

            originalWndProc = (long) Win32Bindings.GET_WINDOW_LONG_W.invokeExact(
                hwnd, Win32Bindings.GWLP_WNDPROC
            );

            Win32Bindings.SET_WINDOW_LONG_W.invokeExact(
                hwnd, Win32Bindings.GWLP_WNDPROC, wndProcStub.address()
            );

            subclassed = true;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to subclass window for WM_GETMINMAXINFO", t);
        }
    }

    @SuppressWarnings("unused")
    private static long customWindowProc(MemorySegment hwnd, int msg, long wParam, long lParam) {
        try {
            WindowsWindowEngine engine = activeEngine;
            if (engine == null) {
                return 0L;
            }

            if (msg == Win32Bindings.WM_GETMINMAXINFO && lParam != 0) {
                // Let the original WndProc fill in defaults first
                long result = (long) Win32Bindings.CALL_WINDOW_PROC_W.invokeExact(
                    MemorySegment.ofAddress(engine.originalWndProc),
                    hwnd, msg, wParam, lParam
                );

                // MINMAXINFO layout: ptReserved(8), ptMaxSize(8), ptMaxPosition(8),
                //                    ptMinTrackSize(8), ptMaxTrackSize(8)
                MemorySegment info = MemorySegment.ofAddress(lParam)
                    .reinterpret(Win32Bindings.MINMAXINFO.byteSize());

                // ptMinTrackSize at offset 24 (POINT: x=24, y=28)
                if (engine.minWidth > 0) {
                    info.set(ValueLayout.JAVA_INT, 24, engine.minWidth);
                }
                if (engine.minHeight > 0) {
                    info.set(ValueLayout.JAVA_INT, 28, engine.minHeight);
                }

                // ptMaxTrackSize at offset 32 (POINT: x=32, y=36)
                if (engine.maxWidth > 0) {
                    info.set(ValueLayout.JAVA_INT, 32, engine.maxWidth);
                }
                if (engine.maxHeight > 0) {
                    info.set(ValueLayout.JAVA_INT, 36, engine.maxHeight);
                }

                return result;
            }

            // All other messages: forward to original WndProc
            return (long) Win32Bindings.CALL_WINDOW_PROC_W.invokeExact(
                MemorySegment.ofAddress(engine.originalWndProc),
                hwnd, msg, wParam, lParam
            );
        } catch (Throwable t) {
            return 0L;
        }
    }

    @Override
    public void setMinSize(int width, int height) {
        this.minWidth = width;
        this.minHeight = height;
        ensureSubclassed();
        // Immediately clamp if current size violates constraints
        int[] current = getSize();
        if (current[0] < width || current[1] < height) {
            setSize(Math.max(current[0], width), Math.max(current[1], height));
        }
    }

    @Override
    public void setMaxSize(int width, int height) {
        this.maxWidth = width;
        this.maxHeight = height;
        ensureSubclassed();
        // Immediately clamp if current size violates constraints
        int[] current = getSize();
        if ((width > 0 && current[0] > width) || (height > 0 && current[1] > height)) {
            setSize(
                width > 0 ? Math.min(current[0], width) : current[0],
                height > 0 ? Math.min(current[1], height) : current[1]
            );
        }
    }

    @Override
    public void setTitle(String title) {
        try {
            MemorySegment titlePtr = Win32Bindings.allocateWideString(arena, title);
            Win32Bindings.SET_WINDOW_TEXT_W.invokeExact(getHwnd(), titlePtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set title", t);
        }
    }

    @Override
    public String getTitle() {
        try {
            MemorySegment hwnd = getHwnd();
            int length = (int) Win32Bindings.GET_WINDOW_TEXT_LENGTH_W.invokeExact(hwnd);
            if (length <= 0) return "";
            int bufSize = length + 1;
            MemorySegment buffer = arena.allocate((long) bufSize * 2);
            Win32Bindings.GET_WINDOW_TEXT_W.invokeExact(hwnd, buffer, bufSize);
            return Win32Bindings.readWideString(buffer, bufSize);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get title", t);
        }
    }

    @Override
    public void setResizable(boolean resizable) {
        try {
            MemorySegment hwnd = getHwnd();
            long style = (long) Win32Bindings.GET_WINDOW_LONG_W.invokeExact(hwnd, Win32Bindings.GWL_STYLE);
            if (resizable) {
                style |= Win32Bindings.WS_THICKFRAME | Win32Bindings.WS_MAXIMIZEBOX;
            } else {
                style &= ~(Win32Bindings.WS_THICKFRAME | Win32Bindings.WS_MAXIMIZEBOX);
            }
            Win32Bindings.SET_WINDOW_LONG_W.invokeExact(hwnd, Win32Bindings.GWL_STYLE, style);
            // Force window to redraw frame
            Win32Bindings.SET_WINDOW_POS.invokeExact(
                hwnd, MemorySegment.NULL,
                0, 0, 0, 0,
                Win32Bindings.SWP_NOMOVE | Win32Bindings.SWP_NOSIZE | Win32Bindings.SWP_NOZORDER
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set resizable", t);
        }
    }

    @Override
    public void setAlwaysOnTop(boolean alwaysOnTop) {
        try {
            MemorySegment insertAfter = MemorySegment.ofAddress(
                alwaysOnTop ? Win32Bindings.HWND_TOPMOST : Win32Bindings.HWND_NOTOPMOST
            );
            Win32Bindings.SET_WINDOW_POS.invokeExact(
                getHwnd(), insertAfter,
                0, 0, 0, 0,
                Win32Bindings.SWP_NOMOVE | Win32Bindings.SWP_NOSIZE
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set always on top", t);
        }
    }

    @Override
    public void setOpacity(double opacity) {
        try {
            MemorySegment hwnd = getHwnd();
            double clamped = Math.max(0.0, Math.min(1.0, opacity));
            byte alpha = (byte) (clamped * 255);

            // Ensure WS_EX_LAYERED is set
            long exStyle = (long) Win32Bindings.GET_WINDOW_LONG_W.invokeExact(hwnd, Win32Bindings.GWL_EXSTYLE);
            if ((exStyle & Win32Bindings.WS_EX_LAYERED) == 0) {
                Win32Bindings.SET_WINDOW_LONG_W.invokeExact(
                    hwnd, Win32Bindings.GWL_EXSTYLE, exStyle | Win32Bindings.WS_EX_LAYERED
                );
            }

            Win32Bindings.SET_LAYERED_WINDOW_ATTRIBUTES.invokeExact(
                hwnd, 0, alpha, Win32Bindings.LWA_ALPHA
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set opacity", t);
        }
    }

    @Override
    public double getOpacity() {
        try {
            MemorySegment hwnd = getHwnd();
            long exStyle = (long) Win32Bindings.GET_WINDOW_LONG_W.invokeExact(hwnd, Win32Bindings.GWL_EXSTYLE);
            if ((exStyle & Win32Bindings.WS_EX_LAYERED) == 0) {
                return 1.0;
            }

            MemorySegment alphaOut = arena.allocate(ValueLayout.JAVA_BYTE);
            Win32Bindings.GET_LAYERED_WINDOW_ATTRIBUTES.invokeExact(
                hwnd, MemorySegment.NULL, alphaOut, MemorySegment.NULL
            );
            int alpha = Byte.toUnsignedInt(alphaOut.get(ValueLayout.JAVA_BYTE, 0));
            return alpha / 255.0;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get opacity", t);
        }
    }

    @Override
    public void setTitleBarStyle(String style) {
        try {
            MemorySegment hwnd = getHwnd();
            long currentStyle = (long) Win32Bindings.GET_WINDOW_LONG_W.invokeExact(hwnd, Win32Bindings.GWL_STYLE);
            switch (style.toLowerCase()) {
                case "hidden", "hiddeninset" -> {
                    long newStyle = currentStyle & ~Win32Bindings.WS_CAPTION;
                    Win32Bindings.SET_WINDOW_LONG_W.invokeExact(hwnd, Win32Bindings.GWL_STYLE, newStyle);
                }
                case "default" -> {
                    long newStyle = currentStyle | Win32Bindings.WS_CAPTION;
                    Win32Bindings.SET_WINDOW_LONG_W.invokeExact(hwnd, Win32Bindings.GWL_STYLE, newStyle);
                }
            }
            // Force redraw
            Win32Bindings.SET_WINDOW_POS.invokeExact(
                hwnd, MemorySegment.NULL,
                0, 0, 0, 0,
                Win32Bindings.SWP_NOMOVE | Win32Bindings.SWP_NOSIZE | Win32Bindings.SWP_NOZORDER
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set title bar style", t);
        }
    }
}
