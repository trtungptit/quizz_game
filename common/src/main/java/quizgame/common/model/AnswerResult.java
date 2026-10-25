package quizgame.common.model;

import java.util.List;

/** Kết quả chấm một câu của người chơi. */
public record AnswerResult(int qIndex, boolean correct, List<Integer> correctIndexes, List<String> acceptedAnswers,
                           int points, int totalScore) {
}
