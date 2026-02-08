package build.krema.core.platform.windows;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Shared Win32 FFM bindings for Windows platform engines.
 * Loaded once and reused by WindowsWindowEngine, WindowsScreenEngine, etc.
 *
 * <p>Binds to user32.dll, kernel32.dll, and shell32.dll.</p>
 */
public final class Win32Bindings {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup USER32;
    private static final SymbolLookup KERNEL32;
    private static final SymbolLookup SHELL32;
    private static final SymbolLookup SHCORE; // nullable — Windows 8.1+

    // RECT struct layout: { LONG left, top, right, bottom }
    public static final StructLayout RECT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("left"),
        ValueLayout.JAVA_INT.withName("top"),
        ValueLayout.JAVA_INT.withName("right"),
        ValueLayout.JAVA_INT.withName("bottom")
    );

    // POINT struct layout: { LONG x, y }
    public static final StructLayout POINT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("x"),
        ValueLayout.JAVA_INT.withName("y")
    );

    // MONITORINFO struct layout (40 bytes)
    // { DWORD cbSize, RECT rcMonitor, RECT rcWork, DWORD dwFlags }
    public static final StructLayout MONITORINFO = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("cbSize"),
        RECT.withName("rcMonitor"),
        RECT.withName("rcWork"),
        ValueLayout.JAVA_INT.withName("dwFlags")
    );

    // MINMAXINFO struct layout (used with WM_GETMINMAXINFO)
    public static final StructLayout MINMAXINFO = MemoryLayout.structLayout(
        POINT.withName("ptReserved"),
        POINT.withName("ptMaxSize"),
        POINT.withName("ptMaxPosition"),
        POINT.withName("ptMinTrackSize"),
        POINT.withName("ptMaxTrackSize")
    );

    // --- ShowWindow constants ---
    public static final int SW_HIDE = 0;
    public static final int SW_SHOWNORMAL = 1;
    public static final int SW_SHOWMINIMIZED = 2;
    public static final int SW_SHOWMAXIMIZED = 3;
    public static final int SW_SHOW = 5;
    public static final int SW_MINIMIZE = 6;
    public static final int SW_RESTORE = 9;

    // --- SetWindowPos constants ---
    public static final long HWND_TOPMOST = -1L;
    public static final long HWND_NOTOPMOST = -2L;
    public static final int SWP_NOMOVE = 0x0002;
    public static final int SWP_NOSIZE = 0x0001;
    public static final int SWP_NOZORDER = 0x0004;
    public static final int SWP_SHOWWINDOW = 0x0040;
    public static final int SWP_NOACTIVATE = 0x0010;

    // --- GetWindowLong / SetWindowLong constants ---
    public static final int GWL_STYLE = -16;
    public static final int GWL_EXSTYLE = -20;
    public static final int GWLP_WNDPROC = -4;

    // --- Window style constants ---
    public static final int WS_OVERLAPPEDWINDOW = 0x00CF0000;
    public static final int WS_THICKFRAME = 0x00040000;
    public static final int WS_MAXIMIZEBOX = 0x00010000;
    public static final int WS_MINIMIZEBOX = 0x00020000;
    public static final int WS_CAPTION = 0x00C00000;
    public static final int WS_MAXIMIZE = 0x01000000;
    public static final int WS_MINIMIZE = 0x20000000;
    public static final int WS_VISIBLE = 0x10000000;
    public static final int WS_EX_TOPMOST = 0x00000008;
    public static final int WS_EX_LAYERED = 0x00080000;

    // --- Monitor flags ---
    public static final int MONITORINFOF_PRIMARY = 0x00000001;
    public static final int MONITOR_DEFAULTTONEAREST = 0x00000002;
    public static final int MONITOR_DEFAULTTOPRIMARY = 0x00000001;

    // --- Layered window attributes ---
    public static final int LWA_ALPHA = 0x00000002;

    // --- Window placement flags ---
    public static final int WPF_RESTORETOMAXIMIZED = 0x0002;

    // --- Hotkey modifiers ---
    public static final int MOD_ALT = 0x0001;
    public static final int MOD_CONTROL = 0x0002;
    public static final int MOD_SHIFT = 0x0004;
    public static final int MOD_WIN = 0x0008;
    public static final int MOD_NOREPEAT = 0x4000;

    // --- Window messages ---
    public static final int WM_NULL = 0x0000;
    public static final int WM_GETMINMAXINFO = 0x0024;
    public static final int WM_LBUTTONUP = 0x0202;
    public static final int WM_RBUTTONUP = 0x0205;
    public static final int WM_LBUTTONDBLCLK = 0x0203;
    public static final int WM_HOTKEY = 0x0312;
    public static final int WM_USER = 0x0400;

    // --- Shell_NotifyIcon operations ---
    public static final int NIM_ADD = 0x00000000;
    public static final int NIM_MODIFY = 0x00000001;
    public static final int NIM_DELETE = 0x00000002;

    // --- NOTIFYICONDATA flags ---
    public static final int NIF_MESSAGE = 0x00000001;
    public static final int NIF_ICON = 0x00000002;
    public static final int NIF_TIP = 0x00000004;
    public static final int NIF_INFO = 0x00000010;

    // --- Balloon notification icon flags ---
    public static final int NIIF_NONE = 0x00000000;
    public static final int NIIF_INFO = 0x00000001;
    public static final int NIIF_WARNING = 0x00000002;
    public static final int NIIF_ERROR = 0x00000003;

    // --- Image/Icon constants ---
    public static final int IMAGE_ICON = 1;
    public static final int LR_LOADFROMFILE = 0x00000010;

    // --- TrackPopupMenu flags ---
    public static final int TPM_RETURNCMD = 0x0100;
    public static final int TPM_NONOTIFY = 0x0080;

    // --- Menu item flags ---
    public static final int MF_STRING = 0x0000;
    public static final int MF_SEPARATOR = 0x0800;
    public static final int MF_GRAYED = 0x0001;

    // --- CreateWindowEx special parent ---
    public static final long HWND_MESSAGE = -3L;

    // --- SystemMetrics ---
    public static final int SM_CXSCREEN = 0;
    public static final int SM_CYSCREEN = 1;

    // --- Window operations ---
    public static final MethodHandle GET_WINDOW_RECT;
    public static final MethodHandle SET_WINDOW_POS;
    public static final MethodHandle SHOW_WINDOW;
    public static final MethodHandle IS_WINDOW_VISIBLE;
    public static final MethodHandle IS_ICONIC;
    public static final MethodHandle IS_ZOOMED;
    public static final MethodHandle SET_FOREGROUND_WINDOW;
    public static final MethodHandle GET_FOREGROUND_WINDOW;
    public static final MethodHandle SET_WINDOW_TEXT_W;
    public static final MethodHandle GET_WINDOW_TEXT_W;
    public static final MethodHandle GET_WINDOW_TEXT_LENGTH_W;
    public static final MethodHandle GET_WINDOW_LONG_W;
    public static final MethodHandle SET_WINDOW_LONG_W;
    public static final MethodHandle SET_LAYERED_WINDOW_ATTRIBUTES;
    public static final MethodHandle GET_LAYERED_WINDOW_ATTRIBUTES;

    // --- Screen / Monitor ---
    public static final MethodHandle GET_SYSTEM_METRICS;
    public static final MethodHandle ENUM_DISPLAY_MONITORS;
    public static final MethodHandle GET_MONITOR_INFO_W;
    public static final MethodHandle MONITOR_FROM_POINT;
    public static final MethodHandle MONITOR_FROM_WINDOW;

    // --- Cursor ---
    public static final MethodHandle GET_CURSOR_POS;

    // --- Hotkeys ---
    public static final MethodHandle REGISTER_HOT_KEY;
    public static final MethodHandle UNREGISTER_HOT_KEY;
    public static final MethodHandle GET_MESSAGE_W;
    public static final MethodHandle PEEK_MESSAGE_W;

    // --- Menu operations ---
    public static final MethodHandle CREATE_POPUP_MENU;
    public static final MethodHandle APPEND_MENU_W;
    public static final MethodHandle TRACK_POPUP_MENU;
    public static final MethodHandle DESTROY_MENU;

    // --- Shell ---
    public static final MethodHandle SHELL_NOTIFY_ICON_W;

    // --- DPI ---
    public static final MethodHandle GET_DPI_FOR_WINDOW;
    public static final MethodHandle GET_DPI_FOR_MONITOR; // shcore.dll, nullable

    // --- DPI monitor type ---
    public static final int MDT_EFFECTIVE_DPI = 0;

    // --- WndProc forwarding ---
    public static final MethodHandle CALL_WINDOW_PROC_W;

    // --- Message dispatch ---
    public static final MethodHandle POST_QUIT_MESSAGE;

    // --- Window class and creation ---
    public static final MethodHandle REGISTER_CLASS_EX_W;
    public static final MethodHandle CREATE_WINDOW_EX_W;
    public static final MethodHandle DEF_WINDOW_PROC_W;
    public static final MethodHandle DESTROY_WINDOW;
    public static final MethodHandle POST_MESSAGE_W;

    // --- Icons ---
    public static final MethodHandle LOAD_IMAGE_W;
    public static final MethodHandle DESTROY_ICON;

    // --- Module ---
    public static final MethodHandle GET_MODULE_HANDLE_W;

    static {
        USER32 = SymbolLookup.libraryLookup("user32.dll", Arena.global());
        KERNEL32 = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
        SHELL32 = SymbolLookup.libraryLookup("shell32.dll", Arena.global());

        SymbolLookup shcore = null;
        try {
            shcore = SymbolLookup.libraryLookup("shcore.dll", Arena.global());
        } catch (Exception ignored) {
            // shcore.dll not available (pre-Windows 8.1)
        }
        SHCORE = shcore;

        // Window operations
        GET_WINDOW_RECT = user32Downcall("GetWindowRect",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        SET_WINDOW_POS = user32Downcall("SetWindowPos",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT));

        SHOW_WINDOW = user32Downcall("ShowWindow",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        IS_WINDOW_VISIBLE = user32Downcall("IsWindowVisible",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        IS_ICONIC = user32Downcall("IsIconic",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        IS_ZOOMED = user32Downcall("IsZoomed",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        SET_FOREGROUND_WINDOW = user32Downcall("SetForegroundWindow",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        GET_FOREGROUND_WINDOW = user32Downcall("GetForegroundWindow",
            FunctionDescriptor.of(ValueLayout.ADDRESS));

        SET_WINDOW_TEXT_W = user32Downcall("SetWindowTextW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        GET_WINDOW_TEXT_W = user32Downcall("GetWindowTextW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        GET_WINDOW_TEXT_LENGTH_W = user32Downcall("GetWindowTextLengthW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        GET_WINDOW_LONG_W = user32Downcall("GetWindowLongPtrW",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        SET_WINDOW_LONG_W = user32Downcall("SetWindowLongPtrW",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));

        SET_LAYERED_WINDOW_ATTRIBUTES = user32Downcall("SetLayeredWindowAttributes",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_INT));

        GET_LAYERED_WINDOW_ATTRIBUTES = user32Downcall("GetLayeredWindowAttributes",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        // Screen / Monitor
        GET_SYSTEM_METRICS = user32Downcall("GetSystemMetrics",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

        ENUM_DISPLAY_MONITORS = user32Downcall("EnumDisplayMonitors",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

        GET_MONITOR_INFO_W = user32Downcall("GetMonitorInfoW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        MONITOR_FROM_POINT = user32Downcall("MonitorFromPoint",
            FunctionDescriptor.of(ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

        MONITOR_FROM_WINDOW = user32Downcall("MonitorFromWindow",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        // Cursor
        GET_CURSOR_POS = user32Downcall("GetCursorPos",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        // Hotkeys
        REGISTER_HOT_KEY = user32Downcall("RegisterHotKey",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

        UNREGISTER_HOT_KEY = user32Downcall("UnregisterHotKey",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        GET_MESSAGE_W = user32Downcall("GetMessageW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

        PEEK_MESSAGE_W = user32Downcall("PeekMessageW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

        // Menu operations
        CREATE_POPUP_MENU = user32Downcall("CreatePopupMenu",
            FunctionDescriptor.of(ValueLayout.ADDRESS));

        APPEND_MENU_W = user32Downcall("AppendMenuW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

        TRACK_POPUP_MENU = user32Downcall("TrackPopupMenu",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        DESTROY_MENU = user32Downcall("DestroyMenu",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        // Shell notifications
        SHELL_NOTIFY_ICON_W = shellDowncall("Shell_NotifyIconW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        // DPI (may not be available on older Windows)
        GET_DPI_FOR_WINDOW = user32DowncallOptional("GetDpiForWindow",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        // WndProc forwarding
        CALL_WINDOW_PROC_W = user32Downcall("CallWindowProcW",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));

        // DPI per-monitor (shcore.dll, Windows 8.1+)
        GET_DPI_FOR_MONITOR = shcoreDowncallOptional("GetDpiForMonitor",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        // Message dispatch
        POST_QUIT_MESSAGE = user32Downcall("PostQuitMessage",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));

        // Window class and creation
        REGISTER_CLASS_EX_W = user32Downcall("RegisterClassExW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        CREATE_WINDOW_EX_W = user32Downcall("CreateWindowExW",
            FunctionDescriptor.of(ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        DEF_WINDOW_PROC_W = user32Downcall("DefWindowProcW",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));

        DESTROY_WINDOW = user32Downcall("DestroyWindow",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        POST_MESSAGE_W = user32Downcall("PostMessageW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));

        // Icons
        LOAD_IMAGE_W = user32Downcall("LoadImageW",
            FunctionDescriptor.of(ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

        DESTROY_ICON = user32Downcall("DestroyIcon",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        // Module handle
        GET_MODULE_HANDLE_W = kernel32Downcall("GetModuleHandleW",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    }

    private static MethodHandle user32Downcall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = USER32.find(name)
            .orElseThrow(() -> new RuntimeException("user32 symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private static MethodHandle user32DowncallOptional(String name, FunctionDescriptor descriptor) {
        return USER32.find(name)
            .map(symbol -> LINKER.downcallHandle(symbol, descriptor))
            .orElse(null);
    }

    private static MethodHandle shellDowncall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = SHELL32.find(name)
            .orElseThrow(() -> new RuntimeException("shell32 symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private static MethodHandle shcoreDowncallOptional(String name, FunctionDescriptor descriptor) {
        if (SHCORE == null) return null;
        return SHCORE.find(name)
            .map(symbol -> LINKER.downcallHandle(symbol, descriptor))
            .orElse(null);
    }

    private static MethodHandle kernel32Downcall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = KERNEL32.find(name)
            .orElseThrow(() -> new RuntimeException("kernel32 symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    /**
     * Allocates a UTF-16LE (wide) string in the given arena.
     */
    public static MemorySegment allocateWideString(Arena arena, String str) {
        byte[] bytes = (str + "\0").getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
        MemorySegment segment = arena.allocate(bytes.length);
        segment.copyFrom(MemorySegment.ofArray(bytes));
        return segment;
    }

    /**
     * Reads a UTF-16LE (wide) string from a memory segment.
     */
    public static String readWideString(MemorySegment segment, int maxChars) {
        byte[] bytes = segment.reinterpret((long) maxChars * 2).toArray(ValueLayout.JAVA_BYTE);
        // Find null terminator
        int len = 0;
        for (int i = 0; i < bytes.length - 1; i += 2) {
            if (bytes[i] == 0 && bytes[i + 1] == 0) break;
            len += 2;
        }
        return new String(bytes, 0, len, java.nio.charset.StandardCharsets.UTF_16LE);
    }

    private Win32Bindings() {}
}
