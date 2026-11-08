package quizgame.common.model;

/** Mời chọn hộp sau câu afterQIndex; round = 1 hoặc 2. */
public record BoxOffer(int round, int afterQIndex, long timeoutMs) {
}
