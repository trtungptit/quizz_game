package quizgame.server.service;

import quizgame.common.model.BoxEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Luật thuần của hộp quà bí ẩn (không đụng mạng/CSDL) để dễ kiểm thử. */
public final class BoxRules {
    public static final long PICK_TIMEOUT_MS = 6000;
    public static final long PICK_GRACE_MS = 500;
    public static final long REVEAL_MS = 3000;
    public static final int MAX_POINTS = 1000;
    static final double STEAL_RATE = 0.15;
    static final double LOSE_RATE = 0.20;

    private BoxRules() {
    }

    /** Trận ít hơn số câu này thì không có hộp (hộp sẽ sát nhau hoặc sát câu cuối). */
    public static final int MIN_QUESTIONS = 4;

    /** Cứ sau mỗi GROUP_SIZE câu thì có 1 hộp. */
    public static final int GROUP_SIZE = 4;

    /**
     * Chỉ số câu (từ 0) mà sau đó xuất hiện hộp: dưới 4 câu không có; đúng 4 câu thì hộp ở giữa (sau câu 2);
     * còn lại cứ sau câu 4, 8, 12... có 1 hộp, bỏ hộp rơi vào câu cuối vì lúc đó trận đã kết thúc.
     */
    public static List<Integer> positions(int questions) {
        if (questions < MIN_QUESTIONS) return List.of();
        if (questions == GROUP_SIZE) return List.of(GROUP_SIZE / 2 - 1);
        List<Integer> result = new ArrayList<>();
        for (int after = GROUP_SIZE; after < questions; after += GROUP_SIZE) result.add(after - 1);
        return List.copyOf(result);
    }

    /** Xáo 4 hiệu ứng vào 4 hộp (mỗi hộp một hiệu ứng khác nhau). */
    public static List<BoxEffect> shuffle(Random random) {
        List<BoxEffect> boxes = new ArrayList<>(Arrays.asList(BoxEffect.values()));
        Collections.shuffle(boxes, random);
        return List.copyOf(boxes);
    }

    /** Số điểm bị cướp từ người có targetScore điểm. */
    public static int stealAmount(int targetScore) {
        return (int) Math.round(Math.max(0, targetScore) * STEAL_RATE);
    }

    /** Số điểm bị mất khi mở trúng Mất điểm. */
    public static int loseAmount(int score) {
        return (int) Math.round(Math.max(0, score) * LOSE_RATE);
    }

    /** Điểm cuối của một câu sau hiệu ứng hộp (NO_LATE trước, DOUBLE sau). */
    public static int points(int base, boolean correct, boolean noLate, boolean doubled) {
        if (!correct) return 0;
        int points = noLate ? MAX_POINTS : base;
        return doubled ? points * 2 : points;
    }
}
