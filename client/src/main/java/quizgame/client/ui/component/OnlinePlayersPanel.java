package quizgame.client.ui.component;

import com.formdev.flatlaf.FlatClientProperties;
import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Dialogs;
import quizgame.client.ui.MainFrame;
import quizgame.client.util.Async;
import quizgame.common.model.ChangeType;
import quizgame.common.model.UserInfo;
import quizgame.common.model.UserStatus;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Danh sách người chơi online (dùng ở HOME và ROOM). Cập nhật từng dòng khi có USER_UPDATE
 * nên không làm mất vị trí cuộn và nội dung ô tìm. Gọi attach()/detach() khi màn hiện/ẩn.
 */
public class OnlinePlayersPanel extends CardPanel {
    private final MainFrame frame;
    private final OnlineUsersModel model;
    private final JTextField search = new JTextField();
    private final JPanel list = new JPanel();
    private final JLabel notice = new JLabel(" ", JLabel.CENTER);
    private final JLabel footer = new JLabel(" ");
    private final Map<String, Row> rows = new LinkedHashMap<>();
    private final OnlineUsersModel.Listener modelListener = new OnlineUsersModel.Listener() {
        @Override
        public void onReplaced() {
            rebuild();
        }

        @Override
        public void onChanged(UserInfo user, ChangeType change) {
            applyChange(user, change);
        }

        @Override
        public void onInvitedChanged(String username) {
            Row row = rows.get(username);
            if (row != null) {
                row.refreshButton();
            }
        }
    };

    public OnlinePlayersPanel(MainFrame frame, String title) {
        super(new BorderLayout(0, AppTheme.GAP_S));
        this.frame = frame;
        this.model = frame.onlineUsers();

        JLabel heading = new JLabel(title);
        heading.setFont(AppTheme.FONT_BOLD.deriveFont(14f));
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Tìm người chơi...");
        search.setMargin(new java.awt.Insets(6, 12, 6, 12));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        JPanel top = new JPanel(new BorderLayout(0, AppTheme.GAP_S));
        top.setOpaque(false);
        top.add(heading, BorderLayout.NORTH);
        top.add(search, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel listHolder = new JPanel(new BorderLayout());
        listHolder.setOpaque(false);
        notice.setForeground(AppTheme.TEXT_MUTED);
        notice.setBorder(BorderFactory.createEmptyBorder(AppTheme.GAP_M, 0, AppTheme.GAP_M, 0));
        JPanel stack = new JPanel(new BorderLayout());
        stack.setOpaque(false);
        stack.add(notice, BorderLayout.NORTH);
        stack.add(list, BorderLayout.CENTER);
        listHolder.add(stack, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(listHolder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        footer.setFont(AppTheme.FONT_SMALL.deriveFont(13f));
        footer.setForeground(AppTheme.TEXT_MUTED);
        add(footer, BorderLayout.SOUTH);
        rebuild();
    }

    /** Bắt đầu theo dõi thay đổi (gọi khi màn hiện). */
    public void attach() {
        model.addListener(modelListener);
        rebuild();
    }

    /** Ngừng theo dõi (gọi khi màn ẩn). */
    public void detach() {
        model.removeListener(modelListener);
    }

    // ---------------------------------------------------------------- Cập nhật danh sách

    private void rebuild() {
        list.removeAll();
        rows.clear();
        for (UserInfo u : model.snapshot()) {
            Row row = new Row(u);
            rows.put(u.username(), row);
            list.add(row);
            list.add(Box.createVerticalStrut(AppTheme.GAP_S));
        }
        refresh();
    }

    private void applyChange(UserInfo user, ChangeType change) {
        Row row = rows.get(user.username());
        if (change == ChangeType.REMOVE) {
            if (row != null) {
                removeRow(row);
            }
        } else if (row != null) {
            row.update(user);
        } else {
            Row created = new Row(user);
            rows.put(user.username(), created);
            list.add(created);
            list.add(Box.createVerticalStrut(AppTheme.GAP_S));
        }
        refresh();
    }

    private void removeRow(Row row) {
        int index = list.getComponentZOrder(row);
        list.remove(row);
        if (index >= 0 && index < list.getComponentCount()) {
            list.remove(index); // dải đệm đi kèm dòng
        }
        rows.remove(row.user.username());
    }

    private void applyFilter() {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        for (Row row : rows.values()) {
            boolean show = q.isEmpty() || row.user.username().toLowerCase(Locale.ROOT).contains(q);
            row.setVisible(show);
        }
        refreshNotice();
        list.revalidate();
        list.repaint();
    }

    private void refresh() {
        applyFilter();
        long idle = rows.values().stream().filter(r -> r.user.status() == UserStatus.IDLE).count();
        footer.setText("Online: " + rows.size() + " • Rảnh: " + idle);
    }

    private void refreshNotice() {
        if (!model.isLoaded()) {
            notice.setText("Đang tải...");
        } else if (rows.isEmpty()) {
            notice.setText("Chưa có ai khác online");
        } else if (rows.values().stream().noneMatch(Row::isVisible)) {
            notice.setText("Không tìm thấy người chơi");
        } else {
            notice.setText("");
        }
        notice.setVisible(!notice.getText().isEmpty());
    }

    // ---------------------------------------------------------------- Mời

    private void invite(String username) {
        model.setInvited(username, true);
        if (frame.currentRoom() != null) {
            sendInvite(username);
            return;
        }
        // Chưa ở trong phòng: tạo phòng (tên mặc định) rồi mời và vào ROOM
        frame.createRoomAndEnter(frame.defaultRoomName(), null, room -> sendInvite(username),
                () -> model.setInvited(username, false));
    }

    private void sendInvite(String username) {
        Async.ui(frame.client().invite(username), v -> { }, error -> {
            model.setInvited(username, false);
            Dialogs.error(frame, error);
        });
    }

    // ---------------------------------------------------------------- Một dòng

    private final class Row extends RoundPanel {
        private UserInfo user;
        private final JLabel name = new JLabel();
        private final JLabel info = new JLabel();
        private final Avatar avatar = new Avatar();
        private final AppButton button = AppButton.soft("MỜI");

        Row(UserInfo user) {
            super(new BorderLayout(AppTheme.GAP_S, 0), AppTheme.ROW_BG, 14);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
            setPreferredSize(new Dimension(100, 58));
            setAlignmentX(LEFT_ALIGNMENT);
            name.setFont(AppTheme.FONT_BOLD);
            info.setFont(AppTheme.FONT_SMALL);
            info.setForeground(AppTheme.TEXT_MUTED);
            JPanel text = new JPanel(new BorderLayout());
            text.setOpaque(false);
            text.add(name, BorderLayout.NORTH);
            text.add(info, BorderLayout.CENTER);
            button.setPreferredSize(new Dimension(64, 30));
            button.setFont(AppTheme.FONT_BOLD.deriveFont(11f));
            button.addActionListener(e -> invite(this.user.username()));
            JPanel east = new JPanel(new BorderLayout());
            east.setOpaque(false);
            east.add(button, BorderLayout.CENTER);
            add(avatar, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            add(east, BorderLayout.EAST);
            update(user);
        }

        void update(UserInfo u) {
            this.user = u;
            name.setText(u.username());
            info.setText(u.totalWins() + " trận thắng • " + u.status().label());
            avatar.setSeed(u.username());
            refreshButton();
        }

        void refreshButton() {
            if (user.status() != UserStatus.IDLE) {
                button.setText("BẬN");
                button.setEnabled(false);
            } else if (model.isInvited(user.username())) {
                button.setText("ĐÃ MỜI");
                button.setEnabled(false);
            } else {
                button.setText("MỜI");
                button.setEnabled(true);
            }
        }

    }

    /** Avatar tròn: màu theo tên, chữ cái đầu của tên. */
    private static final class Avatar extends JComponent {
        private static final Color[][] PALETTE = {
                {new Color(0x60A5FA), new Color(0x6366F1)}, {new Color(0xF472B6), new Color(0xA855F7)},
                {new Color(0xFBBF24), new Color(0xF97316)}, {new Color(0x34D399), new Color(0x0EA5E9)}
        };
        private String seed = "?";

        Avatar() {
            setPreferredSize(new Dimension(38, 38));
        }

        void setSeed(String seed) {
            this.seed = seed.isEmpty() ? "?" : seed;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color[] colors = PALETTE[Math.floorMod(seed.hashCode(), PALETTE.length)];
            int d = Math.min(getWidth(), getHeight());
            int y = (getHeight() - d) / 2;
            g2.setPaint(new GradientPaint(0, y, colors[0], d, y + d, colors[1]));
            g2.fillOval(0, y, d, d);
            g2.setColor(Color.WHITE);
            g2.setFont(AppTheme.FONT_BOLD.deriveFont(Font.BOLD, 15f));
            String letter = seed.substring(0, 1).toUpperCase(Locale.ROOT);
            int tx = (d - g2.getFontMetrics().stringWidth(letter)) / 2;
            int ty = y + (d - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent();
            g2.drawString(letter, tx, ty);
            g2.dispose();
        }
    }
}
