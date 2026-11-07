package quizgame.client.ui;

import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import static org.junit.jupiter.api.Assertions.*;

/** Màu gốc kết hợp hình khối và độ dày viền đã sửa; không cần server. */
class AppThemeVisualTest {
    @Test
    void originalPaletteKeepsRefinedCornersAndFlatButtons() {
        assertEquals(new Color(0xF4F1FF), AppTheme.BACKGROUND);
        assertEquals(new Color(0xDDD6FE), AppTheme.BORDER);
        assertEquals(new Color(0x5B3DF5), AppTheme.PRIMARY);
        assertEquals(new Color(0x1E1B4B), AppTheme.TEXT);
        assertEquals(new Color(0x6B7280), AppTheme.TEXT_MUTED);
        assertEquals(new Color(0x2E1065), AppTheme.TITLE);
        assertEquals(new Color(0x22C55E), AppTheme.CORRECT);
        assertEquals(new Color(0xEF4444), AppTheme.WRONG);
        assertTrue(AppTheme.ARC_CARD >= 12 && AppTheme.ARC_CARD <= 14);
        assertTrue(AppTheme.ARC_BUTTON >= 8 && AppTheme.ARC_BUTTON <= 10);
        assertEquals(0, AppTheme.BUTTON_DEPTH, "Nút không còn cạnh nổi giả 3D");
    }

    @Test
    void originalPurplePinkGradientReturnsWithoutChangingFontOrSpacing() {
        java.awt.LinearGradientPaint paint = assertInstanceOf(java.awt.LinearGradientPaint.class,
                AppTheme.horizontalGradient(200));
        assertArrayEquals(new Color[]{new Color(0x3B1FD1), new Color(0x7C3AED), new Color(0xC026D3)}, paint.getColors());
        assertEquals("Segoe UI", AppTheme.FONT_FAMILY);
        assertEquals(16, AppTheme.GAP_M);
        assertEquals(24, AppTheme.GAP_L);
    }

    @Test
    void inputsKeepThinBordersAndNewCornerSizeWithOriginalColors() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            assertEquals(1, UIManager.getInt("Component.borderWidth"));
            assertEquals(18, UIManager.getInt("TextComponent.arc"));
            assertEquals(AppTheme.BORDER, UIManager.getColor("Component.borderColor"));
            assertEquals(AppTheme.PRIMARY, UIManager.getColor("Component.focusedBorderColor"));
        });
    }


}
