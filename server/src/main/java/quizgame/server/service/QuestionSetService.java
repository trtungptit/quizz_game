package quizgame.server.service;

import quizgame.common.model.QuestionSetSummary;
import quizgame.common.model.QuestionSet;
import quizgame.common.model.QuestionDraft;
import quizgame.server.dao.Database;
import quizgame.server.dao.QuestionSetDao;

import java.sql.SQLException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Tạo, sửa, xóa mềm và đọc bộ câu hỏi công khai. */
public final class QuestionSetService {
    private final Database database;
    private final QuestionSetDao sets;
    private final QuestionImageReader images;

    public QuestionSetService(Database database, QuestionSetDao sets) {
        this(database, sets, new QuestionImageReader(Path.of("images")));
    }

    public QuestionSetService(Database database, QuestionSetDao sets, QuestionImageReader images) {
        this.database = database;
        this.sets = sets;
        this.images = images;
    }

    public List<QuestionSetSummary> list() {
        try {
            return database.transaction(sets::list);
        } catch (SQLException ex) {
            throw new ServiceException("INTERNAL", "Không thể tải danh sách bộ câu hỏi lúc này");
        }
    }

    public long create(long ownerId, String owner, QuestionSet set) {
        if (ownerId < 1 || owner == null) throw new ServiceException("NOT_LOGGED_IN", "Bạn cần đăng nhập trước");
        QuestionSetValidator.validate(set);
        // Chủ sở hữu luôn lấy từ phiên đăng nhập, bỏ qua trường owner do Client gửi.
        var canonical = new QuestionSet(0, set.name(), set.topic(), set.description(), owner, set.questions());
        List<byte[]> decoded = prepareImages(set, owner);
        var refs = new java.util.HashMap<Integer, String>();
        boolean saved = false;
        try {
            saveImages(decoded, refs);
            long id = database.transaction(connection -> sets.create(connection, ownerId, canonical, refs));
            saved = true;
            return id;
        } catch (SQLException | java.io.IOException ex) {
            throw new ServiceException("INTERNAL", "Không thể lưu bộ câu hỏi lúc này");
        } finally {
            if (!saved) refs.values().forEach(images::deleteUpload);
        }
    }

    public List<quizgame.common.model.RoomState> update(long ownerId, String owner, QuestionSet set) {
        if (ownerId < 1 || owner == null) throw new ServiceException("NOT_LOGGED_IN", "Bạn cần đăng nhập trước");
        if (set == null || set.id() < 1) throw new ServiceException("BAD_REQUEST", "Mã bộ câu hỏi không hợp lệ");
        var refs = new java.util.HashMap<Integer, String>();
        boolean saved = false;
        try {
            var changed = database.transaction(c -> {
                sets.requireEditable(c, set.id(), ownerId);
                QuestionSetValidator.validateUpdate(set);
                saveImages(prepareImages(set, owner), refs);
                sets.update(c, set, refs);
                return sets.refreshWaitingRooms(c, set.id(), false, set.questions().size());
            });
            saved = true;
            return changed;
        } catch (SQLException ex) {
            throw new ServiceException("INTERNAL", "Không thể sửa bộ câu hỏi lúc này");
        } finally {
            if (!saved) refs.values().forEach(images::deleteUpload);
        }
    }

    public List<quizgame.common.model.RoomState> delete(long ownerId, long id) {
        if (ownerId < 1) throw new ServiceException("NOT_LOGGED_IN", "Bạn cần đăng nhập trước");
        if (id < 1) throw new ServiceException("BAD_REQUEST", "Mã bộ câu hỏi không hợp lệ");
        try {
            return database.transaction(c -> {
                sets.requireEditable(c, id, ownerId);
                sets.delete(c, id);
                return sets.refreshWaitingRooms(c, id, true, 0);
            });
        } catch (SQLException ex) {
            throw new ServiceException("INTERNAL", "Không thể xóa bộ câu hỏi lúc này");
        }
    }

    private List<byte[]> prepareImages(QuestionSet set, String owner) {
        var preview = new QuestionSet(Long.MAX_VALUE, set.name(), set.topic(), set.description(), owner, set.questions());
        String response = quizgame.server.net.ProtocolMessages.response("SET_GET", Long.MAX_VALUE,
                new com.google.gson.Gson().toJsonTree(preview), null);
        if (response.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > quizgame.common.protocol.FramedIO.MAX_FRAME)
            throw new ServiceException("INVALID_SET", "Bộ câu hỏi quá lớn để tải qua kết nối hiện tại");
        List<byte[]> decoded = new ArrayList<>();
        for (int i = 0; i < set.questions().size(); i++)
            decoded.add(images.decodeUpload(set.questions().get(i).imageBase64(), i + 1));
        return decoded;
    }

    private void saveImages(List<byte[]> decoded, java.util.Map<Integer, String> refs) throws java.io.IOException {
        for (int i = 0; i < decoded.size(); i++)
            if (decoded.get(i) != null) refs.put(i, images.saveUpload(decoded.get(i)));
    }

    public QuestionSet get(long id) {
        if (id < 1) throw new ServiceException("BAD_REQUEST", "Mã bộ câu hỏi không hợp lệ");
        try {
            var stored = database.transaction(connection -> sets.get(connection, id))
                    .orElseThrow(() -> new ServiceException("SET_NOT_FOUND", "Không tìm thấy bộ câu hỏi"));
            var set = stored.value();
            var questions = new ArrayList<>(set.questions());
            for (var entry : stored.imageRefs().entrySet()) {
                int index = entry.getKey();
                var q = questions.get(index);
                questions.set(index, new QuestionDraft(q.type(), q.content(), q.options(),
                        q.correctIndexes(), q.acceptedAnswers(), q.timeSeconds(), images.read(entry.getValue())));
            }
            return new QuestionSet(set.id(), set.name(), set.topic(), set.description(),
                    set.owner(), List.copyOf(questions));
        } catch (SQLException ex) {
            throw new ServiceException("INTERNAL", "Không thể tải bộ câu hỏi lúc này");
        }
    }
}
