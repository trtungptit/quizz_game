package quizgame.client.net;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import quizgame.common.model.ChangeType;
import quizgame.common.model.LeaderboardRow;
import quizgame.common.model.QuestionSetSummary;
import quizgame.common.model.UserInfo;
import quizgame.common.model.UserStatus;
import quizgame.common.protocol.Envelope;
import quizgame.common.protocol.FramedIO;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Server giả chỉ dùng trong test: ServerSocket cục bộ. */
class SocketGameClientTest {
    private final Gson gson = new Gson();
    private ServerSocket serverSocket;
    private Socket serverSide;
    private DataInputStream in;
    private DataOutputStream out;
    private SocketGameClient client;

    @BeforeEach
    void setUp() throws Exception {
        serverSocket = new ServerSocket(0);
        client = new SocketGameClient();
        client.connect("localhost", serverSocket.getLocalPort()).get(5, TimeUnit.SECONDS);
        serverSide = serverSocket.accept();
        in = new DataInputStream(serverSide.getInputStream());
        out = new DataOutputStream(serverSide.getOutputStream());
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        serverSide.close();
        serverSocket.close();
    }

    private Envelope readRequest() throws IOException {
        return gson.fromJson(FramedIO.read(in), Envelope.class);
    }

    private void send(String json) throws IOException {
        FramedIO.write(out, json);
    }

    @Test
    void matchesResponsesByReqIdWhenOverlapping() throws Exception {
        var sets = client.listSets();
        var board = client.getLeaderboard();
        Envelope first = readRequest();
        Envelope second = readRequest();
        assertEquals("SET_LIST", first.type());
        assertEquals("LEADERBOARD", second.type());
        // Trả lời yêu cầu sau trước
        send("{\"type\":\"LEADERBOARD\",\"reqId\":" + second.reqId() + ",\"ok\":true,\"data\":"
                + "[{\"rank\":1,\"username\":\"an\",\"totalWins\":3,\"totalScore\":900}]}");
        send("{\"type\":\"SET_LIST\",\"reqId\":" + first.reqId() + ",\"ok\":true,\"data\":"
                + "[{\"id\":7,\"name\":\"Toán\",\"topic\":\"Số học\",\"owner\":\"an\",\"questionCount\":3,"
                + "\"types\":[\"SINGLE\"],\"hasImages\":false}]}");
        List<LeaderboardRow> rows = board.get(3, TimeUnit.SECONDS);
        List<QuestionSetSummary> list = sets.get(3, TimeUnit.SECONDS);
        assertEquals("an", rows.get(0).username());
        assertEquals(7L, list.get(0).id());
        assertEquals("Toán", list.get(0).name());
    }

    @Test
    void errorResponseBecomesGameException() throws Exception {
        var future = client.login("an", "sai");
        Envelope req = readRequest();
        assertEquals("LOGIN", req.type());
        send("{\"type\":\"LOGIN\",\"reqId\":" + req.reqId() + ",\"ok\":false,\"error\":"
                + "{\"code\":\"WRONG_CREDENTIALS\",\"message\":\"Sai tên đăng nhập hoặc mật khẩu\"}}");
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
        GameException cause = assertInstanceOf(GameException.class, ex.getCause());
        assertEquals("WRONG_CREDENTIALS", cause.code());
        assertEquals("Sai tên đăng nhập hoặc mật khẩu", cause.getMessage());
    }

    @Test
    void loginReturnsUserInfo() throws Exception {
        var future = client.login("an", "123456");
        Envelope req = readRequest();
        send("{\"type\":\"LOGIN\",\"reqId\":" + req.reqId() + ",\"ok\":true,\"data\":"
                + "{\"username\":\"an\",\"totalWins\":12}}");
        assertEquals(new UserInfo("an", 12, UserStatus.IDLE), future.get(3, TimeUnit.SECONDS));
    }

    @Test
    void nullableFieldsAreSentAsNull() throws Exception {
        client.configRoom(null, 5, null);
        Envelope req = readRequest();
        assertEquals("ROOM_CONFIG", req.type());
        assertTrue(req.data().getAsJsonObject().get("setId").isJsonNull());
        assertEquals(5, req.data().getAsJsonObject().get("numQuestions").getAsInt());
    }

    @Test
    void pushEventReachesListener() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        List<String> seen = new ArrayList<>();
        client.addListener(new GameEventListener() {
            @Override
            public void onOnlineList(List<UserInfo> users) {
                seen.add("list:" + users.get(0).username());
                latch.countDown();
            }

            @Override
            public void onUserUpdate(UserInfo user, ChangeType change) {
                seen.add("update:" + user.username() + ":" + change);
                latch.countDown();
            }
        });
        send("{\"type\":\"ONLINE_LIST\",\"data\":[{\"username\":\"binh\",\"totalWins\":9,\"status\":\"IDLE\"}]}");
        send("{\"type\":\"USER_UPDATE\",\"data\":{\"user\":{\"username\":\"chi\",\"totalWins\":1,"
                + "\"status\":\"PLAYING\"},\"change\":\"UPDATE\"}}");
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(List.of("list:binh", "update:chi:UPDATE"), seen);
    }

    @Test
    void serverCloseCallsOnDisconnectedOnceAndFailsPending() throws Exception {
        AtomicInteger count = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        client.addListener(new GameEventListener() {
            @Override
            public void onDisconnected() {
                count.incrementAndGet();
                latch.countDown();
            }
        });
        var pending = client.listSets();
        readRequest();
        serverSide.close();
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        ExecutionException ex = assertThrows(ExecutionException.class, () -> pending.get(3, TimeUnit.SECONDS));
        assertEquals("Mất kết nối tới Server", ex.getCause().getMessage());
        // Gọi thêm close() và gửi yêu cầu mới không được gọi onDisconnected lần nữa
        client.close();
        assertThrows(ExecutionException.class, () -> client.listSets().get(1, TimeUnit.SECONDS));
        Thread.sleep(200);
        assertEquals(1, count.get());
    }

    @Test
    void intentionalCloseDoesNotCallOnDisconnected() throws Exception {
        AtomicInteger count = new AtomicInteger();
        client.addListener(new GameEventListener() {
            @Override
            public void onDisconnected() {
                count.incrementAndGet();
            }
        });
        client.close();
        Thread.sleep(300);
        assertEquals(0, count.get());
    }

    @Test
    void unansweredRequestTimesOut() throws Exception {
        SocketGameClient fast = new SocketGameClient(300);
        fast.connect("localhost", serverSocket.getLocalPort()).get(5, TimeUnit.SECONDS);
        Socket other = serverSocket.accept();
        try {
            var future = fast.listSets();
            ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
            assertEquals("TIMEOUT", ((GameException) ex.getCause()).code());
        } finally {
            fast.close();
            other.close();
        }
    }

    @Test
    void connectFailureGivesVietnameseMessage() throws Exception {
        int freePort;
        try (ServerSocket s = new ServerSocket(0)) {
            freePort = s.getLocalPort();
        }
        var future = new SocketGameClient().connect("localhost", freePort);
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(7, TimeUnit.SECONDS));
        assertEquals("Không kết nối được tới Server localhost:" + freePort, ex.getCause().getMessage());
    }
}
