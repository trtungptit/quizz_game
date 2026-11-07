package quizgame.client.ui.component;

import org.junit.jupiter.api.Test;
import quizgame.client.ui.AppTheme;

import javax.swing.JLabel;
import java.awt.FontMetrics;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextLinesTest {
    private final FontMetrics metrics = new JLabel().getFontMetrics(AppTheme.FONT_BASE);

    @Test
    void wrappedWordsFitAndPreserveText() {
        String text = "Câu trả lời tiếng Việt có nhiều từ để kiểm tra xuống dòng";
        List<String> lines = TextLines.wrap(text, metrics, 120);
        assertTrue(lines.size() > 1);
        assertEquals(text, String.join(" ", lines));
        assertTrue(lines.stream().allMatch(line -> metrics.stringWidth(line) <= 120));
    }

    @Test
    void longUnbrokenTextAndSupplementaryCharactersAreNotLost() {
        String text = "abcdefghijklmno".repeat(3) + "😀😀😀";
        List<String> lines = TextLines.wrap(text, metrics, 70);
        assertEquals(text, String.join("", lines));
        assertTrue(lines.stream().allMatch(line -> metrics.stringWidth(line) <= 70));
        assertTrue(lines.stream().noneMatch(line -> Character.isHighSurrogate(line.charAt(line.length() - 1))));
    }

    @Test
    void explicitBlankLinesAndTinyWidthsStillTerminate() {
        assertEquals(List.of("A", "", "B"), TextLines.wrap("A\n\nB", metrics, 100));
        assertEquals(List.of(""), TextLines.wrap("", metrics, 100));
        assertEquals("abc", String.join("", TextLines.wrap("abc", metrics, 0)));
    }
}
