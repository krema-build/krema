package build.krema.core.api.shortcut;

import java.util.List;
import java.util.Map;

import build.krema.core.KremaCommand;
import build.krema.core.event.EventEmitter;
import build.krema.core.shortcut.GlobalShortcutEngine;
import build.krema.core.shortcut.GlobalShortcutEngineFactory;

/**
 * Global keyboard shortcut API.
 * Allows registering system-wide hotkeys that work even when the app is not focused.
 */
public class GlobalShortcut {

    private final GlobalShortcutEngine engine;
    private EventEmitter eventEmitter;

    public GlobalShortcut() {
        this.engine = GlobalShortcutEngineFactory.get();
    }

    /**
     * Sets the event emitter for shortcut trigger events.
     */
    public void setEventEmitter(EventEmitter emitter) {
        this.eventEmitter = emitter;
    }

    @KremaCommand("shortcut:register")
    public boolean register(Map<String, Object> options) {
        String accelerator = (String) options.get("accelerator");
        if (accelerator == null || accelerator.isEmpty()) {
            return false;
        }

        return engine.register(accelerator, shortcut -> {
            if (eventEmitter != null) {
                eventEmitter.emit("shortcut:triggered", Map.of(
                    "accelerator", shortcut
                ));
            }
        });
    }

    @KremaCommand("shortcut:unregister")
    public boolean unregister(Map<String, Object> options) {
        String accelerator = (String) options.get("accelerator");
        if (accelerator == null || accelerator.isEmpty()) {
            return false;
        }
        return engine.unregister(accelerator);
    }

    @KremaCommand("shortcut:unregisterAll")
    public void unregisterAll() {
        engine.unregisterAll();
    }

    @KremaCommand("shortcut:isRegistered")
    public boolean isRegistered(Map<String, Object> options) {
        String accelerator = (String) options.get("accelerator");
        if (accelerator == null || accelerator.isEmpty()) {
            return false;
        }
        return engine.isRegistered(accelerator);
    }

    @KremaCommand("shortcut:getAll")
    public List<String> getAll() {
        return List.copyOf(engine.getRegistered());
    }
}
