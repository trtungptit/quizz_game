package quizgame.client.ui;

/** Vòng đời của một màn trong MainFrame. */
public interface Screen {
    /** Gọi mỗi khi màn được hiện (đăng ký listener, tải dữ liệu). */
    default void onShow() {
    }

    /** Gọi khi màn bị ẩn (hủy listener, dừng Timer). */
    default void onHide() {
    }

    /** Hỏi trước khi rời màn; false = ở lại (ví dụ còn thay đổi chưa lưu). */
    default boolean canLeave() {
        return true;
    }
}
