package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;

/** Tiêu đề IN HOA có gạch vàng cam bên dưới. */
public class SectionTitle extends JPanel {
    private final JLabel label = new JLabel();

    public SectionTitle(String text) {
        super(new BorderLayout(0, 6));
        setOpaque(false);
        label.setFont(AppTheme.FONT_TITLE.deriveFont(28f));
        label.setForeground(AppTheme.TITLE);
        setText(text);
        add(label, BorderLayout.CENTER);
        add(new Underline(), BorderLayout.SOUTH);
    }

    public void setText(String text) {
        label.setText(text.toUpperCase(Locale.ROOT));
    }

    private static final class Underline extends JPanel {
        Underline() {
            setOpaque(false);
            setPreferredSize(new Dimension(64, 5));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, AppTheme.ACCENT, 64, 0, AppTheme.ORANGE));
            g2.fillRoundRect(0, 0, 64, 5, 5, 5);
            g2.dispose();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(64, 5);
        }
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
