package quizgame.common.protocol;

/** Lỗi Server trả về: code để xử lý, message để hiện cho người dùng. */
public record ErrorInfo(String code, String message) {
}
