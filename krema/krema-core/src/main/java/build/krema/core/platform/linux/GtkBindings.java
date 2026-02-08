package build.krema.core.platform.linux;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Shared GTK3/GDK3 FFM bindings for Linux platform engines.
 * Loaded once and reused by LinuxWindowEngine, LinuxScreenEngine, etc.
 */
public final class GtkBindings {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup GTK;
    private static final SymbolLookup GDK;
    private static final SymbolLookup GOBJECT;
    private static final SymbolLookup GLIB;

    // GdkRectangle struct layout: { int x, y, width, height }
    public static final StructLayout GDK_RECTANGLE = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("x"),
        ValueLayout.JAVA_INT.withName("y"),
        ValueLayout.JAVA_INT.withName("width"),
        ValueLayout.JAVA_INT.withName("height")
    );

    // GdkGeometry struct layout (used by gtk_window_set_geometry_hints)
    // Fields: min_width, min_height, max_width, max_height, base_width, base_height,
    //         width_inc, height_inc (8 ints), then min_aspect, max_aspect (2 doubles),
    //         then win_gravity (1 int) + 4 bytes padding
    public static final StructLayout GDK_GEOMETRY = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("min_width"),
        ValueLayout.JAVA_INT.withName("min_height"),
        ValueLayout.JAVA_INT.withName("max_width"),
        ValueLayout.JAVA_INT.withName("max_height"),
        ValueLayout.JAVA_INT.withName("base_width"),
        ValueLayout.JAVA_INT.withName("base_height"),
        ValueLayout.JAVA_INT.withName("width_inc"),
        ValueLayout.JAVA_INT.withName("height_inc"),
        ValueLayout.JAVA_DOUBLE.withName("min_aspect"),
        ValueLayout.JAVA_DOUBLE.withName("max_aspect"),
        ValueLayout.JAVA_INT.withName("win_gravity"),
        MemoryLayout.paddingLayout(4)
    );

    // GdkWindowHints flags
    public static final int GDK_HINT_MIN_SIZE = 1 << 1;
    public static final int GDK_HINT_MAX_SIZE = 1 << 2;

    // GDK window state flags
    public static final int GDK_WINDOW_STATE_ICONIFIED = 1 << 2;
    public static final int GDK_WINDOW_STATE_FULLSCREEN = 1 << 4;

    // --- Window operations ---
    public static final MethodHandle GTK_WINDOW_MAXIMIZE;
    public static final MethodHandle GTK_WINDOW_UNMAXIMIZE;
    public static final MethodHandle GTK_WINDOW_ICONIFY;
    public static final MethodHandle GTK_WINDOW_DEICONIFY;
    public static final MethodHandle GTK_WINDOW_FULLSCREEN;
    public static final MethodHandle GTK_WINDOW_UNFULLSCREEN;
    public static final MethodHandle GTK_WINDOW_MOVE;
    public static final MethodHandle GTK_WINDOW_RESIZE;
    public static final MethodHandle GTK_WINDOW_GET_POSITION;
    public static final MethodHandle GTK_WINDOW_GET_SIZE;
    public static final MethodHandle GTK_WINDOW_SET_TITLE;
    public static final MethodHandle GTK_WINDOW_GET_TITLE;
    public static final MethodHandle GTK_WINDOW_SET_RESIZABLE;
    public static final MethodHandle GTK_WINDOW_IS_MAXIMIZED;
    public static final MethodHandle GTK_WINDOW_SET_KEEP_ABOVE;
    public static final MethodHandle GTK_WINDOW_PRESENT;
    public static final MethodHandle GTK_WINDOW_SET_DECORATED;
    public static final MethodHandle GTK_WINDOW_IS_ACTIVE;
    public static final MethodHandle GTK_WINDOW_SET_GEOMETRY_HINTS;

    // --- Widget operations ---
    public static final MethodHandle GTK_WIDGET_SHOW;
    public static final MethodHandle GTK_WIDGET_HIDE;
    public static final MethodHandle GTK_WIDGET_IS_VISIBLE;
    public static final MethodHandle GTK_WIDGET_SET_OPACITY;
    public static final MethodHandle GTK_WIDGET_GET_OPACITY;
    public static final MethodHandle GTK_WIDGET_GET_WINDOW;

    // --- GDK window state ---
    public static final MethodHandle GDK_WINDOW_GET_STATE;

    // --- GDK monitor/display ---
    public static final MethodHandle GDK_DISPLAY_GET_DEFAULT;
    public static final MethodHandle GDK_DISPLAY_GET_N_MONITORS;
    public static final MethodHandle GDK_DISPLAY_GET_MONITOR;
    public static final MethodHandle GDK_DISPLAY_GET_PRIMARY_MONITOR;
    public static final MethodHandle GDK_MONITOR_GET_GEOMETRY;
    public static final MethodHandle GDK_MONITOR_GET_WORKAREA;
    public static final MethodHandle GDK_MONITOR_GET_SCALE_FACTOR;
    public static final MethodHandle GDK_MONITOR_GET_REFRESH_RATE;
    public static final MethodHandle GDK_MONITOR_GET_MODEL;
    public static final MethodHandle GDK_DISPLAY_GET_MONITOR_AT_POINT;

    // --- Menu operations ---
    public static final MethodHandle GTK_MENU_NEW;
    public static final MethodHandle GTK_MENU_ITEM_NEW_WITH_LABEL;
    public static final MethodHandle GTK_CHECK_MENU_ITEM_NEW_WITH_LABEL;
    public static final MethodHandle GTK_CHECK_MENU_ITEM_SET_ACTIVE;
    public static final MethodHandle GTK_SEPARATOR_MENU_ITEM_NEW;
    public static final MethodHandle GTK_MENU_SHELL_APPEND;
    public static final MethodHandle GTK_MENU_ITEM_SET_SUBMENU;
    public static final MethodHandle GTK_WIDGET_SHOW_ALL;
    public static final MethodHandle GTK_WIDGET_SET_SENSITIVE;
    public static final MethodHandle GTK_MENU_POPUP_AT_POINTER;

    // --- GLib signal ---
    public static final MethodHandle G_SIGNAL_CONNECT_DATA;

    // --- GDK cursor ---
    public static final MethodHandle GDK_DISPLAY_GET_DEFAULT_SEAT;
    public static final MethodHandle GDK_SEAT_GET_POINTER;
    public static final MethodHandle GDK_DEVICE_GET_POSITION;

    // --- GDK atoms ---
    public static final MethodHandle GDK_ATOM_INTERN;
    public static final MethodHandle GDK_ATOM_NAME;

    // --- Clipboard operations ---
    public static final MethodHandle GTK_CLIPBOARD_GET;
    public static final MethodHandle GTK_CLIPBOARD_SET_TEXT;
    public static final MethodHandle GTK_CLIPBOARD_WAIT_FOR_TEXT;
    public static final MethodHandle GTK_CLIPBOARD_WAIT_IS_TEXT_AVAILABLE;
    public static final MethodHandle GTK_CLIPBOARD_CLEAR;
    public static final MethodHandle GTK_CLIPBOARD_WAIT_FOR_TARGETS;
    public static final MethodHandle GTK_CLIPBOARD_WAIT_FOR_CONTENTS;
    public static final MethodHandle GTK_SELECTION_DATA_GET_DATA;
    public static final MethodHandle GTK_SELECTION_DATA_GET_LENGTH;
    public static final MethodHandle GTK_SELECTION_DATA_FREE;

    // --- GLib memory ---
    public static final MethodHandle G_FREE;

    static {
        GTK = SymbolLookup.libraryLookup("libgtk-3.so.0", Arena.global());
        GDK = SymbolLookup.libraryLookup("libgdk-3.so.0", Arena.global());
        GOBJECT = SymbolLookup.libraryLookup("libgobject-2.0.so.0", Arena.global());
        GLIB = SymbolLookup.libraryLookup("libglib-2.0.so.0", Arena.global());

        // Window operations (all take GtkWindow* as first arg)
        GTK_WINDOW_MAXIMIZE = gtkDowncall("gtk_window_maximize",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        GTK_WINDOW_UNMAXIMIZE = gtkDowncall("gtk_window_unmaximize",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        GTK_WINDOW_ICONIFY = gtkDowncall("gtk_window_iconify",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        GTK_WINDOW_DEICONIFY = gtkDowncall("gtk_window_deiconify",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        GTK_WINDOW_FULLSCREEN = gtkDowncall("gtk_window_fullscreen",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        GTK_WINDOW_UNFULLSCREEN = gtkDowncall("gtk_window_unfullscreen",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        GTK_WINDOW_MOVE = gtkDowncall("gtk_window_move",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        GTK_WINDOW_RESIZE = gtkDowncall("gtk_window_resize",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        GTK_WINDOW_GET_POSITION = gtkDowncall("gtk_window_get_position",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GTK_WINDOW_GET_SIZE = gtkDowncall("gtk_window_get_size",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GTK_WINDOW_SET_TITLE = gtkDowncall("gtk_window_set_title",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GTK_WINDOW_GET_TITLE = gtkDowncall("gtk_window_get_title",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GTK_WINDOW_SET_RESIZABLE = gtkDowncall("gtk_window_set_resizable",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        GTK_WINDOW_IS_MAXIMIZED = gtkDowncall("gtk_window_is_maximized",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        GTK_WINDOW_SET_KEEP_ABOVE = gtkDowncall("gtk_window_set_keep_above",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        GTK_WINDOW_PRESENT = gtkDowncall("gtk_window_present",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        GTK_WINDOW_SET_DECORATED = gtkDowncall("gtk_window_set_decorated",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        GTK_WINDOW_IS_ACTIVE = gtkDowncall("gtk_window_is_active",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        GTK_WINDOW_SET_GEOMETRY_HINTS = gtkDowncall("gtk_window_set_geometry_hints",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        // Widget operations
        GTK_WIDGET_SHOW = gtkDowncall("gtk_widget_show",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        GTK_WIDGET_HIDE = gtkDowncall("gtk_widget_hide",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        GTK_WIDGET_IS_VISIBLE = gtkDowncall("gtk_widget_is_visible",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        GTK_WIDGET_SET_OPACITY = gtkDowncall("gtk_widget_set_opacity",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));
        GTK_WIDGET_GET_OPACITY = gtkDowncall("gtk_widget_get_opacity",
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
        GTK_WIDGET_GET_WINDOW = gtkDowncall("gtk_widget_get_window",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        // GDK window state
        GDK_WINDOW_GET_STATE = gdkDowncall("gdk_window_get_state",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        // GDK display/monitor
        GDK_DISPLAY_GET_DEFAULT = gdkDowncall("gdk_display_get_default",
            FunctionDescriptor.of(ValueLayout.ADDRESS));
        GDK_DISPLAY_GET_N_MONITORS = gdkDowncall("gdk_display_get_n_monitors",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        GDK_DISPLAY_GET_MONITOR = gdkDowncall("gdk_display_get_monitor",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        GDK_DISPLAY_GET_PRIMARY_MONITOR = gdkDowncall("gdk_display_get_primary_monitor",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GDK_MONITOR_GET_GEOMETRY = gdkDowncall("gdk_monitor_get_geometry",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GDK_MONITOR_GET_WORKAREA = gdkDowncall("gdk_monitor_get_workarea",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GDK_MONITOR_GET_SCALE_FACTOR = gdkDowncall("gdk_monitor_get_scale_factor",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        GDK_MONITOR_GET_REFRESH_RATE = gdkDowncall("gdk_monitor_get_refresh_rate",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        GDK_MONITOR_GET_MODEL = gdkDowncall("gdk_monitor_get_model",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GDK_DISPLAY_GET_MONITOR_AT_POINT = gdkDowncall("gdk_display_get_monitor_at_point",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

        // Menu operations
        GTK_MENU_NEW = gtkDowncall("gtk_menu_new",
            FunctionDescriptor.of(ValueLayout.ADDRESS));
        GTK_MENU_ITEM_NEW_WITH_LABEL = gtkDowncall("gtk_menu_item_new_with_label",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GTK_CHECK_MENU_ITEM_NEW_WITH_LABEL = gtkDowncall("gtk_check_menu_item_new_with_label",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GTK_CHECK_MENU_ITEM_SET_ACTIVE = gtkDowncall("gtk_check_menu_item_set_active",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        GTK_SEPARATOR_MENU_ITEM_NEW = gtkDowncall("gtk_separator_menu_item_new",
            FunctionDescriptor.of(ValueLayout.ADDRESS));
        GTK_MENU_SHELL_APPEND = gtkDowncall("gtk_menu_shell_append",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GTK_MENU_ITEM_SET_SUBMENU = gtkDowncall("gtk_menu_item_set_submenu",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GTK_WIDGET_SHOW_ALL = gtkDowncall("gtk_widget_show_all",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        GTK_WIDGET_SET_SENSITIVE = gtkDowncall("gtk_widget_set_sensitive",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        GTK_MENU_POPUP_AT_POINTER = gtkDowncall("gtk_menu_popup_at_pointer",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        // GLib signal connection
        // gulong g_signal_connect_data(gpointer instance, const gchar *signal, GCallback handler,
        //                              gpointer data, GClosureNotify destroy_data, GConnectFlags flags)
        G_SIGNAL_CONNECT_DATA = gobjectDowncall("g_signal_connect_data",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        // GDK cursor
        GDK_DISPLAY_GET_DEFAULT_SEAT = gdkDowncall("gdk_display_get_default_seat",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GDK_SEAT_GET_POINTER = gdkDowncall("gdk_seat_get_pointer",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GDK_DEVICE_GET_POSITION = gdkDowncall("gdk_device_get_position",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        // GDK atoms
        GDK_ATOM_INTERN = gdkDowncall("gdk_atom_intern",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        GDK_ATOM_NAME = gdkDowncall("gdk_atom_name",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        // Clipboard operations
        GTK_CLIPBOARD_GET = gtkDowncall("gtk_clipboard_get",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GTK_CLIPBOARD_SET_TEXT = gtkDowncall("gtk_clipboard_set_text",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        GTK_CLIPBOARD_WAIT_FOR_TEXT = gtkDowncall("gtk_clipboard_wait_for_text",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GTK_CLIPBOARD_WAIT_IS_TEXT_AVAILABLE = gtkDowncall("gtk_clipboard_wait_is_text_available",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        GTK_CLIPBOARD_CLEAR = gtkDowncall("gtk_clipboard_clear",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        GTK_CLIPBOARD_WAIT_FOR_TARGETS = gtkDowncall("gtk_clipboard_wait_for_targets",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GTK_CLIPBOARD_WAIT_FOR_CONTENTS = gtkDowncall("gtk_clipboard_wait_for_contents",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GTK_SELECTION_DATA_GET_DATA = gtkDowncall("gtk_selection_data_get_data",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        GTK_SELECTION_DATA_GET_LENGTH = gtkDowncall("gtk_selection_data_get_length",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        GTK_SELECTION_DATA_FREE = gtkDowncall("gtk_selection_data_free",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

        // GLib memory
        G_FREE = glibDowncall("g_free",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    }

    private static MethodHandle gtkDowncall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = GTK.find(name)
            .orElseThrow(() -> new RuntimeException("GTK symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private static MethodHandle gdkDowncall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = GDK.find(name)
            .orElseThrow(() -> new RuntimeException("GDK symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private static MethodHandle gobjectDowncall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = GOBJECT.find(name)
            .orElseThrow(() -> new RuntimeException("GObject symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private static MethodHandle glibDowncall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = GLIB.find(name)
            .orElseThrow(() -> new RuntimeException("GLib symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private GtkBindings() {}
}
