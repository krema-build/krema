package build.krema.plugin.sql;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

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
            boolean result = commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:"));
            assertTrue(result);
        }

        @Test
        @DisplayName("throws when database already open")
        void throwsWhenAlreadyOpen() throws Exception {
            commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:"));
            assertThrows(IllegalStateException.class, () ->
                commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:")));
        }
    }

    @Nested
    @DisplayName("sql:execute")
    class Execute {

        @BeforeEach
        void openDb() throws Exception {
            commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:"));
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, age INTEGER)", null));
        }

        @Test
        @DisplayName("inserts row and returns affected count")
        void insertsRow() throws Exception {
            SqlPlugin.SqlExecuteResult result = commands.execute(
                new SqlPlugin.SqlExecuteRequest("testdb",
                    "INSERT INTO users (name, age) VALUES (?, ?)", List.of("Alice", 30)));
            assertEquals(1, result.rowsAffected());
            assertTrue(result.lastInsertId() > 0);
        }

        @Test
        @DisplayName("updates rows and returns affected count")
        void updatesRows() throws Exception {
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO users (name, age) VALUES (?, ?)", List.of("Alice", 30)));
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO users (name, age) VALUES (?, ?)", List.of("Bob", 25)));

            SqlPlugin.SqlExecuteResult result = commands.execute(
                new SqlPlugin.SqlExecuteRequest("testdb",
                    "UPDATE users SET age = ? WHERE age < ?", List.of(99, 31)));
            assertEquals(2, result.rowsAffected());
        }

        @Test
        @DisplayName("deletes rows and returns affected count")
        void deletesRows() throws Exception {
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO users (name, age) VALUES (?, ?)", List.of("Alice", 30)));

            SqlPlugin.SqlExecuteResult result = commands.execute(
                new SqlPlugin.SqlExecuteRequest("testdb",
                    "DELETE FROM users WHERE name = ?", List.of("Alice")));
            assertEquals(1, result.rowsAffected());
        }

        @Test
        @DisplayName("throws on SQL syntax error")
        void throwsOnSyntaxError() {
            assertThrows(SQLException.class, () ->
                commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                    "INVALID SQL STATEMENT", null)));
        }

        @Test
        @DisplayName("throws when database not open")
        void throwsWhenNotOpen() {
            assertThrows(IllegalStateException.class, () ->
                commands.execute(new SqlPlugin.SqlExecuteRequest("nonexistent",
                    "SELECT 1", null)));
        }
    }

    @Nested
    @DisplayName("sql:select")
    class Select {

        @BeforeEach
        void openAndSeed() throws Exception {
            commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:"));
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, age INTEGER)", null));
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO users (name, age) VALUES (?, ?)", List.of("Alice", 30)));
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO users (name, age) VALUES (?, ?)", List.of("Bob", 25)));
        }

        @Test
        @DisplayName("returns all rows")
        void returnsAllRows() throws Exception {
            List<Map<String, Object>> rows = commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT * FROM users ORDER BY name", null));
            assertEquals(2, rows.size());
            assertEquals("Alice", rows.get(0).get("name"));
            assertEquals("Bob", rows.get(1).get("name"));
        }

        @Test
        @DisplayName("returns filtered rows with parameters")
        void returnsFilteredRows() throws Exception {
            List<Map<String, Object>> rows = commands.select(
                new SqlPlugin.SqlSelectRequest("testdb",
                    "SELECT name FROM users WHERE age > ?", List.of(27)));
            assertEquals(1, rows.size());
            assertEquals("Alice", rows.get(0).get("name"));
        }

        @Test
        @DisplayName("returns empty list for no matches")
        void returnsEmptyForNoMatches() throws Exception {
            List<Map<String, Object>> rows = commands.select(
                new SqlPlugin.SqlSelectRequest("testdb",
                    "SELECT * FROM users WHERE age > ?", List.of(100)));
            assertTrue(rows.isEmpty());
        }
    }

    @Nested
    @DisplayName("sql:batch")
    class Batch {

        @BeforeEach
        void openDb() throws Exception {
            commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:"));
        }

        @Test
        @DisplayName("executes multiple statements in transaction")
        void executesInTransaction() throws Exception {
            boolean result = commands.batch(new SqlPlugin.SqlBatchRequest("testdb", List.of(
                "CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT)",
                "INSERT INTO items (name) VALUES ('A')",
                "INSERT INTO items (name) VALUES ('B')"
            )));
            assertTrue(result);

            List<Map<String, Object>> rows = commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT * FROM items", null));
            assertEquals(2, rows.size());
        }

        @Test
        @DisplayName("rolls back on error")
        void rollsBackOnError() throws Exception {
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT NOT NULL)", null));
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO items (name) VALUES (?)", List.of("existing")));

            assertThrows(SQLException.class, () ->
                commands.batch(new SqlPlugin.SqlBatchRequest("testdb", List.of(
                    "INSERT INTO items (name) VALUES ('A')",
                    "INSERT INTO items (name) VALUES (NULL)" // violates NOT NULL
                ))));

            // Only the original row should remain
            List<Map<String, Object>> rows = commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT * FROM items", null));
            assertEquals(1, rows.size());
        }
    }

    @Nested
    @DisplayName("sql:close")
    class Close {

        @Test
        @DisplayName("closes open database")
        void closesOpenDatabase() throws Exception {
            commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:"));
            assertTrue(commands.close("testdb"));
        }

        @Test
        @DisplayName("returns false for nonexistent database")
        void returnsFalseForNonexistent() throws Exception {
            assertFalse(commands.close("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Plugin lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("shutdown closes all connections")
        void shutdownClosesConnections() throws Exception {
            commands.open(new SqlPlugin.SqlOpenRequest("db1", ":memory:"));
            commands.open(new SqlPlugin.SqlOpenRequest("db2", ":memory:"));
            plugin.shutdown();

            // After shutdown, connections should no longer be accessible
            assertThrows(IllegalStateException.class, () ->
                commands.execute(new SqlPlugin.SqlExecuteRequest("db1", "SELECT 1", null)));
        }
    }

    @Nested
    @DisplayName("Parameter binding")
    class ParameterBinding {

        @BeforeEach
        void openDb() throws Exception {
            commands.open(new SqlPlugin.SqlOpenRequest("testdb", ":memory:"));
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "CREATE TABLE data (id INTEGER PRIMARY KEY, text_val TEXT, int_val INTEGER, real_val REAL, bool_val INTEGER)",
                null));
        }

        @Test
        @DisplayName("binds null parameters")
        void bindsNull() throws Exception {
            java.util.ArrayList<Object> params = new java.util.ArrayList<>();
            params.add(null);
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO data (text_val) VALUES (?)", params));
            List<Map<String, Object>> rows = commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT text_val FROM data", null));
            assertNull(rows.get(0).get("text_val"));
        }

        @Test
        @DisplayName("binds string parameters")
        void bindsStrings() throws Exception {
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO data (text_val) VALUES (?)", List.of("hello")));
            List<Map<String, Object>> rows = commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT text_val FROM data", null));
            assertEquals("hello", rows.get(0).get("text_val"));
        }

        @Test
        @DisplayName("binds integer parameters")
        void bindsIntegers() throws Exception {
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO data (int_val) VALUES (?)", List.of(42)));
            List<Map<String, Object>> rows = commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT int_val FROM data", null));
            assertEquals(42, ((Number) rows.get(0).get("int_val")).intValue());
        }

        @Test
        @DisplayName("binds double parameters")
        void bindsDoubles() throws Exception {
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO data (real_val) VALUES (?)", List.of(3.14)));
            List<Map<String, Object>> rows = commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT real_val FROM data", null));
            assertEquals(3.14, ((Number) rows.get(0).get("real_val")).doubleValue(), 0.001);
        }

        @Test
        @DisplayName("binds boolean parameters")
        void bindsBooleans() throws Exception {
            commands.execute(new SqlPlugin.SqlExecuteRequest("testdb",
                "INSERT INTO data (bool_val) VALUES (?)", List.of(true)));
            List<Map<String, Object>> rows = commands.select(
                new SqlPlugin.SqlSelectRequest("testdb", "SELECT bool_val FROM data", null));
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
}
