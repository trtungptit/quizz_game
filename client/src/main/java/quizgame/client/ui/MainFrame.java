package quizgame.client.ui;

import quizgame.client.net.GameClient;
import quizgame.client.net.GameEventListener;
import quizgame.client.net.GameException;
import quizgame.client.ui.component.OnlineUsersModel;
import quizgame.client.ui.dialog.InviteDialog;
import quizgame.client.ui.screen.GamePanel;
import quizgame.client.ui.screen.HistoryPanel;
import quizgame.client.ui.screen.HomePanel;
import quizgame.client.ui.screen.LeaderboardPanel;
import quizgame.client.ui.screen.LoginPanel;
import quizgame.client.ui.screen.ResultPanel;
import quizgame.client.ui.screen.RoomPanel;
import quizgame.client.ui.screen.SetEditorPanel;
import quizgame.client.util.Async;
import quizgame.common.model.AnswerResult;
import quizgame.common.model.BoxOffer;
import quizgame.common.model.BoxResult;
import quizgame.common.model.BoxStatus;
import quizgame.common.model.ChangeType;
import quizgame.common.model.GameStart;
import quizgame.common.model.Invite;
import quizgame.common.model.InviteResult;
import quizgame.common.model.MatchResult;
import quizgame.common.model.Question;
import quizgame.common.model.QuestionSetSummary;
import quizgame.common.model.RoomState;
import quizgame.common.model.ScoreRow;
import quizgame.common.model.UserInfo;
import quizgame.common.model.UserStatus;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Cửa sổ duy nhất: NavBar ở trên, CardLayout chứa các màn ở dưới. Đồng thời là nơi duy nhất nhận
 * sự kiện Server đẩy xuống rồi chuyển (trên luồng giao diện, đúng thứ tự) cho từng màn.
 */
public class MainFrame extends JFrame {
    public static final String LOGIN = "LOGIN";
    public static final String HOME = "HOME";
    public static final String ROOM = "ROOM";
    public static final String GAME = "GAME";
    public static final String RESULT = "RESULT";
    public static final String SET_EDITOR = "SET_EDITOR";
    public static final String LEADERBOARD = "LEADERBOARD";
    public static final String HISTORY = "HISTORY";

    private final GameClient client;
    private final OnlineUsersModel onlineUsers = new OnlineUsersModel();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final Map<String, JComponent> screens = new HashMap<>();
    private final NavBar navBar;
    private final InviteDialog inviteDialog;

    private final LoginPanel loginPanel;
    private final RoomPanel roomPanel;
    private final GamePanel gamePanel;
    private final ResultPanel resultPanel;
    private final SetEditorPanel setEditorPanel;

    private volatile String currentKey;
    private RoomState currentRoom;
    private boolean roomExitPending;
    private long sessionEpoch;
    private String departedRoomCode;

    public MainFrame(GameClient client) {
        this(client, Preferences.userNodeForPackage(LoginPanel.class));
    }

    /** prefs: nơi nhớ máy chủ lần trước (test truyền nút riêng để không đụng cài đặt thật). */
    public MainFrame(GameClient client, Preferences prefs) {
        super(AppTheme.APP_NAME);
        this.client = client;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1100, 720);
        setLocationRelativeTo(null);

        navBar = new NavBar(this::showScreen, this::logout);
        inviteDialog = new InviteDialog(this);
        loginPanel = new LoginPanel(client, this::onLoggedIn, prefs);
        roomPanel = new RoomPanel(this);
        gamePanel = new GamePanel(this);
        resultPanel = new ResultPanel(this);
        setEditorPanel = new SetEditorPanel(this);
        register(LOGIN, loginPanel);
        register(HOME, new HomePanel(this));
        register(ROOM, roomPanel);
        register(GAME, gamePanel);
        register(RESULT, resultPanel);
        register(SET_EDITOR, setEditorPanel);
        register(LEADERBOARD, new LeaderboardPanel(this));
        register(HISTORY, new HistoryPanel(this));
        add(navBar, BorderLayout.NORTH);
        add(cards, BorderLayout.CENTER);

        client.addListener(new EventForwarder());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Còn thay đổi chưa lưu thì hỏi trước khi thoát
                Screen current = screenOf(currentKey);
                if (current != null && !current.canLeave()) {
                    return;
                }
                client.close();
                dispose();
                System.exit(0);
            }
        });
        switchTo(LOGIN, true);
    }

    private void register(String key, JComponent screen) {
        screens.put(key, screen);
        cards.add(screen, key);
    }

    // ---------------------------------------------------------------- Thông tin dùng chung

    public GameClient client() {
        return client;
    }

    public OnlineUsersModel onlineUsers() {
        return onlineUsers;
    }

    /** Tên người dùng đang đăng nhập (null nếu chưa). */
    public String username() {
        UserInfo self = onlineUsers.self();
        return self == null ? null : self.username();
    }

    /** Số trận thắng hiện tại của mình. */
    public int totalWins() {
        UserInfo self = onlineUsers.self();
        return self == null ? 0 : self.totalWins();
    }

    /** Phòng đang ở (null nếu không ở phòng nào). */
    public RoomState currentRoom() {
        return currentRoom;
    }

    /** Key của màn đang hiện. */
    public String currentScreen() {
        return currentKey;
    }

    public String defaultRoomName() {
        return "Phòng của " + username();
    }

    // ---------------------------------------------------------------- Chuyển màn

    /** Chuyển màn; nếu màn hiện tại còn thay đổi chưa lưu thì hỏi trước. */
    public void showScreen(String key) {
        navigate(key, null);
    }

    private void navigate(String key, Runnable after) {
        if (roomExitPending) return;
        if (currentRoom != null && !ROOM.equals(key) && !GAME.equals(key) && !RESULT.equals(key)) {
            Screen old = screenOf(currentKey);
            if (old != null && !old.canLeave()) return;
            leaveAndNavigate(key, after);
        } else {
            switchTo(key, false);
            if (key.equals(currentKey) && after != null) after.run();
        }
    }

    /** Chỉ bỏ state phòng khi server đã ACK; bấm lặp trong lúc chờ không gửi lệnh trùng. */
    private void leaveAndNavigate(String key, Runnable after) {
        leaveAndNavigate(key, after, GAME.equals(currentKey));
    }

    private void leaveAndNavigate(String key, Runnable after, boolean quitFirst) {
        if (roomExitPending) return;
        roomExitPending = true;
        long epoch = sessionEpoch;
        CompletableFuture<Void> readyToLeave = quitFirst
                ? tolerate(client.quitGame(), "NOT_PLAYING")
                : CompletableFuture.completedFuture(null);
        CompletableFuture<Void> leaving = readyToLeave.thenCompose(v -> tolerate(client.leaveRoom(), "NOT_IN_ROOM"));
        Async.ui(leaving, v -> {
            if (epoch != sessionEpoch) return;
            roomExitPending = false;
            clearRoomState();
            switchTo(key, true);
            if (after != null) after.run();
        }, error -> {
            if (epoch != sessionEpoch) return;
            roomExitPending = false;
            Dialogs.error(this, error);
        });
    }

    private static CompletableFuture<Void> tolerate(CompletableFuture<Void> future, String code) {
        return future.handle((v, error) -> {
            if (error == null) return null;
            Throwable cause = error;
            while ((cause instanceof java.util.concurrent.CompletionException
                    || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof GameException e && code.equals(e.code())) return null;
            throw new java.util.concurrent.CompletionException(cause);
        });
    }

    private void switchTo(String key, boolean force) {
        Screen old = screenOf(currentKey);
        boolean same = key.equals(currentKey);
        if (!same && !force && old != null && !old.canLeave()) {
            return;
        }
        if (!same && old != null) {
            old.onHide();
        }
        currentKey = key;
        navBar.setVisible(!LOGIN.equals(key) && !GAME.equals(key));
        navBar.setActive(key);
        cardLayout.show(cards, key);
        Screen next = screenOf(key);
        if (!same && next != null) {
            next.onShow();
        }
    }

    private Screen screenOf(String key) {
        JComponent c = key == null ? null : screens.get(key);
        return c instanceof Screen s ? s : null;
    }

    /** Vào phòng với trạng thái đã có (sau createRoom, joinRoom hoặc đồng ý lời mời). */
    public void showRoom(RoomState room) {
        if (room == null) {
            Dialogs.error(this, "Server không trả về thông tin phòng");
            return;
        }
        inviteDialog.clear();
        if (currentRoom == null) {
            roomPanel.reset();
        }
        currentRoom = room;
        if (room.code().equals(departedRoomCode)) departedRoomCode = null;
        roomPanel.render(room);
        switchTo(ROOM, true);
    }

    /** Tạo phòng (kèm cấu hình bộ câu hỏi nếu set != null), vào ROOM rồi báo onSuccess; lỗi thì hiện hộp thoại và gọi onFailure. */
    public void createRoomAndEnter(String name, QuestionSetSummary set, Consumer<RoomState> onSuccess,
                                   Runnable onFailure) {
        CompletableFuture<RoomState> future = client.createRoom(name);
        if (set != null) {
            // Lần đầu chọn bộ: mặc định tối đa 10 câu
            future = future.thenCompose(room ->
                    client.configRoom(set.id(), Math.min(10, set.questionCount()), null).thenApply(v -> room));
        }
        Async.ui(future, room -> {
            if (room == null) {
                Dialogs.error(this, "Server không trả về thông tin phòng");
                if (onFailure != null) {
                    onFailure.run();
                }
                return;
            }
            showRoom(room);
            if (onSuccess != null) {
                onSuccess.accept(room);
            }
        }, error -> {
            Dialogs.error(this, error);
            if (onFailure != null) {
                onFailure.run();
            }
        });
    }

    /** Mở màn soạn bộ câu hỏi: id null = tạo mới, ngược lại tải bộ về để sửa (có trạng thái chờ). */
    public void openSetEditor(Long id) {
        navigate(SET_EDITOR, () -> {
            if (id == null) setEditorPanel.startNew();
            else setEditorPanel.loadRemote(id);
        });
    }

    /** Rời phòng: gọi leaveRoom() rồi về HOME. */
    public void leaveRoom() {
        leaveAndNavigate(HOME, null);
    }

    /** Không coi rời phòng thành công khi QUIT_GAME/LEAVE_ROOM thất bại. */
    public void leaveGame() {
        leaveAndNavigate(HOME, null, true);
    }

    private void goHomeFromRoom() {
        clearRoomState();
        switchTo(HOME, true);
    }

    private void clearRoomState() {
        if (currentRoom != null) departedRoomCode = currentRoom.code();
        currentRoom = null;
        UserInfo self = onlineUsers.self();
        if (self != null) onlineUsers.setSelf(new UserInfo(self.username(), self.totalWins(), UserStatus.IDLE));
        onlineUsers.clearInvited();
        roomPanel.reset();
        gamePanel.end();
    }

    // ---------------------------------------------------------------- Đăng nhập, đăng xuất, mất kết nối

    private void onLoggedIn(UserInfo user) {
        sessionEpoch++;
        roomExitPending = false;
        onlineUsers.setSelf(user);
        switchTo(HOME, true);
    }

    private void logout() {
        Screen current = screenOf(currentKey);
        if (current != null && !current.canLeave()) {
            return;
        }
        // Dù logout lỗi hay không vẫn đóng kết nối rồi về màn đăng nhập
        client.logout().whenComplete((v, e) -> {
            client.close();
            SwingUtilities.invokeLater(this::backToLogin);
        });
    }

    private void onConnectionLost() {
        Dialogs.error(this, "Mất kết nối tới Server");
        backToLogin();
    }

    private void backToLogin() {
        sessionEpoch++;
        roomExitPending = false;
        departedRoomCode = null;
        onlineUsers.clear();
        currentRoom = null;
        roomPanel.reset();
        gamePanel.end();
        inviteDialog.clear();
        setEditorPanel.discard();
        loginPanel.reset();
        switchTo(LOGIN, true);
    }

    // ---------------------------------------------------------------- Sự kiện từ Server

    /** Mọi sự kiện đẩy xuống đi qua đây, bọc trong invokeLater nên các màn luôn nhận đúng thứ tự trên EDT. */
    private final class EventForwarder implements GameEventListener {
        @Override
        public void onOnlineList(List<UserInfo> users) {
            SwingUtilities.invokeLater(() -> onlineUsers.replaceAll(users));
        }

        @Override
        public void onUserUpdate(UserInfo user, ChangeType change) {
            SwingUtilities.invokeLater(() -> onlineUsers.apply(user, change));
        }

        @Override
        public void onInviteReceived(Invite invite) {
            SwingUtilities.invokeLater(() -> {
                if (!GAME.equals(currentKey) && !roomExitPending && currentRoom == null) {
                    inviteDialog.enqueue(invite);
                }
            });
        }

        @Override
        public void onInviteResult(String toUser, InviteResult result) {
            SwingUtilities.invokeLater(() -> {
                onlineUsers.setInvited(toUser, false);
                roomPanel.onInviteResult(toUser, result);
            });
        }

        @Override
        public void onRoomState(RoomState room) {
            SwingUtilities.invokeLater(() -> {
                if (room.code().equals(departedRoomCode)
                        || room.members().stream().noneMatch(m -> m.username().equals(username()))) return;
                currentRoom = room;
                roomPanel.render(room);
            });
        }

        @Override
        public void onReadyReminder(String fromUser) {
            SwingUtilities.invokeLater(() -> roomPanel.onReadyReminder(fromUser));
        }

        @Override
        public void onKicked() {
            SwingUtilities.invokeLater(() -> {
                goHomeFromRoom();
                Dialogs.info(MainFrame.this, "Bạn đã bị mời ra khỏi phòng");
            });
        }

        @Override
        public void onGameStart(GameStart start) {
            SwingUtilities.invokeLater(() -> {
                inviteDialog.clear();
                onlineUsers.clearInvited();
                gamePanel.begin(start);
                switchTo(GAME, true);
            });
        }

        @Override
        public void onQuestion(Question question) {
            SwingUtilities.invokeLater(() -> gamePanel.onQuestion(question));
        }

        @Override
        public void onAnswerResult(AnswerResult result) {
            SwingUtilities.invokeLater(() -> gamePanel.onAnswerResult(result));
        }

        @Override
        public void onScoreboard(List<ScoreRow> rows) {
            SwingUtilities.invokeLater(() -> gamePanel.onScoreboard(rows));
        }

        @Override
        public void onPlayerLeft(String username) {
            SwingUtilities.invokeLater(() -> gamePanel.onPlayerLeft(username));
        }

        @Override
        public void onBoxOffer(BoxOffer offer) {
            SwingUtilities.invokeLater(() -> gamePanel.onBoxOffer(offer));
        }

        @Override
        public void onBoxResult(BoxResult result) {
            SwingUtilities.invokeLater(() -> gamePanel.onBoxResult(result));
        }

        @Override
        public void onBoxStatus(List<BoxStatus> rows) {
            SwingUtilities.invokeLater(() -> gamePanel.onBoxStatus(rows));
        }

        @Override
        public void onGameEnd(MatchResult result) {
            SwingUtilities.invokeLater(() -> {
                if (currentRoom == null || !GAME.equals(currentKey)) return;
                int total = gamePanel.totalQuestions();
                gamePanel.end();
                resultPanel.show(result, total);
                switchTo(RESULT, true);
            });
        }

        @Override
        public void onDisconnected() {
            SwingUtilities.invokeLater(MainFrame.this::onConnectionLost);
        }
    }
}
