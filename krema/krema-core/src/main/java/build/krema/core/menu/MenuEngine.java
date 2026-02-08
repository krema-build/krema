package build.krema.core.menu;

import java.util.List;
import java.util.function.Consumer;

import build.krema.core.ports.MenuPort;

/**
 * Platform-agnostic interface for native menus.
 * Implementations provide platform-specific menu functionality.
 *
 * @deprecated Use {@link MenuPort} instead. This interface is maintained
 *             for backward compatibility and will be removed in a future version.
 * @see MenuPort
 */
@Deprecated(since = "2.0", forRemoval = true)
public interface MenuEngine extends MenuPort {

    /**
     * Sets the application menu bar.
     *
     * @param menu list of top-level menu items (each with submenus)
     */
    void setApplicationMenu(List<MenuItem> menu);

    /**
     * Shows a context menu at the specified position.
     *
     * @param items menu items
     * @param x screen x coordinate
     * @param y screen y coordinate
     */
    void showContextMenu(List<MenuItem> items, double x, double y);

    /**
     * Sets the dock menu (macOS only).
     *
     * @param items menu items
     */
    void setDockMenu(List<MenuItem> items);

    /**
     * Updates an existing menu item.
     *
     * @param itemId the item ID
     * @param enabled whether the item is enabled
     * @param checked whether the item is checked (for checkboxes)
     */
    void updateItem(String itemId, boolean enabled, boolean checked);

    /**
     * Registers a callback for menu item clicks.
     *
     * @param callback receives the menu item ID when clicked
     */
    void setMenuClickCallback(Consumer<String> callback);
}
