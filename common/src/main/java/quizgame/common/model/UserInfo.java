package quizgame.common.model;

/** Thông tin một người chơi trong danh sách online. */
public record UserInfo(String username, int totalWins, UserStatus status) {
}
