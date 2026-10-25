package quizgame.client.net;

import quizgame.common.model.AnswerResult;
import quizgame.common.model.ChangeType;
import quizgame.common.model.GameStart;
import quizgame.common.model.Invite;
import quizgame.common.model.InviteResult;
import quizgame.common.model.MatchResult;
import quizgame.common.model.Question;
import quizgame.common.model.RoomState;
import quizgame.common.model.ScoreRow;
import quizgame.common.model.UserInfo;

import java.util.List;

/** Nhận sự kiện Server đẩy xuống. Có thể chạy trên luồng đọc socket: cập nhật giao diện phải qua invokeLater. */
public interface GameEventListener {
    default void onOnlineList(List<UserInfo> users) {
    }

    default void onUserUpdate(UserInfo user, ChangeType change) {
    }

    default void onInviteReceived(Invite invite) {
    }

    default void onInviteResult(String toUser, InviteResult result) {
    }

    default void onRoomState(RoomState room) {
    }

    default void onReadyReminder(String fromUser) {
    }

    default void onKicked() {
    }

    default void onGameStart(GameStart start) {
    }

    default void onQuestion(Question question) {
    }

    default void onAnswerResult(AnswerResult result) {
    }

    default void onScoreboard(List<ScoreRow> rows) {
    }

    default void onPlayerLeft(String username) {
    }

    default void onGameEnd(MatchResult result) {
    }

    default void onDisconnected() {
    }
}
