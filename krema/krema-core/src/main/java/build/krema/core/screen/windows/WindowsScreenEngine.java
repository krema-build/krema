package build.krema.core.screen.windows;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

import build.krema.core.platform.windows.Win32Bindings;
import build.krema.core.screen.CursorPosition;
import build.krema.core.screen.ScreenBounds;
import build.krema.core.screen.ScreenEngine;
import build.krema.core.screen.ScreenInfo;

/**
 * Windows ScreenEngine implementation using Win32 EnumDisplayMonitors via FFM.
 */
public final class WindowsScreenEngine implements ScreenEngine {

    private static final Linker LINKER = Linker.nativeLinker();

    @Override
    public List<ScreenInfo> getAllScreens() {
        List<ScreenInfo> screens = new ArrayList<>();
        try (Arena arena = Arena.ofConfined()) {
            // Use a list stored in thread-local context since the callback is invoked synchronously
            FunctionDescriptor callbackDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // HMONITOR
                ValueLayout.ADDRESS, // HDC
                ValueLayout.ADDRESS, // LPRECT
                ValueLayout.JAVA_LONG // LPARAM
            );

            MethodHandle callback = MethodHandles.lookup().findStatic(
                WindowsScreenEngine.class,
                "monitorEnumCallback",
                MethodType.methodType(int.class, List.class, MemorySegment.class,
                    MemorySegment.class, MemorySegment.class, long.class)
            ).bindTo(screens);

            MemorySegment callbackStub = LINKER.upcallStub(callback, callbackDesc, arena);

            Win32Bindings.ENUM_DISPLAY_MONITORS.invokeExact(
                MemorySegment.NULL, MemorySegment.NULL, callbackStub, 0L
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to enumerate monitors", t);
        }
        return screens;
    }

    @SuppressWarnings("unused")
    private static int monitorEnumCallback(List<ScreenInfo> screens,
                                            MemorySegment hMonitor,
                                            MemorySegment hdc,
                                            MemorySegment lpRect,
                                            long lParam) {
        try (Arena arena = Arena.ofConfined()) {
            ScreenInfo info = buildScreenInfo(arena, hMonitor);
            if (info != null) {
                screens.add(info);
            }
        } catch (Throwable t) {
            // Continue enumeration even if one monitor fails
        }
        return 1; // TRUE = continue enumeration
    }

    @Override
    public ScreenInfo getPrimaryScreen() {
        List<ScreenInfo> all = getAllScreens();
        return all.stream()
            .filter(ScreenInfo::isPrimary)
            .findFirst()
            .orElse(all.isEmpty() ? null : all.get(0));
    }

    @Override
    public CursorPosition getCursorPosition() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment point = arena.allocate(Win32Bindings.POINT);
            Win32Bindings.GET_CURSOR_POS.invokeExact(point);
            int x = point.get(ValueLayout.JAVA_INT, 0);
            int y = point.get(ValueLayout.JAVA_INT, 4);
            return new CursorPosition(x, y);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get cursor position", t);
        }
    }

    @Override
    public ScreenInfo getScreenAtPoint(double x, double y) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment hMonitor = (MemorySegment) Win32Bindings.MONITOR_FROM_POINT.invokeExact(
                (int) x, (int) y, Win32Bindings.MONITOR_DEFAULTTONEAREST
            );
            if (hMonitor.address() == 0) return null;
            return buildScreenInfo(arena, hMonitor);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get screen at point", t);
        }
    }

    private static ScreenInfo buildScreenInfo(Arena arena, MemorySegment hMonitor) throws Throwable {
        MemorySegment monitorInfo = arena.allocate(Win32Bindings.MONITORINFO);
        monitorInfo.set(ValueLayout.JAVA_INT, 0, (int) Win32Bindings.MONITORINFO.byteSize());

        int result = (int) Win32Bindings.GET_MONITOR_INFO_W.invokeExact(hMonitor, monitorInfo);
        if (result == 0) return null;

        // rcMonitor: offset 4 (after cbSize)
        int monLeft = monitorInfo.get(ValueLayout.JAVA_INT, 4);
        int monTop = monitorInfo.get(ValueLayout.JAVA_INT, 8);
        int monRight = monitorInfo.get(ValueLayout.JAVA_INT, 12);
        int monBottom = monitorInfo.get(ValueLayout.JAVA_INT, 16);

        // rcWork: offset 20
        int workLeft = monitorInfo.get(ValueLayout.JAVA_INT, 20);
        int workTop = monitorInfo.get(ValueLayout.JAVA_INT, 24);
        int workRight = monitorInfo.get(ValueLayout.JAVA_INT, 28);
        int workBottom = monitorInfo.get(ValueLayout.JAVA_INT, 32);

        // dwFlags: offset 36
        int flags = monitorInfo.get(ValueLayout.JAVA_INT, 36);
        boolean isPrimary = (flags & Win32Bindings.MONITORINFOF_PRIMARY) != 0;

        ScreenBounds frame = new ScreenBounds(
            monLeft, monTop,
            monRight - monLeft, monBottom - monTop
        );
        ScreenBounds workarea = new ScreenBounds(
            workLeft, workTop,
            workRight - workLeft, workBottom - workTop
        );

        double scaleFactor = 1.0;
        if (Win32Bindings.GET_DPI_FOR_MONITOR != null) {
            try {
                MemorySegment dpiX = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment dpiY = arena.allocate(ValueLayout.JAVA_INT);
                int hr = (int) Win32Bindings.GET_DPI_FOR_MONITOR.invokeExact(
                    hMonitor, Win32Bindings.MDT_EFFECTIVE_DPI, dpiX, dpiY
                );
                if (hr == 0) { // S_OK
                    scaleFactor = dpiX.get(ValueLayout.JAVA_INT, 0) / 96.0;
                }
            } catch (Throwable ignored) {
                // Fall back to 1.0
            }
        }
        // Default refresh rate (Win32 MONITORINFO doesn't include it)
        double refreshRate = 60.0;

        String name = isPrimary ? "Primary Monitor" : "Monitor";

        return new ScreenInfo(name, frame, workarea, scaleFactor, refreshRate, isPrimary);
    }
}
