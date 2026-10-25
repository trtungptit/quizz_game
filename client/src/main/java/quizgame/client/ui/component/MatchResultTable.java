package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Format;
import quizgame.common.model.MatchResult;
import quizgame.common.model.MatchRow;
import quizgame.common.model.PlayerStatus;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Bảng kết quả trận (dùng ở màn KẾT QUẢ và hộp thoại chi tiết lịch sử); tô vàng dòng của mình. */
public class MatchResultTable extends JPanel {
    private final String me;
    private final StyledTable table = new StyledTable("Hạng", "Người chơi", "Điểm", "Số câu đúng",
            "Thời gian giải xong", "Ghi chú");
    private List<MatchRow> rows = List.of();

    public MatchResultTable(String me) {
        super(new BorderLayout());
        this.me = me;
        setOpaque(false);
        table.setCellStyle(new StyledTable.CellStyle() {
            @Override
            public Color background(int row, int col) {
                return isMe(row) ? AppTheme.HIGHLIGHT : null;
            }

            @Override
            public boolean bold(int row, int col) {
                return isMe(row);
            }
        });
        add(table.inScrollPane(), BorderLayout.CENTER);
    }

    private boolean isMe(int row) {
        return row < rows.size() && rows.get(row).username().equals(me);
    }

    /** Dựng bảng; totalQuestions = số câu của trận (null nếu không biết thì chỉ hiện số câu đúng). */
    public void setResult(MatchResult result, Integer totalQuestions) {
        rows = result.rows();
        List<Object[]> data = new ArrayList<>();
        for (MatchRow r : rows) {
            boolean finished = r.status() == PlayerStatus.FINISHED;
            data.add(new Object[]{
                    r.rank(),
                    r.username() + (r.username().equals(me) ? " (bạn)" : ""),
                    r.score(),
                    totalQuestions == null ? String.valueOf(r.correctCount()) : r.correctCount() + "/" + totalQuestions,
                    finished ? Format.duration(r.finishTimeMs()) : "—",
                    note(r.status())});
        }
        table.setRows(data);
    }

    private static String note(PlayerStatus status) {
        return switch (status) {
            case QUIT -> "Bỏ cuộc";
            case DISCONNECTED -> "Mất kết nối";
            case FINISHED -> "";
        };
    }
}
