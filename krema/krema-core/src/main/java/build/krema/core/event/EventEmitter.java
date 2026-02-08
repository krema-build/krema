package build.krema.core.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import build.krema.core.webview.WebViewEngine;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Emits events from the backend to the frontend.
 * Also supports backend-side event listeners.
 */
public class EventEmitter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebViewEngine engine;
    private final Map<String, List<Consumer<KremaEvent>>> listeners = new ConcurrentHashMap<>();
    private final Set<Consumer<KremaEvent>> globalListeners = ConcurrentHashMap.newKeySet();

    public EventEmitter(WebViewEngine engine) {
        this.engine = engine;
    }

    /**
     * Emits an event to the frontend and notifies backend listeners.
     */
    public void emit(String eventName, Object payload) {
        KremaEvent event = new KremaEvent(eventName, payload);
        emitToFrontend(event);
        notifyListeners(event);
    }

    /**
     * Emits an event with no payload.
     */
    public void emit(String eventName) {
        emit(eventName, null);
    }

    /**
     * Emits a pre-built event.
     */
    public void emit(KremaEvent event) {
        emitToFrontend(event);
        notifyListeners(event);
    }

    private void emitToFrontend(KremaEvent event) {
        try {
            String payloadJson = MAPPER.writeValueAsString(Map.of(
                "payload", event.payload() != null ? event.payload() : Map.of(),
                "timestamp", event.timestamp()
            ));
            // __krema_emit expects (eventName, dataJson) as separate params
            String escapedPayload = payloadJson.replace("\\", "\\\\").replace("'", "\\'");
            String js = "window.__krema_emit && window.__krema_emit('" + event.name() + "', '" + escapedPayload + "')";
            engine.eval(js);
        } catch (JsonProcessingException e) {
            System.err.println("[Krema] Failed to serialize event: " + e.getMessage());
        }
    }

    private void notifyListeners(KremaEvent event) {
        // Notify specific listeners
        List<Consumer<KremaEvent>> specificListeners = listeners.get(event.name());
        if (specificListeners != null) {
            for (Consumer<KremaEvent> listener : specificListeners) {
                safeInvoke(listener, event);
            }
        }

        // Notify global listeners
        for (Consumer<KremaEvent> listener : globalListeners) {
            safeInvoke(listener, event);
        }
    }

    private void safeInvoke(Consumer<KremaEvent> listener, KremaEvent event) {
        try {
            listener.accept(event);
        } catch (Exception e) {
            System.err.println("[Krema] Event listener error: " + e.getMessage());
        }
    }

    /**
     * Adds a backend listener for a specific event.
     *
     * @return a Runnable that removes the listener when called
     */
    public Runnable on(String eventName, Consumer<KremaEvent> listener) {
        listeners.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> off(eventName, listener);
    }

    /**
     * Removes a backend listener for a specific event.
     */
    public void off(String eventName, Consumer<KremaEvent> listener) {
        List<Consumer<KremaEvent>> eventListeners = listeners.get(eventName);
        if (eventListeners != null) {
            eventListeners.remove(listener);
        }
    }

    /**
     * Adds a backend listener that receives all events.
     */
    public Runnable onAll(Consumer<KremaEvent> listener) {
        globalListeners.add(listener);
        return () -> globalListeners.remove(listener);
    }

    /**
     * Adds a one-time backend listener.
     */
    public void once(String eventName, Consumer<KremaEvent> listener) {
        Consumer<KremaEvent> wrapper = new Consumer<>() {
            @Override
            public void accept(KremaEvent event) {
                off(eventName, this);
                listener.accept(event);
            }
        };
        on(eventName, wrapper);
    }

    /**
     * Clears all backend listeners.
     */
    public void clear() {
        listeners.clear();
        globalListeners.clear();
    }
}
