package quizgame.server.service;

import quizgame.server.dao.Database;
import java.sql.SQLException;

/** Dọn dữ liệu trạng thái runtime còn sót lại sau khi server tắt bất thường. */
public final class StartupRecovery {
    private final Database database;
    public StartupRecovery(Database database) { this.database = database; }

    public void reconcile() throws SQLException {
        database.transaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE invitations SET status='cancelled', responded_at=CURRENT_TIMESTAMP(3) "
                        + "WHERE status='pending'");
                statement.executeUpdate("UPDATE room_members rm JOIN rooms r ON r.room_id=rm.room_id "
                        + "SET rm.left_at=CURRENT_TIMESTAMP(3) WHERE rm.left_at IS NULL "
                        + "AND r.status IN ('waiting','playing')");
                statement.executeUpdate("UPDATE rooms SET status='cancelled' WHERE status IN ('waiting','playing')");
            }
            return null;
        });
    }
}
