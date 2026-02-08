package build.krema.core.window.macos;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import build.krema.core.window.WindowEngine;
import build.krema.core.window.WindowState;

/**
 * macOS WindowEngine implementation using Cocoa NSWindow APIs via FFM.
 * Uses NSApplication.sharedApplication.keyWindow to get the main window.
 */
public final class MacOSWindowEngine implements WindowEngine {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup OBJC_LOOKUP;

    // Objective-C runtime functions
    private static final MethodHandle OBJC_GET_CLASS;
    private static final MethodHandle SEL_REGISTER_NAME;
    private static final MethodHandle OBJC_MSG_SEND;
    private static final MethodHandle OBJC_MSG_SEND_BOOL;
    private static final MethodHandle OBJC_MSG_SEND_DOUBLE;

    // Common selectors
    private static final MemorySegment SEL_SHARED_APPLICATION;
    private static final MemorySegment SEL_KEY_WINDOW;
    private static final MemorySegment SEL_MAIN_WINDOW;
    private static final MemorySegment SEL_FRAME;
    private static final MemorySegment SEL_SET_FRAME_DISPLAY;
    private static final MemorySegment SEL_MINIATURIZE;
    private static final MemorySegment SEL_DEMINIATURIZE;
    private static final MemorySegment SEL_ZOOM;
    private static final MemorySegment SEL_TOGGLE_FULLSCREEN;
    private static final MemorySegment SEL_CENTER;
    private static final MemorySegment SEL_MAKE_KEY_AND_ORDER_FRONT;
    private static final MemorySegment SEL_ORDER_OUT;
    private static final MemorySegment SEL_ORDER_FRONT;
    private static final MemorySegment SEL_IS_MINIATURIZED;
    private static final MemorySegment SEL_IS_ZOOMED;
    private static final MemorySegment SEL_IS_VISIBLE;
    private static final MemorySegment SEL_IS_KEY_WINDOW;
    private static final MemorySegment SEL_STYLE_MASK;
    private static final MemorySegment SEL_SET_STYLE_MASK;
    private static final MemorySegment SEL_SET_TITLE;
    private static final MemorySegment SEL_TITLE;
    private static final MemorySegment SEL_UTF8_STRING;
    private static final MemorySegment SEL_SET_ALPHA_VALUE;
    private static final MemorySegment SEL_ALPHA_VALUE;
    private static final MemorySegment SEL_SET_LEVEL;
    private static final MemorySegment SEL_SET_CONTENT_MIN_SIZE;
    private static final MemorySegment SEL_SET_CONTENT_MAX_SIZE;
    private static final MemorySegment SEL_SET_TITLEBAR_APPEARS_TRANSPARENT;
    private static final MemorySegment SEL_STANDARD_WINDOW_BUTTON;
    private static final MemorySegment SEL_SET_FRAME_ORIGIN;
    private static final MemorySegment SEL_SUPERVIEW;
    private static final MemorySegment SEL_SET_HIDDEN;

    // NSWindow style masks
    private static final long NS_WINDOW_STYLE_MASK_TITLED = 1 << 0;
    private static final long NS_WINDOW_STYLE_MASK_CLOSABLE = 1 << 1;
    private static final long NS_WINDOW_STYLE_MASK_MINIATURIZABLE = 1 << 2;
    private static final long NS_WINDOW_STYLE_MASK_RESIZABLE = 1 << 3;
    private static final long NS_WINDOW_STYLE_MASK_FULLSCREEN = 1 << 14;
    private static final long NS_WINDOW_STYLE_MASK_FULL_SIZE_CONTENT_VIEW = 1 << 15;

    // Window levels
    private static final int NS_NORMAL_WINDOW_LEVEL = 0;
    private static final int NS_FLOATING_WINDOW_LEVEL = 3;

    // NSWindowButton types
    private static final long NS_WINDOW_CLOSE_BUTTON = 0;
    private static final long NS_WINDOW_MINIATURIZE_BUTTON = 1;
    private static final long NS_WINDOW_ZOOM_BUTTON = 2;

    // CGRect layout
    private static final long CGRECT_X = 0;
    private static final long CGRECT_Y = 8;
    private static final long CGRECT_WIDTH = 16;
    private static final long CGRECT_HEIGHT = 24;

    static {
        OBJC_LOOKUP = SymbolLookup.libraryLookup("/usr/lib/libobjc.dylib", Arena.global());

        // Load AppKit framework
        SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/AppKit.framework/AppKit",
            Arena.global()
        );

        OBJC_GET_CLASS = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_getClass").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        SEL_REGISTER_NAME = LINKER.downcallHandle(
            OBJC_LOOKUP.find("sel_registerName").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // objc_msgSend for id return
        OBJC_MSG_SEND = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // objc_msgSend for BOOL return
        OBJC_MSG_SEND_BOOL = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // objc_msgSend for double return
        OBJC_MSG_SEND_DOUBLE = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // Cache selectors
        SEL_SHARED_APPLICATION = sel("sharedApplication");
        SEL_KEY_WINDOW = sel("keyWindow");
        SEL_MAIN_WINDOW = sel("mainWindow");
        SEL_FRAME = sel("frame");
        SEL_SET_FRAME_DISPLAY = sel("setFrame:display:");
        SEL_MINIATURIZE = sel("miniaturize:");
        SEL_DEMINIATURIZE = sel("deminiaturize:");
        SEL_ZOOM = sel("zoom:");
        SEL_TOGGLE_FULLSCREEN = sel("toggleFullScreen:");
        SEL_CENTER = sel("center");
        SEL_MAKE_KEY_AND_ORDER_FRONT = sel("makeKeyAndOrderFront:");
        SEL_ORDER_OUT = sel("orderOut:");
        SEL_ORDER_FRONT = sel("orderFront:");
        SEL_IS_MINIATURIZED = sel("isMiniaturized");
        SEL_IS_ZOOMED = sel("isZoomed");
        SEL_IS_VISIBLE = sel("isVisible");
        SEL_IS_KEY_WINDOW = sel("isKeyWindow");
        SEL_STYLE_MASK = sel("styleMask");
        SEL_SET_STYLE_MASK = sel("setStyleMask:");
        SEL_SET_TITLE = sel("setTitle:");
        SEL_TITLE = sel("title");
        SEL_UTF8_STRING = sel("UTF8String");
        SEL_SET_ALPHA_VALUE = sel("setAlphaValue:");
        SEL_ALPHA_VALUE = sel("alphaValue");
        SEL_SET_LEVEL = sel("setLevel:");
        SEL_SET_CONTENT_MIN_SIZE = sel("setContentMinSize:");
        SEL_SET_CONTENT_MAX_SIZE = sel("setContentMaxSize:");
        SEL_SET_TITLEBAR_APPEARS_TRANSPARENT = sel("setTitlebarAppearsTransparent:");
        SEL_STANDARD_WINDOW_BUTTON = sel("standardWindowButton:");
        SEL_SET_FRAME_ORIGIN = sel("setFrameOrigin:");
        SEL_SUPERVIEW = sel("superview");
        SEL_SET_HIDDEN = sel("setHidden:");
    }

    private final Arena arena = Arena.ofAuto();

    /**
     * Gets the key window (main application window).
     */
    private MemorySegment getWindow() throws Throwable {
        MemorySegment nsApp = getClass("NSApplication");
        MemorySegment app = msgSend(nsApp, SEL_SHARED_APPLICATION);
        MemorySegment window = msgSend(app, SEL_KEY_WINDOW);

        // Fallback to main window if no key window
        if (window.address() == 0) {
            window = msgSend(app, SEL_MAIN_WINDOW);
        }

        return window;
    }

    @Override
    public WindowState getState() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) {
                return WindowState.of(0, 0, 0, 0);
            }

            int[] pos = getPosition();
            int[] size = getSize();
            boolean minimized = isMinimized();
            boolean maximized = isMaximized();
            boolean fullscreen = isFullscreen();
            boolean focused = isFocused();
            boolean visible = isVisible();

            return new WindowState(pos[0], pos[1], size[0], size[1],
                                   minimized, maximized, fullscreen, focused, visible);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get window state", t);
        }
    }

    @Override
    public int[] getPosition() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) {
                return new int[]{0, 0};
            }

            double[] frame = getFrame(window);
            // macOS uses bottom-left origin, convert to top-left
            // We'd need screen height for proper conversion, for now return raw
            return new int[]{(int) frame[0], (int) frame[1]};
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get window position", t);
        }
    }

    @Override
    public int[] getSize() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) {
                return new int[]{0, 0};
            }

            double[] frame = getFrame(window);
            return new int[]{(int) frame[2], (int) frame[3]};
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get window size", t);
        }
    }

    private double[] getFrame(MemorySegment window) throws Throwable {
        // frame returns CGRect struct
        MethodHandle msgSendRect = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(
                MemoryLayout.structLayout(
                    ValueLayout.JAVA_DOUBLE.withName("x"),
                    ValueLayout.JAVA_DOUBLE.withName("y"),
                    ValueLayout.JAVA_DOUBLE.withName("width"),
                    ValueLayout.JAVA_DOUBLE.withName("height")
                ),
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS
            )
        );

        MemorySegment rect = (MemorySegment) msgSendRect.invokeExact(
            (SegmentAllocator) arena,
            window,
            SEL_FRAME
        );

        return new double[]{
            rect.get(ValueLayout.JAVA_DOUBLE, CGRECT_X),
            rect.get(ValueLayout.JAVA_DOUBLE, CGRECT_Y),
            rect.get(ValueLayout.JAVA_DOUBLE, CGRECT_WIDTH),
            rect.get(ValueLayout.JAVA_DOUBLE, CGRECT_HEIGHT)
        };
    }

    @Override
    public boolean isMinimized() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return false;
            return msgSendBool(window, SEL_IS_MINIATURIZED);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to check minimized state", t);
        }
    }

    @Override
    public boolean isMaximized() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return false;
            return msgSendBool(window, SEL_IS_ZOOMED);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to check maximized state", t);
        }
    }

    @Override
    public boolean isFullscreen() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return false;
            long mask = msgSendLong(window, SEL_STYLE_MASK);
            return (mask & NS_WINDOW_STYLE_MASK_FULLSCREEN) != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to check fullscreen state", t);
        }
    }

    @Override
    public boolean isFocused() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return false;
            return msgSendBool(window, SEL_IS_KEY_WINDOW);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to check focused state", t);
        }
    }

    @Override
    public boolean isVisible() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return false;
            return msgSendBool(window, SEL_IS_VISIBLE);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to check visible state", t);
        }
    }

    @Override
    public void minimize() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;
            msgSendWithArg(window, SEL_MINIATURIZE, MemorySegment.NULL);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to minimize window", t);
        }
    }

    @Override
    public void maximize() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;
            // zoom: toggles between maximized and normal
            if (!isMaximized()) {
                msgSendWithArg(window, SEL_ZOOM, MemorySegment.NULL);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to maximize window", t);
        }
    }

    @Override
    public void restore() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;

            if (isMinimized()) {
                msgSendWithArg(window, SEL_DEMINIATURIZE, MemorySegment.NULL);
            } else if (isMaximized()) {
                msgSendWithArg(window, SEL_ZOOM, MemorySegment.NULL);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to restore window", t);
        }
    }

    @Override
    public void toggleFullscreen() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;
            msgSendWithArg(window, SEL_TOGGLE_FULLSCREEN, MemorySegment.NULL);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to toggle fullscreen", t);
        }
    }

    @Override
    public void setFullscreen(boolean fullscreen) {
        try {
            if (isFullscreen() != fullscreen) {
                toggleFullscreen();
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set fullscreen", t);
        }
    }

    @Override
    public void center() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;
            msgSend(window, SEL_CENTER);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to center window", t);
        }
    }

    @Override
    public void focus() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;
            msgSendWithArg(window, SEL_MAKE_KEY_AND_ORDER_FRONT, MemorySegment.NULL);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to focus window", t);
        }
    }

    @Override
    public void show() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;
            msgSendWithArg(window, SEL_ORDER_FRONT, MemorySegment.NULL);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to show window", t);
        }
    }

    @Override
    public void hide() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;
            msgSendWithArg(window, SEL_ORDER_OUT, MemorySegment.NULL);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to hide window", t);
        }
    }

    @Override
    public void setPosition(int x, int y) {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;

            double[] currentFrame = getFrame(window);
            setFrameDisplay(window, x, y, currentFrame[2], currentFrame[3]);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set window position", t);
        }
    }

    @Override
    public void setSize(int width, int height) {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;

            double[] currentFrame = getFrame(window);
            setFrameDisplay(window, currentFrame[0], currentFrame[1], width, height);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set window size", t);
        }
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;
            setFrameDisplay(window, x, y, width, height);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set window bounds", t);
        }
    }

    private void setFrameDisplay(MemorySegment window, double x, double y, double width, double height) throws Throwable {
        // setFrame:display: takes CGRect and BOOL
        MethodHandle mh = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
                ValueLayout.JAVA_INT
            )
        );

        // CGRect is passed as 4 doubles (x, y, width, height), then BOOL display
        mh.invokeExact(window, SEL_SET_FRAME_DISPLAY, x, y, width, height, 1);
    }

    @Override
    public void setMinSize(int width, int height) {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;

            // setContentMinSize: takes NSSize (2 doubles)
            MethodHandle mh = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE
                )
            );
            mh.invokeExact(window, SEL_SET_CONTENT_MIN_SIZE, (double) width, (double) height);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set minimum size", t);
        }
    }

    @Override
    public void setMaxSize(int width, int height) {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;

            MethodHandle mh = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE
                )
            );
            mh.invokeExact(window, SEL_SET_CONTENT_MAX_SIZE, (double) width, (double) height);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set maximum size", t);
        }
    }

    @Override
    public void setTitle(String title) {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;

            MemorySegment nsTitle = createNSString(title);
            msgSendWithArg(window, SEL_SET_TITLE, nsTitle);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set title", t);
        }
    }

    @Override
    public String getTitle() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return "";

            MemorySegment nsTitle = msgSend(window, SEL_TITLE);
            if (nsTitle.address() == 0) return "";

            MemorySegment utf8 = msgSend(nsTitle, SEL_UTF8_STRING);
            if (utf8.address() == 0) return "";

            return utf8.reinterpret(Integer.MAX_VALUE).getString(0);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get title", t);
        }
    }

    @Override
    public void setResizable(boolean resizable) {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;

            long currentMask = msgSendLong(window, SEL_STYLE_MASK);
            long newMask;

            if (resizable) {
                newMask = currentMask | NS_WINDOW_STYLE_MASK_RESIZABLE;
            } else {
                newMask = currentMask & ~NS_WINDOW_STYLE_MASK_RESIZABLE;
            }

            msgSendWithLong(window, SEL_SET_STYLE_MASK, newMask);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set resizable", t);
        }
    }

    @Override
    public void setAlwaysOnTop(boolean alwaysOnTop) {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;

            int level = alwaysOnTop ? NS_FLOATING_WINDOW_LEVEL : NS_NORMAL_WINDOW_LEVEL;

            MethodHandle mh = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );
            mh.invokeExact(window, SEL_SET_LEVEL, level);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set always on top", t);
        }
    }

    @Override
    public void setOpacity(double opacity) {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;

            MethodHandle mh = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE)
            );
            mh.invokeExact(window, SEL_SET_ALPHA_VALUE, Math.max(0.0, Math.min(1.0, opacity)));
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set opacity", t);
        }
    }

    @Override
    public double getOpacity() {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return 1.0;
            return msgSendDouble(window, SEL_ALPHA_VALUE);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get opacity", t);
        }
    }

    // ===== Frameless Window Support =====

    @Override
    public void setTitleBarStyle(String style) {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;

            long currentMask = msgSendLong(window, SEL_STYLE_MASK);
            long newMask = currentMask;

            switch (style.toLowerCase()) {
                case "hidden", "hiddeninset" -> {
                    // Add full size content view mask
                    newMask |= NS_WINDOW_STYLE_MASK_FULL_SIZE_CONTENT_VIEW;
                    msgSendWithLong(window, SEL_SET_STYLE_MASK, newMask);
                    setTitlebarAppearsTransparent(true);
                }
                case "default" -> {
                    // Remove full size content view mask
                    newMask &= ~NS_WINDOW_STYLE_MASK_FULL_SIZE_CONTENT_VIEW;
                    msgSendWithLong(window, SEL_SET_STYLE_MASK, newMask);
                    setTitlebarAppearsTransparent(false);
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set title bar style", t);
        }
    }

    @Override
    public void setTrafficLightPosition(int x, int y) {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;

            // Get traffic light buttons and position their superview
            MemorySegment closeButton = getStandardWindowButton(window, NS_WINDOW_CLOSE_BUTTON);
            if (closeButton.address() == 0) return;

            // Get the superview (contains all traffic light buttons)
            MemorySegment superview = msgSend(closeButton, SEL_SUPERVIEW);
            if (superview.address() == 0) return;

            // Set frame origin (position)
            MethodHandle mh = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE
                )
            );
            mh.invokeExact(superview, SEL_SET_FRAME_ORIGIN, (double) x, (double) y);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set traffic light position", t);
        }
    }

    @Override
    public void setTitlebarAppearsTransparent(boolean transparent) {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;

            MethodHandle mh = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN)
            );
            mh.invokeExact(window, SEL_SET_TITLEBAR_APPEARS_TRANSPARENT, transparent);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set titlebar transparency", t);
        }
    }

    @Override
    public void setFullSizeContentView(boolean extend) {
        try {
            MemorySegment window = getWindow();
            if (window.address() == 0) return;

            long currentMask = msgSendLong(window, SEL_STYLE_MASK);
            long newMask;

            if (extend) {
                newMask = currentMask | NS_WINDOW_STYLE_MASK_FULL_SIZE_CONTENT_VIEW;
            } else {
                newMask = currentMask & ~NS_WINDOW_STYLE_MASK_FULL_SIZE_CONTENT_VIEW;
            }

            msgSendWithLong(window, SEL_SET_STYLE_MASK, newMask);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set full size content view", t);
        }
    }

    private MemorySegment getStandardWindowButton(MemorySegment window, long buttonType) throws Throwable {
        MethodHandle mh = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );
        return (MemorySegment) mh.invokeExact(window, SEL_STANDARD_WINDOW_BUTTON, buttonType);
    }

    // ===== Objective-C helpers =====

    private MemorySegment getClass(String name) {
        try {
            MemorySegment namePtr = arena.allocateFrom(name);
            return (MemorySegment) OBJC_GET_CLASS.invokeExact(namePtr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get class: " + name, t);
        }
    }

    private static MemorySegment sel(String name) {
        try {
            try (Arena temp = Arena.ofConfined()) {
                MemorySegment namePtr = temp.allocateFrom(name);
                return (MemorySegment) SEL_REGISTER_NAME.invokeExact(namePtr);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get selector: " + name, t);
        }
    }

    private MemorySegment msgSend(MemorySegment target, MemorySegment selector) throws Throwable {
        return (MemorySegment) OBJC_MSG_SEND.invokeExact(target, selector);
    }

    private boolean msgSendBool(MemorySegment target, MemorySegment selector) throws Throwable {
        return (boolean) OBJC_MSG_SEND_BOOL.invokeExact(target, selector);
    }

    private double msgSendDouble(MemorySegment target, MemorySegment selector) throws Throwable {
        return (double) OBJC_MSG_SEND_DOUBLE.invokeExact(target, selector);
    }

    private long msgSendLong(MemorySegment target, MemorySegment selector) throws Throwable {
        MethodHandle mh = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        return (long) mh.invokeExact(target, selector);
    }

    private void msgSendWithArg(MemorySegment target, MemorySegment selector, MemorySegment arg) throws Throwable {
        MethodHandle mh = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        mh.invokeExact(target, selector, arg);
    }

    private void msgSendWithLong(MemorySegment target, MemorySegment selector, long arg) throws Throwable {
        MethodHandle mh = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );
        mh.invokeExact(target, selector, arg);
    }

    private MemorySegment createNSString(String str) throws Throwable {
        if (str == null) {
            str = "";
        }
        // Use CFStringCreateWithCString - toll-free bridged with NSString
        SymbolLookup cfLookup = SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation",
            Arena.global()
        );
        MethodHandle cfStringCreate = LINKER.downcallHandle(
            cfLookup.find("CFStringCreateWithCString").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );

        MemorySegment utf8Ptr = arena.allocateFrom(str);
        return (MemorySegment) cfStringCreate.invokeExact(MemorySegment.NULL, utf8Ptr, 0x08000100);
    }
}
