package quizgame.server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/** DAO tài khoản; toàn bộ giá trị bên ngoài được bind qua PreparedStatement. */
public final class AccountDao {
    public Optional<Account> findByUsername(Connection connection, String username) throws SQLException {
        String sql = "SELECT user_id, username, password_hash, total_wins FROM users WHERE username = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Account> findById(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT user_id, username, password_hash, total_wins FROM users WHERE user_id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        }
    }

    public Account create(Connection connection, String username, String passwordHash) throws SQLException {
        String sql = "INSERT INTO users(username, password_hash, display_name, total_wins) VALUES (?, ?, ?, 0)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, username);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Không lấy được mã tài khoản mới");
                return new Account(keys.getLong(1), username, passwordHash, 0);
            }
        }
    }

    private static Account read(ResultSet rs) throws SQLException {
        long wins = rs.getLong("total_wins");
        return new Account(rs.getLong("user_id"), rs.getString("username"), rs.getString("password_hash"),
                (int) Math.min(Integer.MAX_VALUE, wins));
    }
}
