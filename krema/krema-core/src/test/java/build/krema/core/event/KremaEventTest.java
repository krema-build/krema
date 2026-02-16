package build.krema.core.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KremaEvent")
class KremaEventTest {

    @Test
    @DisplayName("two-arg constructor sets name and payload with auto timestamp")
    void twoArgConstructor() {
        long before = System.currentTimeMillis();
        KremaEvent event = new KremaEvent("test", "data");
        long after = System.currentTimeMillis();

        assertEquals("test", event.name());
        assertEquals("data", event.payload());
        assertTrue(event.timestamp() >= before && event.timestamp() <= after);
    }

    @Test
    @DisplayName("three-arg constructor preserves explicit timestamp")
    void threeArgConstructor() {
        KremaEvent event = new KremaEvent("evt", "payload", 12345L);

        assertEquals("evt", event.name());
        assertEquals("payload", event.payload());
        assertEquals(12345L, event.timestamp());
    }

    @Test
    @DisplayName("of(name) creates event with null payload")
    void ofNameOnly() {
        KremaEvent event = KremaEvent.of("ready");

        assertEquals("ready", event.name());
        assertNull(event.payload());
    }

    @Test
    @DisplayName("of(name, payload) creates event with given payload")
    void ofNameAndPayload() {
        Map<String, Integer> payload = Map.of("x", 10);
        KremaEvent event = KremaEvent.of("move", payload);

        assertEquals("move", event.name());
        assertEquals(payload, event.payload());
    }

    @Test
    @DisplayName("payload can be any object type")
    void payloadCanBeAnyType() {
        KremaEvent withString = KremaEvent.of("a", "string");
        KremaEvent withInt = KremaEvent.of("b", 42);
        KremaEvent withMap = KremaEvent.of("c", Map.of("k", "v"));

        assertEquals("string", withString.payload());
        assertEquals(42, withInt.payload());
        assertEquals(Map.of("k", "v"), withMap.payload());
    }
}
