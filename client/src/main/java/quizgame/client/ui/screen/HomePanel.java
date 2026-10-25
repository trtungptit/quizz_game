package quizgame.client.ui.screen;

import com.formdev.flatlaf.FlatClientProperties;
import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Dialogs;
import quizgame.client.ui.MainFrame;
import quizgame.client.ui.Screen;
import quizgame.client.ui.component.AppButton;
import quizgame.client.ui.component.GradientPanel;
import quizgame.client.ui.component.OnlinePlayersPanel;
import quizgame.client.ui.component.OnlineUsersModel;
import quizgame.client.ui.component.QuestionSetCard;
import quizgame.client.ui.component.SectionTitle;
import quizgame.client.ui.component.StatePanel;
import quizgame.client.util.Async;
import quizgame.common.model.QuestionSetSummary;
import quizgame.common.model.UserInfo;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Trang chủ: khám phá bộ câu hỏi (trái) và người chơi online (phải). */
public class HomePanel extends JPanel implements Screen {
    private final MainFrame frame;
    private final JTextField search = new JTextField();
    private final JCheckBox onlyMine = new JCheckBox("Chỉ bộ của tôi");
    private final JPanel grid = new JPanel(new GridLayout(0, 2, AppTheme.GAP_M, AppTheme.GAP_M));
    private final StatePanel state;
    private final JLabel selfName = new JLabel();
    private final JLabel selfWins = new JLabel();
    private final OnlinePlayersPanel online;
    private List<QuestionSetSummary> sets = List.of();
    private boolean busy;

    private final OnlineUsersModel.Listener selfListener = new OnlineUsersModel.Listener() {
        @Override
        public void onSelfChanged() {
            refreshSelf();
        }
    };

    public HomePanel(MainFrame frame) {
        super(new BorderLayout(AppTheme.GAP_L, 0));
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(AppTheme.GAP_L, AppTheme.GAP_L, AppTheme.GAP_L, AppTheme.GAP_L));

        // ----- Cột trái
        JPanel gridHolder = new JPanel(new BorderLayout());
        gridHolder.setOpaque(false);
        grid.setOpaque(false);
        gridHolder.add(grid, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(gridHolder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        state = new StatePanel(scroll);

        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Tìm bộ câu hỏi...");
        search.setPreferredSize(new Dimension(100, 40));
        search.setMargin(new java.awt.Insets(6, 12, 6, 12));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                renderSets();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                renderSets();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                renderSets();
            }
        });
        AppButton createSet = AppButton.soft("TẠO BỘ CÂU HỎI");
        createSet.setPreferredSize(new Dimension(190, 40));
        createSet.addActionListener(e -> frame.openSetEditor(null));
        JPanel searchRow = new JPanel(new BorderLayout(AppTheme.GAP_M, 0));
        searchRow.setOpaque(false);
        searchRow.add(search, BorderLayout.CENTER);
        searchRow.add(createSet, BorderLayout.EAST);
        onlyMine.setOpaque(false);
        onlyMine.addActionListener(e -> renderSets());

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new javax.swing.BoxLayout(head, javax.swing.BoxLayout.Y_AXIS));
        SectionTitle title = new SectionTitle("Khám phá bộ câu hỏi");
        title.setAlignmentX(LEFT_ALIGNMENT);
        searchRow.setAlignmentX(LEFT_ALIGNMENT);
        onlyMine.setAlignmentX(LEFT_ALIGNMENT);
        head.add(title);
        head.add(javax.swing.Box.createVerticalStrut(AppTheme.GAP_M));
        head.add(searchRow);
        head.add(javax.swing.Box.createVerticalStrut(AppTheme.GAP_S));
        head.add(onlyMine);
        head.add(javax.swing.Box.createVerticalStrut(AppTheme.GAP_S));

        AppButton createRoom = new AppButton("TẠO PHÒNG THI ĐẤU", AppTheme.ACCENT, AppTheme.TITLE);
        createRoom.setFont(AppTheme.FONT_BOLD.deriveFont(17f));
        createRoom.setPreferredSize(new Dimension(100, 56));
        createRoom.addActionListener(e -> createRoomOnly());

        JPanel left = new JPanel(new BorderLayout(0, AppTheme.GAP_S));
        left.setOpaque(false);
        left.add(head, BorderLayout.NORTH);
        left.add(state, BorderLayout.CENTER);
        left.add(createRoom, BorderLayout.SOUTH);
        add(left, BorderLayout.CENTER);

        // ----- Cột phải
        online = new OnlinePlayersPanel(frame, "NGƯỜI CHƠI ONLINE");
        JPanel right = new JPanel(new BorderLayout(0, AppTheme.GAP_S));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(320, 100));
        right.add(buildSelfBox(), BorderLayout.NORTH);
        right.add(online, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    private JPanel buildSelfBox() {
        JPanel box = GradientPanel.diagonal(new BorderLayout(0, 6), AppTheme.ARC_CARD * 2);
        box.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        selfName.setFont(AppTheme.FONT_TITLE.deriveFont(17f));
        selfName.setForeground(java.awt.Color.WHITE);
        selfWins.setFont(AppTheme.FONT_BASE);
        selfWins.setForeground(java.awt.Color.WHITE);
        box.add(selfName, BorderLayout.NORTH);
        box.add(selfWins, BorderLayout.CENTER);
        return box;
    }

    // ---------------------------------------------------------------- Vòng đời

    @Override
    public void onShow() {
        frame.onlineUsers().addListener(selfListener);
        online.attach();
        refreshSelf();
        loadSets();
    }

    @Override
    public void onHide() {
        frame.onlineUsers().removeListener(selfListener);
        online.detach();
    }

    private void refreshSelf() {
        UserInfo self = frame.onlineUsers().self();
        if (self == null) {
            selfName.setText(" ");
            selfWins.setText(" ");
            return;
        }
        selfName.setText(self.username() + " • " + self.status().label().toUpperCase(Locale.ROOT));
        selfWins.setText("Tổng số trận thắng: " + self.totalWins());
    }

    // ---------------------------------------------------------------- Bộ câu hỏi

    private void loadSets() {
        state.showLoading();
        Async.ui(frame.client().listSets(), result -> {
            sets = result;
            renderSets();
        }, error -> state.showError(error, this::loadSets));
    }

    private void renderSets() {
        if (sets.isEmpty()) {
            state.showEmpty("Chưa có bộ câu hỏi nào, bấm TẠO BỘ CÂU HỎI");
            return;
        }
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        String me = frame.username();
        List<QuestionSetSummary> shown = new ArrayList<>();
        for (QuestionSetSummary s : sets) {
            boolean match = q.isEmpty() || s.name().toLowerCase(Locale.ROOT).contains(q)
                    || (s.topic() != null && s.topic().toLowerCase(Locale.ROOT).contains(q));
            if (match && (!onlyMine.isSelected() || s.owner().equals(me))) {
                shown.add(s);
            }
        }
        if (shown.isEmpty()) {
            state.showEmpty("Không có bộ câu hỏi nào phù hợp");
            return;
        }
        grid.removeAll();
        for (QuestionSetSummary s : shown) {
            boolean mine = s.owner().equals(me);
            grid.add(new QuestionSetCard(s, mine, () -> play(s), () -> frame.openSetEditor(s.id()), () -> delete(s)));
        }
        // Số thẻ lẻ: thêm ô trống để thẻ cuối không bị kéo giãn hết hàng
        if (shown.size() % 2 == 1) {
            JPanel filler = new JPanel();
            filler.setOpaque(false);
            grid.add(filler);
        }
        grid.revalidate();
        grid.repaint();
        state.showContent();
    }

    private void delete(QuestionSetSummary set) {
        if (!Dialogs.confirm(frame, "Xóa bộ câu hỏi \"" + set.name() + "\"?")) {
            return;
        }
        Async.ui(frame.client().deleteSet(set.id()), v -> loadSets(), error -> Dialogs.error(frame, error));
    }

    // ---------------------------------------------------------------- Tạo phòng

    private void play(QuestionSetSummary set) {
        enterRoom(set);
    }

    private void createRoomOnly() {
        enterRoom(null);
    }

    /** Hỏi tên phòng rồi tạo phòng (kèm cấu hình bộ câu hỏi nếu có) và vào ROOM. */
    private void enterRoom(QuestionSetSummary set) {
        String name = Dialogs.ask(frame, "Tên phòng:", frame.defaultRoomName());
        if (name == null || busy) {
            return;
        }
        busy = true;
        frame.createRoomAndEnter(name, set, room -> busy = false, () -> busy = false);
    }
}
