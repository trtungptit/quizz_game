package quizgame.client.ui.component;

import org.junit.jupiter.api.Test;
import quizgame.common.model.ScoreRow;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreWindowTest {
    private static List<ScoreRow> players(int n) {
        List<ScoreRow> rows = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            rows.add(new ScoreRow(i, "p" + i, 1000 - i, 1, false));
        }
        return rows;
    }

    private static List<String> names(List<ScoreRow> rows) {
        return rows.stream().map(ScoreRow::username).toList();
    }

    @Test
    void fewerThanFiveShowsAll() {
        assertEquals(List.of("p1", "p2", "p3"), names(ScoreWindow.around(players(3), "p2")));
        assertEquals(List.of(), names(ScoreWindow.around(List.of(), "p1")));
    }

    @Test
    void middleHasTwoAboveAndTwoBelow() {
        assertEquals(List.of("p3", "p4", "p5", "p6", "p7"), names(ScoreWindow.around(players(10), "p5")));
    }

    @Test
    void topShiftsWindowDown() {
        assertEquals(List.of("p1", "p2", "p3", "p4", "p5"), names(ScoreWindow.around(players(10), "p1")));
        assertEquals(List.of("p1", "p2", "p3", "p4", "p5"), names(ScoreWindow.around(players(10), "p2")));
    }

    @Test
    void bottomShiftsWindowUp() {
        assertEquals(List.of("p6", "p7", "p8", "p9", "p10"), names(ScoreWindow.around(players(10), "p10")));
        assertEquals(List.of("p6", "p7", "p8", "p9", "p10"), names(ScoreWindow.around(players(10), "p9")));
    }

    @Test
    void exactlyFiveAndUnknownPlayer() {
        assertEquals(5, ScoreWindow.around(players(5), "p3").size());
        assertEquals(List.of("p1", "p2", "p3", "p4", "p5"), names(ScoreWindow.around(players(8), "ai-do")));
    }
}
