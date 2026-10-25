package quizgame.client.ui.screen;

import org.junit.jupiter.api.Test;
import quizgame.common.model.LeaderboardRow;
import quizgame.common.model.MatchResult;
import quizgame.common.model.QuestionDraft;
import quizgame.common.model.QuestionSetSummary;
import quizgame.common.model.QuestionType;
import quizgame.common.model.RoomMember;
import quizgame.common.model.RoomPhase;
import quizgame.common.model.RoomState;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test các hàm tĩnh chứa luật nghiệp vụ của các màn. */
class ScreenLogicTest {
    private static final QuestionSetSummary SET = new QuestionSetSummary(1, "Toán", "Số", "an", 5,
            List.of(QuestionType.SINGLE), false);

    private static RoomState room(List<RoomMember> members, QuestionSetSummary set, RoomPhase phase,
                                  List<String> pending) {
        return new RoomState("ABC234", "P", "an", members, pending, set, 5, null, phase);
    }

    private static RoomMember m(String name, boolean ready) {
        return new RoomMember(name, ready);
    }

    @Test
    void startRequiresEverythingAtOnce() {
        List<RoomMember> ready = List.of(m("an", true), m("binh", true));
        assertTrue(RoomPanel.canStart(room(ready, SET, RoomPhase.WAITING, List.of())));
        assertFalse(RoomPanel.canStart(room(ready, null, RoomPhase.WAITING, List.of())), "chưa chọn bộ");
        assertFalse(RoomPanel.canStart(room(List.of(m("an", true)), SET, RoomPhase.WAITING, List.of())), "một người");
        assertFalse(RoomPanel.canStart(room(List.of(m("an", true), m("binh", false)), SET, RoomPhase.WAITING,
                List.of())), "có người chưa sẵn sàng");
        assertFalse(RoomPanel.canStart(room(ready, SET, RoomPhase.PLAYING, List.of())), "đang chơi");
    }

    @Test
    void roomLogLinesDescribeChanges() {
        RoomState first = room(List.of(m("an", false)), null, RoomPhase.WAITING, List.of());
        assertEquals(List.of("Bạn đã vào phòng P"), RoomPanel.describeChange(null, first));

        RoomState joined = room(List.of(m("an", false), m("binh", false)), null, RoomPhase.WAITING, List.of("chi"));
        assertEquals(List.of("binh đã vào phòng", "Đã mời: chi • Đang chờ phản hồi..."),
                RoomPanel.describeChange(first, joined));

        RoomState ready = room(List.of(m("an", true), m("binh", false)), SET, RoomPhase.WAITING, List.of());
        assertEquals(List.of("an • Sẵn sàng", "Đã chọn bộ câu hỏi: Toán"), RoomPanel.describeChange(joined, ready));

        RoomState left = room(List.of(m("an", true)), SET, RoomPhase.WAITING, List.of());
        assertEquals(List.of("binh đã rời phòng"), RoomPanel.describeChange(ready, left));
        assertEquals(List.of(), RoomPanel.describeChange(left, left));
    }

    @Test
    void leaderboardGapOnlyWhenRanksAreNotAdjacent() {
        List<LeaderboardRow> adjacent = List.of(new LeaderboardRow(1, "a", 3, 1), new LeaderboardRow(2, "b", 2, 1));
        assertEquals(adjacent, LeaderboardPanel.withGap(adjacent));

        List<LeaderboardRow> gap = new ArrayList<>(List.of(new LeaderboardRow(1, "a", 3, 1),
                new LeaderboardRow(2, "b", 2, 1), new LeaderboardRow(83, "me", 1, 1)));
        List<LeaderboardRow> result = LeaderboardPanel.withGap(gap);
        assertEquals(4, result.size());
        assertNull(result.get(2));
        assertEquals("me", result.get(3).username());
        assertEquals(1, LeaderboardPanel.withGap(List.of(new LeaderboardRow(5, "x", 1, 1))).size());
    }

    @Test
    void resultBanner() {
        assertEquals("BẠN THẮNG!", ResultPanel.bannerFor(new MatchResult(1, "an", false, List.of()), "an"));
        assertEquals("BÌNH THẮNG!", ResultPanel.bannerFor(new MatchResult(1, "bình", false, List.of()), "an"));
        assertEquals("HÒA, KHÔNG AI ĐƯỢC TÍNH THẮNG",
                ResultPanel.bannerFor(new MatchResult(1, null, true, List.of()), "an"));
    }

    @Test
    void saveChecksNameQuestionsAndEachQuestion() {
        QuestionDraft good = new QuestionDraft(QuestionType.SINGLE, "Q", List.of("a", "b", "c", "d"), List.of(0),
                List.of(), 20, null);
        QuestionDraft bad = new QuestionDraft(QuestionType.SINGLE, "Q", List.of("a", "b", "", "d"), List.of(0),
                List.of(), 20, null);
        assertEquals(-1, SetEditorPanel.check(" ", List.of(good)).index());
        assertEquals(-1, SetEditorPanel.check("Bộ", List.of()).index());
        SetEditorPanel.Problem p = SetEditorPanel.check("Bộ", List.of(good, bad));
        assertNotNull(p);
        assertEquals(1, p.index());
        assertTrue(p.message().startsWith("Câu 2: "));
        assertNull(SetEditorPanel.check("Bộ", List.of(good, good)));
    }
}
