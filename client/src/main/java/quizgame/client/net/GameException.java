package quizgame.client.net;

/** Lỗi Server trả (ok:false) hoặc lỗi mạng; message là câu tiếng Việt hiện thẳng cho người dùng. */
public class GameException extends Exception {
    private final String code;

    public GameException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Mã lỗi theo PROTOCOL.md (hoặc mã nội bộ như CONNECT_FAILED, DISCONNECTED). */
    public String code() {
        return code;
    }
}
