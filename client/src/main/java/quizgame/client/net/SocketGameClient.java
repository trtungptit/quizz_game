package quizgame.client.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import quizgame.common.model.Answer;
import quizgame.common.model.AnswerResult;
import quizgame.common.model.ChangeType;
import quizgame.common.model.GameStart;
import quizgame.common.model.Invite;
import quizgame.common.model.InviteResult;
import quizgame.common.model.LeaderboardRow;
import quizgame.common.model.MatchResult;
import quizgame.common.model.MatchSummary;
import quizgame.common.model.Question;
import quizgame.common.model.QuestionSet;
import quizgame.common.model.QuestionSetSummary;
import quizgame.common.model.RoomState;
import quizgame.common.model.ScoreRow;
import quizgame.common.model.UserInfo;
import quizgame.common.model.UserStatus;
import quizgame.common.protocol.Envelope;
import quizgame.common.protocol.FramedIO;
import quizgame.common.protocol.ProtocolException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Cài đặt GameClient thật qua TCP socket, theo docs/PROTOCOL.md. */
public class SocketGameClient implements GameClient {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final long PING_INTERVAL_SECONDS = 5;
    private static final long REQUEST_TIMEOUT_MS = 15_000;
    private static final String LOST_MESSAGE = "Mất kết nối tới Server";

    // serializeNulls: các trường null (imageBase64, setId...) vẫn được gửi là null đúng giao thức
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();
    private final long requestTimeoutMs;
    private volatile Connection conn;

    // Một luồng nền dùng chung để hủy các yêu cầu quá hạn
    private static final ScheduledExecutorService TIMEOUTS = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "request-timeout");
        t.setDaemon(true);
        return t;
    });

    public SocketGameClient() {
        this(REQUEST_TIMEOUT_MS);
    }

    /** Dùng cho test: đặt thời gian chờ phản hồi riêng. */
    public SocketGameClient(long requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    // Dữ liệu trung gian của giao thức (không thuộc model dùng chung)
    private record LoginData(String username, int totalWins) { }

    private record IdData(long id) { }

    private record UserUpdateData(UserInfo user, ChangeType change) { }

    private record InviteResultData(String toUser, InviteResult result) { }

    private record FromUserData(String fromUser) { }

    private record UsernameData(String username) { }

    private record Pending(CompletableFuture<Object> future, Type type) { }

    // ---------------------------------------------------------------- Kết nối

    @Override
    public CompletableFuture<Void> connect(String host, int port) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            close();
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                socket.setTcpNoDelay(true);
                Connection c = new Connection(socket);
                conn = c;
                c.start();
                result.complete(null);
            } catch (IOException e) {
                result.completeExceptionally(new GameException("CONNECT_FAILED",
                        "Không kết nối được tới Server " + host + ":" + port));
            }
        }, "connect");
        thread.setDaemon(true);
        thread.start();
        return result;
    }

    @Override
    public void close() {
        Connection c = conn;
        if (c != null) {
            c.intentional = true;
            c.end();
        }
    }

    @Override
    public void addListener(GameEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(GameEventListener listener) {
        listeners.remove(listener);
    }

    // ---------------------------------------------------------------- Tài khoản

    @Override
    public CompletableFuture<UserInfo> login(String username, String password) {
        return this.<LoginData>request("LOGIN", map("username", username, "password", password), LoginData.class)
                .thenApply(d -> new UserInfo(d.username(), d.totalWins(), UserStatus.IDLE));
    }

    @Override
    public CompletableFuture<Void> register(String username, String password) {
        return request("REGISTER", map("username", username, "password", password), Void.class);
    }

    @Override
    public CompletableFuture<Void> logout() {
        return request("LOGOUT", null, Void.class);
    }

    // ---------------------------------------------------------------- Phòng

    @Override
    public CompletableFuture<RoomState> createRoom(String name) {
        return request("CREATE_ROOM", map("name", name), RoomState.class);
    }

    @Override
    public CompletableFuture<RoomState> joinRoom(String code) {
        return request("JOIN_ROOM", map("code", code), RoomState.class);
    }

    @Override
    public CompletableFuture<Void> leaveRoom() {
        return request("LEAVE_ROOM", null, Void.class);
    }

    @Override
    public CompletableFuture<Void> setReady(boolean ready) {
        return request("SET_READY", map("ready", ready), Void.class);
    }

    @Override
    public CompletableFuture<Void> remindReady() {
        return request("REMIND_READY", null, Void.class);
    }

    @Override
    public CompletableFuture<Void> kick(String username) {
        return request("KICK", map("username", username), Void.class);
    }

    @Override
    public CompletableFuture<Void> configRoom(Long setId, int numQuestions, Integer timeOverrideSeconds) {
        return request("ROOM_CONFIG",
                map("setId", setId, "numQuestions", numQuestions, "timeOverrideSeconds", timeOverrideSeconds),
                Void.class);
    }

    @Override
    public CompletableFuture<Void> startGame() {
        return request("START_GAME", null, Void.class);
    }

    // ---------------------------------------------------------------- Lời mời

    @Override
    public CompletableFuture<Void> invite(String username) {
        return request("INVITE", map("username", username), Void.class);
    }

    @Override
    public CompletableFuture<RoomState> replyInvite(long inviteId, boolean accept) {
        return request("INVITE_REPLY", map("inviteId", inviteId, "accept", accept), RoomState.class);
    }

    // ---------------------------------------------------------------- Chơi

    @Override
    public CompletableFuture<Void> answer(int qIndex, Answer answer) {
        return request("ANSWER", map("qIndex", qIndex, "answer", answer), Void.class);
    }

    @Override
    public CompletableFuture<Void> quitGame() {
        return request("QUIT_GAME", null, Void.class);
    }

    // ---------------------------------------------------------------- Bộ câu hỏi

    @Override
    public CompletableFuture<List<QuestionSetSummary>> listSets() {
        return request("SET_LIST", null, new TypeToken<List<QuestionSetSummary>>() { }.getType());
    }

    @Override
    public CompletableFuture<QuestionSet> getSet(long id) {
        return request("SET_GET", map("id", id), QuestionSet.class);
    }

    @Override
    public CompletableFuture<Long> createSet(QuestionSet set) {
        return this.<IdData>request("SET_CREATE", map("set", set), IdData.class).thenApply(IdData::id);
    }

    @Override
    public CompletableFuture<Void> updateSet(QuestionSet set) {
        return request("SET_UPDATE", map("set", set), Void.class);
    }

    @Override
    public CompletableFuture<Void> deleteSet(long id) {
        return request("SET_DELETE", map("id", id), Void.class);
    }

    // ---------------------------------------------------------------- Xem

    @Override
    public CompletableFuture<List<LeaderboardRow>> getLeaderboard() {
        return request("LEADERBOARD", null, new TypeToken<List<LeaderboardRow>>() { }.getType());
    }

    @Override
    public CompletableFuture<List<MatchSummary>> getHistory() {
        return request("HISTORY", null, new TypeToken<List<MatchSummary>>() { }.getType());
    }

    @Override
    public CompletableFuture<MatchResult> getHistoryDetail(long matchId) {
        return request("HISTORY_DETAIL", map("matchId", matchId), MatchResult.class);
    }

    // ---------------------------------------------------------------- Nội bộ

    /** Tạo object data cho phép giá trị null. */
    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    /** Gửi một yêu cầu; future hoàn thành khi có phản hồi cùng reqId. */
    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> request(String type, Object data, Type resultType) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Connection c = conn;
        if (c == null || c.ended.get()) {
            future.completeExceptionally(new GameException("DISCONNECTED", LOST_MESSAGE));
            return future;
        }
        long reqId = c.nextReqId.getAndIncrement();
        c.pending.put(reqId, new Pending((CompletableFuture<Object>) (CompletableFuture<?>) future, resultType));
        if (c.ended.get()) {
            // Kết nối vừa đóng giữa lúc kiểm tra và đăng ký: end() có thể đã dọn xong, tự hủy yêu cầu này
            if (c.pending.remove(reqId) != null) {
                future.completeExceptionally(new GameException("DISCONNECTED", LOST_MESSAGE));
            }
            return future;
        }
        ScheduledFuture<?> timeout = TIMEOUTS.schedule(() -> {
            if (c.pending.remove(reqId) != null) {
                future.completeExceptionally(new GameException("TIMEOUT", "Server không phản hồi, hãy thử lại"));
            }
        }, requestTimeoutMs, TimeUnit.MILLISECONDS);
        future.whenComplete((v, e) -> timeout.cancel(false));
        JsonElement json = data == null ? null : gson.toJsonTree(data);
        String text = gson.toJson(new Envelope(type, reqId, null, json, null));
        try {
            c.send(text);
        } catch (ProtocolException e) {
            // Quá lớn: chỉ yêu cầu này lỗi, kết nối vẫn dùng được
            c.pending.remove(reqId);
            future.completeExceptionally(new GameException("TOO_LARGE", "Dữ liệu quá lớn (tối đa 2 MB)"));
        } catch (IOException e) {
            c.pending.remove(reqId);
            future.completeExceptionally(new GameException("DISCONNECTED", LOST_MESSAGE));
            c.end();
        }
        return future;
    }

    private void fire(Consumer<GameEventListener> action) {
        for (GameEventListener l : listeners) {
            try {
                action.accept(l);
            } catch (RuntimeException e) {
                // Lỗi của một listener không được làm chết luồng đọc
                e.printStackTrace();
            }
        }
    }

    /** Một phiên kết nối: socket, luồng đọc, luồng PING và các yêu cầu đang chờ. */
    private final class Connection {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        final AtomicLong nextReqId = new AtomicLong(1);
        final Map<Long, Pending> pending = new ConcurrentHashMap<>();
        final AtomicBoolean ended = new AtomicBoolean();
        final Object writeLock = new Object();
        volatile boolean intentional;
        private ScheduledExecutorService pinger;

        Connection(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        }

        void start() {
            Thread reader = new Thread(this::readLoop, "socket-reader");
            reader.setDaemon(true);
            reader.start();
            pinger = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "socket-ping");
                t.setDaemon(true);
                return t;
            });
            pinger.scheduleWithFixedDelay(this::ping, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);
            if (ended.get()) {
                pinger.shutdownNow();
            }
        }

        void send(String text) throws IOException {
            synchronized (writeLock) {
                FramedIO.write(out, text);
            }
        }

        private void ping() {
            // PING không được trả lời đúng hạn = kết nối chết lặng: đóng và báo mất kết nối
            SocketGameClient.this.<Void>request("PING", null, Void.class).whenComplete((v, e) -> {
                if (e instanceof GameException ge && "TIMEOUT".equals(ge.code())) {
                    end();
                }
            });
        }

        private void readLoop() {
            try {
                while (true) {
                    String text = FramedIO.read(in);
                    try {
                        handle(gson.fromJson(text, Envelope.class));
                    } catch (RuntimeException e) {
                        // Thông điệp sai cấu trúc: bỏ qua, không đóng kết nối
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                end();
            }
        }

        private void handle(Envelope env) {
            if (env == null || env.type() == null) {
                return;
            }
            if (env.reqId() != null) {
                Pending p = pending.remove(env.reqId());
                if (p != null) {
                    complete(p, env);
                }
            } else {
                dispatch(env.type(), env.data());
            }
        }

        private void complete(Pending p, Envelope env) {
            if (!Boolean.TRUE.equals(env.ok())) {
                String code = env.error() != null ? env.error().code() : "BAD_RESPONSE";
                String message = env.error() != null ? env.error().message() : "Phản hồi không hợp lệ";
                p.future().completeExceptionally(new GameException(code, message));
                return;
            }
            JsonElement data = env.data();
            if (p.type() == Void.class || data == null || data.isJsonNull()) {
                p.future().complete(null);
                return;
            }
            try {
                p.future().complete(gson.fromJson(data, p.type()));
            } catch (JsonParseException e) {
                p.future().completeExceptionally(new GameException("BAD_RESPONSE", "Phản hồi không hợp lệ"));
            }
        }

        private void dispatch(String type, JsonElement data) {
            switch (type) {
                case "ONLINE_LIST" -> {
                    List<UserInfo> users = gson.fromJson(data, new TypeToken<List<UserInfo>>() { }.getType());
                    fire(l -> l.onOnlineList(users));
                }
                case "USER_UPDATE" -> {
                    UserUpdateData d = gson.fromJson(data, UserUpdateData.class);
                    fire(l -> l.onUserUpdate(d.user(), d.change()));
                }
                case "INVITE_RECEIVED" -> {
                    Invite invite = gson.fromJson(data, Invite.class);
                    fire(l -> l.onInviteReceived(invite));
                }
                case "INVITE_RESULT" -> {
                    InviteResultData d = gson.fromJson(data, InviteResultData.class);
                    fire(l -> l.onInviteResult(d.toUser(), d.result()));
                }
                case "ROOM_STATE" -> {
                    RoomState room = gson.fromJson(data, RoomState.class);
                    fire(l -> l.onRoomState(room));
                }
                case "READY_REMINDER" -> {
                    FromUserData d = gson.fromJson(data, FromUserData.class);
                    fire(l -> l.onReadyReminder(d.fromUser()));
                }
                case "KICKED" -> fire(GameEventListener::onKicked);
                case "GAME_START" -> {
                    GameStart start = gson.fromJson(data, GameStart.class);
                    fire(l -> l.onGameStart(start));
                }
                case "QUESTION" -> {
                    Question q = gson.fromJson(data, Question.class);
                    fire(l -> l.onQuestion(q));
                }
                case "ANSWER_RESULT" -> {
                    AnswerResult r = gson.fromJson(data, AnswerResult.class);
                    fire(l -> l.onAnswerResult(r));
                }
                case "SCOREBOARD" -> {
                    List<ScoreRow> rows = gson.fromJson(data, new TypeToken<List<ScoreRow>>() { }.getType());
                    fire(l -> l.onScoreboard(rows));
                }
                case "PLAYER_LEFT" -> {
                    UsernameData d = gson.fromJson(data, UsernameData.class);
                    fire(l -> l.onPlayerLeft(d.username()));
                }
                case "GAME_END" -> {
                    MatchResult r = gson.fromJson(data, MatchResult.class);
                    fire(l -> l.onGameEnd(r));
                }
                default -> {
                    // type lạ (ví dụ PONG không có reqId): bỏ qua
                }
            }
        }

        /** Kết thúc phiên đúng 1 lần: đóng socket, báo lỗi mọi yêu cầu đang chờ, gọi onDisconnected nếu không chủ động. */
        void end() {
            if (!ended.compareAndSet(false, true)) {
                return;
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // đã đóng
            }
            if (pinger != null) {
                pinger.shutdownNow();
            }
            for (Long id : pending.keySet()) {
                Pending p = pending.remove(id);
                if (p != null) {
                    p.future().completeExceptionally(new GameException("DISCONNECTED", LOST_MESSAGE));
                }
            }
            if (!intentional) {
                fire(GameEventListener::onDisconnected);
            }
        }
    }
}
