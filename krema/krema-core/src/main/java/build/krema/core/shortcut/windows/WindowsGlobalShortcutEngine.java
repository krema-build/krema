package build.krema.core.shortcut.windows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import build.krema.core.platform.windows.Win32Bindings;
import build.krema.core.shortcut.GlobalShortcutEngine;

/**
 * Windows GlobalShortcutEngine implementation using Win32 RegisterHotKey/UnregisterHotKey.
 * Runs a background thread polling for WM_HOTKEY messages.
 */
public final class WindowsGlobalShortcutEngine implements GlobalShortcutEngine {

    private static final int PM_REMOVE = 0x0001;

    // MSG struct: { HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam, DWORD time, POINT pt }
    // Size on 64-bit: 8 + 4 + 8 + 8 + 4 + 8 + 4(padding) = 48 bytes (conservative: 64)
    private static final long MSG_SIZE = 64;

    private final Map<String, ShortcutEntry> shortcuts = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread eventThread;

    private record ShortcutEntry(int id, int modifiers, int vk, Consumer<String> callback) {}

    @Override
    public boolean register(String accelerator, Consumer<String> callback) {
        if (shortcuts.containsKey(accelerator)) return false;

        int[] parsed = parseAccelerator(accelerator);
        if (parsed == null) return false;

        int modifiers = parsed[0];
        int vk = parsed[1];
        int id = nextId.getAndIncrement();

        try {
            int result = (int) Win32Bindings.REGISTER_HOT_KEY.invokeExact(
                MemorySegment.NULL, id, modifiers | Win32Bindings.MOD_NOREPEAT, vk
            );
            if (result == 0) return false;

            shortcuts.put(accelerator, new ShortcutEntry(id, modifiers, vk, callback));
            startEventThread();
            return true;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to register hotkey: " + accelerator, t);
        }
    }

    @Override
    public boolean unregister(String accelerator) {
        ShortcutEntry entry = shortcuts.remove(accelerator);
        if (entry == null) return false;

        try {
            Win32Bindings.UNREGISTER_HOT_KEY.invokeExact(MemorySegment.NULL, entry.id());
        } catch (Throwable t) {
            throw new RuntimeException("Failed to unregister hotkey: " + accelerator, t);
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
        for (String key : Set.copyOf(shortcuts.keySet())) {
            unregister(key);
        }
    }

    @Override
    public Set<String> getRegistered() {
        return Set.copyOf(shortcuts.keySet());
    }

    private void startEventThread() {
        if (running.compareAndSet(false, true)) {
            eventThread = new Thread(this::eventLoop, "krema-win32-hotkey");
            eventThread.setDaemon(true);
            eventThread.start();
        }
    }

    private void stopEventThread() {
        running.set(false);
        if (eventThread != null) {
            eventThread.interrupt();
            eventThread = null;
        }
    }

    private void eventLoop() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment msg = arena.allocate(MSG_SIZE);

            while (running.get()) {
                int result = (int) Win32Bindings.PEEK_MESSAGE_W.invokeExact(
                    msg, MemorySegment.NULL,
                    Win32Bindings.WM_HOTKEY, Win32Bindings.WM_HOTKEY,
                    PM_REMOVE
                );

                if (result != 0) {
                    // wParam is the hotkey ID, at offset 8 (after HWND) + 4 (after message) on 64-bit
                    // Simplified: read the wParam field
                    long wParam = msg.get(ValueLayout.JAVA_LONG, 16);
                    int hotkeyId = (int) wParam;

                    for (var entry : shortcuts.values()) {
                        if (entry.id() == hotkeyId) {
                            try {
                                entry.callback().accept(findAcceleratorById(hotkeyId));
                            } catch (Exception ignored) {
                                // Don't crash event loop on callback error
                            }
                            break;
                        }
                    }
                } else {
                    // No message, sleep briefly
                    Thread.sleep(50);
                }
            }
        } catch (InterruptedException ignored) {
            // Thread shutdown
        } catch (Throwable t) {
            throw new RuntimeException("Hotkey event loop error", t);
        }
    }

    private String findAcceleratorById(int id) {
        for (var e : shortcuts.entrySet()) {
            if (e.getValue().id() == id) return e.getKey();
        }
        return "unknown";
    }

    /**
     * Parses an accelerator string like "Ctrl+Alt+A" into [modifiers, virtualKeyCode].
     */
    private static int[] parseAccelerator(String accelerator) {
        if (accelerator == null || accelerator.isBlank()) return null;

        int modifiers = 0;
        int vk = 0;
        String[] parts = accelerator.split("\\+");

        for (String part : parts) {
            String key = part.trim().toUpperCase();
            switch (key) {
                case "CTRL", "CONTROL" -> modifiers |= Win32Bindings.MOD_CONTROL;
                case "ALT" -> modifiers |= Win32Bindings.MOD_ALT;
                case "SHIFT" -> modifiers |= Win32Bindings.MOD_SHIFT;
                case "SUPER", "WIN", "CMD", "META" -> modifiers |= Win32Bindings.MOD_WIN;
                case "SPACE" -> vk = 0x20;
                case "ENTER", "RETURN" -> vk = 0x0D;
                case "TAB" -> vk = 0x09;
                case "ESCAPE", "ESC" -> vk = 0x1B;
                case "BACKSPACE" -> vk = 0x08;
                case "DELETE", "DEL" -> vk = 0x2E;
                case "INSERT", "INS" -> vk = 0x2D;
                case "HOME" -> vk = 0x24;
                case "END" -> vk = 0x23;
                case "PAGEUP" -> vk = 0x21;
                case "PAGEDOWN" -> vk = 0x22;
                case "UP" -> vk = 0x26;
                case "DOWN" -> vk = 0x28;
                case "LEFT" -> vk = 0x25;
                case "RIGHT" -> vk = 0x27;
                case "F1" -> vk = 0x70;
                case "F2" -> vk = 0x71;
                case "F3" -> vk = 0x72;
                case "F4" -> vk = 0x73;
                case "F5" -> vk = 0x74;
                case "F6" -> vk = 0x75;
                case "F7" -> vk = 0x76;
                case "F8" -> vk = 0x77;
                case "F9" -> vk = 0x78;
                case "F10" -> vk = 0x79;
                case "F11" -> vk = 0x7A;
                case "F12" -> vk = 0x7B;
                default -> {
                    if (key.length() == 1) {
                        char c = key.charAt(0);
                        if (c >= 'A' && c <= 'Z') {
                            vk = c; // VK_A to VK_Z
                        } else if (c >= '0' && c <= '9') {
                            vk = c; // VK_0 to VK_9
                        }
                    }
                }
            }
        }

        return vk != 0 ? new int[]{ modifiers, vk } : null;
    }
}
