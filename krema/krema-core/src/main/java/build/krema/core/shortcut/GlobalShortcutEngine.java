package build.krema.core.shortcut;

import java.util.Set;
import java.util.function.Consumer;

import build.krema.core.ports.GlobalShortcutPort;

/**
 * Platform-agnostic interface for global keyboard shortcuts.
 *
 * @deprecated Use {@link GlobalShortcutPort} instead. This interface is maintained
 *             for backward compatibility and will be removed in a future version.
 * @see GlobalShortcutPort
 */
@Deprecated(since = "2.0", forRemoval = true)
public interface GlobalShortcutEngine extends GlobalShortcutPort {

    /**
     * Registers a global shortcut.
     *
     * @param accelerator The shortcut string (e.g., "Cmd+Shift+Space", "Ctrl+Alt+P")
     * @param callback Called when the shortcut is triggered
     * @return true if registration succeeded
     */
    boolean register(String accelerator, Consumer<String> callback);

    /**
     * Unregisters a global shortcut.
     *
     * @param accelerator The shortcut string to unregister
     * @return true if the shortcut was registered and has been removed
     */
    boolean unregister(String accelerator);

    /**
     * Checks if a shortcut is registered.
     */
    boolean isRegistered(String accelerator);

    /**
     * Unregisters all shortcuts.
     */
    void unregisterAll();

    /**
     * Gets all registered shortcuts.
     */
    Set<String> getRegistered();
}
