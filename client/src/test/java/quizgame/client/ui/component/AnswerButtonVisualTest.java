package quizgame.client.ui.component;

import org.junit.jupiter.api.Test;
import quizgame.client.ui.AppTheme;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class AnswerButtonVisualTest {
    @Test
    void normalAnswersRestoreFourColorsAndKeepAccessibleName() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            for (int i = 0; i < 4; i++) {
                AnswerButton button = new AnswerButton(i, "Đáp án thử nghiệm");
                button.setSize(360, 84);
                assertEquals(optionColor(i).getRGB(), RefinedComponentsTest.render(button).getRGB(160, 15));
                assertEquals(Color.WHITE, button.getForeground());
                assertEquals(button.answerText(), button.getAccessibleContext().getAccessibleName());
                assertEquals(Color.WHITE.getRGB(), hoverBorder(button));
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
                assertEquals(optionColor(i).getRGB(), correct.getRGB(160, 15));
                assertEquals(Color.WHITE, button.getForeground());
                assertTrue(count(correct, Color.WHITE, 290, 20, 350, 65) > 30, "Phải có dấu tích");
                button.setState(AnswerButton.State.WRONG);
                BufferedImage wrong = RefinedComponentsTest.render(button);
                assertEquals(AppTheme.WRONG.getRGB(), wrong.getRGB(160, 15));
                assertEquals(Color.WHITE, button.getForeground());
                assertTrue(count(wrong, Color.WHITE, 290, 20, 350, 65) > 30, "Phải có dấu chéo");
                button.setState(AnswerButton.State.DIM);
                assertEquals(Color.WHITE, button.getForeground());
                button.setState(AnswerButton.State.NORMAL);
                assertEquals(Color.WHITE, button.getForeground());
            }
        });
    }

    private static Color optionColor(int index) {
        return new Color[]{AppTheme.ANSWER_A, AppTheme.ANSWER_B, AppTheme.ANSWER_C, AppTheme.ANSWER_D}[index];
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
