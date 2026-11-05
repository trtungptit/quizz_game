package quizgame.server.net;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import quizgame.common.model.QuestionDraft;
import quizgame.common.model.QuestionSet;
import quizgame.common.model.QuestionType;
import quizgame.common.protocol.FramedIO;
import quizgame.server.config.ServerConfig;
import quizgame.server.dao.AccountDao;
import quizgame.server.dao.Database;
import quizgame.server.dao.QuestionSetDao;
import quizgame.server.service.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "QUIZ_TEST_DB_URL", matches = ".+")
class QuestionSetCreateIntegrationTest {
    @TempDir Path imageRoot;
    private static final com.google.gson.Gson GSON = new GsonBuilder().serializeNulls().create();

    static QuestionSet draft(String image) {
        return new QuestionSet(0, "Kiến thức Việt Nam", "Tổng hợp", "Bộ câu hỏi mới", "forged_owner", List.of(
                new QuestionDraft(QuestionType.SINGLE, "1 + 1?", List.of("1", "2", "3", "4"), List.of(1), List.of(), 5, image),
                new QuestionDraft(QuestionType.MULTI, "Số chẵn?", List.of("1", "2", "3", "4"), List.of(1, 3), List.of(), 120, null),
                new QuestionDraft(QuestionType.TYPE, "Thủ đô Việt Nam?", List.of(), List.of(),
                        List.of("Hà Nội", "  HÀ   NỘI  ", "Ha Noi"), 30, null)));
    }

    static String payload(QuestionSet set) { return GSON.toJson(Map.of("set", set)); }

    static String png() throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", out);
        return java.util.Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test void createsAllTypesAndImagesUnderAuthenticatedOwnerAndReadsBackOverTcp() throws Exception {
        try (Fixture f = new Fixture(imageRoot); Peer peer = new Peer(f.server.port())) {
            peer.send("SET_CREATE", payload(draft(null)));
            code(peer.read(), "NOT_LOGGED_IN");
            peer.login(f.username);
            String image = png();
            peer.send("SET_CREATE", payload(draft(image)));
            var response = peer.read();
            assertTrue(response.get("ok").getAsBoolean(), response.toString());
            long id = response.getAsJsonObject("data").get("id").getAsLong();
            assertTrue(id > 0);
            peer.send("SET_GET", "{\"id\":" + id + "}");
            var set = GSON.fromJson(peer.read().get("data"), QuestionSet.class);
            assertEquals(f.username, set.owner());
            assertEquals("Kiến thức Việt Nam", set.name());
            assertEquals(3, set.questions().size());
            assertEquals(List.of(1), set.questions().get(0).correctIndexes());
            assertEquals(image, set.questions().get(0).imageBase64());
            assertEquals(List.of(1, 3), set.questions().get(1).correctIndexes());
            assertEquals(List.of("Hà Nội", "Ha Noi"), set.questions().get(2).acceptedAnswers());
            assertEquals(120, set.questions().get(1).timeSeconds());
            assertEquals(3, f.count("questions"));
            assertEquals(8, f.count("question_options"));
            assertEquals(2, f.count("accepted_answers"));
            peer.send("SET_LIST", "null");
            var list = peer.read().getAsJsonArray("data");
            var summary = java.util.stream.StreamSupport.stream(list.spliterator(), false)
                    .map(e -> e.getAsJsonObject()).filter(e -> e.get("id").getAsLong() == id).findFirst().orElseThrow();
            assertEquals(3, summary.get("questionCount").getAsInt());
            assertTrue(summary.get("hasImages").getAsBoolean());
            assertEquals(f.username, summary.get("owner").getAsString());
            assertFalse(summary.has("correctIndexes"));
            // Đọc lại bằng service mới để xác nhận dữ liệu không chỉ nằm trong RAM.
            assertEquals(image, new QuestionSetService(f.db, new QuestionSetDao(), new QuestionImageReader(imageRoot))
                    .get(id).questions().get(0).imageBase64());
        }
    }

    @Test void rejectsInvalidSetsAndMalformedJsonWithoutWritingOrDisconnecting() throws Exception {
        try (Fixture f = new Fixture(imageRoot); Peer peer = new Peer(f.server.port())) {
            peer.login(f.username);
            peer.send("SET_CREATE", payload(draft("AQID")));
            var error = peer.read();
            code(error, "INVALID_SET");
            assertTrue(error.getAsJsonObject("error").get("message").getAsString().contains("Câu 1:"));
            var invalid = GSON.toJsonTree(Map.of("set", draft(null))).getAsJsonObject();
            invalid.getAsJsonObject("set").getAsJsonArray("questions").get(1).getAsJsonObject().addProperty("timeSeconds", 4);
            peer.send("SET_CREATE", invalid.toString());
            error = peer.read();
            code(error, "INVALID_SET");
            assertTrue(error.getAsJsonObject("error").get("message").getAsString().startsWith("Câu 2:"));
            for (String value : List.of("null", "{}", "{\"set\":null}", "{\"set\":[]}")) {
                peer.send("SET_CREATE", value); code(peer.read(), "BAD_REQUEST");
            }
            for (String field : List.of("id", "timeSeconds", "correctIndexes")) {
                var malformed = GSON.toJsonTree(Map.of("set", draft(null))).getAsJsonObject();
                var set = malformed.getAsJsonObject("set");
                if (field.equals("id")) set.addProperty(field, "0");
                else if (field.equals("timeSeconds")) set.getAsJsonArray("questions").get(0).getAsJsonObject().addProperty(field, 5.5);
                else set.getAsJsonArray("questions").get(0).getAsJsonObject().add(field, JsonParser.parseString("[\"1\"]"));
                peer.send("SET_CREATE", malformed.toString()); code(peer.read(), "BAD_REQUEST");
            }
            assertEquals(0, f.count("question_sets"));
            try (var files = Files.list(imageRoot)) { assertEquals(0, files.count()); }
            peer.send("PING", "null"); assertEquals("PONG", peer.read().get("type").getAsString());
            peer.send("SET_CREATE", payload(draft(null))); assertTrue(peer.read().get("ok").getAsBoolean());
        }
    }

    @Test void rollsBackAllRowsAndRemovesImagesWhenSqlFailsMidSave() throws Exception {
        try (Fixture f = new Fixture(imageRoot); Peer peer = new Peer(f.server.port())) {
            peer.login(f.username);
            String trigger = "fail_create_" + UUID.randomUUID().toString().replace("-", "");
            f.db.transaction(c -> {
                try (var s = c.createStatement()) {
                    s.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON accepted_answers FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test rollback'");
                }
                return null;
            });
            try {
                peer.send("SET_CREATE", payload(draft(png())));
                code(peer.read(), "INTERNAL");
                for (String table : List.of("question_sets", "questions", "question_options", "accepted_answers"))
                    assertEquals(0, f.count(table), table);
                try (var files = Files.list(imageRoot)) { assertEquals(0, files.count()); }
            } finally {
                f.db.transaction(c -> { try (var s = c.createStatement()) { s.execute("DROP TRIGGER " + trigger); } return null; });
            }
            peer.send("SET_CREATE", payload(draft(null))); assertTrue(peer.read().get("ok").getAsBoolean());
        }
    }

    @Test void returnsInternalAndWritesNoRowsWhenImageDirectoryIsUnavailable() throws Exception {
        Path file = imageRoot.resolve("not-a-directory");
        Files.writeString(file, "occupied");
        try (Fixture f = new Fixture(file); Peer peer = new Peer(f.server.port())) {
            peer.login(f.username);
            peer.send("SET_CREATE", payload(draft(png())));
            code(peer.read(), "INTERNAL");
            assertEquals(0, f.count("question_sets"));
            peer.send("PING", "null"); assertEquals("PONG", peer.read().get("type").getAsString());
        }
    }

    static void code(JsonObject result, String code) {
        assertFalse(result.get("ok").getAsBoolean(), result.toString());
        assertEquals(code, result.getAsJsonObject("error").get("code").getAsString());
    }

    static class Fixture implements AutoCloseable {
        final Database db = new Database(ServerConfig.from(Map.of("QUIZ_DB_URL", System.getenv("QUIZ_TEST_DB_URL"),
                "QUIZ_DB_USER", System.getenv("QUIZ_TEST_DB_USER"), "QUIZ_DB_PASSWORD", System.getenv("QUIZ_TEST_DB_PASSWORD"))));
        final String username = "create_" + UUID.randomUUID().toString().substring(0, 8);
        final long ownerId;
        final QuizServer server;

        Fixture(Path images) throws Exception { this(images, false); }
        Fixture(Path images, boolean withRooms) throws Exception {
            var auth = new AuthService(db, new AccountDao(), new PasswordHasher());
            auth.register(username, "fixture-only".toCharArray());
            ownerId = db.transaction(c -> {
                try (var s = c.prepareStatement("SELECT user_id FROM users WHERE username=?")) {
                    s.setString(1, username);
                    try (var r = s.executeQuery()) { assertTrue(r.next()); return r.getLong(1); }
                }
            });
            var online = new OnlineRegistry();
            var rooms = withRooms ? new RoomManager(db, new quizgame.server.dao.RoomDao(), online) : null;
            server = new QuizServer("127.0.0.1", 0, auth, online, rooms, null,
                    new QuestionSetService(db, new QuestionSetDao(), new QuestionImageReader(images)));
            server.start();
        }

        long count(String table) throws Exception {
            return db.transaction(c -> {
                String sql = switch (table) {
                    case "question_sets" -> "SELECT COUNT(*) FROM question_sets WHERE owner_id=?";
                    case "questions" -> "SELECT COUNT(*) FROM questions q JOIN question_sets s ON q.set_id=s.set_id WHERE s.owner_id=?";
                    case "question_options" -> "SELECT COUNT(*) FROM question_options a JOIN questions q ON a.question_id=q.question_id JOIN question_sets s ON q.set_id=s.set_id WHERE s.owner_id=?";
                    case "accepted_answers" -> "SELECT COUNT(*) FROM accepted_answers a JOIN questions q ON a.question_id=q.question_id JOIN question_sets s ON q.set_id=s.set_id WHERE s.owner_id=?";
                    default -> throw new IllegalArgumentException();
                };
                try (var s = c.prepareStatement(sql)) {
                    s.setLong(1, ownerId); try (var r = s.executeQuery()) { r.next(); return r.getLong(1); }
                }
            });
        }

        public void close() throws Exception {
            server.close();
            db.transaction(c -> {
                for (String sql : List.of("DELETE q FROM questions q JOIN question_sets s ON q.set_id=s.set_id WHERE s.owner_id=?",
                        "DELETE FROM question_sets WHERE owner_id=?", "DELETE FROM users WHERE user_id=?")) {
                    try (var s = c.prepareStatement(sql)) { s.setLong(1, ownerId); s.executeUpdate(); }
                }
                return null;
            });
        }
    }

    static final class Peer implements AutoCloseable {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        long reqId;
        Peer(int port) throws Exception {
            socket = new Socket("127.0.0.1", port); socket.setSoTimeout(10000);
            in = new DataInputStream(socket.getInputStream()); out = new DataOutputStream(socket.getOutputStream());
        }
        void send(String type, String data) throws Exception {
            FramedIO.write(out, "{\"type\":\"" + type + "\",\"reqId\":" + (++reqId) + ",\"data\":" + data + "}");
        }
        JsonObject read() throws Exception {
            while (true) {
                var value = JsonParser.parseString(FramedIO.read(in)).getAsJsonObject();
                if (!value.has("reqId")) continue;
                assertEquals(reqId, value.get("reqId").getAsLong()); return value;
            }
        }
        JsonObject event(String type) throws Exception {
            while (true) {
                var value = JsonParser.parseString(FramedIO.read(in)).getAsJsonObject();
                if (type.equals(value.get("type").getAsString())) return value;
            }
        }
        void login(String username) throws Exception {
            send("LOGIN", "{\"username\":\"" + username + "\",\"password\":\"fixture-only\"}");
            assertTrue(read().get("ok").getAsBoolean());
            assertEquals("ONLINE_LIST", JsonParser.parseString(FramedIO.read(in)).getAsJsonObject().get("type").getAsString());
        }
        public void close() throws Exception { socket.close(); }
    }
}
