package quizgame.client.ui.screen;

import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Format;
import quizgame.client.ui.MainFrame;
import quizgame.client.ui.Screen;
import quizgame.client.ui.component.CardPanel;
import quizgame.client.ui.component.SectionTitle;
import quizgame.client.ui.component.StatePanel;
import quizgame.client.ui.component.StyledTable;
import quizgame.client.ui.dialog.MatchDetailDialog;
import quizgame.client.util.Async;
import quizgame.common.model.MatchSummary;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/** Lịch sử các trận của mình; nháy đúp một dòng để xem chi tiết. */
public class HistoryPanel extends JPanel implements Screen {
    private final MainFrame frame;
    private final StyledTable table = new StyledTable("Thời gian", "Phòng", "Bộ câu hỏi", "Hạng của tôi", "Điểm");
    private final StatePanel state;
    private List<MatchSummary> matches = List.of();

    public HistoryPanel(MainFrame frame) {
        super(new BorderLayout(0, AppTheme.GAP_M));
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(AppTheme.GAP_L, AppTheme.GAP_L, AppTheme.GAP_L, AppTheme.GAP_L));
        add(new SectionTitle("Lịch sử trận đấu"), BorderLayout.NORTH);

        table.setRowSelectionAllowed(true);
        table.setSelectionBackground(AppTheme.SOFT);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && row >= 0 && row < matches.size()) {
                    MatchDetailDialog.show(frame, frame.client(), frame.username(), matches.get(row));
                }
            }
        });
        CardPanel card = new CardPanel(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder());
        card.add(table.inScrollPane(), BorderLayout.CENTER);
        state = new StatePanel(card);
        add(state, BorderLayout.CENTER);

        JLabel note = new JLabel("Nháy đúp vào một trận để xem chi tiết.");
        note.setForeground(AppTheme.TEXT_MUTED);
        add(note, BorderLayout.SOUTH);
    }

    @Override
    public void onShow() {
        load();
    }

    private void load() {
        state.showLoading();
        Async.ui(frame.client().getHistory(), result -> {
            matches = result;
            if (result.isEmpty()) {
                state.showEmpty("Bạn chưa chơi trận nào");
                return;
            }
            List<Object[]> rows = new ArrayList<>();
            for (MatchSummary m : result) {
                rows.add(new Object[]{Format.dateTime(m.startedAt()), m.roomName(), m.setName(), m.myRank(),
                        m.myScore()});
            }
            table.setRows(rows);
            state.showContent();
        }, error -> state.showError(error, this::load));
    }
}
