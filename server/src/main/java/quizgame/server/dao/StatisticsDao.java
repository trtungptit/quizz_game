package quizgame.server.dao;

import quizgame.common.model.*;
import quizgame.server.service.ServiceException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Đọc kết quả đã chốt, theo danh tính của phiên đăng nhập. */
public final class StatisticsDao {
    private final Database database;

    public StatisticsDao(Database database) {
        this.database = database;
    }

    public List<LeaderboardRow> leaderboard(long userId) throws SQLException {
        String sql = """
            WITH scores AS (
                SELECT p.user_id, SUM(p.final_score) AS points
                FROM match_players p JOIN matches m USING (match_id)
                WHERE m.status IN ('completed','draw') GROUP BY p.user_id
            ), ranked AS (
                SELECT u.user_id, u.username, u.total_wins, COALESCE(s.points,0) AS points,
                       ROW_NUMBER() OVER (ORDER BY u.total_wins DESC, COALESCE(s.points,0) DESC, u.username) AS pos
                FROM users u LEFT JOIN scores s USING (user_id)
            )
            SELECT * FROM ranked WHERE pos <= 50 OR user_id = ? ORDER BY pos
            """;
        try (var connection = database.openConnection(); var query = connection.prepareStatement(sql)) {
            query.setLong(1, userId);
            var rows = new ArrayList<LeaderboardRow>();
            try (var rs = query.executeQuery()) {
                while (rs.next()) rows.add(new LeaderboardRow(rs.getInt("pos"), rs.getString("username"),
                        rs.getInt("total_wins"), rs.getInt("points")));
            }
            return rows;
        }
    }

    // Cùng thứ tự với bảng kết quả GameManager gửi khi kết thúc trận.
    private static final String PLAYER_ORDER = """
            (p.player_status <> 'FINISHED'), p.final_score DESC,
            COALESCE(NULLIF(p.finish_time_ms,0),9223372036854775807), BINARY u.username
            """;

    public List<MatchSummary> history(long userId) throws SQLException {
        String sql = """
            WITH recent AS (
                SELECT m.match_id, m.room_id, m.started_at
                FROM match_players me JOIN matches m USING (match_id)
                WHERE me.user_id = ? AND m.status IN ('completed','draw')
                ORDER BY m.started_at DESC, m.match_id DESC LIMIT 100
            ), ranked AS (
                SELECT p.*, ROW_NUMBER() OVER (PARTITION BY p.match_id ORDER BY %s) AS pos
                FROM recent JOIN match_players p USING (match_id) JOIN users u USING (user_id)
            )
            SELECT recent.match_id, recent.started_at, r.name, s.title, ranked.pos, ranked.final_score
            FROM recent JOIN ranked USING (match_id) JOIN rooms r ON r.room_id = recent.room_id
            JOIN rounds rd ON rd.match_id = recent.match_id JOIN question_sets s ON s.set_id = rd.set_id
            WHERE ranked.user_id = ? ORDER BY recent.started_at DESC, recent.match_id DESC
            """.formatted(PLAYER_ORDER);
        try (var connection = database.openConnection(); var query = connection.prepareStatement(sql)) {
            query.setLong(1, userId);
            query.setLong(2, userId);
            var rows = new ArrayList<MatchSummary>();
            try (var rs = query.executeQuery()) {
                while (rs.next()) rows.add(new MatchSummary(rs.getLong("match_id"),
                        rs.getObject("started_at", LocalDateTime.class).toString(), rs.getString("name"),
                        rs.getString("title"), rs.getInt("pos"), rs.getInt("final_score")));
            }
            return rows;
        }
    }

    public MatchResult detail(long userId, long matchId) throws SQLException {
        String sql = """
            SELECT w.username AS winner, m.status, u.username, p.final_score, p.correct_count,
                   p.finish_time_ms, p.player_status
            FROM matches m JOIN match_players p USING (match_id) JOIN users u ON u.user_id = p.user_id
            LEFT JOIN users w ON w.user_id = m.winner_id
            WHERE m.match_id = ? AND m.status IN ('completed','draw')
              AND EXISTS (SELECT 1 FROM match_players me WHERE me.match_id = m.match_id AND me.user_id = ?)
            ORDER BY %s
            """.formatted(PLAYER_ORDER);
        try (var connection = database.openConnection(); var query = connection.prepareStatement(sql)) {
            query.setLong(1, matchId);
            query.setLong(2, userId);
            try (var rs = query.executeQuery()) {
                if (!rs.next()) throw new ServiceException("MATCH_NOT_FOUND", "Không tìm thấy trận đấu");
                String winner = rs.getString("winner");
                boolean draw = "draw".equals(rs.getString("status"));
                var rows = new ArrayList<MatchRow>();
                do {
                    rows.add(new MatchRow(rows.size() + 1, rs.getString("username"), rs.getInt("final_score"),
                            rs.getInt("correct_count"), rs.getLong("finish_time_ms"),
                            PlayerStatus.valueOf(rs.getString("player_status"))));
                } while (rs.next());
                return new MatchResult(matchId, winner, draw, rows);
            }
        }
    }
}
