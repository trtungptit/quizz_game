package quizgame.client.ui.component;

import org.junit.jupiter.api.Test;
import quizgame.client.ui.AppTheme;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class AnswerButtonVisualTest {
    @Test
    void normalAnswersUseQuietSurfaceWithDarkTextAndAccessibleName() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            for (int i = 0; i < 4; i++) {
                AnswerButton button = new AnswerButton(i, "Đáp án thử nghiệm");
                button.setSize(360, 84);
                assertEquals(AppTheme.CARD.getRGB(), RefinedComponentsTest.render(button).getRGB(160, 15));
                assertEquals(AppTheme.TEXT, button.getForeground());
                assertEquals(button.answerText(), button.getAccessibleContext().getAccessibleName());
                assertEquals(AppTheme.PRIMARY.getRGB(), hoverBorder(button));
            }
        });
    }

    @Test
    void resultFeedbackOverridesOptionColorAndStaysVisibleWhenLocked() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            for (int i = 0; i < 4; i++) {
                AnswerButton button = new AnswerButton(i, "Nội dung");
                button.setSize(360, 84);
                button.setEnabled(false);
                button.setState(AnswerButton.State.CORRECT);
                BufferedImage correct = RefinedComponentsTest.render(button);
                assertEquals(AppTheme.CORRECT_SOFT.getRGB(), correct.getRGB(160, 15));
                assertEquals(AppTheme.CORRECT, button.getForeground());
                assertTrue(count(correct, AppTheme.CORRECT, 290, 20, 350, 65) > 30, "Phải có dấu tích");
                button.setState(AnswerButton.State.WRONG);
                BufferedImage wrong = RefinedComponentsTest.render(button);
                assertEquals(AppTheme.WRONG_SOFT.getRGB(), wrong.getRGB(160, 15));
                assertEquals(AppTheme.WRONG, button.getForeground());
                assertTrue(count(wrong, AppTheme.WRONG, 290, 20, 350, 65) > 30, "Phải có dấu chéo");
                button.setState(AnswerButton.State.DIM);
                assertEquals(AppTheme.TEXT_MUTED, button.getForeground());
                button.setState(AnswerButton.State.NORMAL);
                assertEquals(AppTheme.TEXT, button.getForeground());
            }
        });
    }

    private static int hoverBorder(AnswerButton button) {
        button.getModel().setRollover(true);
        return RefinedComponentsTest.render(button).getRGB(160, 2);
    }

    private static int count(BufferedImage image, Color color, int x0, int y0, int x1, int y1) {
        int total = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            if (image.getRGB(x, y) == color.getRGB()) total++;
        }
        return total;
    }
}
