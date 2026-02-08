package build.krema.core.screen.macos;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

import build.krema.core.screen.*;

/**
 * macOS ScreenEngine implementation using Cocoa APIs via FFM.
 * Uses NSScreen for display information and NSEvent for cursor position.
 */
public final class MacOSScreenEngine implements ScreenEngine {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup OBJC_LOOKUP;

    // Objective-C runtime functions
    private static final MethodHandle OBJC_GET_CLASS;
    private static final MethodHandle SEL_REGISTER_NAME;
    private static final MethodHandle OBJC_MSG_SEND;
    private static final MethodHandle OBJC_MSG_SEND_LONG;
    private static final MethodHandle OBJC_MSG_SEND_DOUBLE;

    // Common selectors
    private static final MemorySegment SEL_SCREENS;
    private static final MemorySegment SEL_MAIN_SCREEN;
    private static final MemorySegment SEL_FRAME;
    private static final MemorySegment SEL_VISIBLE_FRAME;
    private static final MemorySegment SEL_BACKING_SCALE_FACTOR;
    private static final MemorySegment SEL_LOCALIZED_NAME;
    private static final MemorySegment SEL_OBJECT_ENUMERATOR;
    private static final MemorySegment SEL_NEXT_OBJECT;
    private static final MemorySegment SEL_MOUSE_LOCATION;
    private static final MemorySegment SEL_UTF8_STRING;

    // CGRect offsets (origin.x, origin.y, size.width, size.height)
    private static final long CGRECT_X = 0;
    private static final long CGRECT_Y = 8;
    private static final long CGRECT_WIDTH = 16;
    private static final long CGRECT_HEIGHT = 24;
    private static final long CGRECT_SIZE = 32;

    // NSPoint offsets
    private static final long NSPOINT_X = 0;
    private static final long NSPOINT_Y = 8;
    private static final long NSPOINT_SIZE = 16;

    static {
        OBJC_LOOKUP = SymbolLookup.libraryLookup("/usr/lib/libobjc.dylib", Arena.global());

        // Load AppKit framework
        SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/AppKit.framework/AppKit",
            Arena.global()
        );

        // objc_getClass(const char* name) -> Class
        OBJC_GET_CLASS = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_getClass").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // sel_registerName(const char* name) -> SEL
        SEL_REGISTER_NAME = LINKER.downcallHandle(
            OBJC_LOOKUP.find("sel_registerName").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // objc_msgSend(id self, SEL op, ...) -> id
        OBJC_MSG_SEND = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            Linker.Option.firstVariadicArg(2)
        );

        // objc_msgSend for NSInteger/long return
        OBJC_MSG_SEND_LONG = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            Linker.Option.firstVariadicArg(2)
        );

        // objc_msgSend for CGFloat/double return
        OBJC_MSG_SEND_DOUBLE = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            Linker.Option.firstVariadicArg(2)
        );

        // Cache selectors
        SEL_SCREENS = sel("screens");
        SEL_MAIN_SCREEN = sel("mainScreen");
        SEL_FRAME = sel("frame");
        SEL_VISIBLE_FRAME = sel("visibleFrame");
        SEL_BACKING_SCALE_FACTOR = sel("backingScaleFactor");
        SEL_LOCALIZED_NAME = sel("localizedName");
        SEL_OBJECT_ENUMERATOR = sel("objectEnumerator");
        SEL_NEXT_OBJECT = sel("nextObject");
        SEL_MOUSE_LOCATION = sel("mouseLocation");
        SEL_UTF8_STRING = sel("UTF8String");
    }

    private final Arena arena = Arena.ofAuto();

    @Override
    public List<ScreenInfo> getAllScreens() {
        try {
            MemorySegment nsScreenClass = getClass("NSScreen");
            MemorySegment screensArray = msgSend(nsScreenClass, SEL_SCREENS);

            List<ScreenInfo> screens = new ArrayList<>();
            MemorySegment mainScreen = msgSend(nsScreenClass, SEL_MAIN_SCREEN);

            // Use enumerator pattern (works correctly on ARM64)
            MemorySegment enumerator = msgSend(screensArray, SEL_OBJECT_ENUMERATOR);
            while (true) {
                MemorySegment screen = msgSend(enumerator, SEL_NEXT_OBJECT);
                if (screen.address() == 0) {
                    break;
                }
                boolean isPrimary = screen.address() == mainScreen.address();
                screens.add(getScreenInfo(screen, isPrimary));
            }

            return screens;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get all screens", t);
        }
    }

    @Override
    public ScreenInfo getPrimaryScreen() {
        try {
            MemorySegment nsScreenClass = getClass("NSScreen");
            MemorySegment mainScreen = msgSend(nsScreenClass, SEL_MAIN_SCREEN);
            return getScreenInfo(mainScreen, true);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get primary screen", t);
        }
    }

    @Override
    public CursorPosition getCursorPosition() {
        try {
            MemorySegment nsEventClass = getClass("NSEvent");

            // mouseLocation returns NSPoint (struct by value)
            // When FFM returns a struct, we must pass SegmentAllocator as first arg
            MethodHandle msgSendPoint = LINKER.downcallHandle(
                OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.of(
                    MemoryLayout.structLayout(
                        ValueLayout.JAVA_DOUBLE.withName("x"),
                        ValueLayout.JAVA_DOUBLE.withName("y")
                    ),
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
                ),
                Linker.Option.firstVariadicArg(2)
            );

            // Pass arena as SegmentAllocator for struct return value allocation
            MemorySegment result = (MemorySegment) msgSendPoint.invokeExact(
                (SegmentAllocator) arena,
                nsEventClass,
                SEL_MOUSE_LOCATION
            );

            double x = result.get(ValueLayout.JAVA_DOUBLE, NSPOINT_X);
            double y = result.get(ValueLayout.JAVA_DOUBLE, NSPOINT_Y);

            // macOS uses bottom-left origin; convert to top-left for consistency
            ScreenInfo primary = getPrimaryScreen();
            double convertedY = primary.frame().height() - y;

            return new CursorPosition(x, convertedY);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get cursor position", t);
        }
    }

    @Override
    public ScreenInfo getScreenAtPoint(double x, double y) {
        List<ScreenInfo> screens = getAllScreens();
        for (ScreenInfo screen : screens) {
            if (screen.frame().contains(x, y)) {
                return screen;
            }
        }
        return null;
    }

    private ScreenInfo getScreenInfo(MemorySegment screen, boolean isPrimary) throws Throwable {
        // Get frame (CGRect)
        ScreenBounds frame = getRect(screen, SEL_FRAME);

        // Get visible frame (CGRect) - excludes menu bar and dock
        ScreenBounds visibleFrame = getRect(screen, SEL_VISIBLE_FRAME);

        // Get scale factor
        double scaleFactor = msgSendDouble(screen, SEL_BACKING_SCALE_FACTOR);

        // Get localized name
        String name = getLocalizedName(screen);

        // Refresh rate: not directly available from NSScreen
        // Would need CGDisplayModeRef for this - use 60.0 as default
        double refreshRate = 60.0;

        return new ScreenInfo(name, frame, visibleFrame, scaleFactor, refreshRate, isPrimary);
    }

    private ScreenBounds getRect(MemorySegment target, MemorySegment selector) throws Throwable {
        // CGRect is returned as a struct; FFM requires SegmentAllocator for struct returns
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
            ),
            Linker.Option.firstVariadicArg(2)
        );

        // Pass arena as SegmentAllocator for struct return value allocation
        MemorySegment rect = (MemorySegment) msgSendRect.invokeExact(
            (SegmentAllocator) arena,
            target,
            selector
        );

        double x = rect.get(ValueLayout.JAVA_DOUBLE, CGRECT_X);
        double y = rect.get(ValueLayout.JAVA_DOUBLE, CGRECT_Y);
        double width = rect.get(ValueLayout.JAVA_DOUBLE, CGRECT_WIDTH);
        double height = rect.get(ValueLayout.JAVA_DOUBLE, CGRECT_HEIGHT);

        return new ScreenBounds(x, y, width, height);
    }

    private String getLocalizedName(MemorySegment screen) throws Throwable {
        MemorySegment nsName = msgSend(screen, SEL_LOCALIZED_NAME);
        if (nsName.address() == 0) {
            return "Unknown";
        }
        MemorySegment utf8 = msgSend(nsName, SEL_UTF8_STRING);
        if (utf8.address() == 0) {
            return "Unknown";
        }
        return utf8.reinterpret(Integer.MAX_VALUE).getString(0);
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

    private long msgSendLong(MemorySegment target, MemorySegment selector) throws Throwable {
        return (long) OBJC_MSG_SEND_LONG.invokeExact(target, selector);
    }

    private double msgSendDouble(MemorySegment target, MemorySegment selector) throws Throwable {
        return (double) OBJC_MSG_SEND_DOUBLE.invokeExact(target, selector);
    }
}
