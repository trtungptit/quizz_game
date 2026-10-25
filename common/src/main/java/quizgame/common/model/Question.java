package quizgame.common.model;

import java.util.List;

/** Một câu hỏi gửi cho người chơi (không có đáp án đúng). */
public record Question(int qIndex, int total, QuestionType type, String content, List<String> options,
                       String imageId, long timeLimitMs) {
}
