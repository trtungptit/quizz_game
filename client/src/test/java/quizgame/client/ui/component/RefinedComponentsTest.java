package quizgame.client.ui.component;

import org.junit.jupiter.api.Test;
import quizgame.client.ui.AppTheme;
import quizgame.common.model.QuestionSetSummary;
import quizgame.common.model.QuestionType;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra kết quả vẽ thật thay vì chỉ kiểm tra token màu. */
class RefinedComponentsTest {
    @Test
    void secondaryButtonsUseOriginalSoftPurpleAndKeepHoverFeedback() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            AppButton button = AppButton.soft("Sửa");
            button.setSize(160, 44);
            int normal = render(button).getRGB(40, 10);
            assertEquals(AppTheme.SOFT.getRGB(), normal);
            assertEquals(AppTheme.SOFT_TEXT, button.getForeground());
            button.getModel().setRollover(true);
            assertNotEquals(normal, render(button).getRGB(40, 10));
        });
    }

    @Test
    void questionSetCardsRestoreStripeColorWithoutChangingInsetsOrTitleCase() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            QuestionSetCard card = new QuestionSetCard(new QuestionSetSummary(2, "Sinh học căn bản", "Sinh học",
                    "tester", 10, List.of(QuestionType.SINGLE), false), true, () -> {}, () -> {}, () -> {});
            card.setSize(340, 260);
            BufferedImage image = render(card);
            assertEquals(new Color(0xF59E0B).getRGB(), image.getRGB(170, 3));
            assertEquals(12, card.getInsets().top, "Không thêm lại khoảng đệm 7 px của thẻ cũ");
            assertTrue(findText(card, "Sinh học căn bản"), "Không viết hoa toàn bộ tên bộ");
        });
    }

    @Test
    void tableHeadersRestoreGradientWithoutBreakingSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            StyledTable table = new StyledTable("Tên", "Điểm");
            Component header = table.getTableHeader().getDefaultRenderer()
                    .getTableCellRendererComponent(table, "Tên", false, false, -1, 0);
            header.setSize(200, 34);
            assertEquals(Color.WHITE, header.getForeground());
            BufferedImage image = render((JComponent) header);
            assertNotEquals(image.getRGB(20, 8), image.getRGB(180, 8));
        });
    }

    @Test
    void sectionUnderlineRestoresGoldOrangeWithoutChangingThickness() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            JComponent underline = (JComponent) new SectionTitle("Bộ câu hỏi").getComponent(1);
            underline.setSize(100, 5);
            BufferedImage image = render(underline);
            assertNotEquals(image.getRGB(10, 2), image.getRGB(50, 2));
        });
    }

    @Test
    void resultBannerRestoresGoldOrangeGradient() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            JPanel banner = GradientPanel.accent(new BorderLayout(), AppTheme.ARC_CARD * 2);
            banner.setSize(300, 90);
            BufferedImage image = render(banner);
            assertNotEquals(image.getRGB(20, 15), image.getRGB(250, 15));
        });
    }

    static BufferedImage render(JComponent component) {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        component.paint(g);
        g.dispose();
        return image;
    }

    private static boolean findText(Component component, String text) {
        if (component instanceof JTextArea area && text.equals(area.getText())) return true;
        if (component instanceof Container parent) {
            for (Component child : parent.getComponents()) if (findText(child, text)) return true;
        }
        return false;
    }
}
