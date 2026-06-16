package build.krema.plugin.sql;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import build.krema.core.KremaCommand;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.plugin.PluginContext;

/**
 * SQL plugin providing SQLite database access.
 * Lives in its own module to keep sqlite-jdbc out of krema-core.
 */
public class SqlPlugin implements KremaPlugin {

    private PluginContext context;
    private final ConcurrentHashMap<String, ConnectionHandle> handles = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "krema.sql";
    }

    @Override
    public String getName() {
        return "SQL";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "SQLite database access for Krema applications";
    }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;
    }

    @Override
    public void shutdown() {
        handles.forEach((name, handle) -> {
            handles.remove(name, handle);
            handle.dispose();
        });
    }

    @Override
    public List<Object> getCommandHandlers() {
        return List.of(new SqlCommands(this));
    }

    @Override
    public List<String> getRequiredPermissions() {
        return List.of("sql:read", "sql:write");
    }

    public record SqlOpenRequest(String name, String path) {}
    public record SqlExecuteRequest(String name, String sql, List<Object> params) {}
    public record SqlSelectRequest(String name, String sql, List<Object> params) {}
    public record SqlBatchRequest(String name, List<String> statements) {}
    public record SqlExecuteResult(int rowsAffected, long lastInsertId) {}

    public static class SqlCommands {

        private final SqlPlugin plugin;

        SqlCommands(SqlPlugin plugin) {
            this.plugin = plugin;
        }

        @KremaCommand("sql:open")
        public CompletableFuture<Boolean> open(SqlOpenRequest request) {
            String name = request.name();
            ConnectionHandle handle = new ConnectionHandle(name);
            ConnectionHandle existing = plugin.handles.putIfAbsent(name, handle);
            if (existing != null) {
                return failedFuture(existing.openRejectedException());
            }

            return handle.submit(() -> {
                String dbPath = request.path();
                if (dbPath == null || dbPath.isBlank()) {
                    Path dataDir = plugin.context.getAppDataDir();
                    Files.createDirectories(dataDir);
                    dbPath = dataDir.resolve(name + ".db").toString();
                }

                Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

                // Enable WAL mode for better concurrent read performance.
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");
                }

                handle.markOpen(conn);
                return true;
            }).whenComplete((result, error) -> {
                if (error != null) {
                    plugin.handles.remove(name, handle);
                    handle.markFailed();
                    handle.dispose();
                }
            });
        }

        @KremaCommand("sql:execute")
        public CompletableFuture<SqlExecuteResult> execute(SqlExecuteRequest request) {
            return submitOnOpenConnection(request.name(), conn -> {
                try (PreparedStatement stmt = conn.prepareStatement(request.sql(),
                        Statement.RETURN_GENERATED_KEYS)) {
                    bindParams(stmt, request.params());
                    int rowsAffected = stmt.executeUpdate();

                    long lastInsertId = 0;
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            lastInsertId = keys.getLong(1);
                        }
                    }

                    return new SqlExecuteResult(rowsAffected, lastInsertId);
                }
            });
        }

        @KremaCommand("sql:select")
        public CompletableFuture<List<Map<String, Object>>> select(SqlSelectRequest request) {
            return submitOnOpenConnection(request.name(), conn -> {
                try (PreparedStatement stmt = conn.prepareStatement(request.sql())) {
                    bindParams(stmt, request.params());

                    try (ResultSet rs = stmt.executeQuery()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int columnCount = meta.getColumnCount();
                        List<Map<String, Object>> rows = new ArrayList<>();

                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int i = 1; i <= columnCount; i++) {
                                row.put(meta.getColumnLabel(i), rs.getObject(i));
                            }
                            rows.add(row);
                        }

                        return rows;
                    }
                }
            });
        }

        @KremaCommand("sql:close")
        public CompletableFuture<Boolean> close(String name) {
            ConnectionHandle handle = plugin.handles.get(name);
            if (handle == null || !handle.beginClosing()) {
                return CompletableFuture.completedFuture(false);
            }

            return handle.submit(() -> {
                try {
                    handle.closeConnection();
                    return true;
                } finally {
                    plugin.handles.remove(name, handle);
                    handle.markClosed();
                    handle.dispose();
                }
            });
        }

        @KremaCommand("sql:batch")
        public CompletableFuture<Boolean> batch(SqlBatchRequest request) {
            return submitOnOpenConnection(request.name(), conn -> {
                boolean originalAutoCommit = conn.getAutoCommit();

                try {
                    conn.setAutoCommit(false);
                    try (Statement stmt = conn.createStatement()) {
                        for (String sql : request.statements()) {
                            stmt.execute(sql);
                        }
                    }
                    conn.commit();
                    return true;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            });
        }

        private <T> CompletableFuture<T> submitOnOpenConnection(String name, SqlCallable<T> work) {
            ConnectionHandle handle = plugin.handles.get(name);
            if (handle == null || !handle.isOpen()) {
                return failedFuture(noOpenDatabase(name));
            }
            Connection connection = handle.openConnection();
            return handle.submit(() -> work.call(connection));
        }

        private void bindParams(PreparedStatement stmt, List<Object> params) throws SQLException {
            if (params == null) return;
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                int idx = i + 1;
                if (param == null) {
                    stmt.setNull(idx, Types.NULL);
                } else if (param instanceof Number n) {
                    if (param instanceof Integer || param instanceof Long) {
                        stmt.setLong(idx, n.longValue());
                    } else {
                        stmt.setDouble(idx, n.doubleValue());
                    }
                } else if (param instanceof Boolean b) {
                    stmt.setBoolean(idx, b);
                } else {
                    stmt.setString(idx, param.toString());
                }
            }
        }

        private static IllegalStateException noOpenDatabase(String name) {
            return new IllegalStateException("No database named '" + name + "' is open");
        }

        private static <T> CompletableFuture<T> failedFuture(Throwable error) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(error);
            return future;
        }
    }

    @FunctionalInterface
    private interface SqlCallable<T> {
        T call(Connection connection) throws Exception;
    }

    private enum ConnectionState {
        OPENING,
        OPEN,
        CLOSING,
        CLOSED,
        FAILED
    }

    private static class ConnectionHandle {
        private final String name;
        private final ExecutorService executor;
        private final AtomicBoolean disposed = new AtomicBoolean(false);
        private Connection connection;
        private ConnectionState state = ConnectionState.OPENING;

        ConnectionHandle(String name) {
            this.name = name;
            this.executor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "krema-sql-" + name);
                thread.setDaemon(true);
                return thread;
            });
        }

        synchronized boolean isOpen() {
            return state == ConnectionState.OPEN;
        }

        synchronized Connection openConnection() {
            if (state != ConnectionState.OPEN || connection == null) {
                throw SqlCommands.noOpenDatabase(name);
            }
            return connection;
        }

        synchronized void markOpen(Connection connection) {
            this.connection = connection;
            if (state == ConnectionState.OPENING) {
                state = ConnectionState.OPEN;
            }
        }

        synchronized boolean beginClosing() {
            if (state == ConnectionState.CLOSING || state == ConnectionState.CLOSED || state == ConnectionState.FAILED) {
                return false;
            }
            state = ConnectionState.CLOSING;
            return true;
        }

        synchronized void markClosed() {
            state = ConnectionState.CLOSED;
        }

        synchronized void markFailed() {
            state = ConnectionState.FAILED;
        }

        synchronized RuntimeException openRejectedException() {
            if (state == ConnectionState.OPENING) {
                return new IllegalStateException("Database '" + name + "' is opening; retry after open completes");
            }
            if (state == ConnectionState.CLOSING) {
                return new IllegalStateException("Database '" + name + "' is closing; retry after close completes");
            }
            if (state == ConnectionState.CLOSED || state == ConnectionState.FAILED) {
                return SqlCommands.noOpenDatabase(name);
            }
            return new IllegalStateException("Database '" + name + "' is already open");
        }

        <T> CompletableFuture<T> submit(Callable<T> work) {
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                executor.execute(() -> {
                    try {
                        future.complete(work.call());
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
            } catch (RejectedExecutionException e) {
                future.completeExceptionally(SqlCommands.noOpenDatabase(name));
            }
            return future;
        }

        synchronized void closeConnection() throws SQLException {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            connection = null;
        }

        void dispose() {
            if (disposed.compareAndSet(false, true)) {
                try {
                    closeConnection();
                } catch (SQLException e) {
                    System.err.println("[Krema] Failed to close SQL connection '" + name + "': " + e.getMessage());
                } finally {
                    executor.shutdown();
                }
            }
        }
    }
}
