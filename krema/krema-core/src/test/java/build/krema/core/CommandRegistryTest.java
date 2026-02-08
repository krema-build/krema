package build.krema.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import build.krema.core.CommandRegistry;
import build.krema.core.KremaCommand;
import build.krema.core.CommandRegistry.CommandException;
import build.krema.core.ipc.IpcHandler.IpcRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CommandRegistry")
class CommandRegistryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
    }

    @Test
    @DisplayName("register adds commands and size/hasCommand/getCommandNames work")
    void registerAndQuery() {
        registry.register(new TestCommands());

        assertTrue(registry.size() > 0);
        assertTrue(registry.hasCommand("greet"));
        assertTrue(registry.getCommandNames().contains("greet"));
    }

    @Test
    @DisplayName("invoke with string arg returns correct result")
    void invokeWithStringArg() throws CommandException {
        registry.register(new TestCommands());

        ObjectNode args = MAPPER.createObjectNode();
        args.put("name", "World");
        IpcRequest request = new IpcRequest("greet", args);

        Object result = registry.invoke(request);
        assertEquals("Hello, World!", result);
    }

    @Test
    @DisplayName("invoke with int args returns correct result")
    void invokeWithIntArgs() throws CommandException {
        registry.register(new TestCommands());

        ObjectNode args = MAPPER.createObjectNode();
        args.put("a", 5);
        args.put("b", 3);
        IpcRequest request = new IpcRequest("custom_add", args);

        Object result = registry.invoke(request);
        assertEquals(8, result);
    }

    @Test
    @DisplayName("custom command name via @KremaCommand annotation")
    void customCommandName() {
        registry.register(new TestCommands());
        assertTrue(registry.hasCommand("custom_add"));
    }

    @Test
    @DisplayName("unknown command throws CommandException")
    void unknownCommandThrows() {
        assertThrows(CommandException.class, () ->
                registry.invoke(new IpcRequest("nonexistent", null)));
    }

    @Test
    @DisplayName("duplicate command throws IllegalArgumentException")
    void duplicateCommandThrows() {
        registry.register(new TestCommands());
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new TestCommands()));
    }

    @Test
    @DisplayName("POJO parameter deserialization")
    void pojoParameter() throws CommandException {
        registry.register(new PojoCommands());

        ObjectNode args = MAPPER.createObjectNode();
        args.put("x", 10);
        args.put("y", 20);
        IpcRequest request = new IpcRequest("computeSum", args);

        Object result = registry.invoke(request);
        assertEquals(30, result);
    }

    @Test
    @DisplayName("IpcRequest parameter injection")
    void ipcRequestInjection() throws CommandException {
        registry.register(new RequestCommands());

        ObjectNode args = MAPPER.createObjectNode();
        args.put("data", "test");
        IpcRequest request = new IpcRequest("echoCommand", args);

        Object result = registry.invoke(request);
        assertEquals("echoCommand", result);
    }

    // --- Test command classes ---

    public static class TestCommands {
        @KremaCommand
        public String greet(String name) {
            return "Hello, " + name + "!";
        }

        @KremaCommand("custom_add")
        public int add(int a, int b) {
            return a + b;
        }
    }

    public static class PojoCommands {
        @KremaCommand
        public int computeSum(Coords coords) {
            return coords.x + coords.y;
        }
    }

    public static class Coords {
        public int x;
        public int y;
    }

    public static class RequestCommands {
        @KremaCommand
        public String echoCommand(IpcRequest request) {
            return request.getCommand();
        }
    }

    // --- Reflective path tests ---

    @Nested
    @DisplayName("reflective command path")
    class ReflectivePath {

        private CommandRegistry reflectiveRegistry;

        @BeforeEach
        void setUp() throws Exception {
            reflectiveRegistry = new CommandRegistry();
            Field registrarsField = CommandRegistry.class.getDeclaredField("registrars");
            registrarsField.setAccessible(true);
            ((Map<?, ?>) registrarsField.get(reflectiveRegistry)).clear();
        }

        @Test
        @DisplayName("string parameter resolved by name")
        void stringParam() throws CommandException {
            reflectiveRegistry.register(new ReflectiveCommands());

            ObjectNode args = MAPPER.createObjectNode();
            args.put("name", "World");
            IpcRequest request = new IpcRequest("greet", args);

            assertEquals("Hello, World!", reflectiveRegistry.invoke(request));
        }

        @Test
        @DisplayName("int parameters resolved by name")
        void intParams() throws CommandException {
            reflectiveRegistry.register(new ReflectiveCommands());

            ObjectNode args = MAPPER.createObjectNode();
            args.put("a", 10);
            args.put("b", 20);
            IpcRequest request = new IpcRequest("reflective_add", args);

            assertEquals(30, reflectiveRegistry.invoke(request));
        }

        @Test
        @DisplayName("missing int arg defaults to 0")
        void missingIntArgDefault() throws CommandException {
            reflectiveRegistry.register(new ReflectiveCommands());

            ObjectNode args = MAPPER.createObjectNode();
            args.put("a", 5);
            IpcRequest request = new IpcRequest("reflective_add", args);

            assertEquals(5, reflectiveRegistry.invoke(request));
        }

        @Test
        @DisplayName("boolean parameter")
        void booleanParam() throws CommandException {
            reflectiveRegistry.register(new ReflectiveCommands());

            ObjectNode args = MAPPER.createObjectNode();
            args.put("flag", true);
            IpcRequest request = new IpcRequest("checkFlag", args);

            assertEquals("yes", reflectiveRegistry.invoke(request));
        }

        @Test
        @DisplayName("double parameter")
        void doubleParam() throws CommandException {
            reflectiveRegistry.register(new ReflectiveCommands());

            ObjectNode args = MAPPER.createObjectNode();
            args.put("value", 3.14);
            IpcRequest request = new IpcRequest("doubleIt", args);

            assertEquals(6.28, (double) reflectiveRegistry.invoke(request), 0.001);
        }

        @Test
        @DisplayName("long parameter")
        void longParam() throws CommandException {
            reflectiveRegistry.register(new ReflectiveCommands());

            ObjectNode args = MAPPER.createObjectNode();
            args.put("value", 9999999999L);
            IpcRequest request = new IpcRequest("longValue", args);

            assertEquals(9999999999L, reflectiveRegistry.invoke(request));
        }

        @Test
        @DisplayName("POJO single parameter deserializes entire args")
        void pojoParam() throws CommandException {
            reflectiveRegistry.register(new ReflectivePojoCommands());

            ObjectNode args = MAPPER.createObjectNode();
            args.put("x", 3);
            args.put("y", 7);
            IpcRequest request = new IpcRequest("sumCoords", args);

            assertEquals(10, reflectiveRegistry.invoke(request));
        }

        @Test
        @DisplayName("IpcRequest parameter injection")
        void ipcRequestParam() throws CommandException {
            reflectiveRegistry.register(new ReflectiveCommands());

            IpcRequest request = new IpcRequest("echoCmd", null);

            assertEquals("echoCmd", reflectiveRegistry.invoke(request));
        }

        @Test
        @DisplayName("no-arg method")
        void noArgMethod() throws CommandException {
            reflectiveRegistry.register(new ReflectiveCommands());

            IpcRequest request = new IpcRequest("noArgs", null);

            assertEquals("ok", reflectiveRegistry.invoke(request));
        }

        @Test
        @DisplayName("null args with string parameter returns null for that param")
        void nullArgsStringParam() throws CommandException {
            reflectiveRegistry.register(new ReflectiveCommands());

            IpcRequest request = new IpcRequest("greet", null);

            assertEquals("Hello, null!", reflectiveRegistry.invoke(request));
        }

        @Test
        @DisplayName("command that throws wraps cause in CommandException")
        void throwingCommand() {
            reflectiveRegistry.register(new ReflectiveCommands());

            IpcRequest request = new IpcRequest("explode", null);

            CommandException ex = assertThrows(CommandException.class,
                    () -> reflectiveRegistry.invoke(request));
            assertTrue(ex.getMessage().contains("boom"));
        }

        @Test
        @DisplayName("custom command name via annotation value")
        void customName() {
            reflectiveRegistry.register(new ReflectiveCommands());
            assertTrue(reflectiveRegistry.hasCommand("reflective_add"));
        }

        @Test
        @DisplayName("float parameter")
        void floatParam() throws CommandException {
            reflectiveRegistry.register(new AllTypesCommands());

            ObjectNode args = MAPPER.createObjectNode();
            args.put("value", 2.5);
            IpcRequest request = new IpcRequest("floatVal", args);

            assertEquals(2.5f, (float) reflectiveRegistry.invoke(request), 0.001f);
        }

        @Test
        @DisplayName("short parameter")
        void shortParam() throws CommandException {
            reflectiveRegistry.register(new AllTypesCommands());

            ObjectNode args = MAPPER.createObjectNode();
            args.put("value", 42);
            IpcRequest request = new IpcRequest("shortVal", args);

            assertEquals((short) 42, reflectiveRegistry.invoke(request));
        }

        @Test
        @DisplayName("byte parameter")
        void byteParam() throws CommandException {
            reflectiveRegistry.register(new AllTypesCommands());

            ObjectNode args = MAPPER.createObjectNode();
            args.put("value", 7);
            IpcRequest request = new IpcRequest("byteVal", args);

            assertEquals((byte) 7, reflectiveRegistry.invoke(request));
        }

        @Test
        @DisplayName("char parameter")
        void charParam() throws CommandException {
            reflectiveRegistry.register(new AllTypesCommands());

            ObjectNode args = MAPPER.createObjectNode();
            args.put("value", "A");
            IpcRequest request = new IpcRequest("charVal", args);

            assertEquals('A', reflectiveRegistry.invoke(request));
        }

        @Test
        @DisplayName("char parameter with empty string returns null char")
        void charParamEmpty() throws CommandException {
            reflectiveRegistry.register(new AllTypesCommands());

            ObjectNode args = MAPPER.createObjectNode();
            args.put("value", "");
            IpcRequest request = new IpcRequest("charVal", args);

            assertEquals('\0', reflectiveRegistry.invoke(request));
        }

        @Test
        @DisplayName("complex type parameter in multi-param method is deserialized from args node")
        void complexTypeMultiParam() throws CommandException {
            reflectiveRegistry.register(new AllTypesCommands());

            ObjectNode args = MAPPER.createObjectNode();
            args.put("label", "point");
            ObjectNode coordNode = MAPPER.createObjectNode();
            coordNode.put("x", 5);
            coordNode.put("y", 10);
            args.set("coord", coordNode);
            IpcRequest request = new IpcRequest("labeledCoord", args);

            assertEquals("point:(5,10)", reflectiveRegistry.invoke(request));
        }

        @Test
        @DisplayName("missing float arg defaults to 0.0f")
        void missingFloatDefault() throws CommandException {
            reflectiveRegistry.register(new AllTypesCommands());

            IpcRequest request = new IpcRequest("floatVal", MAPPER.createObjectNode());

            assertEquals(0.0f, (float) reflectiveRegistry.invoke(request), 0.001f);
        }

        @Test
        @DisplayName("missing boolean arg defaults to false")
        void missingBooleanDefault() throws CommandException {
            reflectiveRegistry.register(new ReflectiveCommands());

            IpcRequest request = new IpcRequest("checkFlag", MAPPER.createObjectNode());

            assertEquals("no", reflectiveRegistry.invoke(request));
        }
    }

    public static class ReflectiveCommands {
        @KremaCommand
        public String greet(String name) {
            return "Hello, " + name + "!";
        }

        @KremaCommand("reflective_add")
        public int add(int a, int b) {
            return a + b;
        }

        @KremaCommand
        public String checkFlag(boolean flag) {
            return flag ? "yes" : "no";
        }

        @KremaCommand
        public double doubleIt(double value) {
            return value * 2;
        }

        @KremaCommand
        public long longValue(long value) {
            return value;
        }

        @KremaCommand
        public String echoCmd(IpcRequest request) {
            return request.getCommand();
        }

        @KremaCommand
        public String noArgs() {
            return "ok";
        }

        @KremaCommand
        public void explode() {
            throw new RuntimeException("boom");
        }
    }

    public static class ReflectivePojoCommands {
        @KremaCommand
        public int sumCoords(Coords coords) {
            return coords.x + coords.y;
        }
    }

    public static class AllTypesCommands {
        @KremaCommand
        public float floatVal(float value) {
            return value;
        }

        @KremaCommand
        public short shortVal(short value) {
            return value;
        }

        @KremaCommand
        public byte byteVal(byte value) {
            return value;
        }

        @KremaCommand
        public char charVal(char value) {
            return value;
        }

        @KremaCommand
        public String labeledCoord(String label, Coords coord) {
            return label + ":(" + coord.x + "," + coord.y + ")";
        }
    }
}
