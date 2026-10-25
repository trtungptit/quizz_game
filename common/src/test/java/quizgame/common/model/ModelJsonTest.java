package quizgame.common.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Kiểm tra Gson chuyển qua lại các record và đọc được ví dụ trong PROTOCOL.md. */
class ModelJsonTest {
    private final Gson gson = new Gson();

    /** Chuyển sang JSON rồi ngược lại, kết quả phải bằng bản gốc. */
    private <T> void roundTrip(T value) {
        String json = gson.toJson(value);
        @SuppressWarnings("unchecked")
        T back = (T) gson.fromJson(json, value.getClass());
        assertEquals(value, back, json);
    }

    private static QuestionDraft single() {
        return new QuestionDraft(QuestionType.SINGLE, "1+1=?", List.of("1", "2", "3", "4"), List.of(1),
                List.of(), 20, null);
    }

    private static QuestionSetSummary summary() {
        return new QuestionSetSummary(7, "Toán", "Số học", "an", 3,
                List.of(QuestionType.SINGLE, QuestionType.TYPE), true);
    }

    @Test
    void userInfo() {
        roundTrip(new UserInfo("binh", 9, UserStatus.IDLE));
    }

    @Test
    void roomMember() {
        roundTrip(new RoomMember("an", true));
    }

    @Test
    void questionDraft() {
        roundTrip(single());
        roundTrip(new QuestionDraft(QuestionType.MULTI, "Số chẵn?", List.of("1", "2", "3", "4"), List.of(1, 3),
                List.of(), 30, "QUJD"));
        roundTrip(new QuestionDraft(QuestionType.TYPE, "Thủ đô VN?", List.of(), List.of(),
                List.of("Hà Nội", "ha noi"), 15, "QUJD"));
    }

    @Test
    void questionSetSummary() {
        roundTrip(summary());
    }

    @Test
    void questionSet() {
        roundTrip(new QuestionSet(0, "Toán", "Số học", "Mô tả", "an", List.of(single())));
    }

    @Test
    void roomState() {
        roundTrip(new RoomState("ABC234", "Phòng của an", "an",
                List.of(new RoomMember("an", true), new RoomMember("binh", false)), List.of("chi"),
                summary(), 3, 20, RoomPhase.WAITING));
        // set và timeOverrideSeconds là null
        roundTrip(new RoomState("ABC234", "P", "an", List.of(new RoomMember("an", false)), List.of(),
                null, 0, null, RoomPhase.PLAYING));
    }

    @Test
    void invite() {
        roundTrip(new Invite(1, "an", "ABC234", "Phòng", 5000));
    }

    @Test
    void gameStart() {
        roundTrip(new GameStart(3, Map.of("img1", "QUJD")));
    }

    @Test
    void question() {
        roundTrip(new Question(0, 3, QuestionType.SINGLE, "1+1=?", List.of("1", "2", "3", "4"), "img1", 20000));
        roundTrip(new Question(1, 3, QuestionType.TYPE, "Thủ đô?", List.of(), null, 15000));
    }

    @Test
    void answer() {
        roundTrip(new Answer(List.of(0, 2), null));
        roundTrip(new Answer(List.of(), "hà nội"));
    }

    @Test
    void answerResult() {
        roundTrip(new AnswerResult(0, true, List.of(1), List.of(), 850, 850));
    }

    @Test
    void scoreRow() {
        roundTrip(new ScoreRow(1, "an", 850, 2, false));
    }

    @Test
    void matchRowAndResult() {
        MatchRow row = new MatchRow(1, "an", 1700, 2, 35000, PlayerStatus.FINISHED);
        roundTrip(row);
        roundTrip(new MatchResult(5, "an", false, List.of(row,
                new MatchRow(2, "binh", 900, 1, 40000, PlayerStatus.QUIT))));
        roundTrip(new MatchResult(6, null, true, List.of(row)));
    }

    @Test
    void leaderboardRowAndSummary() {
        roundTrip(new LeaderboardRow(1, "an", 12, 20000));
        roundTrip(new MatchSummary(5, "2026-10-25T09:41:00", "Phòng", "Toán", 1, 1700));
    }

    @Test
    void labels() {
        assertEquals("Đang rảnh", UserStatus.IDLE.label());
        assertEquals("Trong phòng", UserStatus.IN_ROOM.label());
        assertEquals("Đang chơi", UserStatus.PLAYING.label());
        assertEquals("Buttons", QuestionType.SINGLE.label());
        assertEquals("Checkboxes", QuestionType.MULTI.label());
        assertEquals("Type answer", QuestionType.TYPE.label());
        for (InviteResult r : InviteResult.values()) {
            assertEquals(false, r.label().isBlank());
        }
    }

    // ---- Ví dụ JSON trong PROTOCOL.md ----

    @Test
    void readOnlineListExample() {
        String json = "[ { \"username\": \"binh\", \"totalWins\": 9, \"status\": \"IDLE\" } ]";
        List<UserInfo> list = gson.fromJson(json, new TypeToken<List<UserInfo>>() { }.getType());
        assertEquals(List.of(new UserInfo("binh", 9, UserStatus.IDLE)), list);
    }

    @Test
    void readRoomStateWithNulls() {
        String json = "{ \"code\": \"ABC234\", \"name\": \"P\", \"host\": \"an\","
                + " \"members\": [ { \"username\": \"an\", \"ready\": false } ], \"pendingInvitees\": [],"
                + " \"set\": null, \"numQuestions\": 0, \"timeOverrideSeconds\": null, \"phase\": \"WAITING\" }";
        RoomState room = gson.fromJson(json, RoomState.class);
        assertNull(room.set());
        assertNull(room.timeOverrideSeconds());
        assertEquals(RoomPhase.WAITING, room.phase());
        assertEquals(new RoomMember("an", false), room.members().get(0));
    }

    @Test
    void readMatchResultDraw() {
        String json = "{ \"matchId\": 5, \"winner\": null, \"draw\": true, \"rows\": ["
                + "{ \"rank\": 1, \"username\": \"an\", \"score\": 900, \"correctCount\": 2,"
                + " \"finishTimeMs\": 30000, \"status\": \"DISCONNECTED\" } ] }";
        MatchResult result = gson.fromJson(json, MatchResult.class);
        assertNull(result.winner());
        assertEquals(PlayerStatus.DISCONNECTED, result.rows().get(0).status());
    }

    @Test
    void readQuestionWithNullImage() {
        String json = "{ \"qIndex\": 0, \"total\": 5, \"type\": \"TYPE\", \"content\": \"?\","
                + " \"options\": [], \"imageId\": null, \"timeLimitMs\": 20000 }";
        Question q = gson.fromJson(json, Question.class);
        assertEquals(QuestionType.TYPE, q.type());
        assertNull(q.imageId());
    }

    @Test
    void readGameStartMap() {
        GameStart start = gson.fromJson("{ \"numQuestions\": 2, \"imagesBase64\": { \"a\": \"QUJD\" } }",
                GameStart.class);
        assertEquals("QUJD", start.imagesBase64().get("a"));
    }
}
