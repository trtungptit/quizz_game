package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Format;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/** Bảng xếp hạng nền tối trong trận: vòng hạng, tên, điểm vàng, tiến độ; dòng của mình được làm nổi. */
public class GameScoreList extends JComponent {
    /** Một dòng hiển thị; progress ví dụ '4/10'. */
    public record Row(int rank, String name, int score, String progress, boolean mine) {
    }

    private static final int ROW_H = 64;
    private static final Font NAME = AppTheme.FONT_BASE.deriveFont(19f);
    private static final Font NAME_BOLD = AppTheme.FONT_BOLD.deriveFont(19f);
    private static final Font SCORE = AppTheme.FONT_BOLD.deriveFont(19f);
    private static final Font SMALL = AppTheme.FONT_BASE.deriveFont(14f);
    private static final Font RANK = AppTheme.FONT_BOLD.deriveFont(14f);

    private List<Row> rows = List.of();

    public GameScoreList() {
        setOpaque(false);
    }

    public void setRows(List<Row> rows) {
        this.rows = List.copyOf(rows);
        revalidate();
        repaint();
    }

    public List<Row> rows() {
        return rows;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(300, Math.max(1, rows.size()) * (ROW_H + 8));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int y = 0;
        for (Row r : rows) {
            int cy = y + ROW_H / 2;
            if (r.mine()) {
                g2.setColor(new Color(255, 255, 255, 30));
                g2.fillRoundRect(0, y, w, ROW_H, 16, 16);
                g2.setColor(AppTheme.GOLD);
                g2.fillRoundRect(0, y, 5, ROW_H, 4, 4);
            }
            // Vòng hạng: hạng 1 vàng, còn lại mờ
            int d = 34;
            int cx = 18;
            g2.setColor(r.rank() == 1 ? AppTheme.GOLD : new Color(255, 255, 255, 40));
            g2.fillOval(cx, cy - d / 2, d, d);
            g2.setFont(RANK);
            FontMetrics fm = g2.getFontMetrics();
            String rank = String.valueOf(r.rank());
            g2.setColor(r.rank() == 1 ? AppTheme.TITLE : Color.WHITE);
            g2.drawString(rank, cx + (d - fm.stringWidth(rank)) / 2, cy - fm.getHeight() / 2 + fm.getAscent());

            // Phải: tiến độ rồi điểm
            g2.setFont(SMALL);
            fm = g2.getFontMetrics();
            int right = w - 14;
            int progressW = fm.stringWidth("10/10");
            g2.setColor(AppTheme.GAME_MUTED);
            g2.drawString(r.progress(), right - fm.stringWidth(r.progress()), cy - fm.getHeight() / 2 + fm.getAscent());
            g2.setFont(SCORE);
            fm = g2.getFontMetrics();
            String score = Format.points(r.score());
            int scoreRight = right - progressW - 18;
            g2.setColor(AppTheme.GOLD);
            g2.drawString(score, scoreRight - fm.stringWidth(score), cy - fm.getHeight() / 2 + fm.getAscent());
            int nameMax = scoreRight - fm.stringWidth(score) - 12 - (cx + d + 14);

            // Tên, cắt bớt nếu quá dài
            g2.setFont(r.mine() ? NAME_BOLD : NAME);
            fm = g2.getFontMetrics();
            String name = r.name() + (r.mine() ? " (bạn)" : "");
            if (fm.stringWidth(name) > nameMax) {
                while (name.length() > 1 && fm.stringWidth(name + "…") > nameMax) name = name.substring(0, name.length() - 1);
                name = name + "…";
            }
            g2.setColor(Color.WHITE);
            g2.drawString(name, cx + d + 14, cy - fm.getHeight() / 2 + fm.getAscent());
            y += ROW_H + 8;
        }
        g2.dispose();
    }
}
