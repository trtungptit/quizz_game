package quizgame.common.model;

/** Tóm tắt một trận trong lịch sử (startedAt dạng ISO-8601). */
public record MatchSummary(long matchId, String startedAt, String roomName, String setName, int myRank,
                           int myScore) {
}
