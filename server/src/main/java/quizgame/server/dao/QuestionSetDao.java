package quizgame.server.dao;

import quizgame.common.model.QuestionSetSummary;
import quizgame.common.model.QuestionType;
import quizgame.common.model.QuestionSet;
import quizgame.common.model.QuestionDraft;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Optional;

/** Lưu và đọc bộ công khai; không trộn đáp án vào danh sách tóm tắt. */
public final class QuestionSetDao {
    public record StoredSet(QuestionSet value, Map<Integer, String> imageRefs) { }

    public long create(Connection connection, long ownerId, QuestionSet set, Map<Integer, String> imageRefs)
            throws SQLException {
        long setId = insert(connection, "INSERT INTO question_sets(owner_id,title,topic,description) VALUES(?,?,?,?)",
                ownerId, set.name(), set.topic(), set.description());
        insertQuestions(connection, setId, set, imageRefs);
        return setId;
    }

    private void insertQuestions(Connection connection, long setId, QuestionSet set, Map<Integer, String> imageRefs)
            throws SQLException {
        for (int index = 0; index < set.questions().size(); index++) {
            var q = set.questions().get(index);
            long questionId = insert(connection,
                    "INSERT INTO questions(set_id,type,prompt,time_limit_seconds,position,image_ref) VALUES(?,?,?,?,?,?)",
                    setId, q.type().name(), q.content(), q.timeSeconds(), index + 1, imageRefs.get(index));
            try (var options = connection.prepareStatement(
                    "INSERT INTO question_options(question_id,option_index,option_text,is_correct) VALUES(?,?,?,?)")) {
                for (int i = 0; i < q.options().size(); i++) {
                    options.setLong(1, questionId); options.setInt(2, i); options.setString(3, q.options().get(i));
                    options.setBoolean(4, q.correctIndexes().contains(i)); options.addBatch();
                }
                if (!q.options().isEmpty()) options.executeBatch();
            }
            // Các đáp án tương đương sau chuẩn hóa chỉ cần lưu một lần.
            try (var answers = connection.prepareStatement(
                    "INSERT INTO accepted_answers(question_id,answer_text,normalized_text) VALUES(?,?,?) "
                            + "ON DUPLICATE KEY UPDATE normalized_text=normalized_text")) {
                java.util.Set<String> normalized = new java.util.HashSet<>();
                for (String answer : q.acceptedAnswers()) {
                    String key = quizgame.server.service.Scoring.normalize(answer);
                    if (!normalized.add(key)) continue;
                    answers.setLong(1, questionId); answers.setString(2, answer); answers.setString(3, key);
                    answers.addBatch();
                }
                if (!normalized.isEmpty()) answers.executeBatch();
            }
        }
    }

    /** Khóa bộ trước khi kiểm tra quyền và trận đang dùng để tránh sửa/xóa đồng thời. */
    public void requireEditable(Connection c, long id, long ownerId) throws SQLException {
        try (var s = c.prepareStatement("SELECT owner_id FROM question_sets WHERE set_id=? AND deleted_at IS NULL "
                + "AND visibility='public' AND publish_status='published' FOR UPDATE")) {
            s.setLong(1, id);
            try (var r = s.executeQuery()) {
                if (!r.next()) throw new quizgame.server.service.ServiceException("SET_NOT_FOUND", "Không tìm thấy bộ câu hỏi");
                if (r.getLong(1) != ownerId)
                    throw new quizgame.server.service.ServiceException("NOT_OWNER", "Chỉ chủ bộ câu hỏi được sửa hoặc xóa");
            }
        }
        try (var s = c.prepareStatement("SELECT 1 FROM rounds r JOIN matches m ON m.match_id=r.match_id "
                + "WHERE r.set_id=? AND m.status IN ('pending','in_progress') LIMIT 1 FOR UPDATE")) {
            s.setLong(1, id);
            try (var r = s.executeQuery()) {
                if (r.next()) throw new quizgame.server.service.ServiceException("SET_IN_USE", "Bộ câu hỏi đang được dùng trong trận chưa kết thúc");
            }
        }
    }

    public void update(Connection c, QuestionSet set, Map<Integer, String> imageRefs) throws SQLException {
        // Giữ nguyên câu/đáp án/ảnh và tên bộ cũ cho lịch sử, không đổi schema.
        long archiveId = insert(c, "INSERT INTO question_sets(owner_id,title,topic,description,visibility,publish_status,deleted_at,created_at,updated_at) "
                + "SELECT owner_id,title,topic,description,visibility,publish_status,CURRENT_TIMESTAMP,created_at,updated_at "
                + "FROM question_sets WHERE set_id=?", set.id());
        execute(c, "UPDATE questions SET set_id=? WHERE set_id=?", archiveId, set.id());
        execute(c, "UPDATE rounds SET set_id=? WHERE set_id=?", archiveId, set.id());
        execute(c, "UPDATE question_sets SET title=?,topic=?,description=?,updated_at=CURRENT_TIMESTAMP WHERE set_id=?",
                set.name(), set.topic(), set.description(), set.id());
        insertQuestions(c, set.id(), set, imageRefs);
    }

    public void delete(Connection c, long id) throws SQLException {
        execute(c, "UPDATE question_sets SET deleted_at=CURRENT_TIMESTAMP WHERE set_id=?", id);
    }

    public List<quizgame.common.model.RoomState> refreshWaitingRooms(Connection c, long setId, boolean deleted, int count)
            throws SQLException {
        List<Long> roomIds = new ArrayList<>();
        try (var s = c.prepareStatement("SELECT room_id FROM rooms WHERE set_id=? AND status='waiting' ORDER BY room_id FOR UPDATE")) {
            s.setLong(1, setId);
            try (var r = s.executeQuery()) { while (r.next()) roomIds.add(r.getLong(1)); }
        }
        List<quizgame.common.model.RoomState> states = new ArrayList<>();
        var rooms = new RoomDao();
        for (long roomId : roomIds) {
            execute(c, "UPDATE room_members SET ready=FALSE WHERE room_id=? AND left_at IS NULL", roomId);
            if (deleted) execute(c, "UPDATE rooms SET set_id=NULL,num_questions=0,time_override_seconds=NULL WHERE room_id=?", roomId);
            else execute(c, "UPDATE rooms SET num_questions=LEAST(num_questions,?) WHERE room_id=?", count, roomId);
            states.add(rooms.snapshot(c, rooms.findById(c, roomId, false).orElseThrow()));
        }
        return List.copyOf(states);
    }

    private static void execute(Connection c, String sql, Object... values) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
            s.executeUpdate();
        }
    }

    private static long insert(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Không lấy được ID vừa tạo");
                return keys.getLong(1);
            }
        }
    }

    public Optional<StoredSet> get(Connection connection, long id) throws SQLException {
        return get(connection, id, false);
    }

    public Optional<StoredSet> get(Connection connection, long id, boolean lock) throws SQLException {
        String lockSql = lock ? " FOR UPDATE" : "";
        String name, topic, description, owner;
        try (var s = connection.prepareStatement("SELECT s.title,s.topic,s.description,u.username "
                + "FROM question_sets s JOIN users u ON u.user_id=s.owner_id "
                + "WHERE s.set_id=? AND s.deleted_at IS NULL AND s.visibility='public' AND s.publish_status='published'" + lockSql)) {
            s.setLong(1, id);
            try (var rs = s.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                name = rs.getString(1); topic = rs.getString(2); description = rs.getString(3); owner = rs.getString(4);
            }
        }
        Map<Long, DraftRow> rows = new LinkedHashMap<>();
        try (var s = connection.prepareStatement("SELECT question_id,type,prompt,time_limit_seconds,image_ref "
                + "FROM questions WHERE set_id=? ORDER BY position,question_id" + lockSql)) {
            s.setLong(1, id);
            try (var rs = s.executeQuery()) {
                while (rs.next()) rows.put(rs.getLong(1), new DraftRow(QuestionType.valueOf(rs.getString(2)),
                        rs.getString(3), rs.getInt(4), rs.getString(5)));
            }
        }
        try (var s = connection.prepareStatement("SELECT o.question_id,o.option_index,o.option_text,o.is_correct "
                + "FROM question_options o JOIN questions q ON q.question_id=o.question_id "
                + "WHERE q.set_id=? ORDER BY q.position,o.option_index" + lockSql)) {
            s.setLong(1, id);
            try (var rs = s.executeQuery()) {
                while (rs.next()) {
                    DraftRow row = rows.get(rs.getLong(1));
                    if (row == null || row.options.size() != rs.getInt(2)) throw new SQLException("Thứ tự đáp án không hợp lệ");
                    row.options.add(rs.getString(3));
                    if (rs.getBoolean(4)) row.correct.add(rs.getInt(2));
                }
            }
        }
        try (var s = connection.prepareStatement("SELECT a.question_id,a.answer_text FROM accepted_answers a "
                + "JOIN questions q ON q.question_id=a.question_id WHERE q.set_id=? ORDER BY q.position,a.accepted_answer_id" + lockSql)) {
            s.setLong(1, id);
            try (var rs = s.executeQuery()) {
                while (rs.next()) {
                    DraftRow row = rows.get(rs.getLong(1));
                    if (row == null) throw new SQLException("Câu hỏi không tồn tại");
                    row.answers.add(rs.getString(2));
                }
            }
        }
        List<QuestionDraft> questions = new ArrayList<>();
        Map<Integer, String> refs = new HashMap<>();
        for (DraftRow row : rows.values()) {
            if (row.imageRef != null && !row.imageRef.isBlank()) refs.put(questions.size(), row.imageRef);
            questions.add(new QuestionDraft(row.type, row.prompt, List.copyOf(row.options),
                    List.copyOf(row.correct), List.copyOf(row.answers), row.seconds, null));
        }
        return Optional.of(new StoredSet(new QuestionSet(id, name, topic,
                description, owner, List.copyOf(questions)), Map.copyOf(refs)));
    }

    private static final class DraftRow {
        final QuestionType type;
        final String prompt;
        final int seconds;
        final String imageRef;
        final List<String> options = new ArrayList<>();
        final List<Integer> correct = new ArrayList<>();
        final List<String> answers = new ArrayList<>();
        DraftRow(QuestionType type, String prompt, int seconds, String imageRef) {
            this.type = type; this.prompt = prompt; this.seconds = seconds; this.imageRef = imageRef;
        }
    }

    public Optional<QuestionSetSummary> summary(Connection c,long id) throws SQLException {
        try(var s=c.prepareStatement("SELECT s.title,s.topic,u.username,COUNT(q.question_id),GROUP_CONCAT(DISTINCT q.type),MAX(CASE WHEN q.image_ref IS NOT NULL AND q.image_ref<>'' THEN 1 ELSE 0 END) FROM question_sets s JOIN users u ON u.user_id=s.owner_id LEFT JOIN questions q ON q.set_id=s.set_id WHERE s.set_id=? AND s.deleted_at IS NULL AND s.visibility='public' AND s.publish_status='published' GROUP BY s.set_id,s.title,s.topic,u.username")){
            s.setLong(1,id);try(var r=s.executeQuery()){
                if(!r.next())return Optional.empty();
                EnumSet<QuestionType> types=EnumSet.noneOf(QuestionType.class);
                if(r.getString(5)!=null)for(String t:r.getString(5).split(","))types.add(QuestionType.valueOf(t));
                return Optional.of(new QuestionSetSummary(id,r.getString(1),r.getString(2),r.getString(3),r.getInt(4),List.copyOf(types),r.getBoolean(6)));
            }
        }
    }
    public List<QuestionSetSummary> list(Connection connection) throws SQLException {
        String sql = "SELECT s.set_id,s.title,s.topic,u.username,q.question_id,q.type,q.image_ref "
                + "FROM question_sets s JOIN users u ON u.user_id=s.owner_id "
                + "LEFT JOIN questions q ON q.set_id=s.set_id "
                + "WHERE s.deleted_at IS NULL AND s.visibility='public' AND s.publish_status='published' "
                + "ORDER BY s.set_id DESC,q.position,q.question_id";
        List<QuestionSetSummary> result = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql); var rs = statement.executeQuery()) {
            long currentId = -1;
            String name = null, topic = null, owner = null;
            int count = 0;
            boolean images = false;
            EnumSet<QuestionType> types = EnumSet.noneOf(QuestionType.class);
            while (rs.next()) {
                long id = rs.getLong("set_id");
                if (id != currentId) {
                    if (currentId != -1) result.add(new QuestionSetSummary(currentId, name, topic, owner,
                            count, List.copyOf(types), images));
                    currentId = id; name = rs.getString("title"); topic = rs.getString("topic");
                    owner = rs.getString("username"); count = 0; images = false; types.clear();
                }
                if (rs.getObject("question_id") != null) {
                    count++;
                    types.add(QuestionType.valueOf(rs.getString("type")));
                    String ref = rs.getString("image_ref");
                    images |= ref != null && !ref.isBlank();
                }
            }
            if (currentId != -1) result.add(new QuestionSetSummary(currentId, name, topic, owner,
                    count, List.copyOf(types), images));
        }
        return List.copyOf(result);
    }
}
