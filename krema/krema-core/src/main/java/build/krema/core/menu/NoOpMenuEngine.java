package build.krema.core.menu;

import java.util.List;
import java.util.function.Consumer;

/**
 * No-op MenuEngine implementation for when native menus are not available.
 */
public final class NoOpMenuEngine implements MenuEngine {

    @Override
    public void setApplicationMenu(List<MenuItem> menu) {
        System.out.println("[NoOpMenuEngine] setApplicationMenu: " + menu.size() + " items (native menus not available)");
    }

    @Override
    public void showContextMenu(List<MenuItem> items, double x, double y) {
        System.out.println("[NoOpMenuEngine] showContextMenu: " + items.size() + " items (native menus not available)");
    }

    @Override
    public void setDockMenu(List<MenuItem> items) {
        System.out.println("[NoOpMenuEngine] setDockMenu: " + items.size() + " items (native menus not available)");
    }

    @Override
    public void updateItem(String itemId, boolean enabled, boolean checked) {
        // No-op
    }

    @Override
    public void setMenuClickCallback(Consumer<String> callback) {
        // No-op - no callbacks since no menus
    }
}
