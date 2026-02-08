package build.krema.core.menu.linux;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import build.krema.core.menu.MenuEngine;
import build.krema.core.menu.MenuItem;
import build.krema.core.menu.MenuItem.MenuItemType;
import build.krema.core.platform.linux.GtkBindings;

/**
 * Linux MenuEngine implementation using GTK3 via FFM.
 *
 * <p>Supports native context menus via {@code gtk_menu_popup_at_pointer}.
 * Application menu and dock menu are no-ops since most Linux DEs
 * do not have macOS-style global menu bars or dock menus.</p>
 */
public final class LinuxMenuEngine implements MenuEngine {

    private static final Linker LINKER = Linker.nativeLinker();

    private final Arena arena = Arena.ofAuto();
    private Consumer<String> clickCallback;

    // Map from native GtkMenuItem pointer address to item ID for click dispatch
    private final Map<Long, String> widgetToId = new ConcurrentHashMap<>();

    // Upcall stub for the "activate" signal — prevent GC
    private final MemorySegment activateStub;

    public LinuxMenuEngine() {
        activateStub = createActivateStub();
    }

    @Override
    public void setApplicationMenu(List<MenuItem> menu) {
        // No-op: most Linux DEs don't have a macOS-style global menu bar
    }

    @Override
    public void showContextMenu(List<MenuItem> items, double x, double y) {
        try {
            MemorySegment gtkMenu = (MemorySegment) GtkBindings.GTK_MENU_NEW.invokeExact();
            populateMenu(gtkMenu, items);
            GtkBindings.GTK_WIDGET_SHOW_ALL.invokeExact(gtkMenu);
            // Passing NULL for the GdkEvent triggers popup at the current pointer position
            GtkBindings.GTK_MENU_POPUP_AT_POINTER.invokeExact(gtkMenu, MemorySegment.NULL);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to show context menu", t);
        }
    }

    @Override
    public void setDockMenu(List<MenuItem> items) {
        // No-op: no dock concept on standard Linux DEs
    }

    @Override
    public void updateItem(String itemId, boolean enabled, boolean checked) {
        // Context menus are ephemeral in GTK — items are rebuilt each time.
        // This is a no-op; state should be set when constructing the menu items.
    }

    @Override
    public void setMenuClickCallback(Consumer<String> callback) {
        this.clickCallback = callback;
    }

    private void populateMenu(MemorySegment menu, List<MenuItem> items) throws Throwable {
        for (MenuItem item : items) {
            MemorySegment widget;

            if (item.type() == MenuItemType.SEPARATOR) {
                widget = (MemorySegment) GtkBindings.GTK_SEPARATOR_MENU_ITEM_NEW.invokeExact();
            } else if (item.type() == MenuItemType.CHECKBOX) {
                MemorySegment label = arena.allocateFrom(item.label() != null ? item.label() : "");
                widget = (MemorySegment) GtkBindings.GTK_CHECK_MENU_ITEM_NEW_WITH_LABEL.invokeExact(label);
                GtkBindings.GTK_CHECK_MENU_ITEM_SET_ACTIVE.invokeExact(widget, item.checked() ? 1 : 0);
                connectActivateSignal(widget, item.id());
            } else {
                MemorySegment label = arena.allocateFrom(item.label() != null ? item.label() : "");
                widget = (MemorySegment) GtkBindings.GTK_MENU_ITEM_NEW_WITH_LABEL.invokeExact(label);

                if (item.submenu() != null && !item.submenu().isEmpty()) {
                    MemorySegment submenu = (MemorySegment) GtkBindings.GTK_MENU_NEW.invokeExact();
                    populateMenu(submenu, item.submenu());
                    GtkBindings.GTK_MENU_ITEM_SET_SUBMENU.invokeExact(widget, submenu);
                } else {
                    connectActivateSignal(widget, item.id());
                }
            }

            if (!item.enabled()) {
                GtkBindings.GTK_WIDGET_SET_SENSITIVE.invokeExact(widget, 0);
            }

            GtkBindings.GTK_MENU_SHELL_APPEND.invokeExact(menu, widget);
        }
    }

    private void connectActivateSignal(MemorySegment widget, String itemId) throws Throwable {
        if (itemId == null) return;

        widgetToId.put(widget.address(), itemId);

        MemorySegment signalName = arena.allocateFrom("activate");
        GtkBindings.G_SIGNAL_CONNECT_DATA.invokeExact(
            widget,
            signalName,
            activateStub,
            widget,              // pass the widget pointer as user_data
            MemorySegment.NULL,  // no destroy notify
            0                    // no connect flags
        );
    }

    private MemorySegment createActivateStub() {
        try {
            // GTK "activate" signal handler: void handler(GtkMenuItem *widget, gpointer user_data)
            FunctionDescriptor descriptor = FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS,  // widget (the GtkMenuItem that was activated)
                ValueLayout.ADDRESS   // user_data (we pass the widget pointer)
            );

            MethodHandle handler = MethodHandles.lookup().findVirtual(
                LinuxMenuEngine.class,
                "onMenuItemActivated",
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class)
            ).bindTo(this);

            return LINKER.upcallStub(handler, descriptor, arena);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to create menu activate upcall stub", t);
        }
    }

    @SuppressWarnings("unused")
    private void onMenuItemActivated(MemorySegment widget, MemorySegment userData) {
        String itemId = widgetToId.get(userData.address());
        if (itemId != null && clickCallback != null) {
            clickCallback.accept(itemId);
        }
    }
}
