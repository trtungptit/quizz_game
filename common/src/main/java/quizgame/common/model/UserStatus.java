package quizgame.common.model;

/** Trạng thái của một người chơi đang online. */
public enum UserStatus {
    IDLE("Đang rảnh"),
    IN_ROOM("Trong phòng"),
    PLAYING("Đang chơi");

    private final String label;

    UserStatus(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị. */
    public String label() {
        return label;
    }
}
