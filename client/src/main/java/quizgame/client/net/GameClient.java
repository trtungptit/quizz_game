package quizgame.client.net;

import quizgame.common.model.Answer;
import quizgame.common.model.LeaderboardRow;
import quizgame.common.model.MatchResult;
import quizgame.common.model.MatchSummary;
import quizgame.common.model.QuestionSet;
import quizgame.common.model.QuestionSetSummary;
import quizgame.common.model.RoomState;
import quizgame.common.model.UserInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Giao diện duy nhất để UI nói chuyện với Server. Lỗi Server làm future hoàn thành bằng GameException. */
public interface GameClient {
    // Kết nối
    CompletableFuture<Void> connect(String host, int port);

    /** Đóng chủ động (không gọi onDisconnected). */
    void close();

    // Tài khoản
    /** Trả về tên và số trận thắng (status mặc định IDLE) lấy từ phản hồi LOGIN. */
    CompletableFuture<UserInfo> login(String username, String password);

    CompletableFuture<Void> register(String username, String password);

    CompletableFuture<Void> logout();

    // Phòng
    CompletableFuture<RoomState> createRoom(String name);

    CompletableFuture<RoomState> joinRoom(String code);

    CompletableFuture<Void> leaveRoom();

    CompletableFuture<Void> setReady(boolean ready);

    CompletableFuture<Void> remindReady();

    CompletableFuture<Void> kick(String username);

    CompletableFuture<Void> configRoom(Long setId, int numQuestions, Integer timeOverrideSeconds);

    CompletableFuture<Void> startGame();

    // Lời mời
    CompletableFuture<Void> invite(String username);

    /** Trả RoomState nếu đồng ý, null nếu từ chối. */
    CompletableFuture<RoomState> replyInvite(long inviteId, boolean accept);

    // Chơi
    CompletableFuture<Void> answer(int qIndex, Answer answer);

    CompletableFuture<Void> quitGame();

    // Bộ câu hỏi
    CompletableFuture<List<QuestionSetSummary>> listSets();

    CompletableFuture<QuestionSet> getSet(long id);

    CompletableFuture<Long> createSet(QuestionSet set);

    CompletableFuture<Void> updateSet(QuestionSet set);

    CompletableFuture<Void> deleteSet(long id);

    // Xem
    CompletableFuture<List<LeaderboardRow>> getLeaderboard();

    CompletableFuture<List<MatchSummary>> getHistory();

    CompletableFuture<MatchResult> getHistoryDetail(long matchId);

    void addListener(GameEventListener listener);

    void removeListener(GameEventListener listener);
}
