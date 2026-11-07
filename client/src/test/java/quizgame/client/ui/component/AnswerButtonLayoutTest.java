package quizgame.client.ui.component;

import org.junit.jupiter.api.Test;
import quizgame.client.ui.AppTheme;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerButtonLayoutTest {
    @Test
    void longAnswerPaintsOnSeveralLinesInsteadOfBeingCutAtTheRightEdge() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            AnswerButton button = new AnswerButton(0,
                    "Đáp án có nội dung dài để kiểm tra khả năng hiển thị đầy đủ");
            button.setSize(300, 220);
            BufferedImage image = new BufferedImage(300, 220, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            button.paint(graphics);
            graphics.dispose();
            int whitePixelsOutsideMiddleLine = 0;
            for (int y = 15; y < 205; y++) {
                if (y >= 90 && y <= 130) continue;
                for (int x = 64; x < 240; x++) {
                    Color pixel = new Color(image.getRGB(x, y));
                    if (pixel.getRed() > 240 && pixel.getGreen() > 240 && pixel.getBlue() > 240) {
                        whitePixelsOutsideMiddleLine++;
                    }
                }
            }
            assertTrue(whitePixelsOutsideMiddleLine > 30,
                    "Chữ đáp án dài phải được vẽ trên nhiều dòng, không chỉ một dòng giữa nút");
        });
    }
}
