package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;

import javax.swing.JPanel;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Paint;
import java.awt.RenderingHints;

/** Panel nền gradient (tùy chọn bo góc): thanh điều hướng, thanh trên màn chơi, biểu ngữ, nền đăng nhập. */
public class GradientPanel extends JPanel {
    /** Tạo Paint theo kích thước hiện tại của panel. */
    public interface PaintFactory {
        Paint create(int width, int height);
    }

    private final PaintFactory factory;
    private final int arc;

    /** arc = đường kính góc bo, 0 = hình chữ nhật. */
    public GradientPanel(LayoutManager layout, PaintFactory factory, int arc) {
        super(layout);
        this.factory = factory;
        this.arc = arc;
        setOpaque(false);
    }

    /** Gradient ngang tím -> tím sáng -> hồng. */
    public static GradientPanel horizontal(LayoutManager layout) {
        return new GradientPanel(layout, (w, h) -> AppTheme.horizontalGradient(w), 0);
    }

    /** Gradient chéo tím -> hồng. */
    public static GradientPanel diagonal(LayoutManager layout, int arc) {
        return new GradientPanel(layout, AppTheme::diagonalGradient, arc);
    }

    /** Gradient vàng -> cam (biểu ngữ người thắng). */
    public static GradientPanel accent(LayoutManager layout, int arc) {
        return new GradientPanel(layout, (w, h) -> new GradientPaint(0, 0, AppTheme.ACCENT, w, 0, AppTheme.ORANGE), arc);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(factory.create(getWidth(), getHeight()));
        if (arc > 0) {
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        } else {
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
        g2.dispose();
    }
}
