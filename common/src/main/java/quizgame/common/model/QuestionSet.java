package quizgame.common.model;

import java.util.List;

/** Một bộ câu hỏi đầy đủ (id = 0 khi tạo mới). */
public record QuestionSet(long id, String name, String topic, String description, String owner,
                          List<QuestionDraft> questions) {
}
