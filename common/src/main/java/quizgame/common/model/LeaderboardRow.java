package quizgame.common.model;

/** Một dòng bảng xếp hạng tổng. */
public record LeaderboardRow(int rank, String username, int totalWins, int totalScore) {
}
