package build.krema.plugin.sql;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.*;

import build.krema.core.CommandRegistry;
import build.krema.core.event.EventEmitter;
import build.krema.core.plugin.PluginContext;
import build.krema.core.util.Logger;
import build.krema.core.window.WindowManager;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SQL Plugin")
class SqlPluginTest {

    private SqlPlugin plugin;
    private SqlPlugin.SqlCommands commands;

    @BeforeEach
    void setUp() {
        plugin = new SqlPlugin();
        plugin.initialize(new StubPluginContext());
        commands = (SqlPlugin.SqlCommands) plugin.getCommandHandlers().get(0);
    }

    @AfterEach
    void tearDown() {
        plugin.shutdown();
    }

    @Nested
    @DisplayName("Plugin metadata")
    class Metadata {

        @Test
        @DisplayName("returns correct ID")
        void returnsCorrectId() {
            assertEquals("krema.sql", plugin.getId());
        }

        @Test
        @DisplayName("returns correct name")
        void returnsCorrectName() {
            assertEquals("SQL", plugin.getName());
        }

        @Test
        @DisplayName("returns required permissions")
        void returnsRequiredPermissions() {
            assertEquals(List.of("sql:read", "sql:write"), plugin.getRequiredPermissions());
        }

        @Test
        @DisplayName("returns command handlers")
        void returnsCommandHandlers() {
            assertFalse(plugin.getCommandHandlers().isEmpty());
        }
    }

    @Nested
    @DisplayName("sql:open")
    class Open {

        @Test
        @DisplayName("opens in-memory database")
        void opensInMemoryDatabase() throws Exception {
            boolean result = await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:")));
            assertTrue(result);
        }

        @Test
        @DisplayName("throws when database already open")
        void throwsWhenAlreadyOpen() throws Exception {
            await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:")));
            assertThrows(IllegalStateException.class, () ->
                await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:"))));
        }
    }

    @Nested
    @DisplayName("sql:execute")
    class Execute {

        @BeforeEach
        void openDb() throws Exception {
            await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:")));
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, age INTEGER)", null)));
        }

        @Test
        @DisplayName("inserts row and returns affected count")
        void insertsRow() throws Exception {
            SqlPlugin.SqlExecuteResult result = await(commands.execute(
                new SqlPlugin.SqlExecuteRequest("testdb",
                    "INSERT INTO users (name, age) VALUES (?, ?)", List.of("Alice", 30))));
            assertEquals(1, result.rowsAffected());
            assertTrue(result.lastInsertId() > 0);
        }

        @Test
        @DisplayName("updates rows and returns affected count")
        void updatesRows() throws Exception {
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO users (name, age) VALUES (?, ?)", List.of("Alice", 30))));
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO users (name, age) VALUES (?, ?)", List.of("Bob", 25))));

            SqlPlugin.SqlExecuteResult result = await(commands.execute(
                new SqlPlugin.SqlExecuteRequest("testdb",
                    "UPDATE users SET age = ? WHERE age < ?", List.of(99, 31))));
            assertEquals(2, result.rowsAffected());
        }

        @Test
        @DisplayName("deletes rows and returns affected count")
        void deletesRows() throws Exception {
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO users (name, age) VALUES (?, ?)", List.of("Alice", 30))));

            SqlPlugin.SqlExecuteResult result = await(commands.execute(
                new SqlPlugin.SqlExecuteRequest("testdb",
                    "DELETE FROM users WHERE name = ?", List.of("Alice"))));
            assertEquals(1, result.rowsAffected());
        }

        @Test
        @DisplayName("throws on SQL syntax error")
        void throwsOnSyntaxError() {
            assertThrows(SQLException.class, () ->
                await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                    "INVALID SQL STATEMENT", null))));
        }

        @Test
        @DisplayName("throws when database not open")
        void throwsWhenNotOpen() {
            assertThrows(IllegalStateException.class, () ->
                await(commands.execute(new SqlPlugin.SqlExecuteRequest("nonexistent",
                    "SELECT 1", null))));
        }
    }

    @Nested
    @DisplayName("sql:select")
    class Select {

        @BeforeEach
        void openAndSeed() throws Exception {
            await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:")));
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, age INTEGER)", null)));
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO users (name, age) VALUES (?, ?)", List.of("Alice", 30))));
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO users (name, age) VALUES (?, ?)", List.of("Bob", 25))));
        }

        @Test
        @DisplayName("returns all rows")
        void returnsAllRows() throws Exception {
            List<Map<String, Object>> rows = await(commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT * FROM users ORDER BY name", null)));
            assertEquals(2, rows.size());
            assertEquals("Alice", rows.get(0).get("name"));
            assertEquals("Bob", rows.get(1).get("name"));
        }

        @Test
        @DisplayName("returns filtered rows with parameters")
        void returnsFilteredRows() throws Exception {
            List<Map<String, Object>> rows = await(commands.select(
                new SqlPlugin.SqlSelectRequest("testdb",
                    "SELECT name FROM users WHERE age > ?", List.of(27))));
            assertEquals(1, rows.size());
            assertEquals("Alice", rows.get(0).get("name"));
        }

        @Test
        @DisplayName("returns empty list for no matches")
        void returnsEmptyForNoMatches() throws Exception {
            List<Map<String, Object>> rows = await(commands.select(
                new SqlPlugin.SqlSelectRequest("testdb",
                    "SELECT * FROM users WHERE age > ?", List.of(100))));
            assertTrue(rows.isEmpty());
        }
    }

    @Nested
    @DisplayName("sql:batch")
    class Batch {

        @BeforeEach
        void openDb() throws Exception {
            await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:")));
        }

        @Test
        @DisplayName("executes multiple statements in transaction")
        void executesInTransaction() throws Exception {
            boolean result = await(commands.batch(new SqlPlugin.SqlBatchRequest("testdb", List.of(
                "CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT)",
                "INSERT INTO items (name) VALUES ('A')",
                "INSERT INTO items (name) VALUES ('B')"
            ))));
            assertTrue(result);

            List<Map<String, Object>> rows = await(commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT * FROM items", null)));
            assertEquals(2, rows.size());
        }

        @Test
        @DisplayName("rolls back on error")
        void rollsBackOnError() throws Exception {
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT NOT NULL)", null)));
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO items (name) VALUES (?)", List.of("existing"))));

            assertThrows(SQLException.class, () ->
                await(commands.batch(new SqlPlugin.SqlBatchRequest("testdb", List.of(
                    "INSERT INTO items (name) VALUES ('A')",
                    "INSERT INTO items (name) VALUES (NULL)" // violates NOT NULL
                )))));

            // Only the original row should remain
            List<Map<String, Object>> rows = await(commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT * FROM items", null)));
            assertEquals(1, rows.size());
        }
    }

    @Nested
    @DisplayName("sql:close")
    class Close {

        @Test
        @DisplayName("closes open database")
        void closesOpenDatabase() throws Exception {
            await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:")));
            assertTrue(await(commands.close("testdb")));
        }

        @Test
        @DisplayName("returns false for nonexistent database")
        void returnsFalseForNonexistent() throws Exception {
            assertFalse(await(commands.close("nonexistent")));
        }
    }

    @Nested
    @DisplayName("Connection lifecycle races")
    class ConnectionLifecycleRaces {

        @Test
        @DisplayName("duplicate open is rejected")
        void duplicateOpenIsRejected() throws Exception {
            await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:")));

            IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:"))));
            assertTrue(error.getMessage().contains("already open"));
        }

        @Test
        @DisplayName("duplicate open while opening is rejected as opening")
        void duplicateOpenWhileOpeningIsRejectedAsOpening() throws Exception {
            putOpeningHandle(plugin, "testdb");

            IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:"))));
            assertEquals("Database 'testdb' is opening; retry after open completes", error.getMessage());
        }

        @Test
        @DisplayName("reopen while close is queued is rejected as closing")
        void reopenWhileClosingIsRejected() throws Exception {
            await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:")));
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb", "CREATE TABLE items (id INTEGER)", null)));

            CompletableFuture<List<Map<String, Object>>> slowSelect = commands.select(new SqlPlugin.SqlSelectRequest(
                "testdb",
                "WITH RECURSIVE cnt(x) AS (VALUES(1) UNION ALL SELECT x + 1 FROM cnt WHERE x < 2000) "
                    + "SELECT sum(a.x * b.x) AS total FROM cnt a, cnt b",
                null));
            CompletableFuture<Boolean> close = commands.close("testdb");
            CompletableFuture<Boolean> reopen = commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:"));

            IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                await(reopen));
            assertEquals("Database 'testdb' is closing; retry after close completes", error.getMessage());

            await(slowSelect);
            assertTrue(await(close));
        }

        @Test
        @DisplayName("close during in-flight open closes after open completes")
        void closeDuringInFlightOpenClosesAfterOpenCompletes() throws Exception {
            CompletableFuture<Boolean> open = commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:"));
            CompletableFuture<Boolean> close = commands.close("testdb");

            assertTrue(await(open));
            assertTrue(await(close));
            assertThrows(IllegalStateException.class, () ->
                await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb", "SELECT 1", null))));
        }

        @Test
        @DisplayName("failed open cleans up and close returns false")
        void openFailThenCloseCleansUp() throws Exception {
            assertThrows(SQLException.class, () ->
                await(commands.open(new SqlPlugin.SqlOpenRequest("broken", "/no/such/parent/krema.db"))));

            assertFalse(await(commands.close("broken")));
            assertTrue(await(commands.open(new SqlPlugin.SqlOpenRequest("broken", ":memory:"))));
        }

        @Test
        @DisplayName("operations after close reject as not open")
        void operationAfterCloseRejects() throws Exception {
            await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:")));
            assertTrue(await(commands.close("testdb")));

            IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb", "SELECT 1", null))));
            assertEquals("No database named 'testdb' is open", error.getMessage());
        }

        @Test
        @DisplayName("double close disposes once and returns false on the second close")
        void doubleCloseReturnsFalseOnSecondClose() throws Exception {
            await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:")));

            CompletableFuture<Boolean> firstClose = commands.close("testdb");
            CompletableFuture<Boolean> secondClose = commands.close("testdb");

            assertTrue(await(firstClose));
            assertFalse(await(secondClose));
        }

        @Test
        @DisplayName("statements execute in FIFO submission order")
        void statementsExecuteInSubmissionOrder() throws Exception {
            await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:")));
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "CREATE TABLE log (id INTEGER PRIMARY KEY AUTOINCREMENT, value TEXT)", null)));

            CompletableFuture<SqlPlugin.SqlExecuteResult> first = commands.execute(
                new SqlPlugin.SqlExecuteRequest("testdb", "INSERT INTO log (value) VALUES ('first')", null));
            CompletableFuture<SqlPlugin.SqlExecuteResult> second = commands.execute(
                new SqlPlugin.SqlExecuteRequest("testdb", "INSERT INTO log (value) VALUES ('second')", null));
            CompletableFuture<SqlPlugin.SqlExecuteResult> third = commands.execute(
                new SqlPlugin.SqlExecuteRequest("testdb", "INSERT INTO log (value) VALUES ('third')", null));

            await(first);
            await(second);
            await(third);

            List<Map<String, Object>> rows = await(commands.select(new SqlPlugin.SqlSelectRequest(
                "testdb", "SELECT value FROM log ORDER BY id", null)));
            assertEquals(List.of("first", "second", "third"),
                rows.stream().map(row -> row.get("value")).toList());
        }
    }

    @Nested
    @DisplayName("Plugin lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("shutdown closes all connections")
        void shutdownClosesConnections() throws Exception {
            await(commands.open(new SqlPlugin.SqlOpenRequest("db1", ":memory:")));
            await(commands.open(new SqlPlugin.SqlOpenRequest("db2", ":memory:")));
            plugin.shutdown();

            // After shutdown, connections should no longer be accessible
            assertThrows(IllegalStateException.class, () ->
                await(commands.execute(new SqlPlugin.SqlExecuteRequest("db1", "SELECT 1", null))));
        }
    }

    @Nested
    @DisplayName("Parameter binding")
    class ParameterBinding {

        @BeforeEach
        void openDb() throws Exception {
            await(commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:")));
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "CREATE TABLE data (id INTEGER PRIMARY KEY, text_val TEXT, int_val INTEGER, real_val REAL, bool_val INTEGER)",
                null)));
        }

        @Test
        @DisplayName("binds null parameters")
        void bindsNull() throws Exception {
            java.util.ArrayList<Object> params = new java.util.ArrayList<>();
            params.add(null);
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO data (text_val) VALUES (?)", params)));
            List<Map<String, Object>> rows = await(commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT text_val FROM data", null)));
            assertNull(rows.get(0).get("text_val"));
        }

        @Test
        @DisplayName("binds string parameters")
        void bindsStrings() throws Exception {
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO data (text_val) VALUES (?)", List.of("hello"))));
            List<Map<String, Object>> rows = await(commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT text_val FROM data", null)));
            assertEquals("hello", rows.get(0).get("text_val"));
        }

        @Test
        @DisplayName("binds integer parameters")
        void bindsIntegers() throws Exception {
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO data (int_val) VALUES (?)", List.of(42))));
            List<Map<String, Object>> rows = await(commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT int_val FROM data", null)));
            assertEquals(42, ((Number) rows.get(0).get("int_val")).intValue());
        }

        @Test
        @DisplayName("binds double parameters")
        void bindsDoubles() throws Exception {
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO data (real_val) VALUES (?)", List.of(3.14))));
            List<Map<String, Object>> rows = await(commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT real_val FROM data", null)));
            assertEquals(3.14, ((Number) rows.get(0).get("real_val")).doubleValue(), 0.001);
        }

        @Test
        @DisplayName("binds boolean parameters")
        void bindsBooleans() throws Exception {
            await(commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO data (bool_val) VALUES (?)", List.of(true))));
            List<Map<String, Object>> rows = await(commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT bool_val FROM data", null)));
            assertNotNull(rows.get(0).get("bool_val"));
        }
    }

    private static class StubPluginContext implements PluginContext {
        @Override public WindowManager getWindowManager() { return null; }
        @Override public EventEmitter getEventEmitter() { return null; }
        @Override public CommandRegistry getCommandRegistry() { return null; }
        @Override public Logger getLogger(String name) { return new Logger(name); }
        @Override public Path getPluginDataDir() { return Path.of(System.getProperty("java.io.tmpdir"), "krema-test"); }
        @Override public Path getAppDataDir() { return Path.of(System.getProperty("java.io.tmpdir"), "krema-test"); }
        @Override public Map<String, Object> getConfig() { return Map.of(); }
        @Override public boolean hasPermission(String permission) { return true; }
        @Override public String getAppName() { return "test-app"; }
        @Override public String getAppVersion() { return "1.0.0"; }
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        } catch (TimeoutException e) {
            throw new AssertionError("Timed out waiting for SQL command future", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void putOpeningHandle(SqlPlugin plugin, String name) throws Exception {
        Class<?> handleClass = Class.forName("build.krema.plugin.sql.SqlPlugin$ConnectionHandle");
        Constructor<?> constructor = handleClass.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        Object handle = constructor.newInstance(name);

        Field handlesField = SqlPlugin.class.getDeclaredField("handles");
        handlesField.setAccessible(true);
        ConcurrentHashMap<String, Object> handles =
            (ConcurrentHashMap<String, Object>) handlesField.get(plugin);
        handles.put(name, handle);
    }
}
