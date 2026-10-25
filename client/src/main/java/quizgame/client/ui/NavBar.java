package quizgame.client.ui;

import quizgame.client.ui.component.GradientPanel;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Thanh điều hướng nền gradient: QUIZ, TRANG CHỦ, XẾP HẠNG, LỊCH SỬ và ĐĂNG XUẤT. */
public class NavBar extends GradientPanel {
    private final Map<String, NavItem> items = new LinkedHashMap<>();

    public NavBar(Consumer<String> onNavigate, Runnable onLogout) {
        super(new BorderLayout(), (w, h) -> AppTheme.horizontalGradient(w), 0);
        setPreferredSize(new Dimension(0, 56));
        setBorder(BorderFactory.createEmptyBorder(0, AppTheme.GAP_M, 0, AppTheme.GAP_M));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, AppTheme.GAP_S, 10));
        left.setOpaque(false);
        JLabel logo = new JLabel(AppTheme.APP_NAME);
        logo.setFont(AppTheme.FONT_TITLE.deriveFont(26f));
        logo.setForeground(AppTheme.ACCENT);
        logo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logo.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, AppTheme.GAP_M));
        logo.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onNavigate.accept(MainFrame.HOME);
            }
        });
        left.add(logo);
        addItem(left, "TRANG CHỦ", MainFrame.HOME, () -> onNavigate.accept(MainFrame.HOME));
        addItem(left, "XẾP HẠNG", MainFrame.LEADERBOARD, () -> onNavigate.accept(MainFrame.LEADERBOARD));
        addItem(left, "LỊCH SỬ", MainFrame.HISTORY, () -> onNavigate.accept(MainFrame.HISTORY));
        add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 10));
        right.setOpaque(false);
        right.add(new NavItem("ĐĂNG XUẤT", onLogout));
        add(right, BorderLayout.EAST);
    }

    private void addItem(JPanel parent, String text, String key, Runnable action) {
        NavItem item = new NavItem(text, action);
        items.put(key, item);
        parent.add(item);
    }

    /** Làm sáng mục tương ứng màn đang mở (key không có trên thanh thì không mục nào sáng). */
    public void setActive(String key) {
        // Phòng, kết quả và soạn bộ là các màn đi ra từ TRANG CHỦ nên vẫn sáng mục này
        String target = switch (key) {
            case MainFrame.ROOM, MainFrame.RESULT, MainFrame.SET_EDITOR -> MainFrame.HOME;
            default -> key;
        };
        items.forEach((k, item) -> item.setActive(k.equals(target)));
    }

    /** Một mục chữ trên thanh; mục đang mở nằm trong viên thuốc trắng mờ. */
    private static final class NavItem extends JLabel {
        private boolean active;

        NavItem(String text, Runnable action) {
            super(text, CENTER);
            setFont(AppTheme.FONT_BOLD.deriveFont(13f));
            setForeground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    action.run();
                }
            });
        }

        void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (active) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 60));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
