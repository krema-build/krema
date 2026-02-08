package build.krema.core.ports;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Port interface for global keyboard shortcuts.
 * Implementations provide platform-specific hotkey registration.
 *
 * @see build.krema.core.shortcut.GlobalShortcutEngine
 */
public interface GlobalShortcutPort {

    /**
     * Registers a global keyboard shortcut.
     *
     * @param accelerator the shortcut (e.g., "CmdOrCtrl+Shift+Space")
     * @param callback called when the shortcut is triggered
     * @return true if registration succeeded
     */
    boolean register(String accelerator, Consumer<String> callback);

    /**
     * Unregisters a global keyboard shortcut.
     *
     * @param accelerator the shortcut to unregister
     * @return true if unregistration succeeded
     */
    boolean unregister(String accelerator);

    /**
     * Unregisters all global shortcuts.
     */
    void unregisterAll();

    /**
     * Checks if a shortcut is registered.
     *
     * @param accelerator the shortcut to check
     * @return true if registered
     */
    boolean isRegistered(String accelerator);

    /**
     * Gets all registered shortcuts.
     *
     * @return set of registered accelerator strings
     */
    Set<String> getRegistered();
}
