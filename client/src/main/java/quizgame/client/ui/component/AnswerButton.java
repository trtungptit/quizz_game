package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;

import javax.swing.JButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Bốn màu đáp án gốc; giữ bo góc, xuống dòng, trạng thái chọn và dấu kết quả đã sửa. */
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
        setText(this.text);
        setForeground(Color.WHITE);
        setFont(AppTheme.FONT_BOLD.deriveFont(18f));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public void setState(State state) {
        this.state = state;
        setForeground(Color.WHITE);
        repaint();
    }

    /** Chữ hiển thị của đáp án, ví dụ 'B. TCP'. */
    public String answerText() {
        return text;
    }

    /** Chiều cao tối thiểu để chữ xuống dòng không chạm biểu tượng hoặc dấu kết quả. */
    public int preferredHeightForWidth(int width) {
        return Math.max(84, TextLines.wrap(text, getFontMetrics(getFont()), width - 134).size()
                * getFontMetrics(getFont()).getHeight() + 32);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(300, preferredHeightForWidth(getWidth() > 0 ? getWidth() : 300));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        boolean highlighted = state == State.NORMAL && (isSelected() || isEnabled()
                && (hasFocus() || getModel().isRollover() || getModel().isPressed()));
        Color fill = switch (state) {
            case DIM -> mixWithWhite(COLORS[index], .62f);
            case WRONG -> AppTheme.WRONG;
            default -> COLORS[index];
        };
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, w, h, 20, 20);
        Color outline = switch (state) {
            case CORRECT -> AppTheme.CORRECT;
            case WRONG -> new Color(0xB91C1C);
            default -> highlighted ? Color.WHITE : AppTheme.BORDER;
        };
        g2.setColor(outline);
        g2.setStroke(new BasicStroke(state == State.NORMAL && !highlighted || state == State.DIM ? 1f : 2f));
        g2.drawRoundRect(2, 2, w - 5, h - 5, 18, 18);
        g2.setFont(getFont());
        int cy = h / 2;
        java.util.List<String> lines = TextLines.wrap(text, g2.getFontMetrics(), w - 134);
        int lineHeight = g2.getFontMetrics().getHeight();
        int ty = (h - lineHeight * lines.size()) / 2 + g2.getFontMetrics().getAscent();
        g2.setColor(Color.WHITE);
        drawSymbol(g2, 36, cy);
        g2.setColor(getForeground());
        for (String line : lines) {
            g2.drawString(line, 64, ty);
            ty += lineHeight;
        }
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

    private static Color mixWithWhite(Color color, float ratio) {
        return new Color(Math.round(color.getRed() * (1 - ratio) + 255 * ratio),
                Math.round(color.getGreen() * (1 - ratio) + 255 * ratio),
                Math.round(color.getBlue() * (1 - ratio) + 255 * ratio));
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

}
