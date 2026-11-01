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
                statement.executeUpdate("UPDATE submissions s JOIN matches m ON m.match_id=s.match_id SET s.submitted_at=CURRENT_TIMESTAMP(3),s.is_correct=FALSE,s.awarded_points=0 WHERE s.submitted_at IS NULL AND m.status IN ('pending','in_progress')");
                statement.executeUpdate("UPDATE rounds r JOIN matches m ON m.match_id=r.match_id SET r.status='completed',r.ended_at=CURRENT_TIMESTAMP WHERE r.status IN ('pending','in_progress') AND m.status IN ('pending','in_progress')");
                statement.executeUpdate("UPDATE matches SET status='cancelled',ended_at=CURRENT_TIMESTAMP,winner_id=NULL WHERE status IN ('pending','in_progress')");
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
