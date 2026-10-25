package quizgame.common.model;

import java.util.List;

/** Kết quả cả trận (winner = null khi hòa). */
public record MatchResult(long matchId, String winner, boolean draw, List<MatchRow> rows) {
}
