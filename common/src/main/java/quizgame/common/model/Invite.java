package quizgame.common.model;

/** Lời mời vào phòng gửi tới người chơi. */
public record Invite(long id, String fromUser, String roomCode, String roomName, long expiresInMs) {
}
