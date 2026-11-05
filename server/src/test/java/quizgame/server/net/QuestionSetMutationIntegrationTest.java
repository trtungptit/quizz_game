package quizgame.server.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import quizgame.common.model.QuestionSet;
import quizgame.common.model.QuestionDraft;
import quizgame.common.model.QuestionType;
import quizgame.server.dao.QuestionSetDao;
import quizgame.server.service.QuestionImageReader;
import quizgame.server.service.QuestionSetService;
import quizgame.server.service.ServiceException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static quizgame.server.net.QuestionSetCreateIntegrationTest.*;

@EnabledIfEnvironmentVariable(named = "QUIZ_TEST_DB_URL", matches = ".+")
class QuestionSetMutationIntegrationTest {
    @TempDir Path images;

    private static QuestionSet edited(long id, String name, String image) {
        var draft = draft(image);
        return new QuestionSet(id, name, "Chủ đề mới", "Mô tả mới", "forged_owner", draft.questions());
    }

    private static QuestionSet shortSet(long id) {
        return new QuestionSet(id, "Bộ một câu", "", "", null, List.of(new QuestionDraft(
                QuestionType.TYPE, "Nội dung mới", List.of(), List.of(), List.of("đáp án mới"), 15, null)));
    }

    private static void ok(JsonObject result) {
        assertTrue(result.get("ok").getAsBoolean(), result.toString());
        assertTrue(result.get("data").isJsonNull(), result.toString());
    }

    @Test void updatesMetadataAllTypesAndImageWithoutChangingPublicIdOrOwner() throws Exception {
        try (Fixture f = new Fixture(images); Peer peer = new Peer(f.server.port())) {
            peer.login(f.username);
            long id = f.create(null);
            String image = png();
            peer.send("SET_UPDATE", payload(edited(id, "Đã sửa", image))); ok(peer.read());
            peer.send("SET_GET", "{\"id\":" + id + "}");
            var result = peer.read(); assertTrue(result.get("ok").getAsBoolean(), result.toString());
            var set = new Gson().fromJson(result.get("data"), QuestionSet.class);
            assertEquals(id, set.id()); assertEquals(f.username, set.owner());
            assertEquals("Đã sửa", set.name()); assertEquals("Chủ đề mới", set.topic());
            assertEquals("Mô tả mới", set.description());
            assertEquals(3, set.questions().size());
            assertEquals(List.of(1), set.questions().get(0).correctIndexes());
            assertEquals(List.of(1, 3), set.questions().get(1).correctIndexes());
            assertEquals(List.of("Hà Nội", "Ha Noi"), set.questions().get(2).acceptedAnswers());
            assertEquals(image, set.questions().get(0).imageBase64());
            peer.send("SET_LIST", "null");
            var list = peer.read().getAsJsonArray("data");
            assertEquals(1, list.size()); assertEquals(id, list.get(0).getAsJsonObject().get("id").getAsLong());
            assertTrue(list.get(0).getAsJsonObject().get("hasImages").getAsBoolean());
            assertEquals(2, f.count("question_sets")); // Một bản công khai và một phiên bản cũ ẩn.
            assertEquals("Đã sửa", f.service.get(id).name());
        }
    }

    @Test void enforcesAuthenticationOwnershipIdsAndSoftDeletion() throws Exception {
        try (Fixture f = new Fixture(images); Peer owner = new Peer(f.server.port());
             QuestionSetCreateIntegrationTest.Fixture other = new QuestionSetCreateIntegrationTest.Fixture(images);
             Peer stranger = new Peer(f.server.port())) {
            long id = f.create(null);
            owner.send("SET_UPDATE", payload(edited(id, "No", null))); code(owner.read(), "NOT_LOGGED_IN");
            owner.send("SET_DELETE", "{\"id\":" + id + "}"); code(owner.read(), "NOT_LOGGED_IN");
            owner.login(f.username); stranger.login(other.username);
            stranger.send("SET_UPDATE", payload(edited(id, "No", null))); code(stranger.read(), "NOT_OWNER");
            stranger.send("SET_DELETE", "{\"id\":" + id + "}"); code(stranger.read(), "NOT_OWNER");
            owner.send("SET_UPDATE", payload(edited(Long.MAX_VALUE, "No", null))); code(owner.read(), "SET_NOT_FOUND");
            owner.send("SET_DELETE", "{\"id\":9223372036854775807}"); code(owner.read(), "SET_NOT_FOUND");
            for (String invalid : List.of("null", "{}", "{\"id\":0}", "{\"id\":-1}",
                    "{\"id\":1.5}", "{\"id\":\"1\"}", "{\"id\":true}", "{\"id\":1,\"extra\":0}")) {
                owner.send("SET_DELETE", invalid); code(owner.read(), "BAD_REQUEST");
            }
            owner.send("SET_UPDATE", "{\"set\":null}"); code(owner.read(), "BAD_REQUEST");
            owner.send("SET_UPDATE", payload(edited(0, "No", null))); code(owner.read(), "BAD_REQUEST");
            owner.send("SET_DELETE", "{\"id\":" + id + "}"); ok(owner.read());
            assertEquals(1, f.count("question_sets")); assertEquals(3, f.count("questions"));
            owner.send("SET_LIST", "null"); assertTrue(owner.read().getAsJsonArray("data").isEmpty());
            owner.send("SET_GET", "{\"id\":" + id + "}"); code(owner.read(), "SET_NOT_FOUND");
            owner.send("SET_UPDATE", payload(edited(id, "No", null))); code(owner.read(), "SET_NOT_FOUND");
            owner.send("SET_DELETE", "{\"id\":" + id + "}"); code(owner.read(), "SET_NOT_FOUND");
            owner.send("PING", "null"); assertEquals("PONG", owner.read().get("type").getAsString());
        }
    }

    @Test void rejectsInvalidUpdatesAndKeepsOriginalRowsAndImages() throws Exception {
        try (Fixture f = new Fixture(images); Peer peer = new Peer(f.server.port())) {
            peer.login(f.username); String image = png(); long id = f.create(image);
            var invalid = edited(id, " ", null);
            peer.send("SET_UPDATE", payload(invalid)); code(peer.read(), "INVALID_SET");
            peer.send("SET_UPDATE", payload(edited(id, "No", "AQID"))); code(peer.read(), "INVALID_SET");
            var questions = new java.util.ArrayList<>(draft(null).questions());
            questions.set(1, new QuestionDraft(QuestionType.MULTI, "Sai", List.of("A", "B", "C", "D"),
                    List.of(0, 0), List.of(), 20, null));
            peer.send("SET_UPDATE", payload(new QuestionSet(id, "No", "", "", null, questions)));
            var error = peer.read(); code(error, "INVALID_SET");
            assertTrue(error.getAsJsonObject("error").get("message").getAsString().startsWith("Câu 2:"));
            assertEquals(1, f.count("question_sets"));
            assertEquals("Kiến thức Việt Nam", f.service.get(id).name());
            assertEquals(image, f.service.get(id).questions().get(0).imageBase64());
            try (var files = Files.list(images)) { assertEquals(1, files.count()); }
        }
    }

    @Test void blocksPendingAndRunningMatchesAndPreservesCompletedHistoryAcrossEditAndDelete() throws Exception {
        try (Fixture f = new Fixture(images); Peer peer = new Peer(f.server.port())) {
            peer.login(f.username); String image = png(); long id = f.create(image);
            var history = f.history(id, "pending");
            for (String status : List.of("pending", "in_progress")) {
                f.db.transaction(c -> { execute(c, "UPDATE matches SET status=? WHERE match_id=?", status, history.matchId()); return null; });
                peer.send("SET_UPDATE", payload(edited(id, "No", null))); code(peer.read(), "SET_IN_USE");
                peer.send("SET_DELETE", "{\"id\":" + id + "}"); code(peer.read(), "SET_IN_USE");
            }
            assertEquals(1, f.count("question_sets"));
            f.db.transaction(c -> { execute(c, "UPDATE matches SET status='completed',ended_at=CURRENT_TIMESTAMP WHERE match_id=?", history.matchId()); return null; });
            peer.send("SET_UPDATE", payload(shortSet(id))); ok(peer.read());
            long archive = f.db.transaction(c -> scalar(c, "SELECT set_id FROM rounds WHERE round_id=?", history.roundId()));
            assertNotEquals(id, archive);
            peer.send("SET_GET", "{\"id\":" + archive + "}"); code(peer.read(), "SET_NOT_FOUND");
            f.db.transaction(c -> {
                try (var s = c.prepareStatement("SELECT s.title,q.prompt,q.image_ref,o.option_text,mp.final_score "
                        + "FROM rounds r JOIN question_sets s ON s.set_id=r.set_id JOIN round_questions rq ON rq.round_id=r.round_id "
                        + "JOIN questions q ON q.question_id=rq.question_id JOIN submission_options so ON so.question_id=q.question_id "
                        + "JOIN question_options o ON o.option_id=so.option_id JOIN match_players mp ON mp.match_id=r.match_id WHERE r.round_id=?")) {
                    s.setLong(1, history.roundId()); try (var r = s.executeQuery()) {
                        assertTrue(r.next()); assertEquals("Kiến thức Việt Nam", r.getString(1));
                        assertEquals("1 + 1?", r.getString(2));
                        assertEquals(image, new QuestionImageReader(images).read(r.getString(3)));
                        assertEquals("2", r.getString(4)); assertEquals(777, r.getInt(5));
                    }
                }
                return null;
            });
            peer.send("SET_DELETE", "{\"id\":" + id + "}"); ok(peer.read());
            assertEquals(archive, f.db.transaction(c -> scalar(c, "SELECT set_id FROM rounds WHERE round_id=?", history.roundId())).longValue());
            assertEquals(1L, f.db.transaction(c -> scalar(c, "SELECT COUNT(*) FROM submission_options WHERE submission_id=?", history.submissionId())).longValue());
        }
    }

    @Test void publishesChangedWaitingRoomAndClearsReadyAndDeletedSelection() throws Exception {
        try (Fixture f = new Fixture(images); Peer peer = new Peer(f.server.port())) {
            peer.login(f.username); long id = f.create(null); long room = f.waitingRoom(id);
            peer.send("SET_UPDATE", payload(shortSet(id))); ok(peer.read());
            var state = peer.event("ROOM_STATE").getAsJsonObject("data");
            assertEquals("Bộ một câu", state.getAsJsonObject("set").get("name").getAsString());
            assertEquals(1, state.get("numQuestions").getAsInt());
            assertFalse(state.getAsJsonArray("members").get(0).getAsJsonObject().get("ready").getAsBoolean());
            f.db.transaction(c -> { execute(c, "UPDATE room_members SET ready=TRUE WHERE room_id=?", room); return null; });
            peer.send("SET_DELETE", "{\"id\":" + id + "}"); ok(peer.read());
            state = peer.event("ROOM_STATE").getAsJsonObject("data");
            assertTrue(!state.has("set") || state.get("set").isJsonNull());
            assertEquals(0, state.get("numQuestions").getAsInt());
            assertTrue(!state.has("timeOverrideSeconds") || state.get("timeOverrideSeconds").isJsonNull());
            assertFalse(state.getAsJsonArray("members").get(0).getAsJsonObject().get("ready").getAsBoolean());
        }
    }

    @Test void sqlFailuresRollBackVersionsHistoryRoomsAndNewImageFiles() throws Exception {
        try (Fixture f = new Fixture(images); Peer peer = new Peer(f.server.port())) {
            peer.login(f.username); String image = png(); long id = f.create(image);
            var history = f.history(id, "completed");
            String trigger = "fail_mutate_" + UUID.randomUUID().toString().replace("-", "");
            f.db.transaction(c -> { execute(c, "CREATE TRIGGER " + trigger + " BEFORE INSERT ON accepted_answers FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='rollback test'"); return null; });
            try {
                peer.send("SET_UPDATE", payload(edited(id, "No", image))); code(peer.read(), "INTERNAL");
                assertEquals(1, f.count("question_sets"));
                assertEquals(id, f.db.transaction(c -> scalar(c, "SELECT set_id FROM rounds WHERE round_id=?", history.roundId())).longValue());
                assertEquals("Kiến thức Việt Nam", f.service.get(id).name());
                assertEquals(image, f.service.get(id).questions().get(0).imageBase64());
                try (var files = Files.list(images)) { assertEquals(1, files.count()); }
            } finally { f.db.transaction(c -> { execute(c, "DROP TRIGGER " + trigger); return null; }); }
            f.db.transaction(c -> { execute(c, "CREATE TRIGGER " + trigger + " BEFORE UPDATE ON question_sets FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='rollback test'"); return null; });
            try {
                peer.send("SET_DELETE", "{\"id\":" + id + "}"); code(peer.read(), "INTERNAL");
                assertEquals("Kiến thức Việt Nam", f.service.get(id).name());
                assertEquals(id, f.db.transaction(c -> scalar(c, "SELECT set_id FROM rooms WHERE room_id=?", history.roomId())).longValue());
                assertEquals(1L, f.db.transaction(c -> scalar(c, "SELECT ready FROM room_members WHERE room_id=? AND user_id=?", history.roomId(), f.ownerId)).longValue());
            } finally { f.db.transaction(c -> { execute(c, "DROP TRIGGER " + trigger); return null; }); }
            peer.send("SET_UPDATE", payload(shortSet(id))); ok(peer.read());
        }
    }

    @Test void concurrentUpdatesKeepBothOldVersionsAndOnePublicSet() throws Exception {
        try (Fixture f = new Fixture(images)) {
            long id = f.create(null);
            var pool = Executors.newFixedThreadPool(2);
            try {
                var first = pool.submit(() -> f.service.update(f.ownerId, f.username, edited(id, "Phiên bản A", null)));
                var second = pool.submit(() -> f.service.update(f.ownerId, f.username, edited(id, "Phiên bản B", null)));
                first.get(30, TimeUnit.SECONDS); second.get(30, TimeUnit.SECONDS);
            } finally { pool.shutdownNow(); }
            assertEquals(3, f.count("question_sets"));
            assertEquals(9, f.count("questions"));
            assertEquals(1, f.service.list().stream().filter(s -> s.owner().equals(f.username)).count());
            assertTrue(List.of("Phiên bản A", "Phiên bản B").contains(f.service.get(id).name()));
        }
    }

    @Test void startsWithLatestQuestionVersionEvenWhenTransactionAlreadyHasAnOlderSnapshot() throws Exception {
        try (Fixture f = new Fixture(images)) {
            long id = f.create(null); long room = f.waitingRoom(id);
            try (var c = f.db.openConnection()) {
                c.setAutoCommit(false);
                scalar(c, "SELECT COUNT(*) FROM questions WHERE set_id=?", id);
                f.service.update(f.ownerId, f.username, shortSet(id));
                var matches = new quizgame.server.dao.MatchDao();
                var questions = matches.load(c, id, 1);
                assertEquals("Nội dung mới", questions.get(0).draft().content());
                var started = matches.start(c, room, id, questions,
                        List.of(new quizgame.server.dao.MatchDao.Participant(f.ownerId, f.username)));
                c.commit();
                assertEquals(id, f.db.transaction(read -> scalar(read,
                        "SELECT q.set_id FROM round_questions rq JOIN questions q ON q.question_id=rq.question_id WHERE rq.round_id=?", started.roundId())).longValue());
                assertEquals("SET_IN_USE", assertThrows(ServiceException.class,
                        () -> f.service.delete(f.ownerId, id)).code());
            }
        }
    }

    private record History(long roomId, long matchId, long roundId, long submissionId) { }

    private static final class Fixture extends QuestionSetCreateIntegrationTest.Fixture {
        final QuestionSetService service;
        Fixture(Path images) throws Exception {
            super(images, true);
            service = new QuestionSetService(db, new QuestionSetDao(), new QuestionImageReader(images));
        }
        long create(String image) { return service.create(ownerId, username, draft(image)); }
        long waitingRoom(long setId) throws Exception {
            return db.transaction(c -> {
                String code = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase()
                        .replace('0', 'G').replace('1', 'H');
                long room = insert(c, "INSERT INTO rooms(room_code,name,host_id,set_id,num_questions,time_override_seconds) VALUES(?,?,?,?,3,30)", code, "Phòng thử", ownerId, setId);
                execute(c, "INSERT INTO room_members(room_id,user_id,ready) VALUES(?,?,TRUE)", room, ownerId);
                return room;
            });
        }
        History history(long setId, String status) throws Exception {
            long room = waitingRoom(setId);
            return db.transaction(c -> {
                long match = insert(c, "INSERT INTO matches(room_id,status) VALUES(?,?)", room, status);
                execute(c, "INSERT INTO match_players(match_id,user_id,final_score) VALUES(?,?,777)", match, ownerId);
                long round = insert(c, "INSERT INTO rounds(match_id,set_id,round_number,status) VALUES(?,?,1,'completed')", match, setId);
                long question = scalar(c, "SELECT question_id FROM questions WHERE set_id=? ORDER BY position LIMIT 1", setId);
                long rq = insert(c, "INSERT INTO round_questions(round_id,question_id,position) VALUES(?,?,1)", round, question);
                long submission = insert(c, "INSERT INTO submissions(round_question_id,user_id,question_id,match_id,round_id) VALUES(?,?,?,?,?)", rq, ownerId, question, match, round);
                long option = scalar(c, "SELECT option_id FROM question_options WHERE question_id=? AND option_index=1", question);
                execute(c, "INSERT INTO submission_options(submission_id,question_id,option_id) VALUES(?,?,?)", submission, question, option);
                return new History(room, match, round, submission);
            });
        }
        @Override public void close() throws Exception {
            server.close();
            db.transaction(c -> {
                for (String sql : List.of(
                        "DELETE s FROM submissions s JOIN matches m ON m.match_id=s.match_id JOIN rooms r ON r.room_id=m.room_id WHERE r.host_id=?",
                        "DELETE rq FROM round_questions rq JOIN rounds ro ON ro.round_id=rq.round_id JOIN matches m ON m.match_id=ro.match_id JOIN rooms r ON r.room_id=m.room_id WHERE r.host_id=?",
                        "DELETE ro FROM rounds ro JOIN matches m ON m.match_id=ro.match_id JOIN rooms r ON r.room_id=m.room_id WHERE r.host_id=?",
                        "DELETE m FROM matches m JOIN rooms r ON r.room_id=m.room_id WHERE r.host_id=?",
                        "DELETE i FROM invitations i JOIN rooms r ON r.room_id=i.room_id WHERE r.host_id=?",
                        "DELETE rm FROM room_members rm JOIN rooms r ON r.room_id=rm.room_id WHERE r.host_id=?",
                        "DELETE FROM rooms WHERE host_id=?")) execute(c, sql, ownerId);
                return null;
            });
            super.close();
        }
    }

    private static void execute(Connection c, String sql, Object... values) throws Exception {
        try (var s = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
            s.executeUpdate();
        }
    }
    private static long scalar(Connection c, String sql, Object... values) throws Exception {
        try (var s = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
            try (var r = s.executeQuery()) { assertTrue(r.next()); return r.getLong(1); }
        }
    }
    private static long insert(Connection c, String sql, Object... values) throws Exception {
        try (var s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
            s.executeUpdate();
            try (var r = s.getGeneratedKeys()) { assertTrue(r.next()); return r.getLong(1); }
        }
    }
}
