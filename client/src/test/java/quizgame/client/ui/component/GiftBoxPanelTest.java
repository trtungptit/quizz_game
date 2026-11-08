package quizgame.client.ui.component;

import org.junit.jupiter.api.Test;
import quizgame.client.ui.AppTheme;
import quizgame.common.model.BoxEffect;
import quizgame.common.model.BoxOffer;
import quizgame.common.model.BoxResult;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GiftBoxPanelTest {
    @Test
    void chiGuiMotLanChonHopVaMoKhoaKhiGuiLoi() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            List<int[]> picks = new ArrayList<>();
            GiftBoxPanel panel = new GiftBoxPanel((round, box) -> picks.add(new int[]{round, box}));
            try {
                panel.offer(new BoxOffer(2, 6, 6000));
                List<JButton> cards = cards(panel);
                assertEquals(4, cards.size());
                cards.get(1).doClick(0);
                cards.get(3).doClick(0); // bấm tiếp hộp khác không gửi thêm
                assertEquals(1, picks.size());
                assertArrayEquals(new int[]{2, 1}, picks.get(0));
                for (JButton c : cards) assertFalse(c.isEnabled(), "Đã chọn thì khoá cả 4 hộp");

                panel.pickFailed();
                for (JButton c : cards) assertTrue(c.isEnabled(), "Gửi lỗi thì cho chọn lại");

                // Kết quả của lượt khác bị bỏ qua, đúng lượt thì lật hết
                panel.reveal(new BoxResult(1, 0, false, List.of(BoxEffect.values()), BoxEffect.STEAL, 0, null, 0,
                        null, 0, 3000));
                assertTrue(cards.get(0).isEnabled());
                panel.reveal(new BoxResult(2, 1, true, List.of(BoxEffect.values()), BoxEffect.LOSE, -10, null, 0,
                        null, 40, 3000));
                for (JButton c : cards) assertFalse(c.isEnabled());
            } finally {
                panel.stop();
            }
        });
    }

    private static List<JButton> cards(Container root) {
        List<JButton> out = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && "Hộp".equals(b.getAccessibleContext().getAccessibleName().split(" ")[0])) out.add(b);
            else if (c instanceof Container k) out.addAll(cards(k));
        }
        return out;
    }
}
