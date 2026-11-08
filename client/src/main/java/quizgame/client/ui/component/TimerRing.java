package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Đồng hồ tròn: số giây ở giữa, vòng vàng ngắn dần theo thời gian còn lại, đỏ khi sắp hết. */
public class TimerRing extends JComponent {
    private static final int SIZE = 62;
    private float fraction = 1f;
    private long seconds;
    private boolean danger;
    private boolean blank = true;

    public TimerRing() {
        setPreferredSize(new Dimension(SIZE, SIZE));
        setMinimumSize(new Dimension(SIZE, SIZE));
        setFont(AppTheme.FONT_TITLE.deriveFont(22f));
    }

    /** Cập nhật số giây còn lại và tỉ lệ còn lại (0..1). */
    public void set(long seconds, float fraction, boolean danger) {
        this.seconds = Math.max(0, seconds);
        this.fraction = Math.max(0f, Math.min(1f, fraction));
        this.danger = danger;
        this.blank = false;
        repaint();
    }

    /** Ẩn số (giữa các câu). */
    public void clear() {
        blank = true;
        fraction = 1f;
        danger = false;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int d = Math.min(getWidth(), getHeight()) - 6;
        int x = (getWidth() - d) / 2;
        int y = (getHeight() - d) / 2;
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(AppTheme.GAME_LINE);
        g2.drawOval(x, y, d, d);
        g2.setColor(danger ? AppTheme.WRONG : AppTheme.GOLD);
        g2.drawArc(x, y, d, d, 90, Math.round(360 * fraction));
        if (!blank) {
            String text = String.format("%02d", seconds);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(danger ? AppTheme.GAME_WRONG_TEXT : Color.WHITE);
            g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        }
        g2.dispose();
    }
}
