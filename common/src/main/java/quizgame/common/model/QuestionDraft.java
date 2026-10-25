package quizgame.common.model;

import java.util.List;

/** Một câu hỏi trong bộ câu hỏi (dùng khi soạn và khi lưu). */
public record QuestionDraft(QuestionType type, String content, List<String> options, List<Integer> correctIndexes,
                            List<String> acceptedAnswers, int timeSeconds, String imageBase64) {
}
