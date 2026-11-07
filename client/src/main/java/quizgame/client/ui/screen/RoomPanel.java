package quizgame.client.ui.screen;

import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Dialogs;
import quizgame.client.ui.Format;
import quizgame.client.ui.MainFrame;
import quizgame.client.ui.Screen;
import quizgame.client.ui.component.AppButton;
import quizgame.client.ui.component.CardPanel;
import quizgame.client.ui.component.OnlinePlayersPanel;
import quizgame.client.ui.component.RoundPanel;
import quizgame.client.ui.component.SectionTitle;
import quizgame.client.ui.dialog.SetPickerDialog;
import quizgame.client.util.Async;
import quizgame.common.model.InviteResult;
import quizgame.common.model.QuestionSetSummary;
import quizgame.common.model.RoomMember;
import quizgame.common.model.RoomPhase;
import quizgame.common.model.RoomState;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Phòng thi đấu: thành viên, bộ câu hỏi, cấu hình, nhật ký trạng thái và các nút điều khiển. */
public class RoomPanel extends JPanel implements Screen {
    private static final int MAX_MEMBERS = 5;
    private static final int REMIND_COOLDOWN_MS = 5000;

    private final MainFrame frame;
    private final JLabel roomName = new JLabel();
    private final JLabel codeLabel = new JLabel();
    private final JLabel hostLabel = new JLabel();
    private final JLabel playersTitle = section("NGƯỜI CHƠI");
    private final JPanel playerRows = new JPanel();
    private final JLabel setLabel = new JLabel();
    private final AppButton pickButton = AppButton.soft("CHỌN BỘ CÂU HỎI");
    private final JSpinner numSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
    private final JRadioButton perQuestion = new JRadioButton("Theo từng câu", true);
    private final JRadioButton fixedTime = new JRadioButton("Mỗi câu");
    private final JSpinner secSpinner = new JSpinner(new SpinnerNumberModel(20, 5, 60, 1));
    private final JTextArea log = new JTextArea(5, 20);
    private final JLabel reminder = new JLabel(" ");
    private final AppButton leaveButton = AppButton.soft("RỜI PHÒNG");
    private final AppButton readyButton = AppButton.primary("SẴN SÀNG");
    private final AppButton unreadyButton = AppButton.soft("HỦY SẴN SÀNG");
    private final AppButton startButton = AppButton.primary("BẮT ĐẦU");
    private final OnlinePlayersPanel online;
    private final Timer remindTimer;
    private final Timer flashTimer;

    private RoomState room;
    private boolean updating;
    private boolean remindCooldown;
    private int flashCount;

    public RoomPanel(MainFrame frame) {
        super(new BorderLayout(AppTheme.GAP_L, 0));
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(AppTheme.GAP_L, AppTheme.GAP_L, AppTheme.GAP_L, AppTheme.GAP_L));

        remindTimer = new Timer(REMIND_COOLDOWN_MS, e -> {
            remindCooldown = false;
            refreshButtons();
        });
        remindTimer.setRepeats(false);
        flashTimer = new Timer(300, e -> flash());

        JPanel left = new JPanel(new BorderLayout(0, AppTheme.GAP_S));
        left.setOpaque(false);
        left.add(new SectionTitle("Phòng thi đấu"), BorderLayout.NORTH);
        left.add(buildMain(), BorderLayout.CENTER);
        add(left, BorderLayout.CENTER);

        online = new OnlinePlayersPanel(frame, "MỜI ĐỐI THỦ ONLINE");
        online.setPreferredSize(new Dimension(320, 100));
        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(320, 100));
        right.add(online, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    // ---------------------------------------------------------------- Dựng giao diện

    private JPanel buildMain() {
        CardPanel card = new CardPanel(new BorderLayout(0, AppTheme.GAP_S));
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        roomName.setFont(AppTheme.FONT_TITLE);
        roomName.setForeground(AppTheme.TITLE);
        addRow(body, roomName);

        JPanel info = new JPanel(new GridLayout(1, 2, AppTheme.GAP_M, 0));
        info.setOpaque(false);
        AppButton copy = AppButton.soft("SAO CHÉP");
        copy.setPreferredSize(new Dimension(96, 26));
        copy.setFont(AppTheme.FONT_BOLD.deriveFont(11f));
        copy.addActionListener(e -> copyCode());
        JPanel codeBox = box(new BorderLayout(AppTheme.GAP_S, 0));
        codeLabel.setFont(AppTheme.FONT_BOLD);
        JPanel codeLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        codeLeft.setOpaque(false);
        codeLeft.add(codeLabel);
        codeLeft.add(copy);
        codeBox.add(codeLeft, BorderLayout.CENTER);
        JPanel hostBox = box(new BorderLayout());
        hostLabel.setFont(AppTheme.FONT_BASE);
        hostBox.add(hostLabel, BorderLayout.CENTER);
        info.add(codeBox);
        info.add(hostBox);
        info.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        addRow(body, info);

        addRow(body, playersTitle);
        playerRows.setOpaque(false);
        playerRows.setLayout(new BoxLayout(playerRows, BoxLayout.Y_AXIS));
        addRow(body, playerRows);

        addRow(body, section("BỘ CÂU HỎI ĐÃ CHỌN"));
        JPanel setBox = box(new BorderLayout(AppTheme.GAP_S, 0));
        setLabel.setFont(AppTheme.FONT_BASE);
        pickButton.setPreferredSize(new Dimension(210, 32));
        pickButton.setFont(AppTheme.FONT_BOLD.deriveFont(12f));
        pickButton.addActionListener(e -> pickSet());
        setBox.add(setLabel, BorderLayout.CENTER);
        setBox.add(pickButton, BorderLayout.EAST);
        setBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        addRow(body, setBox);

        JPanel config = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        config.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        group.add(perQuestion);
        group.add(fixedTime);
        perQuestion.setOpaque(false);
        fixedTime.setOpaque(false);
        numSpinner.setPreferredSize(new Dimension(70, 32));
        secSpinner.setPreferredSize(new Dimension(70, 32));
        config.add(new JLabel("Số câu"));
        config.add(numSpinner);
        config.add(Box.createHorizontalStrut(10));
        config.add(new JLabel("Thời gian:"));
        config.add(perQuestion);
        config.add(fixedTime);
        config.add(secSpinner);
        config.add(new JLabel("giây"));
        numSpinner.addChangeListener(e -> sendConfig());
        secSpinner.addChangeListener(e -> sendConfig());
        perQuestion.addActionListener(e -> {
            secSpinner.setEnabled(false);
            sendConfig();
        });
        fixedTime.addActionListener(e -> {
            secSpinner.setEnabled(true);
            sendConfig();
        });
        addRow(body, config);

        addRow(body, section("TRẠNG THÁI"));
        log.setRows(3);
        log.setEditable(false);
        log.setBackground(Color.WHITE);
        log.setLineWrap(true);
        log.setFont(AppTheme.FONT_BASE.deriveFont(13f));
        log.setMargin(new java.awt.Insets(6, 10, 6, 10));
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER, 1, true));
        logScroll.setPreferredSize(new Dimension(100, 66));
        logScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        addRow(body, logScroll);

        reminder.setForeground(AppTheme.ORANGE);
        reminder.setFont(AppTheme.FONT_BOLD);
        addRow(body, reminder);

        JScrollPane scroll = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        card.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.setOpaque(false);
        leaveButton.addActionListener(e -> frame.leaveRoom());
        readyButton.addActionListener(e -> onReadyClicked());
        unreadyButton.addActionListener(e -> sendReady(false));
        startButton.addActionListener(e -> start());
        addWeighted(buttons, leaveButton, 1.0, 0);
        addWeighted(buttons, readyButton, 1.0, AppTheme.GAP_M);
        addWeighted(buttons, unreadyButton, 0.6, AppTheme.GAP_M);
        addWeighted(buttons, startButton, 1.0, AppTheme.GAP_M);
        buttons.setPreferredSize(new Dimension(100, 44));
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    /** Thêm nút vào hàng nút với tỉ lệ chiều rộng (nút phụ hẹp hơn nút chính). */
    private static void addWeighted(JPanel row, javax.swing.JComponent button, double weight, int gapLeft) {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = weight;
        c.weighty = 1;
        c.insets = new Insets(0, gapLeft, 0, 0);
        row.add(button, c);
    }

    private static RoundPanel box(java.awt.LayoutManager layout) {
        RoundPanel panel = new RoundPanel(layout, AppTheme.ROW_BG, 14);
        panel.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));
        return panel;
    }

    private static JLabel section(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppTheme.FONT_BOLD.deriveFont(14f));
        l.setForeground(AppTheme.SOFT_TEXT);
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
        return l;
    }

    private static void addRow(JPanel parent, javax.swing.JComponent c) {
        c.setAlignmentX(LEFT_ALIGNMENT);
        parent.add(c);
        parent.add(Box.createVerticalStrut(2));
    }

    // ---------------------------------------------------------------- Vòng đời

    @Override
    public void onShow() {
        online.attach();
        if (room == null && frame.currentRoom() != null) {
            render(frame.currentRoom());
        }
    }

    @Override
    public void onHide() {
        online.detach();
        remindCooldown = false;
        remindTimer.stop();
        flashTimer.stop();
    }

    /** Xóa trạng thái khi rời phòng. */
    public void reset() {
        room = null;
        log.setText("");
        reminder.setText(" ");
        remindCooldown = false;
        remindTimer.stop();
        flashTimer.stop();
    }

    // ---------------------------------------------------------------- Vẽ lại từ RoomState

    /** Vẽ lại toàn bộ phòng từ RoomState mới nhất (gọi trên luồng giao diện). */
    public void render(RoomState next) {
        List<String> lines = describeChange(room, next);
        room = next;
        updating = true;
        String me = frame.username();
        boolean host = next.host().equals(me);
        boolean waiting = next.phase() == RoomPhase.WAITING;

        roomName.setText("PHÒNG: " + next.name().toUpperCase(Locale.ROOT));
        codeLabel.setText("Mã phòng: " + next.code());
        hostLabel.setText("Chủ phòng: " + next.host());
        playersTitle.setText("NGƯỜI CHƠI (" + next.members().size() + "/" + MAX_MEMBERS + ")");
        buildPlayerRows(next, host && waiting);

        QuestionSetSummary set = next.set();
        setLabel.setText(set == null ? "Chưa chọn bộ câu hỏi"
                : set.name() + " • " + Format.setLine(set));
        pickButton.setText(set == null ? "CHỌN BỘ CÂU HỎI" : "CHỌN LẠI BỘ CÂU HỎI");
        pickButton.setVisible(host);
        pickButton.setEnabled(waiting);

        int max = set == null ? 1 : Math.max(1, set.questionCount());
        numSpinner.setModel(new SpinnerNumberModel(Math.max(1, Math.min(next.numQuestions(), max)), 1, max, 1));
        boolean canEdit = host && waiting && set != null;
        numSpinner.setEnabled(canEdit);
        boolean fixed = next.timeOverrideSeconds() != null;
        perQuestion.setSelected(!fixed);
        fixedTime.setSelected(fixed);
        if (fixed) {
            secSpinner.setValue(Math.max(5, Math.min(60, next.timeOverrideSeconds())));
        }
        perQuestion.setEnabled(host && waiting);
        fixedTime.setEnabled(host && waiting);
        secSpinner.setEnabled(host && waiting && fixed);

        updating = false;
        refreshButtons();
        lines.forEach(this::addLog);
        revalidate();
        repaint();
    }

    private void buildPlayerRows(RoomState r, boolean canKick) {
        playerRows.removeAll();
        String me = frame.username();
        for (int i = 0; i < MAX_MEMBERS; i++) {
            JPanel row = box(new BorderLayout(AppTheme.GAP_S, 0));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 33));
            row.setPreferredSize(new Dimension(100, 33));
            if (i < r.members().size()) {
                RoomMember m = r.members().get(i);
                JPanel nameBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                nameBox.setOpaque(false);
                JLabel name = new JLabel((i + 1) + ". " + m.username() + (m.username().equals(me) ? " (bạn)" : ""));
                name.setFont(AppTheme.FONT_BOLD);
                nameBox.add(name);
                if (m.username().equals(r.host())) {
                    nameBox.add(new Badge("CHỦ PHÒNG", AppTheme.PRIMARY, Color.WHITE));
                }
                JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                right.setOpaque(false);
                right.add(m.ready() ? new Badge("SẴN SÀNG", AppTheme.CORRECT, Color.WHITE)
                        : new Badge("CHƯA", AppTheme.SOFT, AppTheme.SOFT_TEXT));
                if (canKick && !m.username().equals(me)) {
                    AppButton kick = AppButton.soft("KICK");
                    kick.setPreferredSize(new Dimension(60, 28));
                    kick.setFont(AppTheme.FONT_BOLD.deriveFont(11f));
                    kick.addActionListener(e -> kick(m.username()));
                    right.add(kick);
                }
                row.add(nameBox, BorderLayout.CENTER);
                row.add(right, BorderLayout.EAST);
            } else {
                JLabel empty = new JLabel((i + 1) + ". (Trống)");
                empty.setForeground(AppTheme.TEXT_MUTED);
                row.add(empty, BorderLayout.CENTER);
            }
            row.setAlignmentX(LEFT_ALIGNMENT);
            playerRows.add(row);
            playerRows.add(Box.createVerticalStrut(3));
        }
    }

    // ---------------------------------------------------------------- Điều kiện nút

    /** Chủ phòng chỉ bắt đầu được khi đang chờ, mọi người sẵn sàng, có >= 2 người và đã chọn bộ câu hỏi. */
    public static boolean canStart(RoomState r) {
        return r.phase() == RoomPhase.WAITING && r.set() != null && r.members().size() >= 2
                && r.members().stream().allMatch(RoomMember::ready);
    }

    private void refreshButtons() {
        if (room == null) {
            return;
        }
        String me = frame.username();
        boolean waiting = room.phase() == RoomPhase.WAITING;
        boolean host = room.host().equals(me);
        RoomMember mine = room.members().stream().filter(m -> m.username().equals(me)).findFirst().orElse(null);
        boolean ready = mine != null && mine.ready();
        boolean othersNotReady = room.members().stream().anyMatch(m -> !m.ready());

        leaveButton.setEnabled(true);
        if (!waiting || mine == null) {
            readyButton.setText("SẴN SÀNG");
            readyButton.setEnabled(false);
        } else if (!ready) {
            readyButton.setText("SẴN SÀNG");
            readyButton.setEnabled(true);
        } else if (othersNotReady) {
            readyButton.setText("NHẮC MỌI NGƯỜI");
            readyButton.setEnabled(!remindCooldown);
        } else {
            readyButton.setText("ĐÃ SẴN SÀNG");
            readyButton.setEnabled(false);
        }
        unreadyButton.setVisible(waiting && ready);
        startButton.setVisible(host);
        startButton.setEnabled(host && canStart(room));
    }

    // ---------------------------------------------------------------- Hành động

    private void onReadyClicked() {
        if (room == null) {
            return;
        }
        String me = frame.username();
        boolean ready = room.members().stream().anyMatch(m -> m.username().equals(me) && m.ready());
        if (!ready) {
            sendReady(true);
            return;
        }
        remindCooldown = true;
        remindTimer.restart();
        refreshButtons();
        Async.ui(frame.client().remindReady(), v -> { }, error -> {
            remindCooldown = false;
            remindTimer.stop();
            refreshButtons();
            Dialogs.error(frame, error);
        });
    }

    private void sendReady(boolean ready) {
        Async.ui(frame.client().setReady(ready), v -> { }, error -> Dialogs.error(frame, error));
    }

    private void start() {
        Async.ui(frame.client().startGame(), v -> { }, error -> Dialogs.error(frame, error));
    }

    private void kick(String username) {
        if (Dialogs.confirm(frame, "Mời " + username + " ra khỏi phòng?")) {
            Async.ui(frame.client().kick(username), v -> { }, error -> Dialogs.error(frame, error));
        }
    }

    private void copyCode() {
        if (room != null) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(room.code()), null);
            addLog("Đã sao chép mã phòng " + room.code());
        }
    }

    private void pickSet() {
        QuestionSetSummary picked = SetPickerDialog.pick(frame, frame.client());
        if (picked == null || room == null) {
            return;
        }
        Integer override = fixedTime.isSelected() ? ((Number) secSpinner.getValue()).intValue() : null;
        // Lần đầu chọn bộ: mặc định tối đa 10 câu. Chọn lại: giữ số chủ phòng đã chỉnh, cắt theo bộ mới
        int num = room.set() == null ? Math.min(10, picked.questionCount())
                : Math.max(1, Math.min(room.numQuestions(), picked.questionCount()));
        Async.ui(frame.client().configRoom(picked.id(), num, override), v -> { },
                error -> Dialogs.error(frame, error));
    }

    /** Mọi thay đổi Số câu / Thời gian đều gửi configRoom (chỉ chủ phòng). */
    private void sendConfig() {
        if (updating || room == null || room.set() == null || !room.host().equals(frame.username())) {
            return;
        }
        int num = ((Number) numSpinner.getValue()).intValue();
        Integer override = fixedTime.isSelected() ? ((Number) secSpinner.getValue()).intValue() : null;
        Async.ui(frame.client().configRoom(room.set().id(), num, override), v -> { },
                error -> Dialogs.error(frame, error));
    }

    // ---------------------------------------------------------------- Sự kiện chuyển tiếp từ MainFrame

    /** Có người nhắc sẵn sàng. */
    public void onReadyReminder(String fromUser) {
        addLog(fromUser + " nhắc bạn sẵn sàng");
        reminder.setText("Đồng đội của bạn nhắc bạn sẵn sàng");
        flashCount = 0;
        flashTimer.restart();
    }

    /** Kết cục một lời mời mình đã gửi. */
    public void onInviteResult(String toUser, InviteResult result) {
        addLog(toUser + " • " + result.label());
    }

    private void flash() {
        flashCount++;
        boolean on = flashCount % 2 == 1;
        readyButton.setColors(on ? AppTheme.ACCENT : AppTheme.PRIMARY, on ? AppTheme.TITLE : Color.WHITE);
        if (flashCount >= 8) {
            flashTimer.stop();
            reminder.setText(" ");
            readyButton.setColors(AppTheme.PRIMARY, Color.WHITE);
        }
    }

    // ---------------------------------------------------------------- Nhật ký

    private void addLog(String line) {
        log.insert(line + "\n", 0); // dòng mới ở trên
        log.setCaretPosition(0);
    }

    /** Dựng các dòng nhật ký mô tả sự khác nhau giữa hai RoomState (prev = null là lần đầu vào phòng). */
    public static List<String> describeChange(RoomState prev, RoomState next) {
        List<String> lines = new ArrayList<>();
        if (prev == null) {
            lines.add("Bạn đã vào phòng " + next.name());
            next.pendingInvitees().forEach(p -> lines.add("Đã mời: " + p + " • Đang chờ phản hồi..."));
            return lines;
        }
        for (RoomMember m : next.members()) {
            RoomMember before = prev.members().stream().filter(x -> x.username().equals(m.username())).findFirst()
                    .orElse(null);
            if (before == null) {
                lines.add(m.username() + " đã vào phòng");
            } else if (before.ready() != m.ready()) {
                lines.add(m.username() + " • " + (m.ready() ? "Sẵn sàng" : "Hủy sẵn sàng"));
            }
        }
        for (RoomMember m : prev.members()) {
            if (next.members().stream().noneMatch(x -> x.username().equals(m.username()))) {
                lines.add(m.username() + " đã rời phòng");
            }
        }
        for (String p : next.pendingInvitees()) {
            if (!prev.pendingInvitees().contains(p)) {
                lines.add("Đã mời: " + p + " • Đang chờ phản hồi...");
            }
        }
        if (!next.host().equals(prev.host())) {
            lines.add(next.host() + " là chủ phòng mới");
        }
        Long before = prev.set() == null ? null : prev.set().id();
        Long after = next.set() == null ? null : next.set().id();
        if (after != null && !after.equals(before)) {
            lines.add("Đã chọn bộ câu hỏi: " + next.set().name());
        }
        if (prev.phase() == RoomPhase.PLAYING && next.phase() == RoomPhase.WAITING) {
            lines.add("Trận đã kết thúc, mọi người cần sẵn sàng lại");
        }
        return lines;
    }

    // ---------------------------------------------------------------- Thành phần nhỏ

    /** Huy hiệu chữ nhỏ bo tròn. */
    private static final class Badge extends JLabel {
        private final Color bg;

        Badge(String text, Color bg, Color fg) {
            super(text);
            this.bg = bg;
            setForeground(fg);
            setFont(AppTheme.FONT_BOLD.deriveFont(11f));
            setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
