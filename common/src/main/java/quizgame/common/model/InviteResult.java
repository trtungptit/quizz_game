package quizgame.common.model;

/** Kết cục của một lời mời, báo cho người mời. */
public enum InviteResult {
    ACCEPTED("Đã chấp nhận lời mời"),
    REJECTED("Đã từ chối lời mời"),
    EXPIRED("Lời mời đã hết hạn"),
    JOINED_OTHER("Đã vào phòng khác"),
    ROOM_FULL("Phòng đã đầy");

    private final String label;

    InviteResult(String label) {
        this.label = label;
    }

    /** Câu thông báo tiếng Việt cho người mời. */
    public String label() {
        return label;
    }
}
