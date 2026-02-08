package build.krema.plugin.sql;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import build.krema.core.KremaCommand;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.plugin.PluginContext;

/**
 * SQL plugin providing SQLite database access.
 * Lives in its own module to keep sqlite-jdbc out of krema-core.
 */
public class SqlPlugin implements KremaPlugin {

    private PluginContext context;
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

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
        connections.forEach((name, conn) -> {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("[Krema] Failed to close SQL connection '" + name + "': " + e.getMessage());
            }
        });
        connections.clear();
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
        public boolean open(SqlOpenRequest request) throws Exception {
            if (plugin.connections.containsKey(request.name())) {
                throw new IllegalStateException("Database '" + request.name() + "' is already open");
            }

            String dbPath = request.path();
            if (dbPath == null || dbPath.isBlank()) {
                Path dataDir = plugin.context.getAppDataDir();
                Files.createDirectories(dataDir);
                dbPath = dataDir.resolve(request.name() + ".db").toString();
            }

            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            // Enable WAL mode for better concurrent read performance
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
            }

            plugin.connections.put(request.name(), conn);
            return true;
        }

        @KremaCommand("sql:execute")
        public SqlExecuteResult execute(SqlExecuteRequest request) throws SQLException {
            Connection conn = getConnection(request.name());

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
        }

        @KremaCommand("sql:select")
        public List<Map<String, Object>> select(SqlSelectRequest request) throws SQLException {
            Connection conn = getConnection(request.name());

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
        }

        @KremaCommand("sql:close")
        public boolean close(String name) throws SQLException {
            Connection conn = plugin.connections.remove(name);
            if (conn == null) {
                return false;
            }
            conn.close();
            return true;
        }

        @KremaCommand("sql:batch")
        public boolean batch(SqlBatchRequest request) throws SQLException {
            Connection conn = getConnection(request.name());
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
        }

        private Connection getConnection(String name) {
            Connection conn = plugin.connections.get(name);
            if (conn == null) {
                throw new IllegalStateException("No database named '" + name + "' is open");
            }
            return conn;
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
    }
}
