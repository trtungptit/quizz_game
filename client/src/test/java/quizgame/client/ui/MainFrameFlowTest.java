package quizgame.client.ui;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import quizgame.client.net.SocketGameClient;
import quizgame.common.model.QuestionSetSummary;
import quizgame.common.model.QuestionType;
import quizgame.common.protocol.Envelope;
import quizgame.common.protocol.FramedIO;

import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chạy MainFrame thật với SocketGameClient thật nối tới server giả cục bộ (chỉ nằm trong test). */
class MainFrameFlowTest {
    private static final String ROOM = "{\"code\":\"ABC234\",\"name\":\"P\",\"host\":\"an\",\"members\":["
            + "{\"username\":\"an\",\"ready\":false},{\"username\":\"binh\",\"ready\":false}],"
            + "\"pendingInvitees\":[],\"set\":null,\"numQuestions\":0,\"timeOverrideSeconds\":null,"
            + "\"phase\":\"WAITING\"}";
    private static final String SET_GET = "{\"id\":5,\"name\":\"Toán\",\"topic\":\"Số\",\"description\":\"\","
            + "\"owner\":\"binh\",\"questions\":[{\"type\":\"SINGLE\",\"content\":\"1+1=?\","
            + "\"options\":[\"1\",\"2\",\"3\",\"4\"],\"correctIndexes\":[1],\"acceptedAnswers\":[],"
            + "\"timeSeconds\":20,\"imageBase64\":null}]}";

    private final Gson gson = new Gson();
    private final List<Envelope> requests = new CopyOnWriteArrayList<>();
    private volatile boolean wrongPassword;
    private ServerSocket serverSocket;
    private volatile Socket serverSide;
    private volatile DataOutputStream out;
    private Preferences prefs;
    private MainFrame frame;
    private SocketGameClient client;

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "cần màn hình để dựng Swing");
        serverSocket = new ServerSocket(0);
        Thread server = new Thread(this::serve, "fake-server");
        server.setDaemon(true);
        server.start();
        prefs = Preferences.userRoot().node("quizgame-test-" + System.nanoTime());
        client = new SocketGameClient();
        SwingUtilities.invokeAndWait(() -> {
            frame = new MainFrame(client, prefs);
            frame.setVisible(true);
        });
    }

    @AfterEach
    void tearDown() throws IOException, BackingStoreException {
        if (frame == null) {
            return;
        }
        client.close();
        SwingUtilities.invokeLater(frame::dispose);
        if (serverSide != null) {
            serverSide.close();
        }
        serverSocket.close();
        prefs.removeNode();
    }

    // ---------------------------------------------------------------- Server giả

    private void serve() {
        try (Socket s = serverSocket.accept()) {
            serverSide = s;
            DataInputStream in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());
            while (true) {
                Envelope req = gson.fromJson(FramedIO.read(in), Envelope.class);
                requests.add(req);
                handle(req);
            }
        } catch (IOException e) {
            // client hoặc test đóng kết nối
        }
    }

    private void handle(Envelope req) throws IOException {
        switch (req.type()) {
            case "LOGIN" -> {
                if (wrongPassword) {
                    send("{\"type\":\"LOGIN\",\"reqId\":" + req.reqId() + ",\"ok\":false,\"error\":"
                            + "{\"code\":\"WRONG_CREDENTIALS\",\"message\":\"Sai tên đăng nhập hoặc mật khẩu\"}}");
                } else {
                    reply(req, "{\"username\":\"binh\",\"totalWins\":4}");
                    push("{\"type\":\"ONLINE_LIST\",\"data\":[{\"username\":\"an\",\"totalWins\":9,\"status\":\"IDLE\"}]}");
                }
            }
            case "SET_LIST" -> reply(req, "[]");
            case "SET_GET" -> reply(req, SET_GET);
            case "INVITE_REPLY", "CREATE_ROOM" -> reply(req, ROOM);
            default -> reply(req, "null");
        }
    }

    private synchronized void send(String json) throws IOException {
        FramedIO.write(out, json);
    }

    private void reply(Envelope req, String data) throws IOException {
        send("{\"type\":\"" + req.type() + "\",\"reqId\":" + req.reqId() + ",\"ok\":true,\"data\":" + data + "}");
    }

    private void push(String json) throws IOException {
        send(json);
    }

    private boolean sawRequest(String type) {
        return requests.stream().anyMatch(r -> type.equals(r.type()));
    }

    // ---------------------------------------------------------------- Công cụ thao tác giao diện

    private void waitFor(String what, BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Quá thời gian chờ: " + what);
    }

    private void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private static void collect(Component c, Class<?> type, List<Component> found) {
        if (type.isInstance(c)) {
            found.add(c);
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                collect(child, type, found);
            }
        }
    }

    private static List<AbstractButton> buttons(Container root, String text) {
        List<Component> all = new ArrayList<>();
        collect(root, AbstractButton.class, all);
        return all.stream().map(c -> (AbstractButton) c).filter(b -> text.equals(b.getText())).toList();
    }

    private void login(String password) throws Exception {
        onEdt(() -> {
            List<Component> fields = new ArrayList<>();
            collect(frame.getContentPane(), JTextField.class, fields);
            // thứ tự trong màn đăng nhập: máy chủ, cổng, tên, mật khẩu, nhập lại
            ((JTextField) fields.get(0)).setText("localhost");
            ((JTextField) fields.get(1)).setText(String.valueOf(serverSocket.getLocalPort()));
            ((JTextField) fields.get(2)).setText("binh");
            ((JTextField) fields.get(3)).setText(password);
            List<AbstractButton> submit = buttons(frame.getContentPane(), "ĐĂNG NHẬP");
            submit.get(submit.size() - 1).doClick(); // nút gửi là nút ĐĂNG NHẬP sau tab cùng tên
        });
    }

    private String screen() {
        return frame.currentScreen();
    }

    // ---------------------------------------------------------------- Các tình huống

    @Test
    void wrongPasswordShowsServerMessageAndStaysOnLogin() throws Exception {
        wrongPassword = true;
        login("sai-mat-khau");
        waitFor("lỗi đăng nhập", () -> sawRequest("LOGIN"));
        waitFor("dòng lỗi đỏ", () -> {
            List<Component> labels = new ArrayList<>();
            collect(frame.getContentPane(), JLabel.class, labels);
            return labels.stream().anyMatch(l -> ((JLabel) l).getText().contains("Sai tên đăng nhập hoặc mật khẩu"));
        });
        assertEquals(MainFrame.LOGIN, screen());
    }

    @Test
    void loginInviteGameAndResultFlow() throws Exception {
        login("123456");
        waitFor("vào trang chủ", () -> MainFrame.HOME.equals(screen()));
        assertEquals("binh", frame.username());
        assertEquals(4, frame.totalWins());
        waitFor("tải danh sách bộ câu hỏi", () -> sawRequest("SET_LIST"));

        // Hai lời mời xếp hàng; đồng ý lời đầu thì vào phòng
        push("{\"type\":\"INVITE_RECEIVED\",\"data\":{\"id\":7,\"fromUser\":\"an\",\"roomCode\":\"ABC234\","
                + "\"roomName\":\"P\",\"expiresInMs\":5000}}");
        push("{\"type\":\"INVITE_RECEIVED\",\"data\":{\"id\":8,\"fromUser\":\"chi\",\"roomCode\":\"XYZ789\","
                + "\"roomName\":\"Q\",\"expiresInMs\":5000}}");
        waitFor("hộp thoại lời mời", () -> inviteDialog() != null);
        onEdt(() -> {
            JDialog d = inviteDialog();
            assertTrue(labelText(d).contains("(1/2)"), labelText(d));
            buttons(d.getContentPane(), "ĐỒNG Ý").get(0).doClick();
        });
        waitFor("vào phòng", () -> MainFrame.ROOM.equals(screen()));
        assertNotNull(frame.currentRoom());
        assertEquals("ABC234", frame.currentRoom().code());
        assertTrue(sawRequest("INVITE_REPLY"));

        // Trận đấu
        push("{\"type\":\"GAME_START\",\"data\":{\"numQuestions\":1,\"imagesBase64\":{}}}");
        push("{\"type\":\"QUESTION\",\"data\":{\"qIndex\":0,\"total\":1,\"type\":\"SINGLE\",\"content\":\"1+1=?\","
                + "\"options\":[\"1\",\"2\",\"3\",\"4\"],\"imageId\":null,\"timeLimitMs\":10000}}");
        waitFor("sang màn chơi", () -> MainFrame.GAME.equals(screen()));
        onEdt(() -> {
            List<Component> labels = new ArrayList<>();
            collect(frame.getContentPane(), JLabel.class, labels);
            assertTrue(labels.stream().anyMatch(l -> "CÂU 1/1".equals(((JLabel) l).getText())));
        });
        push("{\"type\":\"ANSWER_RESULT\",\"data\":{\"qIndex\":0,\"correct\":true,\"correctIndexes\":[1],"
                + "\"acceptedAnswers\":[],\"points\":900,\"totalScore\":900}}");
        push("{\"type\":\"GAME_END\",\"data\":{\"matchId\":3,\"winner\":\"binh\",\"draw\":false,\"rows\":["
                + "{\"rank\":1,\"username\":\"binh\",\"score\":900,\"correctCount\":1,\"finishTimeMs\":9000,"
                + "\"status\":\"FINISHED\"}]}}");
        waitFor("sang màn kết quả", () -> MainFrame.RESULT.equals(screen()));
        onEdt(() -> {
            List<Component> labels = new ArrayList<>();
            collect(frame.getContentPane(), JLabel.class, labels);
            assertTrue(labels.stream().anyMatch(l -> "BẠN THẮNG!".equals(((JLabel) l).getText())));
        });

        // Rời phòng về trang chủ
        onEdt(() -> {
            List<AbstractButton> leave = buttons(frame.getContentPane(), "RỜI PHÒNG");
            leave.get(leave.size() - 1).doClick();
        });
        waitFor("về trang chủ", () -> MainFrame.HOME.equals(screen()));
        assertTrue(sawRequest("LEAVE_ROOM"));
        assertEquals(null, frame.currentRoom());
    }

    @Test
    void createRoomWithSetConfiguresAtMostTenQuestions() throws Exception {
        login("123456");
        waitFor("vào trang chủ", () -> MainFrame.HOME.equals(screen()));
        QuestionSetSummary set = new QuestionSetSummary(5, "Toán", "Số", "an", 18, List.of(QuestionType.SINGLE),
                false);
        onEdt(() -> frame.createRoomAndEnter("Phòng thử", set, null, null));
        waitFor("vào phòng", () -> MainFrame.ROOM.equals(screen()));
        waitFor("cấu hình phòng", () -> sawRequest("ROOM_CONFIG"));
        Envelope config = requests.stream().filter(r -> "ROOM_CONFIG".equals(r.type())).findFirst().orElseThrow();
        assertEquals(10, config.data().getAsJsonObject().get("numQuestions").getAsInt());
        assertEquals(5, config.data().getAsJsonObject().get("setId").getAsInt());
    }

    @Test
    void editingExistingSetLoadsFromServer() throws Exception {
        login("123456");
        waitFor("vào trang chủ", () -> MainFrame.HOME.equals(screen()));
        onEdt(() -> frame.openSetEditor(5L));
        waitFor("màn soạn bộ", () -> MainFrame.SET_EDITOR.equals(screen()));
        waitFor("tải bộ câu hỏi", () -> sawRequest("SET_GET"));
        waitFor("nạp tên bộ", () -> {
            List<Component> fields = new ArrayList<>();
            collect(frame.getContentPane(), JTextField.class, fields);
            return fields.stream().anyMatch(f -> "Toán".equals(((JTextField) f).getText()));
        });
    }

    @Test
    void serverDisconnectShowsMessageAndReturnsToLogin() throws Exception {
        login("123456");
        waitFor("vào trang chủ", () -> MainFrame.HOME.equals(screen()));
        serverSide.close();
        // Hộp thoại báo mất kết nối là modal: tìm rồi đóng để luồng giao diện đi tiếp
        waitFor("hộp thoại mất kết nối", () -> findDialogWithText("Mất kết nối tới Server") != null);
        onEdt(() -> findDialogWithText("Mất kết nối tới Server").dispose());
        waitFor("về màn đăng nhập", () -> MainFrame.LOGIN.equals(screen()));
        assertEquals(null, frame.username());
    }

    // ---------------------------------------------------------------- Tìm hộp thoại

    private JDialog inviteDialog() {
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog d && d.isVisible() && "Lời mời thi đấu".equals(d.getTitle())) {
                return d;
            }
        }
        return null;
    }

    private JDialog findDialogWithText(String text) {
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog d && d.isVisible() && labelText(d).contains(text)) {
                return d;
            }
        }
        return null;
    }

    private static String labelText(JDialog d) {
        List<Component> labels = new ArrayList<>();
        collect(d.getContentPane(), JLabel.class, labels);
        StringBuilder sb = new StringBuilder();
        labels.forEach(l -> sb.append(((JLabel) l).getText()).append('\n'));
        return sb.toString();
    }
}

