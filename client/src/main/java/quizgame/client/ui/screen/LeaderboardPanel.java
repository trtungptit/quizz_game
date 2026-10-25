package quizgame.client.ui.screen;

import quizgame.client.ui.AppTheme;
import quizgame.client.ui.MainFrame;
import quizgame.client.ui.Screen;
import quizgame.client.ui.component.AppButton;
import quizgame.client.ui.component.CardPanel;
import quizgame.client.ui.component.SectionTitle;
import quizgame.client.ui.component.StatePanel;
import quizgame.client.ui.component.StyledTable;
import quizgame.client.util.Async;
import quizgame.common.model.LeaderboardRow;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/** Bảng xếp hạng toàn hệ thống: 3 hạng đầu vàng/bạc/đồng, dòng của mình tô đậm. */
public class LeaderboardPanel extends JPanel implements Screen {
    private final MainFrame frame;
    private final StyledTable table = new StyledTable("Hạng", "Tên", "Số trận thắng", "Tổng điểm");
    private final StatePanel state;
    private final AppButton refresh = AppButton.soft("LÀM MỚI");
    /** Hàng hiển thị; null ở vị trí dòng '...'. */
    private List<LeaderboardRow> shown = List.of();

    public LeaderboardPanel(MainFrame frame) {
        super(new BorderLayout(0, AppTheme.GAP_M));
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(AppTheme.GAP_L, AppTheme.GAP_L, AppTheme.GAP_L, AppTheme.GAP_L));

        refresh.setPreferredSize(new Dimension(110, 38));
        refresh.addActionListener(e -> load());
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(new SectionTitle("Xếp hạng toàn hệ thống"), BorderLayout.WEST);
        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(refresh, BorderLayout.NORTH);
        head.add(east, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);

        table.setCellStyle(new StyledTable.CellStyle() {
            @Override
            public Color background(int row, int col) {
                if (isMine(row)) {
                    return AppTheme.HIGHLIGHT;
                }
                if (col == 0 && row < shown.size() && shown.get(row) != null) {
                    return switch (shown.get(row).rank()) {
                        case 1 -> AppTheme.GOLD;
                        case 2 -> AppTheme.SILVER;
                        case 3 -> AppTheme.BRONZE;
                        default -> null;
                    };
                }
                return null;
            }

            @Override
            public boolean bold(int row, int col) {
                return isMine(row) || (col == 0 && row < shown.size() && shown.get(row) != null
                        && shown.get(row).rank() <= 3);
            }
        });
        CardPanel card = new CardPanel(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder());
        card.add(table.inScrollPane(), BorderLayout.CENTER);
        state = new StatePanel(card);
        add(state, BorderLayout.CENTER);

        JLabel note = new JLabel("Xếp theo số trận thắng, bằng nhau thì theo tổng điểm. "
                + "Hiện top 50 và luôn có dòng của bạn.");
        note.setForeground(AppTheme.TEXT_MUTED);
        add(note, BorderLayout.SOUTH);
    }

    private boolean isMine(int row) {
        return row < shown.size() && shown.get(row) != null && shown.get(row).username().equals(frame.username());
    }

    @Override
    public void onShow() {
        load();
    }

    private void load() {
        state.showLoading();
        refresh.setEnabled(false);
        Async.ui(frame.client().getLeaderboard(), rows -> {
            refresh.setEnabled(true);
            render(rows);
        }, error -> {
            refresh.setEnabled(true);
            state.showError(error, this::load);
        });
    }

    private void render(List<LeaderboardRow> rows) {
        if (rows.isEmpty()) {
            state.showEmpty("Chưa có ai trên bảng xếp hạng");
            return;
        }
        shown = withGap(rows);
        List<Object[]> data = new ArrayList<>();
        for (LeaderboardRow r : shown) {
            if (r == null) {
                data.add(new Object[]{"", "...", "", ""});
            } else {
                data.add(new Object[]{r.rank(), r.username() + (r.username().equals(frame.username()) ? " (bạn)" : ""),
                        r.totalWins(), r.totalScore()});
            }
        }
        table.setRows(data);
        state.showContent();
    }

    /** Chèn null (dòng '...') trước dòng cuối nếu hạng của nó không liền kề dòng trước. */
    public static List<LeaderboardRow> withGap(List<LeaderboardRow> rows) {
        List<LeaderboardRow> out = new ArrayList<>(rows);
        int n = out.size();
        if (n >= 2 && out.get(n - 1).rank() != out.get(n - 2).rank() + 1) {
            out.add(n - 1, null);
        }
        return out;
    }
}
