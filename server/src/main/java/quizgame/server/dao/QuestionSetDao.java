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

/** Truy vấn bộ công khai chưa xóa; không sửa dữ liệu hay trộn đáp án vào danh sách. */
public final class QuestionSetDao {
    public record StoredSet(QuestionSet value, Map<Integer, String> imageRefs) { }

    public Optional<StoredSet> get(Connection connection, long id) throws SQLException {
        String name, topic, description, owner;
        try (var s = connection.prepareStatement("SELECT s.title,s.topic,s.description,u.username "
                + "FROM question_sets s JOIN users u ON u.user_id=s.owner_id "
                + "WHERE s.set_id=? AND s.deleted_at IS NULL AND s.visibility='public' AND s.publish_status='published'")) {
            s.setLong(1, id);
            try (var rs = s.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                name = rs.getString(1); topic = rs.getString(2); description = rs.getString(3); owner = rs.getString(4);
            }
        }
        Map<Long, DraftRow> rows = new LinkedHashMap<>();
        try (var s = connection.prepareStatement("SELECT question_id,type,prompt,time_limit_seconds,image_ref "
                + "FROM questions WHERE set_id=? ORDER BY position,question_id")) {
            s.setLong(1, id);
            try (var rs = s.executeQuery()) {
                while (rs.next()) rows.put(rs.getLong(1), new DraftRow(QuestionType.valueOf(rs.getString(2)),
                        rs.getString(3), rs.getInt(4), rs.getString(5)));
            }
        }
        try (var s = connection.prepareStatement("SELECT o.question_id,o.option_index,o.option_text,o.is_correct "
                + "FROM question_options o JOIN questions q ON q.question_id=o.question_id "
                + "WHERE q.set_id=? ORDER BY q.position,o.option_index")) {
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
                + "JOIN questions q ON q.question_id=a.question_id WHERE q.set_id=? ORDER BY q.position,a.accepted_answer_id")) {
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
