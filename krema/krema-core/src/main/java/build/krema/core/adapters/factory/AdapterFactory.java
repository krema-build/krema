package build.krema.core.adapters.factory;

import build.krema.core.dialog.DialogEngine;
import build.krema.core.dialog.DialogEngineFactory;
import build.krema.core.dock.DockEngine;
import build.krema.core.dock.DockEngineFactory;
import build.krema.core.menu.MenuEngine;
import build.krema.core.menu.MenuEngineFactory;
import build.krema.core.notification.NotificationEngine;
import build.krema.core.notification.NotificationEngineFactory;
import build.krema.core.platform.Platform;
import build.krema.core.ports.*;
import build.krema.core.screen.ScreenEngine;
import build.krema.core.screen.ScreenEngineFactory;
import build.krema.core.shortcut.GlobalShortcutEngine;
import build.krema.core.shortcut.GlobalShortcutEngineFactory;
import build.krema.core.window.WindowEngine;
import build.krema.core.window.WindowEngineFactory;

/**
 * Unified factory for platform-specific adapters.
 * Provides a single point of access for all platform implementations.
 *
 * <p>This factory centralizes adapter creation and supports the Hexagonal
 * Architecture pattern by returning Port interfaces rather than concrete
 * implementations.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Get individual adapters
 * WindowPort window = AdapterFactory.window();
 * DialogPort dialog = AdapterFactory.dialog();
 *
 * // Or get all adapters at once
 * AdapterFactory.Adapters adapters = AdapterFactory.all();
 * adapters.window().setTitle("Hello");
 * }</pre>
 *
 * <h2>Platform Detection</h2>
 * <p>The factory automatically detects the current platform and returns
 * the appropriate implementation:</p>
 * <ul>
 *   <li>macOS: Cocoa-based implementations via FFM</li>
 *   <li>Windows: Win32-based implementations via FFM</li>
 *   <li>Linux: GTK-based implementations via FFM</li>
 * </ul>
 *
 * @see build.krema.core.ports
 */
public final class AdapterFactory {

    private AdapterFactory() {}

    // === Individual Adapter Getters ===

    /**
     * Gets the window adapter for the current platform.
     */
    public static WindowPort window() {
        return WindowEngineFactory.get();
    }

    /**
     * Gets the dialog adapter for the current platform.
     */
    public static DialogPort dialog() {
        return DialogEngineFactory.get();
    }

    /**
     * Gets the menu adapter for the current platform.
     */
    public static MenuPort menu() {
        return MenuEngineFactory.get();
    }

    /**
     * Gets the notification adapter for the current platform.
     */
    public static NotificationPort notification() {
        return new NotificationPortAdapter(NotificationEngineFactory.get());
    }

    /**
     * Gets the screen adapter for the current platform.
     */
    public static ScreenPort screen() {
        return new ScreenPortAdapter(ScreenEngineFactory.get());
    }

    /**
     * Gets the global shortcut adapter for the current platform.
     */
    public static GlobalShortcutPort globalShortcut() {
        return GlobalShortcutEngineFactory.get();
    }

    /**
     * Gets the dock adapter for the current platform.
     */
    public static DockPort dock() {
        return DockEngineFactory.get();
    }

    // === Bulk Adapter Access ===

    /**
     * Gets all adapters for the current platform.
     */
    public static Adapters all() {
        return new Adapters(
            window(),
            dialog(),
            menu(),
            notification(),
            screen(),
            globalShortcut(),
            dock()
        );
    }

    /**
     * Gets the current platform.
     */
    public static Platform platform() {
        return Platform.current();
    }

    // === Adapter Container ===

    /**
     * Container for all platform adapters.
     */
    public record Adapters(
        WindowPort window,
        DialogPort dialog,
        MenuPort menu,
        NotificationPort notification,
        ScreenPort screen,
        GlobalShortcutPort globalShortcut,
        DockPort dock
    ) {}

    // === Internal Adapters for API Differences ===

    /**
     * Adapter to bridge NotificationEngine to NotificationPort.
     */
    private static class NotificationPortAdapter implements NotificationPort {
        private final NotificationEngine engine;

        NotificationPortAdapter(NotificationEngine engine) {
            this.engine = engine;
        }

        @Override
        public String show(String title, String body, String icon) {
            engine.show(title, body, icon != null ? java.util.Map.of("icon", icon) : null);
            return null; // Original API doesn't return ID
        }

        @Override
        public boolean isSupported() {
            return engine.isSupported();
        }
    }

    /**
     * Adapter to bridge ScreenEngine to ScreenPort.
     */
    private static class ScreenPortAdapter implements ScreenPort {
        private final ScreenEngine engine;

        ScreenPortAdapter(ScreenEngine engine) {
            this.engine = engine;
        }

        @Override
        public java.util.List<build.krema.core.screen.ScreenInfo> getAllScreens() {
            return engine.getAllScreens();
        }

        @Override
        public build.krema.core.screen.ScreenInfo getPrimaryScreen() {
            return engine.getPrimaryScreen();
        }

        @Override
        public build.krema.core.screen.CursorPosition getCursorPosition() {
            return engine.getCursorPosition();
        }
    }
}
