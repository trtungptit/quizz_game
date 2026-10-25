package quizgame.common.model;

import java.util.List;

/** Thông tin tóm tắt của một bộ câu hỏi, dùng cho danh sách. */
public record QuestionSetSummary(long id, String name, String topic, String owner, int questionCount,
                                 List<QuestionType> types, boolean hasImages) {
}
