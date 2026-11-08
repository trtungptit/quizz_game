package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;
import quizgame.client.util.SoundPlayer;

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
        setFont(AppTheme.FONT_BOLD.deriveFont(22f));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addActionListener(e -> SoundPlayer.click());
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

    // Bố cục ô: ô vuông ký hiệu bên trái, chữ, chừa chỗ dấu kết quả bên phải
    private static final int ICON_X = 20;
    private static final int ICON_SIZE = 46;
    private static final int TEXT_X = ICON_X + ICON_SIZE + 18;
    private static final int RIGHT_PAD = 70;

    /** Chiều cao tối thiểu để chữ xuống dòng không chạm biểu tượng hoặc dấu kết quả. */
    public int preferredHeightForWidth(int width) {
        return Math.max(84, TextLines.wrap(text, getFontMetrics(getFont()), width - TEXT_X - RIGHT_PAD).size()
                * getFontMetrics(getFont()).getHeight() + 36);
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
        boolean dim = state == State.DIM;
        // Viền trắng: ô đang chọn/rê chuột, ô đúng, ô chọn sai
        boolean ring = state == State.CORRECT || state == State.WRONG || state == State.NORMAL
                && (isSelected() || isEnabled() && (hasFocus() || getModel().isRollover() || getModel().isPressed()));
        Color fill = switch (state) {
            case DIM -> mix(COLORS[index], AppTheme.GAME_BG_TOP, .58f);
            case WRONG -> AppTheme.WRONG;
            default -> COLORS[index];
        };
        int arc = 24;
        int inset = 0;
        if (ring) {
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, w, h, arc + 8, arc + 8);
            g2.setColor(AppTheme.GAME_BG_TOP);
            g2.fillRoundRect(5, 5, w - 10, h - 10, arc + 2, arc + 2);
            inset = 8;
        } else {
            // Bóng mờ dưới đáy cho cảm giác nổi
            g2.setColor(mix(fill, Color.BLACK, .35f));
            g2.fillRoundRect(0, 4, w, h - 4, arc, arc);
        }
        g2.setColor(fill);
        g2.fillRoundRect(inset, inset, w - 2 * inset, h - 2 * inset - (ring ? 0 : 4), arc, arc);

        int cy = h / 2;
        // Ô vuông mờ chứa ký hiệu ▲ ◆ ● ■
        g2.setColor(new Color(255, 255, 255, dim ? 22 : 60));
        g2.fillRoundRect(ICON_X + inset / 2, cy - ICON_SIZE / 2, ICON_SIZE, ICON_SIZE, 14, 14);
        g2.setColor(new Color(255, 255, 255, dim ? 110 : 255));
        drawSymbol(g2, ICON_X + inset / 2 + ICON_SIZE / 2, cy);

        g2.setFont(getFont());
        java.util.List<String> lines = TextLines.wrap(text, g2.getFontMetrics(), w - TEXT_X - RIGHT_PAD);
        int lineHeight = g2.getFontMetrics().getHeight();
        int ty = (h - lineHeight * lines.size()) / 2 + g2.getFontMetrics().getAscent();
        Color fg = getForeground();
        g2.setColor(dim ? new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 120) : fg);
        for (String line : lines) {
            g2.drawString(line, TEXT_X + inset / 2, ty);
            ty += lineHeight;
        }
        g2.setColor(Color.WHITE);
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

    private static Color mix(Color color, Color other, float ratio) {
        return new Color(Math.round(color.getRed() * (1 - ratio) + other.getRed() * ratio),
                Math.round(color.getGreen() * (1 - ratio) + other.getGreen() * ratio),
                Math.round(color.getBlue() * (1 - ratio) + other.getBlue() * ratio));
    }

    /** Vẽ ký hiệu ▲ ◆ ● ■ bằng hình học. */
    private void drawSymbol(Graphics2D g2, int cx, int cy) {
        int r = 12;
        switch (index) {
            case 0 -> g2.fillPolygon(new int[]{cx, cx - r, cx + r}, new int[]{cy - r, cy + r - 2, cy + r - 2}, 3);
            case 1 -> g2.fillPolygon(new int[]{cx, cx - r, cx, cx + r}, new int[]{cy - r, cy, cy + r, cy}, 4);
            case 2 -> g2.fillOval(cx - r, cy - r, 2 * r, 2 * r);
            default -> g2.fillRect(cx - r + 2, cy - r + 2, 2 * r - 4, 2 * r - 4);
        }
    }

}
