package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;

import javax.swing.JButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Nút đáp án lớn một trong 4 màu cố định: A đỏ ▲, B xanh dương ◆, C cam ●, D xanh lá ■. */
public class AnswerButton extends JButton {
    /** Trạng thái hiển thị sau khi có kết quả. */
    public enum State { NORMAL, CORRECT, WRONG, DIM }

    private static final Color[] COLORS = {AppTheme.ANSWER_A, AppTheme.ANSWER_B, AppTheme.ANSWER_C, AppTheme.ANSWER_D};
    private final int index;
    private final String text;
    private State state = State.NORMAL;

    public AnswerButton(int index, String text) {
        this.index = index;
        this.text = (char) ('A' + index) + ". " + text;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public void setState(State state) {
        this.state = state;
        repaint();
    }

    /** Chữ hiển thị của đáp án, ví dụ 'B. TCP'. */
    public String answerText() {
        return text;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        Color base = COLORS[index];
        Color fill = switch (state) {
            case DIM -> mix(base, Color.WHITE, 0.62f);
            case WRONG -> AppTheme.WRONG;
            default -> base;
        };
        int inset = state == State.CORRECT ? 6 : 0;
        g2.setColor(fill);
        g2.fillRoundRect(inset, inset, w - 2 * inset, h - 2 * inset, 18, 18);
        if (state == State.CORRECT) {
            g2.setColor(AppTheme.CORRECT);
            g2.setStroke(new BasicStroke(5f));
            g2.drawRoundRect(2, 2, w - 5, h - 5, 22, 22);
        } else if (state == State.WRONG) {
            g2.setColor(new Color(0xB91C1C));
            g2.setStroke(new BasicStroke(4f));
            g2.drawRoundRect(2, 2, w - 5, h - 5, 20, 20);
        }
        g2.setColor(Color.WHITE);
        g2.setFont(AppTheme.FONT_BOLD.deriveFont(18f));
        int cy = h / 2;
        int ty = (h - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent();
        drawSymbol(g2, 36, cy);
        g2.drawString(text, 64, ty);
        // Dấu ✓ / ✗ vẽ bằng nét thay vì ký tự để không phụ thuộc font
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (state == State.CORRECT) {
            g2.drawPolyline(new int[]{w - 58, w - 48, w - 30}, new int[]{cy, cy + 9, cy - 10}, 3);
        } else if (state == State.WRONG) {
            g2.drawLine(w - 56, cy - 9, w - 38, cy + 9);
            g2.drawLine(w - 56, cy + 9, w - 38, cy - 9);
        }
        g2.dispose();
    }

    /** Vẽ ký hiệu ▲ ◆ ● ■ bằng hình học. */
    private void drawSymbol(Graphics2D g2, int cx, int cy) {
        int r = 11;
        switch (index) {
            case 0 -> g2.fillPolygon(new int[]{cx, cx - r, cx + r}, new int[]{cy - r, cy + r - 2, cy + r - 2}, 3);
            case 1 -> g2.fillPolygon(new int[]{cx, cx - r, cx, cx + r}, new int[]{cy - r, cy, cy + r, cy}, 4);
            case 2 -> g2.fillOval(cx - r, cy - r, 2 * r, 2 * r);
            default -> g2.fillRect(cx - r + 2, cy - r + 2, 2 * r - 4, 2 * r - 4);
        }
    }

    private static Color mix(Color a, Color b, float ratio) {
        return new Color(Math.round(a.getRed() * (1 - ratio) + b.getRed() * ratio),
                Math.round(a.getGreen() * (1 - ratio) + b.getGreen() * ratio),
                Math.round(a.getBlue() * (1 - ratio) + b.getBlue() * ratio));
    }
}
