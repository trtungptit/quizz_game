package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;
import javax.swing.Icon;
import java.awt.*;
import java.util.Objects;

/** Minh họa vector nhỏ theo cấu trúc câu hỏi; không cần ảnh mạng hoặc thư viện ngoài. */
public final class ChoiceIllustration implements Icon {
    public enum Kind { SINGLE, MULTI, TYPE, TEMPLATE, IMPORT }
    private final Kind kind;

    public ChoiceIllustration(Kind kind) {
        this.kind = Objects.requireNonNull(kind);
    }

    @Override public int getIconWidth() { return 160; }
    @Override public int getIconHeight() { return 104; }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (kind == Kind.TEMPLATE || kind == Kind.IMPORT) drawCsv(g);
        else {
            g.setColor(AppTheme.CARD);
            g.fillRoundRect(4, 8, 152, 88, 12, 12);
            g.setColor(AppTheme.BORDER);
            g.drawRoundRect(4, 8, 152, 88, 12, 12);
            g.setColor(AppTheme.PRIMARY);
            g.fillRoundRect(16, 19, 66, 4, 4, 4);
            g.setColor(AppTheme.BORDER);
            g.fillRoundRect(16, 29, 105, 3, 3, 3);
            switch (kind) {
                case SINGLE -> drawSingle(g);
                case MULTI -> drawMulti(g);
                case TYPE -> drawType(g);
                default -> throw new IllegalStateException();
            }
        }
        g.dispose();
    }

    private static void drawSingle(Graphics2D g) {
        for (int i = 0; i < 4; i++) {
            int x = 16 + i % 2 * 67, y = 44 + i / 2 * 23;
            boolean selected = i == 1;
            g.setColor(selected ? AppTheme.PRIMARY : AppTheme.NEUTRAL);
            g.fillRoundRect(x, y, 60, 18, 6, 6);
            g.setColor(selected ? Color.WHITE : AppTheme.TEXT_MUTED);
            g.drawOval(x + 6, y + 6, 6, 6);
            if (selected) g.fillOval(x + 8, y + 8, 3, 3);
            g.setFont(AppTheme.FONT_BOLD.deriveFont(9f));
            g.drawString(String.valueOf((char) ('A' + i)), x + 20, y + 12);
            g.fillRoundRect(x + 32, y + 8, 18, 2, 2, 2);
        }
    }

    private static void drawMulti(Graphics2D g) {
        for (int i = 0; i < 3; i++) {
            int y = 41 + i * 16;
            boolean selected = i != 1;
            g.setColor(selected ? AppTheme.SOFT : AppTheme.NEUTRAL);
            g.fillRoundRect(16, y, 128, 13, 5, 5);
            g.setColor(selected ? AppTheme.PRIMARY : AppTheme.TEXT_MUTED);
            g.drawRoundRect(22, y + 3, 7, 7, 2, 2);
            if (selected) g.drawPolyline(new int[]{23, 25, 29}, new int[]{y + 6, y + 8, y + 4}, 3);
            g.fillRoundRect(39, y + 5, 75 - i * 13, 3, 3, 3);
        }
    }

    private static void drawType(Graphics2D g) {
        g.setColor(AppTheme.PRIMARY);
        g.drawRoundRect(16, 43, 128, 22, 6, 6);
        g.setFont(AppTheme.FONT_BASE.deriveFont(9f));
        g.setColor(AppTheme.TEXT_MUTED);
        g.drawString("Nhập đáp án", 24, 57);
        g.setColor(AppTheme.PRIMARY);
        g.drawLine(83, 49, 83, 59);
        g.fillRoundRect(96, 73, 48, 14, 5, 5);
        g.setColor(Color.WHITE);
        g.drawLine(110, 80, 130, 80);
        g.drawPolyline(new int[]{126, 130, 126}, new int[]{77, 80, 83}, 3);
    }

    private void drawCsv(Graphics2D g) {
        g.setColor(AppTheme.SOFT);
        g.fillRoundRect(35, 8, 78, 88, 10, 10);
        g.setColor(AppTheme.CARD);
        g.fillRoundRect(42, 12, 64, 78, 7, 7);
        g.setColor(AppTheme.BORDER);
        g.drawRoundRect(42, 12, 64, 78, 7, 7);
        g.setFont(AppTheme.FONT_BOLD.deriveFont(17f));
        g.setColor(AppTheme.PRIMARY);
        g.drawString("CSV", 54, 38);
        g.setColor(AppTheme.BORDER);
        for (int y = 50; y <= 74; y += 8) g.drawLine(53, y, 95, y);
        for (int x = 53; x <= 95; x += 14) g.drawLine(x, 50, x, 74);
        g.setColor(AppTheme.PRIMARY);
        g.fillOval(100, 57, 32, 32);
        g.setColor(Color.WHITE);
        if (kind == Kind.TEMPLATE) {
            g.drawLine(116, 64, 116, 80);
            g.drawPolyline(new int[]{110, 116, 122}, new int[]{74, 80, 74}, 3);
        } else {
            g.drawLine(116, 81, 116, 65);
            g.drawPolyline(new int[]{110, 116, 122}, new int[]{71, 65, 71}, 3);
        }
    }
}
