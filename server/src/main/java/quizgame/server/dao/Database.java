package quizgame.server.dao;

import quizgame.server.config.ServerConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/** Tạo kết nối JDBC và gom giao dịch để các DAO dùng chung một Connection. */
public final class Database {
    @FunctionalInterface
    public interface Work<T> { T run(Connection connection) throws Exception; }

    private final ServerConfig config;
    private volatile boolean ownsInstanceLock;
    private Connection lockConnection;

    public Database(ServerConfig config) { this.config = Objects.requireNonNull(config); }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(config.databaseUrl(), config.databaseUser(), config.databasePassword());
    }

    public synchronized void verifySchemaAndAcquireLock() throws SQLException {
        Connection connection = openConnection();
        try {
            try (var statement = connection.createStatement();
                 var rs = statement.executeQuery("SELECT GET_LOCK('quiz_game_backend', 0)")) {
                if (!rs.next() || rs.getInt(1) != 1) throw new SQLException("Server QUIZ khác đang sử dụng database");
            }
            ownsInstanceLock = true;
            try (var statement = connection.createStatement();
                 var rs = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables "
                         + "WHERE table_schema = DATABASE() AND table_name IN ('users','rooms','room_members','invitations')")) {
                if (!rs.next() || rs.getInt(1) != 4) throw new SQLException("Schema quiz_game chưa được cài đầy đủ");
            }
            lockConnection = connection;
        } catch (SQLException ex) {
            if (ownsInstanceLock) {
                try (var statement = connection.createStatement()) {
                    statement.executeQuery("SELECT RELEASE_LOCK('quiz_game_backend')").close();
                } catch (SQLException release) { ex.addSuppressed(release); }
                ownsInstanceLock = false;
            }
            try { connection.close(); } catch (SQLException close) { ex.addSuppressed(close); }
            throw ex;
        }
    }

    public <T> T transaction(Work<T> work) throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Exception ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                if (ex instanceof SQLException sql) throw sql;
                if (ex instanceof RuntimeException runtime) throw runtime;
                throw new SQLException("Giao dịch database thất bại", ex);
            }
        }
    }

    public synchronized void releaseInstanceLock() {
        if (!ownsInstanceLock) return;
        try {
            if (lockConnection != null && !lockConnection.isClosed()) {
                try (var statement = lockConnection.createStatement()) {
                    statement.executeQuery("SELECT RELEASE_LOCK('quiz_game_backend')").close();
                }
            }
        } catch (SQLException ignored) {
            // Đóng kết nối giữ lock cũng giải phóng named lock khi DB còn hoạt động.
        } finally {
            ownsInstanceLock = false;
            if (lockConnection != null) {
                try { lockConnection.close(); } catch (SQLException ignored) { }
                lockConnection = null;
            }
        }
    }
}
