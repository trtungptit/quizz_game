package quizgame.server.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import quizgame.common.model.QuestionSetSummary;
import quizgame.common.protocol.FramedIO;
import quizgame.server.config.ServerConfig;
import quizgame.server.dao.AccountDao;
import quizgame.server.dao.Database;
import quizgame.server.service.AuthService;
import quizgame.server.service.OnlineRegistry;
import quizgame.server.service.PasswordHasher;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "QUIZ_TEST_DB_URL", matches = ".+")
class QuestionSetReadIntegrationTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path imageRoot;

    @Test
    void readsImagesAndReturnsAnErrorWithoutDisconnectingWhenResponseExceedsFrameLimit() throws Exception {
        java.nio.file.Files.write(imageRoot.resolve("small.png"), new byte[]{1, 2, 3});
        try (Fixture f = new Fixture(imageRoot); Peer peer = new Peer(f.server.port())) {
            peer.login(f.username);
            f.db.transaction(c -> {
                try (var s = c.prepareStatement("UPDATE questions SET image_ref='small.png' WHERE set_id=? AND position=1")) {
                    s.setLong(1, f.setId); s.executeUpdate();
                }
                return null;
            });
            peer.send("SET_LIST", "null");
            assertTrue(peer.read().getAsJsonArray("data").get(0).getAsJsonObject().get("hasImages").getAsBoolean());
            peer.send("SET_GET", "{\"id\":" + f.setId + "}");
            JsonObject result = peer.read();
            assertTrue(result.get("ok").getAsBoolean(), result.toString());
            assertEquals("AQID", result.getAsJsonObject("data").getAsJsonArray("questions").get(0)
                    .getAsJsonObject().get("imageBase64").getAsString());
            java.nio.file.Files.write(imageRoot.resolve("large.png"), new byte[1024 * 1024]);
            f.db.transaction(c -> {
                try (var s = c.prepareStatement("UPDATE questions SET image_ref='large.png' WHERE set_id=?")) {
                    s.setLong(1, f.setId); s.executeUpdate();
                }
                return null;
            });
            peer.send("SET_GET", "{\"id\":" + f.setId + "}");
            assertCode(peer.read(), "INTERNAL");
            peer.send("PING", "null");
            assertEquals("PONG", peer.read().get("type").getAsString());
        }
    }
    @Test
    void listsPublicSetsWithCountsDistinctTypesAndNoAnswers() throws Exception {
        try (Fixture f = new Fixture(); Peer peer = new Peer(f.server.port())) {
            peer.send("SET_LIST", "null");
            assertCode(peer.read(), "NOT_LOGGED_IN");
            peer.login(f.username);
            peer.send("SET_LIST", "{}");
            assertCode(peer.read(), "BAD_REQUEST");
            peer.send("SET_LIST", "null");
            JsonObject response = peer.read();
            assertTrue(response.get("ok").getAsBoolean(), response.toString());
            var sets = response.getAsJsonArray("data");
            assertEquals(1, sets.size());
            var summary = sets.get(0).getAsJsonObject();
            assertEquals(f.setId, summary.get("id").getAsLong());
            assertEquals("Toán cơ bản", summary.get("name").getAsString());
            assertEquals(f.username, summary.get("owner").getAsString());
            assertEquals("Toán", summary.get("topic").getAsString());
            assertEquals(3, summary.get("questionCount").getAsInt());
            assertEquals("[\"SINGLE\",\"MULTI\",\"TYPE\"]", summary.get("types").toString());
            assertFalse(summary.get("hasImages").getAsBoolean());
            assertFalse(response.toString().contains("correctIndexes"));
            QuestionSetSummary typed = new com.google.gson.Gson().fromJson(summary, QuestionSetSummary.class);
            assertEquals(3, typed.questionCount());

            // Bộ công khai phải được đọc bởi người khác, không chỉ chủ sở hữu.
            peer.send("LOGOUT", "null");
            assertTrue(peer.read().get("ok").getAsBoolean());
            peer.send("REGISTER", "{\"username\":\"" + f.username + "x\",\"password\":\"fixture-only\"}");
            assertTrue(peer.read().get("ok").getAsBoolean());
            peer.login(f.username + "x");
            peer.send("SET_GET", "{\"id\":" + f.setId + "}");
            assertTrue(peer.read().get("ok").getAsBoolean());

            // Bộ chưa có câu vẫn xuất hiện với count=0, types=[]; không nhân bản khi JOIN.
            f.db.transaction(c -> {
                try (var s = c.prepareStatement("UPDATE question_sets SET deleted_at=NULL WHERE set_id=?")) {
                    s.setLong(1, f.deletedId); s.executeUpdate();
                }
                return null;
            });
            peer.send("SET_LIST", "null");
            var all = peer.read().getAsJsonArray("data");
            assertEquals(2, all.size());
            var empty = all.get(0).getAsJsonObject();
            assertEquals(f.deletedId, empty.get("id").getAsLong());
            assertEquals(0, empty.get("questionCount").getAsInt());
            assertTrue(empty.getAsJsonArray("types").isEmpty());
            peer.send("SET_GET", "{\"id\":" + f.deletedId + "}");
            assertTrue(peer.read().getAsJsonObject("data").getAsJsonArray("questions").isEmpty());
            f.db.transaction(c -> {
                try (var s = c.prepareStatement("UPDATE question_sets SET deleted_at=CURRENT_TIMESTAMP WHERE set_id IN (?,?)")) {
                    s.setLong(1, f.setId); s.setLong(2, f.deletedId); s.executeUpdate();
                }
                return null;
            });
            peer.send("SET_LIST", "null");
            assertTrue(peer.read().getAsJsonArray("data").isEmpty());
        }
    }

    @Test
    void getsOrderedQuestionDetailsAndRejectsMissingDeletedOrMalformedIds() throws Exception {
        try (Fixture f = new Fixture(); Peer peer = new Peer(f.server.port())) {
            peer.send("SET_GET", "{\"id\":" + f.setId + "}");
            assertCode(peer.read(), "NOT_LOGGED_IN");
            peer.login(f.username);
            peer.send("SET_GET", "{\"id\":" + f.setId + "}");
            JsonObject response = peer.read();
            assertTrue(response.get("ok").getAsBoolean(), response.toString());
            var set = new com.google.gson.Gson().fromJson(response.get("data"), quizgame.common.model.QuestionSet.class);
            assertEquals(f.setId, set.id());
            assertEquals("Toán cơ bản", set.name());
            assertEquals("Các phép tính đơn giản", set.description());
            assertEquals(f.username, set.owner());
            assertEquals(3, set.questions().size());
            var single = set.questions().get(0);
            assertEquals("1 + 1 = ?", single.content());
            assertEquals(java.util.List.of("1", "2", "3", "4"), single.options());
            assertEquals(java.util.List.of(1), single.correctIndexes());
            assertEquals(java.util.List.of(), single.acceptedAnswers());
            assertEquals(20, single.timeSeconds());
            assertNull(single.imageBase64());
            assertEquals(java.util.List.of(1, 3), set.questions().get(1).correctIndexes());
            var text = set.questions().get(2);
            assertEquals(quizgame.common.model.QuestionType.TYPE, text.type());
            assertEquals(java.util.List.of(), text.options());
            assertEquals(java.util.List.of(), text.correctIndexes());
            assertEquals(java.util.List.of("Hà Nội"), text.acceptedAnswers());
            assertEquals(30, text.timeSeconds());

            peer.send("SET_GET", "{\"id\":" + f.deletedId + "}");
            assertCode(peer.read(), "SET_NOT_FOUND");
            peer.send("SET_GET", "{\"id\":9223372036854775807}");
            assertCode(peer.read(), "SET_NOT_FOUND");
            for (String invalid : java.util.List.of("null", "{}", "{\"id\":0}", "{\"id\":-1}",
                    "{\"id\":1.5}", "{\"id\":\"1\"}", "{\"id\":true}",
                    "{\"id\":9223372036854775808}", "{\"id\":1,\"extra\":1}")) {
                peer.send("SET_GET", invalid);
                assertCode(peer.read(), "BAD_REQUEST");
            }
            // Lỗi không làm đóng kết nối hoặc phá trạng thái đăng nhập.
            peer.send("PING", "null");
            assertEquals("PONG", peer.read().get("type").getAsString());
        }
    }

    private static void assertCode(JsonObject response, String code) {
        assertFalse(response.get("ok").getAsBoolean(), response.toString());
        assertEquals(code, response.getAsJsonObject("error").get("code").getAsString());
    }

    private static final class Fixture implements AutoCloseable {
        final Database db;
        final QuizServer server;
        final String username = "sets_" + UUID.randomUUID().toString().substring(0, 8);
        final long setId;
        final long deletedId;
        Fixture() throws Exception { this(java.nio.file.Path.of("images")); }
        Fixture(java.nio.file.Path images) throws Exception {
            db = new Database(ServerConfig.from(Map.of(
                    "QUIZ_DB_URL", System.getenv("QUIZ_TEST_DB_URL"),
                    "QUIZ_DB_USER", System.getenv("QUIZ_TEST_DB_USER"),
                    "QUIZ_DB_PASSWORD", System.getenv("QUIZ_TEST_DB_PASSWORD"))));
            AuthService auth = new AuthService(db, new AccountDao(), new PasswordHasher());
            auth.register(username, "fixture-only".toCharArray());
            long[] ids = db.transaction(c -> {
                long owner;
                try (var s = c.prepareStatement("SELECT user_id FROM users WHERE username=?")) {
                    s.setString(1, username);
                    try (var rs = s.executeQuery()) { assertTrue(rs.next()); owner = rs.getLong(1); }
                }
                long visible = insert(c, "INSERT INTO question_sets(owner_id,title,topic,description) VALUES(?,?,?,?)",
                        owner, "Toán cơ bản", "Toán", "Các phép tính đơn giản");
                long deleted = insert(c, "INSERT INTO question_sets(owner_id,title,description,deleted_at) VALUES(?,?,?,CURRENT_TIMESTAMP)",
                        owner, "Đã xóa", "Không hiển thị");
                long single = insert(c, "INSERT INTO questions(set_id,type,prompt,time_limit_seconds,position) VALUES(?,?,?,?,?)",
                        visible, "SINGLE", "1 + 1 = ?", 20, 1);
                long multi = insert(c, "INSERT INTO questions(set_id,type,prompt,time_limit_seconds,position) VALUES(?,?,?,?,?)",
                        visible, "MULTI", "Chọn số chẵn", 25, 2);
                long text = insert(c, "INSERT INTO questions(set_id,type,prompt,time_limit_seconds,position) VALUES(?,?,?,?,?)",
                        visible, "TYPE", "Thủ đô Việt Nam?", 30, 3);
                // Cố ý ghi đáp án không theo thứ tự để kiểm tra ORDER BY option_index.
                insert(c, "INSERT INTO question_options(question_id,option_index,option_text,is_correct) VALUES(?,?,?,?)", single, 1, "2", true);
                insert(c, "INSERT INTO question_options(question_id,option_index,option_text,is_correct) VALUES(?,?,?,?)", single, 0, "1", false);
                insert(c, "INSERT INTO question_options(question_id,option_index,option_text,is_correct) VALUES(?,?,?,?)", single, 2, "3", false);
                insert(c, "INSERT INTO question_options(question_id,option_index,option_text,is_correct) VALUES(?,?,?,?)", single, 3, "4", false);
                for (int i = 0; i < 4; i++) insert(c,
                        "INSERT INTO question_options(question_id,option_index,option_text,is_correct) VALUES(?,?,?,?)",
                        multi, i, String.valueOf(i + 1), i == 1 || i == 3);
                insert(c, "INSERT INTO accepted_answers(question_id,answer_text,normalized_text) VALUES(?,?,?)", text, "Hà Nội", "hà nội");
                return new long[]{visible, deleted};
            });
            setId = ids[0]; deletedId = ids[1];
            server = new QuizServer("127.0.0.1", 0, auth, new OnlineRegistry(), null, null,
                    new quizgame.server.service.QuestionSetService(db, new quizgame.server.dao.QuestionSetDao(),
                            new quizgame.server.service.QuestionImageReader(images)));
            server.start();
        }
        @Override public void close() throws Exception {
            server.close();
            db.transaction(c -> {
                try (var s = c.prepareStatement("DELETE FROM accepted_answers WHERE question_id IN (SELECT question_id FROM questions WHERE set_id=?)")) {
                    s.setLong(1, setId); s.executeUpdate();
                }
                try (var s = c.prepareStatement("DELETE FROM questions WHERE set_id=?")) { s.setLong(1, setId); s.executeUpdate(); }
                try (var s = c.prepareStatement("DELETE FROM question_sets WHERE set_id IN (?,?)")) { s.setLong(1, setId); s.setLong(2, deletedId); s.executeUpdate(); }
                try (var s = c.prepareStatement("DELETE FROM users WHERE username IN (?,?)")) {
                    s.setString(1, username); s.setString(2, username + "x"); s.executeUpdate();
                }
                return null;
            });
        }
    }

    private static long insert(Connection c, String sql, Object... values) throws Exception {
        try (var s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
            s.executeUpdate();
            try (var keys = s.getGeneratedKeys()) { assertTrue(keys.next()); return keys.getLong(1); }
        }
    }

    private static final class Peer implements AutoCloseable {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        long reqId;
        Peer(int port) throws Exception {
            socket = new Socket("127.0.0.1", port); socket.setSoTimeout(5000);
            in = new DataInputStream(socket.getInputStream()); out = new DataOutputStream(socket.getOutputStream());
        }
        void send(String type, String data) throws Exception {
            FramedIO.write(out, "{\"type\":\"" + type + "\",\"reqId\":" + (++reqId) + ",\"data\":" + data + "}");
        }
        JsonObject read() throws Exception {
            JsonObject value = JsonParser.parseString(FramedIO.read(in)).getAsJsonObject();
            assertEquals(reqId, value.get("reqId").getAsLong());
            return value;
        }
        void login(String username) throws Exception {
            send("LOGIN", "{\"username\":\"" + username + "\",\"password\":\"fixture-only\"}");
            assertTrue(read().get("ok").getAsBoolean());
            assertEquals("ONLINE_LIST", JsonParser.parseString(FramedIO.read(in)).getAsJsonObject().get("type").getAsString());
        }
        @Override public void close() throws Exception { socket.close(); }
    }
}
