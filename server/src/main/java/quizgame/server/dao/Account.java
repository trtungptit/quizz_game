package quizgame.server.dao;

/** Tài khoản nội bộ; hash không được đưa vào log. */
public record Account(long id, String username, String passwordHash, int totalWins) {
    @Override public String toString() {
        return "Account[id=" + id + ", username=" + username + ", passwordHash=<redacted>, totalWins=" + totalWins + "]";
    }
}
