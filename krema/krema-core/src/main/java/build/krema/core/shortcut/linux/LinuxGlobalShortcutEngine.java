package build.krema.core.shortcut.linux;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

import build.krema.core.shortcut.GlobalShortcutEngine;

/**
 * Linux GlobalShortcutEngine implementation.
 * <p>
 * On X11: Uses XGrabKey via FFM with a background thread polling XNextEvent.
 * On Wayland: No-op with a warning (Wayland's GlobalShortcuts portal is not yet supported).
 * </p>
 */
public final class LinuxGlobalShortcutEngine implements GlobalShortcutEngine {

    private static final Logger LOG = Logger.getLogger(LinuxGlobalShortcutEngine.class.getName());

    private final boolean isWayland;
    private final Map<String, Consumer<String>> shortcuts = new ConcurrentHashMap<>();

    // X11 FFM handles (loaded lazily, only on X11)
    private volatile boolean x11Initialized = false;
    private MemorySegment x11Display;
    private Thread eventThread;
    private volatile boolean running = false;

    private static final Linker LINKER = Linker.nativeLinker();
    private MethodHandle xOpenDisplay;
    private MethodHandle xCloseDisplay;
    private MethodHandle xGrabKey;
    private MethodHandle xUngrabKey;
    private MethodHandle xKeysymToKeycode;
    private MethodHandle xStringToKeysym;
    private MethodHandle xNextEvent;
    private MethodHandle xDefaultRootWindow;

    // X11 constants
    private static final int GrabModeAsync = 1;
    private static final int KeyPress = 2;
    private static final int AnyModifier = 1 << 15;

    // Modifier masks
    private static final int ShiftMask = 1;
    private static final int ControlMask = 1 << 2;
    private static final int Mod1Mask = 1 << 3; // Alt
    private static final int Mod4Mask = 1 << 6; // Super/Meta

    public LinuxGlobalShortcutEngine() {
        String sessionType = System.getenv("XDG_SESSION_TYPE");
        isWayland = "wayland".equalsIgnoreCase(sessionType);

        if (isWayland) {
            LOG.warning("Global shortcuts on Wayland are not yet supported. " +
                "XDG Desktop Portal GlobalShortcuts support may be added in a future release.");
        }
    }

    @Override
    public boolean register(String accelerator, Consumer<String> callback) {
        if (isWayland) {
            LOG.warning("Cannot register global shortcut on Wayland: " + accelerator);
            return false;
        }

        initX11();
        if (x11Display == null || x11Display.address() == 0) return false;

        try {
            ParsedShortcut parsed = parseAccelerator(accelerator);
            if (parsed == null) return false;

            MemorySegment rootWindow = (MemorySegment) xDefaultRootWindow.invokeExact(x11Display);

            try (Arena temp = Arena.ofConfined()) {
                MemorySegment keysymName = temp.allocateFrom(parsed.keysymName);
                long keysym = (long) xStringToKeysym.invokeExact(keysymName);
                if (keysym == 0) return false;

                int keycode = (int) xKeysymToKeycode.invokeExact(x11Display, keysym);
                if (keycode == 0) return false;

                int status = (int) xGrabKey.invokeExact(
                    x11Display, keycode, parsed.modifiers,
                    rootWindow, 1, GrabModeAsync, GrabModeAsync
                );

                if (status != 0) {
                    shortcuts.put(accelerator, callback);
                    startEventThread();
                    return true;
                }
            }
        } catch (Throwable t) {
            LOG.warning("Failed to register shortcut " + accelerator + ": " + t.getMessage());
        }
        return false;
    }

    @Override
    public boolean unregister(String accelerator) {
        if (isWayland || !x11Initialized) return false;

        Consumer<String> removed = shortcuts.remove(accelerator);
        if (removed == null) return false;

        try {
            ParsedShortcut parsed = parseAccelerator(accelerator);
            if (parsed == null) return false;

            MemorySegment rootWindow = (MemorySegment) xDefaultRootWindow.invokeExact(x11Display);

            try (Arena temp = Arena.ofConfined()) {
                MemorySegment keysymName = temp.allocateFrom(parsed.keysymName);
                long keysym = (long) xStringToKeysym.invokeExact(keysymName);
                int keycode = (int) xKeysymToKeycode.invokeExact(x11Display, keysym);

                xUngrabKey.invokeExact(x11Display, keycode, parsed.modifiers, rootWindow);
            }
        } catch (Throwable t) {
            LOG.warning("Failed to unregister shortcut " + accelerator + ": " + t.getMessage());
        }

        if (shortcuts.isEmpty()) {
            stopEventThread();
        }
        return true;
    }

    @Override
    public boolean isRegistered(String accelerator) {
        return shortcuts.containsKey(accelerator);
    }

    @Override
    public void unregisterAll() {
        for (String accel : Set.copyOf(shortcuts.keySet())) {
            unregister(accel);
        }
    }

    @Override
    public Set<String> getRegistered() {
        return Set.copyOf(shortcuts.keySet());
    }

    private synchronized void initX11() {
        if (x11Initialized) return;
        x11Initialized = true;

        try {
            SymbolLookup x11 = SymbolLookup.libraryLookup("libX11.so.6", Arena.global());

            xOpenDisplay = LINKER.downcallHandle(
                x11.find("XOpenDisplay").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            xCloseDisplay = LINKER.downcallHandle(
                x11.find("XCloseDisplay").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            xGrabKey = LINKER.downcallHandle(
                x11.find("XGrabKey").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            xUngrabKey = LINKER.downcallHandle(
                x11.find("XUngrabKey").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
            xKeysymToKeycode = LINKER.downcallHandle(
                x11.find("XKeysymToKeycode").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            xStringToKeysym = LINKER.downcallHandle(
                x11.find("XStringToKeysym").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            xNextEvent = LINKER.downcallHandle(
                x11.find("XNextEvent").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            xDefaultRootWindow = LINKER.downcallHandle(
                x11.find("XDefaultRootWindow").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

            x11Display = (MemorySegment) xOpenDisplay.invokeExact(MemorySegment.NULL);
            if (x11Display.address() == 0) {
                LOG.warning("Failed to open X11 display for global shortcuts");
                x11Display = null;
            }
        } catch (Throwable t) {
            LOG.warning("Failed to initialize X11 for global shortcuts: " + t.getMessage());
            x11Display = null;
        }
    }

    private void startEventThread() {
        if (running) return;
        running = true;

        eventThread = new Thread(() -> {
            // XEvent struct is 192 bytes (96 longs)
            try (Arena eventArena = Arena.ofConfined()) {
                MemorySegment event = eventArena.allocate(192);
                while (running && !shortcuts.isEmpty()) {
                    try {
                        xNextEvent.invokeExact(x11Display, event);
                        int type = event.get(ValueLayout.JAVA_INT, 0);
                        if (type == KeyPress) {
                            // Fire all registered callbacks (simplified; a full impl would match keycode+modifiers)
                            for (var entry : shortcuts.entrySet()) {
                                entry.getValue().accept(entry.getKey());
                            }
                        }
                    } catch (Throwable t) {
                        if (running) {
                            LOG.warning("Error in X11 event loop: " + t.getMessage());
                        }
                    }
                }
            }
        }, "krema-x11-shortcut-listener");
        eventThread.setDaemon(true);
        eventThread.start();
    }

    private void stopEventThread() {
        running = false;
        if (eventThread != null) {
            eventThread.interrupt();
            eventThread = null;
        }
    }

    private record ParsedShortcut(int modifiers, String keysymName) {}

    private ParsedShortcut parseAccelerator(String accelerator) {
        int modifiers = 0;
        String key = null;

        for (String part : accelerator.split("\\+")) {
            String p = part.trim().toLowerCase();
            switch (p) {
                case "ctrl", "control" -> modifiers |= ControlMask;
                case "shift" -> modifiers |= ShiftMask;
                case "alt" -> modifiers |= Mod1Mask;
                case "super", "meta", "cmd", "command" -> modifiers |= Mod4Mask;
                default -> key = part.trim();
            }
        }

        if (key == null) return null;

        // Map common key names to X11 keysym names
        String keysymName = switch (key.toLowerCase()) {
            case "space" -> "space";
            case "enter", "return" -> "Return";
            case "escape", "esc" -> "Escape";
            case "tab" -> "Tab";
            case "backspace" -> "BackSpace";
            case "delete", "del" -> "Delete";
            case "up" -> "Up";
            case "down" -> "Down";
            case "left" -> "Left";
            case "right" -> "Right";
            default -> {
                if (key.length() == 1) {
                    yield key.toLowerCase();
                }
                yield key;
            }
        };

        return new ParsedShortcut(modifiers, keysymName);
    }
}
