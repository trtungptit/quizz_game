package quizgame.common.model;

import java.util.List;

/** Đáp án người chơi gửi: choices cho SINGLE/MULTI, text cho TYPE. */
public record Answer(List<Integer> choices, String text) {
}
