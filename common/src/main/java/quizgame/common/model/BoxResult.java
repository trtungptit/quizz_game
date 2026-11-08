package quizgame.common.model;

import java.util.List;

/**
 * Kết quả mở hộp của chính người nhận. boxes là hiệu ứng của cả 4 hộp (để lật hết);
 * delta/targetDelta là điểm đổi ngay; applyQIndex là câu được hưởng DOUBLE/NO_LATE.
 */
public record BoxResult(int round, int chosen, boolean auto, List<BoxEffect> boxes, BoxEffect effect, int delta,
                        String target, int targetDelta, Integer applyQIndex, int totalScore, long nextInMs) {
}
