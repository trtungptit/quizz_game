package quizgame.common.model;

/** Hiệu ứng trong hộp quà bí ẩn. */
public enum BoxEffect {
    /** Cướp điểm: lấy 15% điểm của người đang dẫn đầu (không tính mình). */
    STEAL,
    /** Mất điểm: bị trừ 20% điểm đang có. */
    LOSE,
    /** Nhân đôi: câu kế tiếp đúng thì điểm x2. */
    DOUBLE,
    /** Không sợ muộn: câu kế tiếp đúng thì được trọn 1000 điểm. */
    NO_LATE
}
