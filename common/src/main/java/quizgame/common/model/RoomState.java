package quizgame.common.model;

import java.util.List;

/** Trạng thái hiện tại của một phòng. */
public record RoomState(String code, String name, String host, List<RoomMember> members, List<String> pendingInvitees,
                        QuestionSetSummary set, int numQuestions, Integer timeOverrideSeconds, RoomPhase phase) {
}
