package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;

import javax.swing.BorderFactory;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;

/** Thẻ trắng bo góc, có thể có dải màu ở trên; nội dung bị cắt theo góc bo. */
public class CardPanel extends RoundPanel {
    private static final int STRIPE_HEIGHT = 7;
    private final Color stripe;

    public CardPanel(LayoutManager layout, Color stripe) {
        super(layout, AppTheme.CARD, AppTheme.ARC_CARD * 2);
        this.stripe = stripe;
        int top = stripe == null ? 12 : 12 + STRIPE_HEIGHT;
        setBorder(BorderFactory.createEmptyBorder(top, 16, 12, 16));
    }

    public CardPanel(LayoutManager layout) {
        this(layout, null);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (stripe != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.clip(shape());
            g2.setColor(stripe);
            g2.fillRect(0, 0, getWidth(), STRIPE_HEIGHT);
            g2.dispose();
        }
    }

    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.clip(shape());
        super.paintChildren(g2);
        g2.dispose();
    }
}
