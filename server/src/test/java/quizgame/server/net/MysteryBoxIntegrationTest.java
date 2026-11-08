package quizgame.server.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static quizgame.server.net.GameplayIntegrationTest.answer;
import static quizgame.server.net.RoomInviteWorkflowIntegrationTest.*;

/** Hộp quà qua TCP thật: mời chọn sau câu mốc, giấu vị trí hộp, tự chọn khi hết giờ, áp hiệu ứng và lưu điểm. */
@EnabledIfEnvironmentVariable(named = "QUIZ_TEST_DB_URL", matches = ".+")
class MysteryBoxIntegrationTest {
    static String pick(int round, int box) {
        return "{\"round\":" + round + ",\"box\":" + box + "}";
    }

    /** Dòng BOX_STATUS của username ở lượt round, hoặc null. */
    static JsonObject row(JsonObject statusEvent, String username, int round) {
        for (JsonElement e : statusEvent.getAsJsonArray("data")) {
            JsonObject r = e.getAsJsonObject();
            if (r.get("username").getAsString().equals(username) && r.get("round").getAsInt() == round) return r;
        }
        return null;
    }

    @Test
    void hopQuaXuatHienSauCauMocVaDiemDuocTinhTaiServer() throws Exception {
        try (GameplaySupport g = new GameplaySupport(2)) {
            g.joinAll();
            long set = g.seed(4); // 4 câu -> hộp sau câu 1 và câu 3; đáp án đúng là chỉ số 1
            g.configure(set, 4);
            g.readyAll();
            Peer a = g.f.peers.get(0), b = g.f.peers.get(1);
            a.ok("START_GAME", "null");
            for (Peer p : g.f.peers) {
                p.event("GAME_START");
                assertEquals(0, p.event("QUESTION").getAsJsonObject("data").get("qIndex").getAsInt());
            }
            a.ok("ANSWER", answer(0, 1));
            int firstPoints = a.event("ANSWER_RESULT").getAsJsonObject("data").get("points").getAsInt();
            b.ok("ANSWER", answer(0, 0));
            b.event("ANSWER_RESULT");

            // Sau câu mốc là BOX_OFFER chứ không phải câu kế tiếp
            JsonObject offer = a.event("BOX_OFFER").getAsJsonObject("data");
            assertEquals(1, offer.get("round").getAsInt());
            assertEquals(0, offer.get("afterQIndex").getAsInt());
            assertEquals(6000, offer.get("timeoutMs").getAsLong());
            assertCode(a.request("BOX_PICK", pick(2, 0)), "NO_BOX");
            assertCode(a.request("BOX_PICK", pick(1, 4)), "BAD_REQUEST");
            a.ok("BOX_PICK", pick(1, 0));
            JsonObject result = a.event("BOX_RESULT").getAsJsonObject("data");
            assertEquals(0, result.get("chosen").getAsInt());
            assertFalse(result.get("auto").getAsBoolean());
            JsonArray boxes = result.getAsJsonArray("boxes");
            Set<String> distinct = new HashSet<>();
            boxes.forEach(x -> distinct.add(x.getAsString()));
            assertEquals(4, distinct.size(), "4 hộp phải chứa 4 hiệu ứng khác nhau");
            String effect = result.get("effect").getAsString();
            assertEquals(boxes.get(0).getAsString(), effect);
            int delta = result.get("delta").getAsInt();
            switch (effect) {
                case "STEAL" -> {
                    assertEquals(b.username, result.get("target").getAsString());
                    assertEquals(-delta, result.get("targetDelta").getAsInt());
                }
                case "LOSE" -> assertEquals(-Math.round(firstPoints * 0.2), delta);
                default -> assertEquals(1, result.get("applyQIndex").getAsInt());
            }
            assertEquals(firstPoints + delta, result.get("totalScore").getAsInt());
            assertCode(a.request("BOX_PICK", pick(1, 1)), "NO_BOX");

            // b chưa chọn: thấy a đã chọn nhưng KHÔNG thấy vị trí hộp của a
            b.event("BOX_OFFER");
            JsonObject hidden = b.eventWhere("BOX_STATUS", x -> {
                JsonObject r = row(x, a.username, 1);
                return r != null && "CHOSEN".equals(r.get("state").getAsString());
            });
            JsonObject aSeenByB = row(hidden, a.username, 1);
            assertTrue(aSeenByB.get("box").isJsonNull(), "Lộ vị trí hộp cho người chưa chọn");
            assertEquals(effect, aSeenByB.get("effect").getAsString());

            // a sang câu 2 sau khi mở hộp; hiệu ứng câu sau được áp vào điểm
            assertEquals(1, a.event("QUESTION").getAsJsonObject("data").get("qIndex").getAsInt());
            a.ok("ANSWER", answer(1, 1));
            JsonObject second = a.event("ANSWER_RESULT").getAsJsonObject("data");
            int points = second.get("points").getAsInt();
            switch (effect) {
                case "NO_LATE" -> assertEquals(1000, points);
                case "DOUBLE" -> assertTrue(points >= 1000 && points <= 2000 && points % 2 == 0, "x2: " + points);
                default -> assertTrue(points >= 500 && points <= 1000, "không hiệu ứng: " + points);
            }
            int aTotal = second.get("totalScore").getAsInt();

            // b không chọn: hết 6 giây Server tự chọn; sau đó b thấy được vị trí hộp của a
            JsonObject auto = b.event("BOX_RESULT").getAsJsonObject("data");
            assertTrue(auto.get("auto").getAsBoolean());
            assertEquals(boxes, auto.getAsJsonArray("boxes"), "Cả trận dùng chung cách xếp hộp của lượt");
            JsonObject revealed = b.eventWhere("BOX_STATUS", x -> {
                JsonObject r = row(x, b.username, 1);
                return r != null && "CHOSEN".equals(r.get("state").getAsString());
            });
            assertEquals(0, row(revealed, a.username, 1).get("box").getAsInt());

            // b rời trận -> a thắng; điểm cuối (gồm điểm từ hộp) được lưu vào CSDL
            b.ok("QUIT_GAME", "null");
            JsonObject end = a.event("GAME_END").getAsJsonObject("data");
            long match = end.get("matchId").getAsLong();
            if ("STEAL".equals(effect)) {
                // b bị cướp 0 điểm vì b trả lời sai câu 1; tổng a không đổi
                assertEquals(0, delta);
            }
            Object saved = g.f.value("SELECT mp.final_score FROM match_players mp JOIN users u ON u.user_id=mp.user_id "
                    + "WHERE mp.match_id=? AND u.username=?", match, a.username);
            assertEquals((long) aTotal, ((Number) saved).longValue());
        }
    }
}
