package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Nút phẳng bo góc, có hover/nhấn/focus và màu vô hiệu rõ ràng. */
public class AppButton extends JButton {
    private Color base;

    public AppButton(String text, Color base, Color foreground) {
        super(text);
        this.base = base;
        setForeground(foreground);
        setFont(AppTheme.FONT_BOLD.deriveFont(13f));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(120, 40));
    }

    /** Nút màu chính, chữ trắng. */
    public static AppButton primary(String text) {
        return new AppButton(text, AppTheme.PRIMARY, Color.WHITE);
    }

    /** Nút phụ nền trung tính, không cạnh tranh với hành động chính. */
    public static AppButton soft(String text) {
        return new AppButton(text, AppTheme.NEUTRAL, AppTheme.TEXT);
    }

    /** Đổi màu nền và chữ (dùng cho tab đang chọn / không chọn). */
    public void setColors(Color base, Color foreground) {
        this.base = base;
        setForeground(foreground);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        boolean enabled = isEnabled();
        Color face = enabled ? base : AppTheme.NEUTRAL;
        if (enabled && getModel().isPressed()) face = blend(base, Color.BLACK, 0.12f);
        else if (enabled && getModel().isRollover()) face = blend(base, Color.BLACK, 0.06f);
        int arc = AppTheme.ARC_BUTTON * 2;
        g2.setColor(face);
        g2.fillRoundRect(0, 0, w, h, arc, arc);
        if (enabled && hasFocus()) {
            g2.setColor(base.equals(AppTheme.PRIMARY) ? Color.WHITE : AppTheme.PRIMARY);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(3, 3, Math.max(0, w - 7), Math.max(0, h - 7), arc - 4, arc - 4);
        }
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(getText())) / 2;
        int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(enabled ? getForeground() : AppTheme.TEXT_MUTED);
        g2.drawString(getText(), tx, ty);
        g2.dispose();
    }

    private static Color blend(Color a, Color b, float ratio) {
        int r = Math.round(a.getRed() * (1 - ratio) + b.getRed() * ratio);
        int gr = Math.round(a.getGreen() * (1 - ratio) + b.getGreen() * ratio);
        int bl = Math.round(a.getBlue() * (1 - ratio) + b.getBlue() * ratio);
        return new Color(r, gr, bl);
    }
}
