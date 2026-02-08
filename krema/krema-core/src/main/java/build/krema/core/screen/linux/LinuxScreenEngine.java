package build.krema.core.screen.linux;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

import build.krema.core.platform.linux.GtkBindings;
import build.krema.core.screen.CursorPosition;
import build.krema.core.screen.ScreenBounds;
import build.krema.core.screen.ScreenEngine;
import build.krema.core.screen.ScreenInfo;

/**
 * Linux ScreenEngine implementation using GDK3 monitor APIs via FFM.
 */
public final class LinuxScreenEngine implements ScreenEngine {

    private final Arena arena = Arena.ofAuto();

    @Override
    public List<ScreenInfo> getAllScreens() {
        try {
            MemorySegment display = (MemorySegment) GtkBindings.GDK_DISPLAY_GET_DEFAULT.invokeExact();
            if (display.address() == 0) return List.of();

            int count = (int) GtkBindings.GDK_DISPLAY_GET_N_MONITORS.invokeExact(display);
            MemorySegment primaryMonitor = (MemorySegment) GtkBindings.GDK_DISPLAY_GET_PRIMARY_MONITOR.invokeExact(display);

            List<ScreenInfo> screens = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                MemorySegment monitor = (MemorySegment) GtkBindings.GDK_DISPLAY_GET_MONITOR.invokeExact(display, i);
                if (monitor.address() == 0) continue;

                boolean isPrimary = monitor.address() == primaryMonitor.address();
                screens.add(buildScreenInfo(monitor, isPrimary));
            }
            return screens;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get all screens", t);
        }
    }

    @Override
    public ScreenInfo getPrimaryScreen() {
        try {
            MemorySegment display = (MemorySegment) GtkBindings.GDK_DISPLAY_GET_DEFAULT.invokeExact();
            if (display.address() == 0) return null;

            MemorySegment monitor = (MemorySegment) GtkBindings.GDK_DISPLAY_GET_PRIMARY_MONITOR.invokeExact(display);
            if (monitor.address() == 0) {
                // Fallback to first monitor if no primary is set
                monitor = (MemorySegment) GtkBindings.GDK_DISPLAY_GET_MONITOR.invokeExact(display, 0);
            }
            if (monitor.address() == 0) return null;

            return buildScreenInfo(monitor, true);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get primary screen", t);
        }
    }

    @Override
    public CursorPosition getCursorPosition() {
        try {
            MemorySegment display = (MemorySegment) GtkBindings.GDK_DISPLAY_GET_DEFAULT.invokeExact();
            if (display.address() == 0) return new CursorPosition(0, 0);

            MemorySegment seat = (MemorySegment) GtkBindings.GDK_DISPLAY_GET_DEFAULT_SEAT.invokeExact(display);
            if (seat.address() == 0) return new CursorPosition(0, 0);

            MemorySegment pointer = (MemorySegment) GtkBindings.GDK_SEAT_GET_POINTER.invokeExact(seat);
            if (pointer.address() == 0) return new CursorPosition(0, 0);

            MemorySegment screenOut = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment xOut = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment yOut = arena.allocate(ValueLayout.JAVA_INT);

            GtkBindings.GDK_DEVICE_GET_POSITION.invokeExact(pointer, screenOut, xOut, yOut);

            return new CursorPosition(
                xOut.get(ValueLayout.JAVA_INT, 0),
                yOut.get(ValueLayout.JAVA_INT, 0)
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get cursor position", t);
        }
    }

    @Override
    public ScreenInfo getScreenAtPoint(double x, double y) {
        try {
            MemorySegment display = (MemorySegment) GtkBindings.GDK_DISPLAY_GET_DEFAULT.invokeExact();
            if (display.address() == 0) return null;

            MemorySegment monitor = (MemorySegment) GtkBindings.GDK_DISPLAY_GET_MONITOR_AT_POINT.invokeExact(
                display, (int) x, (int) y
            );
            if (monitor.address() == 0) return null;

            MemorySegment primaryMonitor = (MemorySegment) GtkBindings.GDK_DISPLAY_GET_PRIMARY_MONITOR.invokeExact(display);
            boolean isPrimary = monitor.address() == primaryMonitor.address();

            return buildScreenInfo(monitor, isPrimary);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get screen at point", t);
        }
    }

    private ScreenInfo buildScreenInfo(MemorySegment monitor, boolean isPrimary) throws Throwable {
        MemorySegment geometry = arena.allocate(GtkBindings.GDK_RECTANGLE);
        GtkBindings.GDK_MONITOR_GET_GEOMETRY.invokeExact(monitor, geometry);

        MemorySegment workarea = arena.allocate(GtkBindings.GDK_RECTANGLE);
        GtkBindings.GDK_MONITOR_GET_WORKAREA.invokeExact(monitor, workarea);

        int scaleFactor = (int) GtkBindings.GDK_MONITOR_GET_SCALE_FACTOR.invokeExact(monitor);
        int refreshRateMhz = (int) GtkBindings.GDK_MONITOR_GET_REFRESH_RATE.invokeExact(monitor);
        double refreshRate = refreshRateMhz / 1000.0;

        MemorySegment modelPtr = (MemorySegment) GtkBindings.GDK_MONITOR_GET_MODEL.invokeExact(monitor);
        String name;
        if (modelPtr.address() != 0) {
            name = modelPtr.reinterpret(Integer.MAX_VALUE).getString(0);
        } else {
            name = "Unknown";
        }

        ScreenBounds frame = new ScreenBounds(
            geometry.get(ValueLayout.JAVA_INT, 0),
            geometry.get(ValueLayout.JAVA_INT, 4),
            geometry.get(ValueLayout.JAVA_INT, 8),
            geometry.get(ValueLayout.JAVA_INT, 12)
        );

        ScreenBounds visibleFrame = new ScreenBounds(
            workarea.get(ValueLayout.JAVA_INT, 0),
            workarea.get(ValueLayout.JAVA_INT, 4),
            workarea.get(ValueLayout.JAVA_INT, 8),
            workarea.get(ValueLayout.JAVA_INT, 12)
        );

        return new ScreenInfo(name, frame, visibleFrame, scaleFactor, refreshRate, isPrimary);
    }
}
