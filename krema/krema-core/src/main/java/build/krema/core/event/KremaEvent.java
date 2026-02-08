package build.krema.core.event;

/**
 * Represents an event that can be emitted to the frontend.
 */
public record KremaEvent(String name, Object payload, long timestamp) {

    public KremaEvent(String name, Object payload) {
        this(name, payload, System.currentTimeMillis());
    }

    public static KremaEvent of(String name) {
        return new KremaEvent(name, null);
    }

    public static KremaEvent of(String name, Object payload) {
        return new KremaEvent(name, payload);
    }
}
