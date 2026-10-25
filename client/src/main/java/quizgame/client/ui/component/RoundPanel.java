package quizgame.client.ui.component;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;

/** Panel nền bo góc, có thể có viền; lớp gốc cho thẻ, dòng danh sách, ô đáp án. */
public class RoundPanel extends JPanel {
    private final int arc;
    private Color fill;
    private Color outline;

    /** arc = đường kính góc bo (giống tham số của fillRoundRect). */
    public RoundPanel(LayoutManager layout, Color fill, int arc) {
        super(layout);
        this.fill = fill;
        this.arc = arc;
        setOpaque(false);
    }

    public void setFill(Color fill) {
        this.fill = fill;
        repaint();
    }

    /** Màu viền (null = không viền). */
    public void setOutline(Color outline) {
        this.outline = outline;
        repaint();
    }

    protected Shape shape() {
        return new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fill(shape());
        if (outline != null) {
            g2.setColor(outline);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        }
        g2.dispose();
    }
}
