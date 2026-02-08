package build.krema.core.api.dock;

import java.util.Map;

import build.krema.core.KremaCommand;
import build.krema.core.dock.DockEngine;
import build.krema.core.dock.DockEngineFactory;

/**
 * Dock/Taskbar badge and attention API.
 * Allows setting badge text on the dock icon and requesting user attention.
 */
public class Dock {

    private final DockEngine engine;

    public Dock() {
        this.engine = DockEngineFactory.get();
    }

    /**
     * Sets the badge label on the dock icon.
     * Pass an empty string or null to clear.
     */
    @KremaCommand("dock:setBadge")
    public void setBadge(Map<String, Object> options) {
        String text = (String) options.get("text");
        engine.setBadge(text);
    }

    /**
     * Gets the current badge label.
     */
    @KremaCommand("dock:getBadge")
    public String getBadge() {
        return engine.getBadge();
    }

    /**
     * Clears the badge from the dock icon.
     */
    @KremaCommand("dock:clearBadge")
    public void clearBadge() {
        engine.clearBadge();
    }

    /**
     * Bounces the dock icon to get user attention.
     * Returns the request ID which can be used to cancel.
     */
    @KremaCommand("dock:bounce")
    public long bounce(Map<String, Object> options) {
        System.out.println("[Dock API] bounce called with options: " + options);
        System.out.flush();
        Boolean critical = (Boolean) options.get("critical");
        long result = engine.requestAttention(critical != null && critical);
        System.out.println("[Dock API] bounce returned: " + result);
        System.out.flush();
        return result;
    }

    /**
     * Cancels a previous attention request.
     */
    @KremaCommand("dock:cancelBounce")
    public void cancelBounce(Map<String, Object> options) {
        Object id = options.get("id");
        if (id instanceof Number) {
            engine.cancelAttention(((Number) id).longValue());
        }
    }

    /**
     * Checks if dock badge is supported on the current platform.
     */
    @KremaCommand("dock:isSupported")
    public boolean isSupported() {
        return engine.isSupported();
    }
}
