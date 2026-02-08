package build.krema.core.menu;

import java.util.List;

/**
 * Represents a menu item in a native menu.
 */
public record MenuItem(
    String id,
    String label,
    String accelerator,
    MenuItemType type,
    boolean enabled,
    boolean checked,
    MenuItemRole role,
    List<MenuItem> submenu
) {

    /**
     * Creates a simple menu item.
     */
    public static MenuItem item(String id, String label) {
        return new MenuItem(id, label, null, MenuItemType.NORMAL, true, false, null, null);
    }

    /**
     * Creates a menu item with accelerator.
     */
    public static MenuItem item(String id, String label, String accelerator) {
        return new MenuItem(id, label, accelerator, MenuItemType.NORMAL, true, false, null, null);
    }

    /**
     * Creates a separator.
     */
    public static MenuItem separator() {
        return new MenuItem(null, null, null, MenuItemType.SEPARATOR, true, false, null, null);
    }

    /**
     * Creates a submenu.
     */
    public static MenuItem submenu(String label, List<MenuItem> items) {
        return new MenuItem(null, label, null, MenuItemType.SUBMENU, true, false, null, items);
    }

    /**
     * Creates a checkbox item.
     */
    public static MenuItem checkbox(String id, String label, boolean checked) {
        return new MenuItem(id, label, null, MenuItemType.CHECKBOX, true, checked, null, null);
    }

    /**
     * Creates an item with a standard role.
     */
    public static MenuItem role(MenuItemRole role) {
        return new MenuItem(null, null, null, MenuItemType.NORMAL, true, false, role, null);
    }

    /**
     * Builder for complex menu items.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String label;
        private String accelerator;
        private MenuItemType type = MenuItemType.NORMAL;
        private boolean enabled = true;
        private boolean checked = false;
        private MenuItemRole role;
        private List<MenuItem> submenu;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder accelerator(String accelerator) {
            this.accelerator = accelerator;
            return this;
        }

        public Builder type(MenuItemType type) {
            this.type = type;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder checked(boolean checked) {
            this.checked = checked;
            return this;
        }

        public Builder role(MenuItemRole role) {
            this.role = role;
            return this;
        }

        public Builder submenu(List<MenuItem> submenu) {
            this.submenu = submenu;
            return this;
        }

        public MenuItem build() {
            return new MenuItem(id, label, accelerator, type, enabled, checked, role, submenu);
        }
    }

    /**
     * Menu item types.
     */
    public enum MenuItemType {
        NORMAL,
        SEPARATOR,
        CHECKBOX,
        RADIO,
        SUBMENU
    }

    /**
     * Standard menu item roles that have platform-specific behavior.
     */
    public enum MenuItemRole {
        // App menu
        ABOUT,
        SERVICES,
        HIDE,
        HIDE_OTHERS,
        UNHIDE,
        QUIT,

        // Edit menu
        UNDO,
        REDO,
        CUT,
        COPY,
        PASTE,
        PASTE_AND_MATCH_STYLE,
        DELETE,
        SELECT_ALL,

        // View menu
        RELOAD,
        FORCE_RELOAD,
        TOGGLE_DEV_TOOLS,
        RESET_ZOOM,
        ZOOM_IN,
        ZOOM_OUT,
        TOGGLE_FULLSCREEN,

        // Window menu
        MINIMIZE,
        ZOOM,
        CLOSE,
        FRONT
    }
}
