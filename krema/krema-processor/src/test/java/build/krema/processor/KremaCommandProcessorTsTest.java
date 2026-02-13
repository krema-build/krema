package build.krema.processor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static build.krema.processor.CompilationHelper.compile;
import static build.krema.processor.CompilationHelper.source;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TypeScript type generation")
class KremaCommandProcessorTsTest {

    // ── Primitive & boxed type mappings ──────────────────────────────────

    @Nested
    @DisplayName("Primitive types")
    class PrimitiveTypes {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(source("test.PrimitiveCommands", """
                    package test;
                    import build.krema.core.KremaCommand;
                    public class PrimitiveCommands {
                        @KremaCommand public int returnInt() { return 0; }
                        @KremaCommand public long returnLong() { return 0L; }
                        @KremaCommand public double returnDouble() { return 0.0; }
                        @KremaCommand public float returnFloat() { return 0.0f; }
                        @KremaCommand public short returnShort() { return 0; }
                        @KremaCommand public byte returnByte() { return 0; }
                        @KremaCommand public boolean returnBoolean() { return false; }
                        @KremaCommand public char returnChar() { return 'a'; }
                        @KremaCommand public void returnVoid() {}
                    }
                    """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }

        @Test void intMapsToNumber() { assertContains("'returnInt': { result: number }"); }
        @Test void longMapsToNumber() { assertContains("'returnLong': { result: number }"); }
        @Test void doubleMapsToNumber() { assertContains("'returnDouble': { result: number }"); }
        @Test void floatMapsToNumber() { assertContains("'returnFloat': { result: number }"); }
        @Test void shortMapsToNumber() { assertContains("'returnShort': { result: number }"); }
        @Test void byteMapsToNumber() { assertContains("'returnByte': { result: number }"); }
        @Test void booleanMapsToBoolean() { assertContains("'returnBoolean': { result: boolean }"); }
        @Test void charMapsToString() { assertContains("'returnChar': { result: string }"); }
        @Test void voidMapsToVoid() { assertContains("'returnVoid': { result: void }"); }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    @Nested
    @DisplayName("Boxed types")
    class BoxedTypes {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(source("test.BoxedCommands", """
                    package test;
                    import build.krema.core.KremaCommand;
                    public class BoxedCommands {
                        @KremaCommand public Integer returnInteger() { return 0; }
                        @KremaCommand public Long returnLongBoxed() { return 0L; }
                        @KremaCommand public Double returnDoubleBoxed() { return 0.0; }
                        @KremaCommand public Float returnFloatBoxed() { return 0.0f; }
                        @KremaCommand public Short returnShortBoxed() { return 0; }
                        @KremaCommand public Byte returnByteBoxed() { return 0; }
                        @KremaCommand public Boolean returnBooleanBoxed() { return false; }
                        @KremaCommand public Character returnCharBoxed() { return 'a'; }
                        @KremaCommand public Void returnVoidBoxed() { return null; }
                    }
                    """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }

        @Test void integerMapsToNumber() { assertContains("'returnInteger': { result: number }"); }
        @Test void longMapsToNumber() { assertContains("'returnLongBoxed': { result: number }"); }
        @Test void doubleMapsToNumber() { assertContains("'returnDoubleBoxed': { result: number }"); }
        @Test void floatMapsToNumber() { assertContains("'returnFloatBoxed': { result: number }"); }
        @Test void shortMapsToNumber() { assertContains("'returnShortBoxed': { result: number }"); }
        @Test void byteMapsToNumber() { assertContains("'returnByteBoxed': { result: number }"); }
        @Test void booleanMapsToBoolean() { assertContains("'returnBooleanBoxed': { result: boolean }"); }
        @Test void characterMapsToString() { assertContains("'returnCharBoxed': { result: string }"); }
        @Test void voidBoxedMapsToVoid() { assertContains("'returnVoidBoxed': { result: void }"); }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── Common reference types ──────────────────────────────────────────

    @Nested
    @DisplayName("Common reference types")
    class CommonRefTypes {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(source("test.RefCommands", """
                    package test;
                    import build.krema.core.KremaCommand;
                    import java.nio.file.Path;
                    public class RefCommands {
                        @KremaCommand public String returnString() { return ""; }
                        @KremaCommand public Path returnPath() { return null; }
                        @KremaCommand public Object returnObject() { return null; }
                    }
                    """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }
        @Test void stringMapsToString() { assertContains("'returnString': { result: string }"); }
        @Test void pathMapsToString() { assertContains("'returnPath': { result: string }"); }
        @Test void objectMapsToUnknown() { assertContains("'returnObject': { result: unknown }"); }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── Collections ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Collections")
    class Collections {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(source("test.CollectionCommands", """
                    package test;
                    import build.krema.core.KremaCommand;
                    import java.util.*;
                    public class CollectionCommands {
                        @KremaCommand public List<String> returnStringList() { return null; }
                        @KremaCommand public Set<Integer> returnIntSet() { return null; }
                        @SuppressWarnings("rawtypes")
                        @KremaCommand public List returnRawList() { return null; }
                    }
                    """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }
        @Test void listStringMapsToStringArray() { assertContains("'returnStringList': { result: string[] }"); }
        @Test void setIntegerMapsToNumberArray() { assertContains("'returnIntSet': { result: number[] }"); }
        @Test void rawListMapsToUnknownArray() { assertContains("'returnRawList': { result: unknown[] }"); }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── Maps ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Maps")
    class Maps {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(source("test.MapCommands", """
                    package test;
                    import build.krema.core.KremaCommand;
                    import java.util.*;
                    public class MapCommands {
                        @KremaCommand public Map<String, Integer> returnTypedMap() { return null; }
                        @SuppressWarnings("rawtypes")
                        @KremaCommand public Map returnRawMap() { return null; }
                    }
                    """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }
        @Test void typedMapMapsToRecord() { assertContains("'returnTypedMap': { result: Record<string, number> }"); }
        @Test void rawMapMapsToRecordStringUnknown() { assertContains("'returnRawMap': { result: Record<string, unknown> }"); }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── CompletableFuture ───────────────────────────────────────────────

    @Nested
    @DisplayName("CompletableFuture")
    class CompletableFutureTests {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(source("test.AsyncCommands", """
                    package test;
                    import build.krema.core.KremaCommand;
                    import java.util.concurrent.CompletableFuture;
                    public class AsyncCommands {
                        @KremaCommand public CompletableFuture<String> asyncGreet() { return null; }
                    }
                    """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }
        @Test void unwrapsToInnerType() { assertContains("'asyncGreet': { result: string }"); }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── Enums ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Enums")
    class Enums {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(
                    source("test.Color", """
                        package test;
                        public enum Color { RED, GREEN, BLUE }
                        """),
                    source("test.EnumCommands", """
                        package test;
                        import build.krema.core.KremaCommand;
                        public class EnumCommands {
                            @KremaCommand public Color getColor() { return Color.RED; }
                            @KremaCommand public String colorName(String prefix, Color color) { return prefix + color.name(); }
                        }
                        """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }

        @Test void enumReturnIsMappedToUnion() {
            assertContains("result: 'RED' | 'GREEN' | 'BLUE'");
        }

        @Test void enumParamIsMappedToUnion() {
            assertContains("color: 'RED' | 'GREEN' | 'BLUE'");
        }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── Records ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Records")
    class Records {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(
                    source("test.UserInfo", """
                        package test;
                        public record UserInfo(String name, int age, boolean active) {}
                        """),
                    source("test.RecordCommands", """
                        package test;
                        import build.krema.core.KremaCommand;
                        public class RecordCommands {
                            @KremaCommand public UserInfo getUser() { return null; }
                        }
                        """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }

        @Test void generatesInterface() {
            assertContains("export interface UserInfo {");
        }

        @Test void hasNameField() { assertContains("name: string;"); }
        @Test void hasAgeField() { assertContains("age: number;"); }
        @Test void hasActiveField() { assertContains("active: boolean;"); }

        @Test void resultReferencesInterface() {
            assertContains("'getUser': { result: UserInfo }");
        }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── POJOs ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POJOs")
    class Pojos {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(
                    source("test.DeviceInfo", """
                        package test;
                        public class DeviceInfo {
                            public String model;
                            public int year;
                            private boolean connected;
                            public boolean isConnected() { return connected; }
                            public String getLabel() { return model + " " + year; }
                        }
                        """),
                    source("test.PojoCommands", """
                        package test;
                        import build.krema.core.KremaCommand;
                        public class PojoCommands {
                            @KremaCommand public DeviceInfo getDevice() { return null; }
                        }
                        """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }

        @Test void generatesInterface() {
            assertContains("export interface DeviceInfo {");
        }

        @Test void hasPublicField() { assertContains("model: string;"); }
        @Test void hasPublicIntField() { assertContains("year: number;"); }
        @Test void hasBooleanGetter() { assertContains("connected: boolean;"); }
        @Test void hasStringGetter() { assertContains("label: string;"); }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── POJO flattening ─────────────────────────────────────────────────

    @Nested
    @DisplayName("POJO flattening as args")
    class PojoFlattening {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(
                    source("test.CreateUserRequest", """
                        package test;
                        public record CreateUserRequest(String name, int age) {}
                        """),
                    source("test.FlattenCommands", """
                        package test;
                        import build.krema.core.KremaCommand;
                        public class FlattenCommands {
                            @KremaCommand public String createUser(CreateUserRequest req) { return "ok"; }
                        }
                        """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }

        @Test void fieldsAreFlattenedIntoArgs() {
            assertContains("'createUser': { args: { name: string; age: number }; result: string }");
        }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── Multiple parameters ─────────────────────────────────────────────

    @Nested
    @DisplayName("Multiple parameters")
    class MultipleParams {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(source("test.MultiParamCommands", """
                    package test;
                    import build.krema.core.KremaCommand;
                    public class MultiParamCommands {
                        @KremaCommand public String greet(String name, int count) { return ""; }
                    }
                    """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }

        @Test void eachParamBecomesArgsField() {
            assertContains("'greet': { args: { name: string; count: number }; result: string }");
        }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── Custom command name ─────────────────────────────────────────────

    @Nested
    @DisplayName("Custom command name")
    class CustomCommandName {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(source("test.CustomNameCommands", """
                    package test;
                    import build.krema.core.KremaCommand;
                    public class CustomNameCommands {
                        @KremaCommand("my-custom-command")
                        public String doSomething() { return "ok"; }
                    }
                    """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }

        @Test void usesAnnotationValueAsKey() {
            assertContains("'my-custom-command': { result: string }");
        }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── No-arg commands ─────────────────────────────────────────────────

    @Nested
    @DisplayName("No-arg commands")
    class NoArgCommands {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(source("test.NoArgCommands", """
                    package test;
                    import build.krema.core.KremaCommand;
                    public class NoArgCommands {
                        @KremaCommand public String ping() { return "pong"; }
                    }
                    """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }

        @Test void noArgsFieldInOutput() {
            assertNotNull(result.tsContent());
            assertContains("'ping': { result: string }");
            assertFalse(result.tsContent().contains("'ping': { args:"),
                "No-arg command should not have args field");
        }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── IpcRequest filtering ────────────────────────────────────────────

    @Nested
    @DisplayName("IpcRequest filtering")
    class IpcRequestFiltering {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(source("test.IpcCommands", """
                    package test;
                    import build.krema.core.KremaCommand;
                    import build.krema.core.ipc.IpcHandler;
                    public class IpcCommands {
                        @KremaCommand public String onlyIpc(IpcHandler.IpcRequest req) { return ""; }
                        @KremaCommand public String mixedIpc(String name, IpcHandler.IpcRequest req) { return ""; }
                    }
                    """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }

        @Test void ipcOnlyCommandHasNoArgs() {
            assertNotNull(result.tsContent());
            assertContains("'onlyIpc': { result: string }");
            assertFalse(result.tsContent().contains("'onlyIpc': { args:"),
                "IpcRequest-only command should have no args field");
        }

        @Test void mixedCommandExcludesIpcRequest() {
            assertContains("'mixedIpc': { args: { name: string }; result: string }");
        }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── Arrays ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Arrays")
    class Arrays {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(source("test.ArrayCommands", """
                    package test;
                    import build.krema.core.KremaCommand;
                    public class ArrayCommands {
                        @KremaCommand public int[] returnIntArray() { return null; }
                        @KremaCommand public String[] returnStringArray() { return null; }
                    }
                    """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }
        @Test void intArrayMapsToNumberArray() { assertContains("'returnIntArray': { result: number[] }"); }
        @Test void stringArrayMapsToStringArray() { assertContains("'returnStringArray': { result: string[] }"); }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── Nested complex types ────────────────────────────────────────────

    @Nested
    @DisplayName("Nested complex types")
    class NestedComplexTypes {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(
                    source("test.Address", """
                        package test;
                        public record Address(String street, String city) {}
                        """),
                    source("test.Person", """
                        package test;
                        public record Person(String name, Address address) {}
                        """),
                    source("test.NestedCommands", """
                        package test;
                        import build.krema.core.KremaCommand;
                        public class NestedCommands {
                            @KremaCommand public Person getPerson() { return null; }
                        }
                        """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }

        @Test void generatesPersonInterface() {
            assertContains("export interface Person {");
        }

        @Test void generatesAddressInterface() {
            assertContains("export interface Address {");
        }

        @Test void personReferencesAddress() {
            assertNotNull(result.tsContent());
            assertTrue(result.tsContent().contains("address: Address;"),
                "Person should reference Address by name in:\n" + result.tsContent());
        }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }

    // ── Output structure ────────────────────────────────────────────────

    @Nested
    @DisplayName("Output structure")
    class OutputStructure {

        static final CompilationHelper.CompilationResult result;

        static {
            try {
                result = compile(source("test.StructureCommands", """
                    package test;
                    import build.krema.core.KremaCommand;
                    public class StructureCommands {
                        @KremaCommand public String hello() { return ""; }
                    }
                    """));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test void compiles() { assertTrue(result.success()); }

        @Test void hasHeaderComment() {
            assertContains("// Auto-generated by Krema annotation processor. Do not edit.");
        }

        @Test void hasKremaCommandMap() {
            assertContains("export interface KremaCommandMap {");
        }

        @Test void hasNamespaceDeclaration() {
            assertContains("declare namespace krema {");
        }

        @Test void hasInvokeSignature() {
            assertContains("function invoke<K extends keyof KremaCommandMap>(");
        }

        @Test void hasSpreadArgsSignature() {
            assertContains("...args: KremaCommandMap[K] extends { args: infer A } ? [args: A] : [args?: Record<string, never>]");
        }

        @Test void hasPromiseReturn() {
            assertContains("): Promise<KremaCommandMap[K]['result']>;");
        }

        private void assertContains(String expected) {
            assertNotNull(result.tsContent(), "No .d.ts generated");
            assertTrue(result.tsContent().contains(expected),
                "Expected '" + expected + "' in:\n" + result.tsContent());
        }
    }
}
