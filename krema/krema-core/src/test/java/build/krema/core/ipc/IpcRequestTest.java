package build.krema.core.ipc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import build.krema.core.ipc.IpcHandler.IpcRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IpcRequest")
class IpcRequestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("getCommand returns the command name")
    void getCommand() {
        IpcRequest request = new IpcRequest("test_cmd", null);
        assertEquals("test_cmd", request.getCommand());
    }

    @Test
    @DisplayName("getString returns value for existing key")
    void getStringExistingKey() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("name", "Alice");

        IpcRequest request = new IpcRequest("cmd", args);
        assertEquals("Alice", request.getString("name"));
    }

    @Test
    @DisplayName("getString returns null for missing key")
    void getStringMissingKey() {
        ObjectNode args = MAPPER.createObjectNode();
        IpcRequest request = new IpcRequest("cmd", args);
        assertNull(request.getString("nonexistent"));
    }

    @Test
    @DisplayName("getString returns null when args is null")
    void getStringNullArgs() {
        IpcRequest request = new IpcRequest("cmd", null);
        assertNull(request.getString("name"));
    }

    @Test
    @DisplayName("getInt returns value for existing key")
    void getIntExistingKey() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("count", 42);

        IpcRequest request = new IpcRequest("cmd", args);
        assertEquals(42, request.getInt("count", 0));
    }

    @Test
    @DisplayName("getInt returns default for missing key")
    void getIntMissingKey() {
        ObjectNode args = MAPPER.createObjectNode();
        IpcRequest request = new IpcRequest("cmd", args);
        assertEquals(99, request.getInt("missing", 99));
    }

    @Test
    @DisplayName("getInt returns default when args is null")
    void getIntNullArgs() {
        IpcRequest request = new IpcRequest("cmd", null);
        assertEquals(-1, request.getInt("count", -1));
    }

    @Test
    @DisplayName("getBoolean returns value for existing key")
    void getBooleanExistingKey() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("enabled", true);

        IpcRequest request = new IpcRequest("cmd", args);
        assertTrue(request.getBoolean("enabled", false));
    }

    @Test
    @DisplayName("getBoolean returns default for missing key")
    void getBooleanMissingKey() {
        ObjectNode args = MAPPER.createObjectNode();
        IpcRequest request = new IpcRequest("cmd", args);
        assertTrue(request.getBoolean("missing", true));
    }

    @Test
    @DisplayName("getDouble returns value for existing key")
    void getDoubleExistingKey() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("ratio", 3.14);

        IpcRequest request = new IpcRequest("cmd", args);
        assertEquals(3.14, request.getDouble("ratio", 0.0), 0.001);
    }

    @Test
    @DisplayName("getDouble returns default for missing key")
    void getDoubleMissingKey() {
        ObjectNode args = MAPPER.createObjectNode();
        IpcRequest request = new IpcRequest("cmd", args);
        assertEquals(1.5, request.getDouble("missing", 1.5), 0.001);
    }

    @Test
    @DisplayName("getArgsAs deserializes to POJO")
    void getArgsAsPojo() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("x", 10);
        args.put("y", 20);

        IpcRequest request = new IpcRequest("cmd", args);
        Point point = request.getArgsAs(Point.class);

        assertEquals(10, point.x);
        assertEquals(20, point.y);
    }

    public static class Point {
        public int x;
        public int y;
    }
}
