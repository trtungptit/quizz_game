package quizgame.client.ui.dialog;

import com.formdev.flatlaf.FlatClientProperties;
import quizgame.client.net.GameClient;
import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Format;
import quizgame.client.ui.component.AppButton;
import quizgame.client.ui.component.StatePanel;
import quizgame.client.ui.component.StyledTable;
import quizgame.client.util.Async;
import quizgame.common.model.QuestionSetSummary;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Hộp thoại chọn bộ câu hỏi cho phòng: bảng + ô tìm, nút CHỌN / HỦY. */
public class SetPickerDialog extends JDialog {
    private final GameClient client;
    private final StyledTable table = new StyledTable("Tên", "Chủ đề", "Số câu", "Loại", "Người tạo");
    private final StatePanel state = new StatePanel(table.inScrollPane());
    private final JTextField search = new JTextField();
    private final AppButton choose = AppButton.primary("CHỌN");
    private List<QuestionSetSummary> all = List.of();
    private List<QuestionSetSummary> shown = List.of();
    private QuestionSetSummary result;

    private SetPickerDialog(Frame owner, GameClient client) {
        super(owner, "Chọn bộ câu hỏi", true);
        this.client = client;
        JPanel content = new JPanel(new BorderLayout(0, AppTheme.GAP_S));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel heading = new JLabel("CHỌN BỘ CÂU HỎI");
        heading.setFont(AppTheme.FONT_TITLE);
        heading.setForeground(AppTheme.TITLE);
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Tìm theo tên hoặc chủ đề...");
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
        top.add(heading, BorderLayout.NORTH);
        top.add(search, BorderLayout.CENTER);
        content.add(top, BorderLayout.NORTH);
        content.add(state, BorderLayout.CENTER);

        AppButton cancel = AppButton.soft("HỦY");
        cancel.addActionListener(e -> dispose());
        choose.setEnabled(false);
        choose.addActionListener(e -> confirm());
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.add(cancel, BorderLayout.WEST);
        buttons.add(choose, BorderLayout.EAST);
        content.add(buttons, BorderLayout.SOUTH);

        table.setRowSelectionAllowed(true);
        table.setSelectionBackground(AppTheme.SOFT);
        table.getSelectionModel().addListSelectionListener(e -> choose.setEnabled(table.getSelectedRow() >= 0));
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    confirm();
                }
            }
        });
        setContentPane(content);
        setPreferredSize(new Dimension(760, 480));
        pack();
        setLocationRelativeTo(owner);
        load();
    }

    /** Mở hộp thoại; trả về bộ được chọn hoặc null nếu hủy. */
    public static QuestionSetSummary pick(Frame owner, GameClient client) {
        SetPickerDialog dialog = new SetPickerDialog(owner, client);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void load() {
        state.showLoading();
        Async.ui(client.listSets(), sets -> {
            all = sets;
            if (sets.isEmpty()) {
                state.showEmpty("Chưa có bộ câu hỏi nào, hãy tạo bộ ở trang chủ");
            } else {
                applyFilter();
                state.showContent();
            }
        }, error -> state.showError(error, this::load));
    }

    private void applyFilter() {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        shown = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        for (QuestionSetSummary s : all) {
            boolean match = q.isEmpty() || s.name().toLowerCase(Locale.ROOT).contains(q)
                    || (s.topic() != null && s.topic().toLowerCase(Locale.ROOT).contains(q));
            if (match) {
                shown.add(s);
                rows.add(new Object[]{s.name(), s.topic(), s.questionCount(),
                        Format.types(s.types(), s.hasImages()), s.owner()});
            }
        }
        table.setRows(rows);
    }

    private void confirm() {
        int row = table.getSelectedRow();
        if (row >= 0 && row < shown.size()) {
            result = shown.get(row);
            dispose();
        }
    }
}
