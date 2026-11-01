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

/** Chỉ đọc bộ câu hỏi, không tạo/sửa/xóa và không khởi động trận đấu. */
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
