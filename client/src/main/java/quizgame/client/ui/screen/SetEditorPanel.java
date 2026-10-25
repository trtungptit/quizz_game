package quizgame.client.ui.screen;

import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Dialogs;
import quizgame.client.ui.MainFrame;
import quizgame.client.ui.Screen;
import quizgame.client.ui.component.AppButton;
import quizgame.client.ui.component.CardPanel;
import quizgame.client.ui.component.QuestionEditor;
import quizgame.client.ui.component.StatePanel;
import quizgame.client.util.Async;
import quizgame.client.util.CsvImporter;
import quizgame.client.util.CsvTemplate;
import quizgame.client.util.DraftValidator;
import quizgame.client.util.ImportResult;
import quizgame.common.model.QuestionDraft;
import quizgame.common.model.QuestionSet;
import quizgame.common.model.QuestionType;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** Soạn / sửa bộ câu hỏi: Bước 1 (thông tin + chọn loại câu) và Bước 2 (sửa từng câu). */
public class SetEditorPanel extends JPanel implements Screen {
    private static final String STEP1 = "STEP1";
    private static final String STEP2 = "STEP2";

    private final MainFrame frame;
    private final JTextField nameField = new JTextField();
    private final JTextField topicField = new JTextField();
    private final JTextField descField = new JTextField();
    private final JPanel top = new JPanel(new BorderLayout());
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel center = new JPanel(centerLayout);
    private final QuestionEditor editor;
    private final JPanel numbers = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JLabel emptyHint = new JLabel("(chưa có câu hỏi nào)");
    private final JScrollPane numbersScroll;
    private final AppButton finish = AppButton.primary("HOÀN THÀNH");
    private final JPanel content = new JPanel(new BorderLayout(0, AppTheme.GAP_M));
    private final StatePanel state;

    private final List<QuestionDraft> drafts = new ArrayList<>();
    private Long editingId;
    private int selected = -1;
    private boolean dirty;
    private boolean loading;
    private boolean saving;

    public SetEditorPanel(MainFrame frame) {
        super(new BorderLayout(0, AppTheme.GAP_M));
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(AppTheme.GAP_M, AppTheme.GAP_L, AppTheme.GAP_M, AppTheme.GAP_L));

        DocumentListener markDirty = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                touch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                touch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                touch();
            }
        };
        for (JTextField f : new JTextField[]{nameField, topicField, descField}) {
            f.getDocument().addDocumentListener(markDirty);
            f.setMargin(new Insets(6, 12, 6, 12));
        }

        content.setOpaque(false);
        top.setOpaque(false);
        content.add(top, BorderLayout.NORTH);

        editor = new QuestionEditor(this::deleteCurrent, draft -> {
            if (selected >= 0 && selected < drafts.size()) {
                drafts.set(selected, draft);
                touch();
                refreshNumbers();
            }
        });
        center.setOpaque(false);
        center.add(buildChoices(), STEP1);
        center.add(editor, STEP2);
        content.add(center, BorderLayout.CENTER);

        numbers.setOpaque(false);
        numbersScroll = new JScrollPane(numbers, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        numbersScroll.setBorder(null);
        numbersScroll.setOpaque(false);
        numbersScroll.getViewport().setOpaque(false);
        numbersScroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 8));
        content.add(buildBottomBar(), BorderLayout.SOUTH);
        state = new StatePanel(content);
        add(state, BorderLayout.CENTER);
    }

    private void touch() {
        if (!loading) {
            dirty = true;
        }
    }

    // ---------------------------------------------------------------- Dựng giao diện

    private JPanel buildChoices() {
        JPanel panel = new JPanel(new GridLayout(1, 5, AppTheme.GAP_M, 0));
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(100, 240));
        panel.add(new ChoiceCard("Buttons", "Câu hỏi 1 đáp án", () -> addQuestion(QuestionType.SINGLE)));
        panel.add(new ChoiceCard("Checkboxes", "Câu hỏi nhiều đáp án", () -> addQuestion(QuestionType.MULTI)));
        panel.add(new ChoiceCard("Type answer", "Thuật ngữ, từ khóa", () -> addQuestion(QuestionType.TYPE)));
        panel.add(new ChoiceCard("Download Template", "Tải file CSV mẫu", this::downloadTemplate));
        panel.add(new ChoiceCard("Import file", "Nhập từ file CSV", this::importFile));
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(panel, BorderLayout.NORTH);
        return holder;
    }

    private JPanel buildBottomBar() {
        CardPanel bar = new CardPanel(new BorderLayout(AppTheme.GAP_M, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        finish.setPreferredSize(new Dimension(140, 40));
        finish.addActionListener(e -> save());
        bar.add(finish, BorderLayout.WEST);

        JPanel middle = new JPanel(new BorderLayout());
        middle.setOpaque(false);
        emptyHint.setForeground(AppTheme.TEXT_MUTED);
        middle.add(numbersScroll, BorderLayout.CENTER);
        bar.add(middle, BorderLayout.CENTER);

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        east.setOpaque(false);
        AppButton more = AppButton.soft("...");
        more.setPreferredSize(new Dimension(44, 40));
        more.addActionListener(e -> fileMenu().show(more, 0, more.getHeight()));
        AppButton plus = AppButton.soft("+");
        plus.setPreferredSize(new Dimension(44, 40));
        plus.setFont(AppTheme.FONT_BOLD.deriveFont(18f));
        plus.addActionListener(e -> addMenu().show(plus, 0, plus.getHeight()));
        east.add(more);
        east.add(plus);
        bar.add(east, BorderLayout.EAST);
        bar.setPreferredSize(new Dimension(100, 62));
        return bar;
    }

    private JPopupMenu addMenu() {
        JPopupMenu menu = new JPopupMenu();
        for (QuestionType t : QuestionType.values()) {
            JMenuItem item = new JMenuItem(t.label());
            item.addActionListener(e -> addQuestion(t));
            menu.add(item);
        }
        return menu;
    }

    private JPopupMenu fileMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem download = new JMenuItem("Download Template");
        download.addActionListener(e -> downloadTemplate());
        JMenuItem imp = new JMenuItem("Import file");
        imp.addActionListener(e -> importFile());
        menu.add(download);
        menu.add(imp);
        return menu;
    }

    // ---------------------------------------------------------------- Vòng đời và nạp dữ liệu

    @Override
    public boolean canLeave() {
        return !dirty || Dialogs.confirm(frame, "Bạn có thay đổi chưa lưu. Rời khỏi màn hình soạn bộ câu hỏi?");
    }

    /** Bắt đầu soạn bộ mới (bản nháp trống). */
    public void startNew() {
        state.showContent();
        loading = true;
        nameField.setText("");
        topicField.setText("");
        descField.setText("");
        loading = false;
        drafts.clear();
        editingId = null;
        selected = -1;
        dirty = false;
        showStep();
    }

    /** Tải bộ từ Server để sửa: hiện 'Đang tải...', lỗi thì có nút THỬ LẠI. */
    public void loadRemote(long id) {
        state.showLoading();
        Async.ui(frame.client().getSet(id), this::loadSet, error -> state.showError(error, () -> loadRemote(id)));
    }

    /** Nạp một bộ có sẵn để sửa. */
    public void loadSet(QuestionSet set) {
        state.showContent();
        loading = true;
        nameField.setText(set.name());
        topicField.setText(set.topic() == null ? "" : set.topic());
        descField.setText(set.description() == null ? "" : set.description());
        loading = false;
        drafts.clear();
        drafts.addAll(set.questions());
        editingId = set.id();
        selected = drafts.isEmpty() ? -1 : 0;
        dirty = false;
        showStep();
    }

    /** Bỏ bản nháp (đăng xuất, mất kết nối). */
    public void discard() {
        startNew();
    }

    private void showStep() {
        top.removeAll();
        if (drafts.isEmpty()) {
            top.add(step1Form(), BorderLayout.CENTER);
            centerLayout.show(center, STEP1);
        } else {
            top.add(step2Header(), BorderLayout.CENTER);
            centerLayout.show(center, STEP2);
            select(Math.max(0, Math.min(selected, drafts.size() - 1)));
        }
        refreshNumbers();
        top.revalidate();
        top.repaint();
    }

    private JPanel step1Form() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        JLabel heading = new JLabel("SOẠN BỘ CÂU HỎI");
        heading.setFont(AppTheme.FONT_TITLE.deriveFont(26f));
        heading.setForeground(AppTheme.TITLE);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 8, 0);
        form.add(heading, c);
        addFormRow(form, 1, "Tên", nameField, "Nhập tên bộ câu hỏi");
        addFormRow(form, 2, "Chủ đề", topicField, "Nhập chủ đề");
        addFormRow(form, 3, "Mô tả", descField, "Nhập mô tả ngắn");
        return form;
    }

    private static void addFormRow(JPanel form, int row, String label, JTextField field, String placeholder) {
        field.putClientProperty("JTextField.placeholderText", placeholder);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(4, 0, 4, 16);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.gridx = 0;
        JLabel l = new JLabel(label);
        l.setFont(AppTheme.FONT_BOLD);
        l.setPreferredSize(new Dimension(90, 40));
        form.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);
        field.setPreferredSize(new Dimension(100, 40));
        form.add(field, c);
    }

    private JPanel step2Header() {
        JPanel row = new JPanel(new GridLayout(1, 4, AppTheme.GAP_M, 0));
        row.setOpaque(false);
        JLabel heading = new JLabel("SOẠN BỘ");
        heading.setFont(AppTheme.FONT_TITLE.deriveFont(22f));
        heading.setForeground(AppTheme.TITLE);
        nameField.putClientProperty("JTextField.placeholderText", "Tên bộ câu hỏi");
        topicField.putClientProperty("JTextField.placeholderText", "Chủ đề");
        descField.putClientProperty("JTextField.placeholderText", "Mô tả");
        for (JTextField f : new JTextField[]{nameField, topicField, descField}) {
            f.setPreferredSize(new Dimension(100, 40));
        }
        row.add(heading);
        row.add(nameField);
        row.add(topicField);
        row.add(descField);
        return row;
    }

    // ---------------------------------------------------------------- Danh sách câu

    private void addQuestion(QuestionType type) {
        boolean text = type == QuestionType.TYPE;
        drafts.add(new QuestionDraft(type, "", text ? List.of() : List.of("", "", "", ""), List.of(),
                text ? List.of("") : List.of(), 20, null));
        selected = drafts.size() - 1;
        dirty = true;
        showStep();
    }

    private void select(int index) {
        selected = index;
        editor.load(drafts.get(index));
        refreshNumbers();
    }

    private void deleteCurrent() {
        if (selected < 0 || selected >= drafts.size()) {
            return;
        }
        if (!Dialogs.confirm(frame, "Xóa câu " + (selected + 1) + "?")) {
            return;
        }
        drafts.remove(selected);
        dirty = true;
        selected = Math.min(selected, drafts.size() - 1);
        showStep();
    }

    private void refreshNumbers() {
        numbers.removeAll();
        if (drafts.isEmpty()) {
            numbers.add(emptyHint);
        }
        for (int i = 0; i < drafts.size(); i++) {
            final int index = i;
            boolean valid = DraftValidator.validate(drafts.get(i)) == null;
            NumberButton b = new NumberButton(i + 1, i == selected, valid);
            b.addActionListener(e -> select(index));
            numbers.add(b);
        }
        numbers.revalidate();
        numbers.repaint();
        if (selected >= 0 && selected < numbers.getComponentCount()) {
            numbers.scrollRectToVisible(numbers.getComponent(selected).getBounds());
        }
    }

    // ---------------------------------------------------------------- Lưu

    /** Lỗi đầu tiên của bản nháp: index = số thứ tự câu lỗi (-1 nếu lỗi ở tên hoặc chưa có câu). */
    public record Problem(int index, String message) {
    }

    /** Kiểm tra toàn bộ bản nháp trước khi lưu; null nếu hợp lệ. */
    public static Problem check(String name, List<QuestionDraft> drafts) {
        if (name == null || name.isBlank()) {
            return new Problem(-1, "Tên bộ câu hỏi không được để trống");
        }
        if (drafts.isEmpty()) {
            return new Problem(-1, "Bộ câu hỏi cần có ít nhất 1 câu");
        }
        for (int i = 0; i < drafts.size(); i++) {
            String error = DraftValidator.validate(drafts.get(i));
            if (error != null) {
                return new Problem(i, "Câu " + (i + 1) + ": " + error);
            }
        }
        return null;
    }

    private void save() {
        if (saving) {
            return;
        }
        Problem problem = check(nameField.getText(), drafts);
        if (problem != null) {
            if (problem.index() >= 0) {
                select(problem.index());
            } else {
                nameField.requestFocusInWindow();
            }
            Dialogs.error(frame, problem.message());
            return;
        }
        QuestionSet set = new QuestionSet(editingId == null ? 0 : editingId, nameField.getText().trim(),
                topicField.getText().trim(), descField.getText().trim(), frame.username(), new ArrayList<>(drafts));
        saving = true;
        finish.setEnabled(false);
        CompletableFuture<Object> future = editingId == null
                ? frame.client().createSet(set).thenApply(id -> (Object) id)
                : frame.client().updateSet(set).thenApply(v -> (Object) v);
        Async.ui(future, v -> {
            saving = false;
            finish.setEnabled(true);
            dirty = false;
            frame.showScreen(MainFrame.HOME);
        }, error -> {
            saving = false;
            finish.setEnabled(true);
            Dialogs.error(frame, error);
        });
    }

    // ---------------------------------------------------------------- CSV

    private void downloadTemplate() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Lưu file mẫu");
        chooser.setSelectedFile(new File("mau_bo_cau_hoi.csv"));
        chooser.setFileFilter(new FileNameExtensionFilter("File CSV", "csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getParentFile(), file.getName() + ".csv");
        }
        if (file.exists() && !Dialogs.confirm(this, "File đã tồn tại. Ghi đè?")) {
            return;
        }
        try {
            CsvTemplate.write(file.toPath());
            Dialogs.info(this, "Đã lưu file mẫu: " + file.getName());
        } catch (java.io.IOException e) {
            Dialogs.error(this, "Không ghi được file: " + e.getMessage());
        }
    }

    private void importFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn file CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("File CSV", "csv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        // Đọc file và ảnh ở luồng nền để không chặn giao diện
        new SwingWorker<ImportResult, Void>() {
            @Override
            protected ImportResult doInBackground() throws Exception {
                return CsvImporter.read(path);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    applyImport(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Dialogs.error(SetEditorPanel.this, "Không đọc được file: " + e.getCause().getMessage());
                }
            }
        }.execute();
    }

    private void applyImport(ImportResult result) {
        int first = drafts.size();
        drafts.addAll(result.questions());
        if (!result.questions().isEmpty()) {
            dirty = true;
            selected = first;
            showStep();
        }
        if (!result.errors().isEmpty()) {
            JTextArea area = new JTextArea(String.join("\n", result.errors()), 12, 50);
            area.setEditable(false);
            JOptionPane.showMessageDialog(frame, new JScrollPane(area), "Đã thêm " + result.questions().size()
                    + " câu, bỏ qua " + result.errors().size() + " dòng lỗi", JOptionPane.WARNING_MESSAGE);
        } else if (result.questions().isEmpty()) {
            Dialogs.info(frame, "File không có câu hỏi nào");
        }
    }

    // ---------------------------------------------------------------- Thành phần nhỏ

    /** Ô lớn bấm được ở Bước 1. */
    private static final class ChoiceCard extends CardPanel {
        ChoiceCard(String title, String subtitle, Runnable action) {
            super(new BorderLayout());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            JPanel box = new JPanel();
            box.setOpaque(false);
            box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
            JLabel t = new JLabel("<html><u>" + title + "</u></html>");
            t.setFont(AppTheme.FONT_BOLD.deriveFont(16f));
            t.setAlignmentX(Component.CENTER_ALIGNMENT);
            t.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            t.setMaximumSize(new Dimension(Integer.MAX_VALUE, t.getPreferredSize().height));
            JLabel s = new JLabel(subtitle);
            s.setFont(AppTheme.FONT_BASE.deriveFont(java.awt.Font.ITALIC, 13f));
            s.setForeground(AppTheme.TEXT_MUTED);
            s.setAlignmentX(Component.CENTER_ALIGNMENT);
            s.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            s.setMaximumSize(new Dimension(Integer.MAX_VALUE, s.getPreferredSize().height));
            box.add(Box.createVerticalGlue());
            box.add(t);
            box.add(Box.createVerticalStrut(6));
            box.add(s);
            box.add(Box.createVerticalGlue());
            add(box, BorderLayout.CENTER);
            MouseAdapter click = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    action.run();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    setFill(AppTheme.SOFT);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setFill(Color.WHITE);
                }
            };
            addMouseListener(click);
            box.addMouseListener(click);
            t.addMouseListener(click);
            s.addMouseListener(click);
        }
    }

    /** Nút số câu: nền tím khi đang sửa, gạch dưới xanh (đủ dữ liệu) hoặc đỏ (thiếu). */
    private static final class NumberButton extends JButton {
        private final boolean selected;
        private final boolean valid;

        NumberButton(int number, boolean selected, boolean valid) {
            super(String.valueOf(number));
            this.selected = selected;
            this.valid = valid;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(AppTheme.FONT_BOLD);
            setForeground(selected ? Color.WHITE : AppTheme.TEXT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(Math.max(44, 20 + 9 * String.valueOf(number).length()), 44));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (selected) {
                g2.setColor(AppTheme.PRIMARY);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.setColor(valid ? AppTheme.CORRECT : AppTheme.WRONG);
            g2.fillRect(0, getHeight() - 4, getWidth(), 4);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
