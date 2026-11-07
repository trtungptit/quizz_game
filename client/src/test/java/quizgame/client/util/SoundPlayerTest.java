package quizgame.client.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundPlayerTest {
    private static final Map<String, short[]> TONES = Map.of(
            "click", SoundPlayer.clickTone(),
            "correct", SoundPlayer.correctTone(),
            "wrong", SoundPlayer.wrongTone(),
            "music", SoundPlayer.musicLoop());

    @Test
    void moiTiengDeuCoAmThanhVaKhongBiVoTieng() {
        TONES.forEach((name, s) -> {
            int peak = 0;
            for (short v : s) {
                peak = Math.max(peak, Math.abs(v));
            }
            assertTrue(peak > 1000, name + " quá nhỏ hoặc im lặng");
            assertTrue(peak < Short.MAX_VALUE, name + " bị vỡ tiếng (chạm trần)");
        });
    }

    @Test
    void dauVaCuoiVeGanKhongDeKhongNgheTiengLupBup() {
        TONES.forEach((name, s) -> {
            assertTrue(Math.abs(s[0]) < 200, name + " bắt đầu đột ngột");
            assertTrue(Math.abs(s[s.length - 1]) < 200, name + " kết thúc đột ngột");
        });
    }

    @Test
    void doDaiHopLy() {
        assertTrue(SoundPlayer.clickTone().length < SoundPlayer.RATE / 10, "click phải ngắn");
        int music = SoundPlayer.musicLoop().length;
        assertTrue(music > SoundPlayer.RATE * 5 && music < SoundPlayer.RATE * 15, "nhạc nền nên dài 5-15 giây");
    }
}
