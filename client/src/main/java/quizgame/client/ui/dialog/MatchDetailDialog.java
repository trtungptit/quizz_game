package quizgame.client.ui.dialog;

import quizgame.client.net.GameClient;
import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Format;
import quizgame.client.ui.component.AppButton;
import quizgame.client.ui.component.MatchResultTable;
import quizgame.client.ui.component.StatePanel;
import quizgame.client.util.Async;
import quizgame.common.model.MatchSummary;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;

/** Chi tiết một trận trong lịch sử: tiêu đề + cùng bảng MatchResultTable như màn KẾT QUẢ. */
public class MatchDetailDialog extends JDialog {
    private final GameClient client;
    private final MatchSummary summary;
    private final MatchResultTable table;
    private final StatePanel state;

    private MatchDetailDialog(Frame owner, GameClient client, String me, MatchSummary summary) {
        super(owner, "Chi tiết trận đấu", true);
        this.client = client;
        this.summary = summary;
        table = new MatchResultTable(me);
        state = new StatePanel(table);

        JPanel content = new JPanel(new BorderLayout(0, AppTheme.GAP_S));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel title = new JLabel(summary.roomName() + " • " + summary.setName());
        title.setFont(AppTheme.FONT_TITLE.deriveFont(18f));
        title.setForeground(AppTheme.TITLE);
        JLabel when = new JLabel(Format.dateTime(summary.startedAt()));
        when.setForeground(AppTheme.TEXT_MUTED);
        JPanel top = new JPanel(new BorderLayout());
        top.add(title, BorderLayout.NORTH);
        top.add(when, BorderLayout.CENTER);
        content.add(top, BorderLayout.NORTH);
        content.add(state, BorderLayout.CENTER);

        AppButton close = AppButton.soft("ĐÓNG");
        close.setPreferredSize(new Dimension(110, 40));
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        setPreferredSize(new Dimension(820, 420));
        pack();
        setLocationRelativeTo(owner);
        load();
    }

    /** Mở hộp thoại chi tiết (modal). */
    public static void show(Frame owner, GameClient client, String me, MatchSummary summary) {
        new MatchDetailDialog(owner, client, me, summary).setVisible(true);
    }

    private void load() {
        state.showLoading();
        Async.ui(client.getHistoryDetail(summary.matchId()), result -> {
            table.setResult(result, null);
            state.showContent();
        }, error -> state.showError(error, this::load));
    }
}
