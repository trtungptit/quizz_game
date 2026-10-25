package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Nút bo góc có viền dưới dày 3 px cho cảm giác nổi. */
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
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(120, 40));
    }

    /** Nút màu chính, chữ trắng. */
    public static AppButton primary(String text) {
        return new AppButton(text, AppTheme.PRIMARY, Color.WHITE);
    }

    /** Nút nền tím nhạt, chữ tím đậm. */
    public static AppButton soft(String text) {
        return new AppButton(text, AppTheme.SOFT, AppTheme.SOFT_TEXT);
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
        int depth = AppTheme.BUTTON_DEPTH;
        boolean enabled = isEnabled();
        Color face = enabled ? base : blend(base, Color.WHITE, 0.55f);
        Color edge = enabled ? blend(base, Color.BLACK, 0.18f) : blend(base, Color.WHITE, 0.4f);
        boolean pressed = enabled && getModel().isPressed();
        int arc = AppTheme.ARC_BUTTON * 2;
        g2.setColor(edge);
        g2.fillRoundRect(0, 0, w, h, arc, arc);
        g2.setColor(face);
        g2.fillRoundRect(0, pressed ? depth : 0, w, h - depth, arc, arc);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(getText())) / 2;
        int ty = (h - depth - fm.getHeight()) / 2 + fm.getAscent() + (pressed ? depth : 0);
        g2.setColor(getForeground());
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
