package build.krema.core.api.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import build.krema.core.KremaCommand;
import build.krema.core.event.EventEmitter;
import build.krema.core.menu.MenuEngine;
import build.krema.core.menu.MenuEngineFactory;
import build.krema.core.menu.MenuItem;
import build.krema.core.menu.MenuItem.MenuItemRole;
import build.krema.core.menu.MenuItem.MenuItemType;

/**
 * Native menu commands using platform-specific implementations.
 * On macOS: Uses NSMenu, NSMenuItem via FFM.
 */
public class Menu {

    private final MenuEngine engine;
    private EventEmitter eventEmitter;

    public Menu() {
        this.engine = MenuEngineFactory.get();
        engine.setMenuClickCallback(this::onMenuClick);
    }

    /**
     * Sets the event emitter for menu click events.
     */
    public void setEventEmitter(EventEmitter emitter) {
        this.eventEmitter = emitter;
    }

    private void onMenuClick(String itemId) {
        if (eventEmitter != null) {
            eventEmitter.emit("menu:click", Map.of("id", itemId));
        }
    }

    @KremaCommand("menu:setApplicationMenu")
    public void setApplicationMenu(Map<String, Object> options) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> menuData = (List<Map<String, Object>>) options.get("menu");
        List<MenuItem> menu = parseMenuItems(menuData);
        engine.setApplicationMenu(menu);
    }

    @KremaCommand("menu:showContextMenu")
    public void showContextMenu(Map<String, Object> options) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsData = (List<Map<String, Object>>) options.get("items");
        double x = options.containsKey("x") ? ((Number) options.get("x")).doubleValue() : 0;
        double y = options.containsKey("y") ? ((Number) options.get("y")).doubleValue() : 0;

        List<MenuItem> items = parseMenuItems(itemsData);
        engine.showContextMenu(items, x, y);
    }

    @KremaCommand("menu:setDockMenu")
    public void setDockMenu(Map<String, Object> options) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> menuData = (List<Map<String, Object>>) options.get("menu");
        List<MenuItem> menu = parseMenuItems(menuData);
        engine.setDockMenu(menu);
    }

    @KremaCommand("menu:updateItem")
    public void updateItem(Map<String, Object> options) {
        String id = (String) options.get("id");
        boolean enabled = options.containsKey("enabled") ? (Boolean) options.get("enabled") : true;
        boolean checked = options.containsKey("checked") ? (Boolean) options.get("checked") : false;
        engine.updateItem(id, enabled, checked);
    }

    private List<MenuItem> parseMenuItems(List<Map<String, Object>> itemsData) {
        if (itemsData == null) {
            return List.of();
        }

        List<MenuItem> items = new ArrayList<>();
        for (Map<String, Object> itemData : itemsData) {
            items.add(parseMenuItem(itemData));
        }
        return items;
    }

    private MenuItem parseMenuItem(Map<String, Object> data) {
        String type = (String) data.getOrDefault("type", "normal");

        // Handle separator
        if ("separator".equals(type)) {
            return MenuItem.separator();
        }

        // Handle role-based items
        if (data.containsKey("role")) {
            String roleName = ((String) data.get("role")).toUpperCase().replace("-", "_");
            try {
                MenuItemRole role = MenuItemRole.valueOf(roleName);
                return MenuItem.role(role);
            } catch (IllegalArgumentException e) {
                // Fall through to normal item
            }
        }

        MenuItem.Builder builder = MenuItem.builder();

        if (data.containsKey("id")) {
            builder.id((String) data.get("id"));
        }

        if (data.containsKey("label")) {
            builder.label((String) data.get("label"));
        }

        if (data.containsKey("accelerator")) {
            builder.accelerator((String) data.get("accelerator"));
        }

        builder.type(parseType(type));

        if (data.containsKey("enabled")) {
            builder.enabled((Boolean) data.get("enabled"));
        }

        if (data.containsKey("checked")) {
            builder.checked((Boolean) data.get("checked"));
        }

        if (data.containsKey("submenu")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> submenuData = (List<Map<String, Object>>) data.get("submenu");
            builder.submenu(parseMenuItems(submenuData));
            builder.type(MenuItemType.SUBMENU);
        }

        return builder.build();
    }

    private MenuItemType parseType(String type) {
        return switch (type.toLowerCase()) {
            case "separator" -> MenuItemType.SEPARATOR;
            case "checkbox" -> MenuItemType.CHECKBOX;
            case "radio" -> MenuItemType.RADIO;
            case "submenu" -> MenuItemType.SUBMENU;
            default -> MenuItemType.NORMAL;
        };
    }
}
