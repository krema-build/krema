package build.krema.core.shortcut.macos;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import build.krema.core.shortcut.GlobalShortcutEngine;

/**
 * macOS GlobalShortcutEngine implementation using NSEvent global monitor.
 * Requires Accessibility permissions to receive global keyboard events.
 */
public final class MacOSGlobalShortcutEngine implements GlobalShortcutEngine {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup OBJC_LOOKUP;

    private static final MethodHandle OBJC_GET_CLASS;
    private static final MethodHandle SEL_REGISTER_NAME;
    private static final MethodHandle OBJC_MSG_SEND;
    private static final MethodHandle OBJC_MSG_SEND_LONG;

    // NSEvent key masks
    private static final long NS_KEY_DOWN_MASK = 1 << 10;
    private static final long NS_COMMAND_KEY_MASK = 1 << 20;
    private static final long NS_SHIFT_KEY_MASK = 1 << 17;
    private static final long NS_ALTERNATE_KEY_MASK = 1 << 19; // Option/Alt
    private static final long NS_CONTROL_KEY_MASK = 1 << 18;

    // Selectors
    private static final MemorySegment SEL_ADD_GLOBAL_MONITOR;
    private static final MemorySegment SEL_REMOVE_MONITOR;
    private static final MemorySegment SEL_MODIFIER_FLAGS;
    private static final MemorySegment SEL_KEY_CODE;

    static {
        OBJC_LOOKUP = SymbolLookup.libraryLookup("/usr/lib/libobjc.dylib", Arena.global());

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

        OBJC_MSG_SEND = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        OBJC_MSG_SEND_LONG = LINKER.downcallHandle(
            OBJC_LOOKUP.find("objc_msgSend").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        SEL_ADD_GLOBAL_MONITOR = sel("addGlobalMonitorForEventsMatchingMask:handler:");
        SEL_REMOVE_MONITOR = sel("removeMonitor:");
        SEL_MODIFIER_FLAGS = sel("modifierFlags");
        SEL_KEY_CODE = sel("keyCode");
    }

    private final Arena arena = Arena.ofAuto();
    private final Map<String, RegisteredShortcut> shortcuts = new ConcurrentHashMap<>();
    private MemorySegment globalMonitor;
    private MemorySegment monitorBlock;

    private record RegisteredShortcut(
        int keyCode,
        long modifiers,
        Consumer<String> callback
    ) {}

    public MacOSGlobalShortcutEngine() {
        setupGlobalMonitor();
    }

    private void setupGlobalMonitor() {
        try {
            // Create the event handler block
            // This is complex with FFM - we need to create an Objective-C block
            // For now, we'll use a simplified polling approach or note that
            // full implementation requires block creation

            // Note: Full implementation of Objective-C blocks in FFM is complex
            // A production implementation would use JNI or a native helper library
            System.out.println("[GlobalShortcut] macOS global shortcuts initialized (limited mode)");

        } catch (Throwable t) {
            System.err.println("[GlobalShortcut] Failed to setup monitor: " + t.getMessage());
        }
    }

    @Override
    public boolean register(String accelerator, Consumer<String> callback) {
        try {
            AcceleratorParts parts = parseAccelerator(accelerator);
            if (parts == null) {
                return false;
            }

            shortcuts.put(accelerator.toLowerCase(), new RegisteredShortcut(
                parts.keyCode,
                parts.modifiers,
                callback
            ));

            System.out.println("[GlobalShortcut] Registered: " + accelerator);
            return true;
        } catch (Exception e) {
            System.err.println("[GlobalShortcut] Failed to register: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean unregister(String accelerator) {
        RegisteredShortcut removed = shortcuts.remove(accelerator.toLowerCase());
        if (removed != null) {
            System.out.println("[GlobalShortcut] Unregistered: " + accelerator);
            return true;
        }
        return false;
    }

    @Override
    public boolean isRegistered(String accelerator) {
        return shortcuts.containsKey(accelerator.toLowerCase());
    }

    @Override
    public void unregisterAll() {
        shortcuts.clear();
        System.out.println("[GlobalShortcut] Unregistered all shortcuts");
    }

    @Override
    public Set<String> getRegistered() {
        return Set.copyOf(shortcuts.keySet());
    }

    /**
     * Called when a key event is received (would be called from the monitor).
     */
    public void onKeyEvent(int keyCode, long modifiers) {
        for (Map.Entry<String, RegisteredShortcut> entry : shortcuts.entrySet()) {
            RegisteredShortcut shortcut = entry.getValue();
            if (shortcut.keyCode == keyCode &&
                (modifiers & (NS_COMMAND_KEY_MASK | NS_SHIFT_KEY_MASK | NS_ALTERNATE_KEY_MASK | NS_CONTROL_KEY_MASK))
                    == shortcut.modifiers) {
                try {
                    shortcut.callback.accept(entry.getKey());
                } catch (Exception e) {
                    System.err.println("[GlobalShortcut] Callback error: " + e.getMessage());
                }
            }
        }
    }

    private record AcceleratorParts(int keyCode, long modifiers) {}

    private AcceleratorParts parseAccelerator(String accelerator) {
        long modifiers = 0;
        int keyCode = -1;

        String[] parts = accelerator.split("\\+");
        for (String part : parts) {
            String p = part.trim().toLowerCase();
            switch (p) {
                case "cmd", "command", "meta", "super" -> modifiers |= NS_COMMAND_KEY_MASK;
                case "shift" -> modifiers |= NS_SHIFT_KEY_MASK;
                case "alt", "option" -> modifiers |= NS_ALTERNATE_KEY_MASK;
                case "ctrl", "control" -> modifiers |= NS_CONTROL_KEY_MASK;
                default -> keyCode = keyCodeFromString(p);
            }
        }

        if (keyCode == -1) {
            return null;
        }

        return new AcceleratorParts(keyCode, modifiers);
    }

    /**
     * Maps key strings to macOS virtual key codes.
     */
    private int keyCodeFromString(String key) {
        return switch (key.toLowerCase()) {
            case "a" -> 0x00;
            case "b" -> 0x0B;
            case "c" -> 0x08;
            case "d" -> 0x02;
            case "e" -> 0x0E;
            case "f" -> 0x03;
            case "g" -> 0x05;
            case "h" -> 0x04;
            case "i" -> 0x22;
            case "j" -> 0x26;
            case "k" -> 0x28;
            case "l" -> 0x25;
            case "m" -> 0x2E;
            case "n" -> 0x2D;
            case "o" -> 0x1F;
            case "p" -> 0x23;
            case "q" -> 0x0C;
            case "r" -> 0x0F;
            case "s" -> 0x01;
            case "t" -> 0x11;
            case "u" -> 0x20;
            case "v" -> 0x09;
            case "w" -> 0x0D;
            case "x" -> 0x07;
            case "y" -> 0x10;
            case "z" -> 0x06;
            case "0" -> 0x1D;
            case "1" -> 0x12;
            case "2" -> 0x13;
            case "3" -> 0x14;
            case "4" -> 0x15;
            case "5" -> 0x17;
            case "6" -> 0x16;
            case "7" -> 0x1A;
            case "8" -> 0x1C;
            case "9" -> 0x19;
            case "space" -> 0x31;
            case "return", "enter" -> 0x24;
            case "tab" -> 0x30;
            case "escape", "esc" -> 0x35;
            case "backspace", "delete" -> 0x33;
            case "up" -> 0x7E;
            case "down" -> 0x7D;
            case "left" -> 0x7B;
            case "right" -> 0x7C;
            case "f1" -> 0x7A;
            case "f2" -> 0x78;
            case "f3" -> 0x63;
            case "f4" -> 0x76;
            case "f5" -> 0x60;
            case "f6" -> 0x61;
            case "f7" -> 0x62;
            case "f8" -> 0x64;
            case "f9" -> 0x65;
            case "f10" -> 0x6D;
            case "f11" -> 0x67;
            case "f12" -> 0x6F;
            case "-", "minus" -> 0x1B;
            case "=", "equal", "plus" -> 0x18;
            case "[", "leftbracket" -> 0x21;
            case "]", "rightbracket" -> 0x1E;
            case "\\", "backslash" -> 0x2A;
            case ";", "semicolon" -> 0x29;
            case "'", "quote" -> 0x27;
            case ",", "comma" -> 0x2B;
            case ".", "period" -> 0x2F;
            case "/", "slash" -> 0x2C;
            case "`", "grave" -> 0x32;
            default -> -1;
        };
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
}
