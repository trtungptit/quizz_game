package quizgame.common.model;

/** Một dòng bảng xếp hạng trong lúc chơi. */
public record ScoreRow(int rank, String username, int score, int currentQuestion, boolean finished) {
}
