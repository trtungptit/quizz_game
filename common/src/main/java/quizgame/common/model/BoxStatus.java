package quizgame.common.model;

/** Một người ở một lượt hộp; riêng box (vị trí hộp) bị ẩn nếu người nhận chưa chọn xong lượt đó. */
public record BoxStatus(String username, int round, BoxState state, Integer box, BoxEffect effect, int delta,
                        String target, int targetDelta, Integer applyQIndex) {
}
