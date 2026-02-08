package build.krema.core.menu.windows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import build.krema.core.menu.MenuEngine;
import build.krema.core.menu.MenuItem;
import build.krema.core.platform.windows.Win32Bindings;
import build.krema.core.webview.WebViewCLibEngine;

/**
 * Windows MenuEngine implementation.
 *
 * <p>Windows does not have a macOS-style global application menu bar.
 * {@link #setApplicationMenu} is a no-op. Context menus are supported
 * via Win32 {@code TrackPopupMenu}. Dock menu is also a no-op (no equivalent).</p>
 */
public final class WindowsMenuEngine implements MenuEngine {

    // Menu item flags
    private static final int MF_STRING = 0x00000000;
    private static final int MF_SEPARATOR = 0x00000800;
    private static final int MF_GRAYED = 0x00000001;
    private static final int MF_CHECKED = 0x00000008;
    private static final int MF_POPUP = 0x00000010;

    private final AtomicInteger nextMenuItemId = new AtomicInteger(9000);
    private final Map<Integer, String> menuIdToItemId = new ConcurrentHashMap<>();
    private Consumer<String> menuClickCallback;

    @Override
    public void setApplicationMenu(List<MenuItem> menu) {
        // No-op: Windows doesn't have a global application menu bar.
        // Window-level menus could be added via SetMenu(HWND, HMENU) but
        // webview-based apps typically don't use native window menus.
    }

    @Override
    public void showContextMenu(List<MenuItem> items, double x, double y) {
        if (items == null || items.isEmpty()) return;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment hMenu = (MemorySegment) Win32Bindings.CREATE_POPUP_MENU.invokeExact();
            if (hMenu.address() == 0) return;

            buildMenu(arena, hMenu, items);

            // Get HWND for the owner window
            MemorySegment hwnd = MemorySegment.NULL;
            WebViewCLibEngine engine = WebViewCLibEngine.getActive();
            if (engine != null) {
                hwnd = engine.getNativeWindow();
            }

            // TPM_RETURNCMD makes TrackPopupMenu return the selected item ID
            int TPM_RETURNCMD = 0x0100;
            int TPM_LEFTALIGN = 0x0000;
            int TPM_TOPALIGN = 0x0000;

            int selectedId = (int) Win32Bindings.TRACK_POPUP_MENU.invokeExact(
                hMenu,
                TPM_LEFTALIGN | TPM_TOPALIGN | TPM_RETURNCMD,
                (int) x, (int) y, 0,
                hwnd, MemorySegment.NULL
            );

            Win32Bindings.DESTROY_MENU.invokeExact(hMenu);

            if (selectedId > 0 && menuClickCallback != null) {
                String itemId = menuIdToItemId.get(selectedId);
                if (itemId != null) {
                    menuClickCallback.accept(itemId);
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to show context menu", t);
        }
    }

    @Override
    public void setDockMenu(List<MenuItem> items) {
        // No-op: Windows doesn't have a dock menu equivalent.
    }

    @Override
    public void updateItem(String itemId, boolean enabled, boolean checked) {
        // For context menus, items are rebuilt each time.
        // Tracked state would require maintaining a persistent menu handle.
    }

    @Override
    public void setMenuClickCallback(Consumer<String> callback) {
        this.menuClickCallback = callback;
    }

    private void buildMenu(Arena arena, MemorySegment hMenu, List<MenuItem> items) throws Throwable {
        for (MenuItem item : items) {
            if (item.type() == MenuItem.MenuItemType.SEPARATOR) {
                Win32Bindings.APPEND_MENU_W.invokeExact(
                    hMenu, MF_SEPARATOR, 0L, MemorySegment.NULL
                );
                continue;
            }

            String label = item.label() != null ? item.label() : "";
            MemorySegment labelPtr = Win32Bindings.allocateWideString(arena, label);

            if (item.submenu() != null && !item.submenu().isEmpty()) {
                // Submenu
                MemorySegment subMenu = (MemorySegment) Win32Bindings.CREATE_POPUP_MENU.invokeExact();
                buildMenu(arena, subMenu, item.submenu());
                Win32Bindings.APPEND_MENU_W.invokeExact(
                    hMenu, MF_STRING | MF_POPUP, subMenu.address(), labelPtr
                );
            } else {
                int flags = MF_STRING;
                if (!item.enabled()) flags |= MF_GRAYED;
                if (item.checked()) flags |= MF_CHECKED;

                int id = nextMenuItemId.getAndIncrement();
                if (item.id() != null) {
                    menuIdToItemId.put(id, item.id());
                }

                Win32Bindings.APPEND_MENU_W.invokeExact(hMenu, flags, (long) id, labelPtr);
            }
        }
    }
}
