package quizgame.common.model;

/** Một dòng kết quả của người chơi sau trận. */
public record MatchRow(int rank, String username, int score, int correctCount, long finishTimeMs,
                       PlayerStatus status) {
}
