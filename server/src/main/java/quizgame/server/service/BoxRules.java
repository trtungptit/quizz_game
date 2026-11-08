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

    /** Chỉ số câu (từ 0) mà sau đó xuất hiện hộp; tối đa 2 lượt, cách nhau ít nhất 2 câu, không sau câu cuối. */
    public static List<Integer> positions(int questions) {
        if (questions < MIN_QUESTIONS) return List.of();
        int p1 = Math.round(questions / 3f);
        int p2 = Math.round(2 * questions / 3f);
        if (p1 >= 1 && p2 - p1 >= 2 && p2 < questions) return List.of(p1 - 1, p2 - 1);
        int middle = Math.round(questions / 2f);
        return middle >= 1 && middle < questions ? List.of(middle - 1) : List.of();
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
