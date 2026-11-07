package quizgame.client.ui;

import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import static org.junit.jupiter.api.Assertions.*;

/** Hợp đồng màu và độ tương phản cho giao diện sáng, không cần server. */
class AppThemeVisualTest {
    @Test
    void neutralSurfacesAndRestrainedCornersMatchApprovedPalette() {
        assertEquals(new Color(0xF7F8FC), AppTheme.BACKGROUND);
        assertEquals(new Color(0xE5E7EB), AppTheme.BORDER);
        assertEquals(new Color(0x6D28D9), AppTheme.PRIMARY);
        assertTrue(AppTheme.ARC_CARD >= 12 && AppTheme.ARC_CARD <= 14);
        assertTrue(AppTheme.ARC_BUTTON >= 8 && AppTheme.ARC_BUTTON <= 10);
        assertEquals(0, AppTheme.BUTTON_DEPTH, "Nút không còn cạnh nổi giả 3D");
    }

    @Test
    void activeTextAndStatusColorsStayReadable() {
        for (Color background : new Color[]{AppTheme.CARD, AppTheme.BACKGROUND}) {
            assertTrue(contrast(AppTheme.TEXT, background) >= 4.5);
            assertTrue(contrast(AppTheme.TEXT_MUTED, background) >= 4.5);
        }
        for (Color background : new Color[]{AppTheme.PRIMARY, AppTheme.CORRECT, AppTheme.WRONG}) {
            assertTrue(contrast(Color.WHITE, background) >= 4.5,
                    "Màu trạng thái phải đủ tương phản với chữ trắng: " + background);
        }
    }

    @Test
    void unfocusedInputsUseThinNeutralBorders() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            assertEquals(1, UIManager.getInt("Component.borderWidth"));
            assertEquals(AppTheme.BORDER, UIManager.getColor("Component.borderColor"));
            assertEquals(AppTheme.PRIMARY, UIManager.getColor("Component.focusedBorderColor"));
        });
    }

    static double contrast(Color a, Color b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }

    private static double luminance(Color color) {
        return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) + .0722 * linear(color.getBlue());
    }

    private static double linear(int value) {
        double v = value / 255.0;
        return v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4);
    }
}
