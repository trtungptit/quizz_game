package quizgame.server.service;

import org.junit.jupiter.api.Test;
import quizgame.common.model.BoxEffect;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BoxRulesTest {
    @Test
    void viTriHopTheoSoCau() {
        assertEquals(List.of(), BoxRules.positions(1));
        assertEquals(List.of(), BoxRules.positions(3), "Trận dưới 4 câu không có hộp");
        assertEquals(List.of(1), BoxRules.positions(4), "Đúng 4 câu thì hộp ở giữa");
        assertEquals(List.of(3), BoxRules.positions(5));
        assertEquals(List.of(3), BoxRules.positions(8), "Hộp sau câu 8 là câu cuối nên bị bỏ");
        assertEquals(List.of(3, 7), BoxRules.positions(9));
        assertEquals(List.of(3, 7), BoxRules.positions(12));
        assertEquals(List.of(3, 7, 11), BoxRules.positions(13));
    }

    @Test
    void cuBonCauMotHopVaKhongBaoGioSauCauCuoi() {
        for (int n = 5; n <= 60; n++) {
            List<Integer> p = BoxRules.positions(n);
            assertEquals((n - 1) / 4, p.size(), "n=" + n + " sai số hộp");
            for (int i : p) assertTrue(i >= 0 && i < n - 1, "n=" + n + " có hộp sau câu cuối");
        }
    }

    @Test
    void bonHopLuonChuaDuBonHieuUngKhacNhau() {
        for (int seed = 0; seed < 100; seed++) {
            List<BoxEffect> boxes = BoxRules.shuffle(new Random(seed));
            assertEquals(4, boxes.size());
            assertEquals(EnumSet.allOf(BoxEffect.class), EnumSet.copyOf(boxes));
        }
    }

    @Test
    void cuopVaMatDiemLamTronVaKhongAm() {
        assertEquals(468, BoxRules.stealAmount(3120));
        assertEquals(0, BoxRules.stealAmount(0));
        assertEquals(296, BoxRules.loseAmount(1480));
        assertEquals(0, BoxRules.loseAmount(0));
        assertTrue(BoxRules.loseAmount(7) <= 7);
    }

    @Test
    void nhanDoiVaKhongSoMuon() {
        assertEquals(640, BoxRules.points(640, true, false, false));
        assertEquals(1000, BoxRules.points(640, true, true, false));
        assertEquals(1280, BoxRules.points(640, true, false, true));
        assertEquals(2000, BoxRules.points(640, true, true, true));
        assertEquals(0, BoxRules.points(0, false, true, true), "Sai thì hộp không cứu được");
    }
}
