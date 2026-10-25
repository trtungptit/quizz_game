package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;
import quizgame.client.util.DraftValidator;
import quizgame.common.model.QuestionDraft;
import quizgame.common.model.QuestionType;

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
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Sửa một câu hỏi; phần đáp án đổi theo loại (Buttons, Checkboxes, Type answer). */
public class QuestionEditor extends JPanel {
    private static final Color CORRECT_BG = new Color(0xDCFCE7);

    private final JLabel title = new JLabel();
    private final JTextArea content = new JTextArea(2, 20);
    private final JSpinner time = new JSpinner(new SpinnerNumberModel(20, DraftValidator.MIN_TIME,
            DraftValidator.MAX_TIME, 1));
    private final ImagePicker image = new ImagePicker();
    private final JPanel answers = new JPanel();
    private final Consumer<QuestionDraft> onChange;

    private QuestionType type = QuestionType.SINGLE;
    private boolean loading;
    private final JTextField[] optionFields = new JTextField[4];
    private final OptionRow[] optionRows = new OptionRow[4];
    private int singleCorrect = -1;
    private final boolean[] multiCorrect = new boolean[4];
    private final List<JTextField> acceptedFields = new ArrayList<>();

    public QuestionEditor(Runnable onDelete, Consumer<QuestionDraft> onChange) {
        super(new BorderLayout(0, AppTheme.GAP_S));
        this.onChange = onChange;
        setOpaque(false);

        title.setFont(AppTheme.FONT_TITLE.deriveFont(17f));
        AppButton delete = AppButton.soft("XÓA CÂU NÀY");
        delete.setPreferredSize(new Dimension(120, 34));
        delete.addActionListener(e -> onDelete.run());
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(delete, BorderLayout.EAST);

        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setFont(AppTheme.FONT_BASE);
        content.setMargin(new Insets(6, 10, 6, 10));
        content.getDocument().addDocumentListener(changeListener());
        JScrollPane contentScroll = new JScrollPane(content);
        contentScroll.setPreferredSize(new Dimension(100, 62));
        time.addChangeListener(e -> fireChange());
        image.setOnChange(this::fireChange);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 4, 12);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.gridx = 0;
        c.gridy = 0;
        form.add(label("Câu hỏi"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(contentScroll, c);
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        form.add(label("Thời gian"), c);
        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        timeRow.setOpaque(false);
        time.setPreferredSize(new Dimension(80, 34));
        timeRow.add(time);
        JLabel unit = new JLabel("giây (" + DraftValidator.MIN_TIME + "–" + DraftValidator.MAX_TIME + ")");
        unit.setForeground(AppTheme.TEXT_MUTED);
        timeRow.add(unit);
        c.gridx = 1;
        form.add(timeRow, c);

        JPanel north = new JPanel(new BorderLayout(0, AppTheme.GAP_S));
        north.setOpaque(false);
        north.add(header, BorderLayout.NORTH);
        north.add(form, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        answers.setOpaque(false);
        answers.setLayout(new BoxLayout(answers, BoxLayout.Y_AXIS));
        JPanel body = new JPanel(new BorderLayout(AppTheme.GAP_M, 0));
        body.setOpaque(false);
        JPanel imageHolder = new JPanel(new BorderLayout());
        imageHolder.setOpaque(false);
        imageHolder.add(image, BorderLayout.NORTH);
        body.add(imageHolder, BorderLayout.WEST);
        JPanel answersHolder = new JPanel(new BorderLayout());
        answersHolder.setOpaque(false);
        answersHolder.add(answers, BorderLayout.NORTH);
        JScrollPane answersScroll = new JScrollPane(answersHolder);
        answersScroll.setBorder(null);
        answersScroll.setOpaque(false);
        answersScroll.getViewport().setOpaque(false);
        body.add(answersScroll, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppTheme.FONT_BOLD);
        l.setPreferredSize(new Dimension(80, 30));
        return l;
    }

    private DocumentListener changeListener() {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                fireChange();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fireChange();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                fireChange();
            }
        };
    }

    private void fireChange() {
        if (!loading) {
            onChange.accept(toDraft());
        }
    }

    // ---------------------------------------------------------------- Nạp và đọc dữ liệu

    /** Nạp một câu để sửa (không gọi onChange). */
    public void load(QuestionDraft d) {
        loading = true;
        type = d.type();
        title.setText(switch (type) {
            case SINGLE -> "BUTTONS — 1 đáp án đúng";
            case MULTI -> "CHECKBOXES — nhiều đáp án đúng";
            case TYPE -> "TYPE ANSWER — người chơi tự nhập";
        });
        content.setText(d.content());
        content.setCaretPosition(0);
        time.setValue(Math.max(DraftValidator.MIN_TIME, Math.min(DraftValidator.MAX_TIME, d.timeSeconds())));
        image.setImageBase64(d.imageBase64());
        buildAnswers(d);
        loading = false;
    }

    /** Đọc lại dữ liệu đang hiện thành QuestionDraft (chưa kiểm tra hợp lệ). */
    public QuestionDraft toDraft() {
        String text = content.getText().trim();
        int seconds = ((Number) time.getValue()).intValue();
        return switch (type) {
            case SINGLE -> new QuestionDraft(type, text, options(), singleCorrect < 0 ? List.of()
                    : List.of(singleCorrect), List.of(), seconds, image.getImageBase64());
            case MULTI -> {
                List<Integer> correct = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    if (multiCorrect[i]) {
                        correct.add(i);
                    }
                }
                yield new QuestionDraft(type, text, options(), correct, List.of(), seconds, image.getImageBase64());
            }
            case TYPE -> {
                List<String> accepted = new ArrayList<>();
                acceptedFields.forEach(f -> accepted.add(f.getText().trim()));
                yield new QuestionDraft(type, text, List.of(), List.of(), accepted, seconds, image.getImageBase64());
            }
        };
    }

    private List<String> options() {
        List<String> list = new ArrayList<>();
        for (JTextField f : optionFields) {
            list.add(f.getText().trim());
        }
        return list;
    }

    // ---------------------------------------------------------------- Phần đáp án

    private void buildAnswers(QuestionDraft d) {
        answers.removeAll();
        acceptedFields.clear();
        switch (type) {
            case SINGLE, MULTI -> buildOptions(d);
            case TYPE -> buildAccepted(d);
        }
        answers.revalidate();
        answers.repaint();
    }

    private void buildOptions(QuestionDraft d) {
        singleCorrect = type == QuestionType.SINGLE && !d.correctIndexes().isEmpty() ? d.correctIndexes().get(0) : -1;
        for (int i = 0; i < 4; i++) {
            multiCorrect[i] = type == QuestionType.MULTI && d.correctIndexes().contains(i);
        }
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < 4; i++) {
            final int index = i;
            JTextField field = new JTextField(i < d.options().size() ? d.options().get(i) : "");
            field.setMargin(new Insets(4, 8, 4, 8));
            field.putClientProperty("FlatLaf.style", "borderWidth: 0; focusWidth: 0");
            field.getDocument().addDocumentListener(changeListener());
            optionFields[i] = field;

            java.awt.Component lead;
            if (type == QuestionType.SINGLE) {
                JRadioButton radio = new JRadioButton();
                radio.setOpaque(false);
                radio.setSelected(index == singleCorrect);
                radio.addActionListener(e -> {
                    singleCorrect = index;
                    refreshHighlights();
                    fireChange();
                });
                group.add(radio);
                lead = radio;
            } else {
                AppButton toggle = new AppButton("S", AppTheme.SOFT, AppTheme.SOFT_TEXT);
                toggle.setPreferredSize(new Dimension(40, 32));
                toggle.addActionListener(e -> {
                    multiCorrect[index] = !multiCorrect[index];
                    refreshHighlights();
                    fireChange();
                });
                lead = toggle;
            }
            optionRows[i] = new OptionRow(lead, (char) ('A' + i) + ".", field);
            answers.add(optionRows[i]);
            answers.add(Box.createVerticalStrut(AppTheme.GAP_S));
        }
        refreshHighlights();
    }

    private void refreshHighlights() {
        for (int i = 0; i < 4; i++) {
            boolean correct = type == QuestionType.SINGLE ? i == singleCorrect : multiCorrect[i];
            optionRows[i].setCorrect(correct);
            if (type == QuestionType.MULTI) {
                AppButton toggle = (AppButton) optionRows[i].lead;
                toggle.setText(correct ? "Đ" : "S");
                toggle.setColors(correct ? AppTheme.CORRECT : AppTheme.SOFT, correct ? Color.WHITE : AppTheme.SOFT_TEXT);
            }
        }
    }

    private void buildAccepted(QuestionDraft d) {
        List<String> initial = d.acceptedAnswers().isEmpty() ? List.of("") : d.acceptedAnswers();
        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        for (String a : initial) {
            addAcceptedRow(rows, a);
        }
        answers.add(rows);
        AppButton add = AppButton.soft("+ Thêm các câu trả lời được chấp nhận");
        add.setPreferredSize(new Dimension(320, 38));
        add.addActionListener(e -> {
            addAcceptedRow(rows, "");
            rows.revalidate();
            rows.repaint();
            fireChange();
        });
        JPanel addHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        addHolder.setOpaque(false);
        addHolder.add(add);
        answers.add(Box.createVerticalStrut(AppTheme.GAP_S));
        answers.add(addHolder);
    }

    private void addAcceptedRow(JPanel rows, String text) {
        JTextField field = new JTextField(text);
        field.setMargin(new Insets(4, 8, 4, 8));
        field.putClientProperty("FlatLaf.style", "borderWidth: 0; focusWidth: 0");
        field.getDocument().addDocumentListener(changeListener());
        acceptedFields.add(field);
        AppButton remove = AppButton.soft("×");
        remove.setPreferredSize(new Dimension(40, 32));
        JPanel lead = new JPanel(new BorderLayout());
        lead.setOpaque(false);
        JLabel caption = new JLabel("Đáp án");
        caption.setFont(AppTheme.FONT_BOLD);
        lead.add(caption, BorderLayout.CENTER);
        OptionRow row = new OptionRow(lead, "", field);
        row.add(remove, BorderLayout.EAST);
        remove.addActionListener(e -> {
            if (acceptedFields.size() <= 1) {
                field.setText(""); // luôn giữ ít nhất 1 dòng
                return;
            }
            acceptedFields.remove(field);
            int index = rows.getComponentZOrder(row);
            rows.remove(row);
            if (index < rows.getComponentCount()) {
                rows.remove(index); // dải đệm đi kèm dòng
            }
            rows.revalidate();
            rows.repaint();
            fireChange();
        });
        rows.add(row);
        rows.add(Box.createVerticalStrut(AppTheme.GAP_S));
    }

    /** Một dòng đáp án bo góc; nền xanh khi là đáp án đúng. */
    private static final class OptionRow extends RoundPanel {
        private final java.awt.Component lead;

        OptionRow(java.awt.Component lead, String letter, JTextField field) {
            super(new BorderLayout(AppTheme.GAP_S, 0), Color.WHITE, 16);
            this.lead = lead;
            setOutline(AppTheme.BORDER);
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            west.setOpaque(false);
            west.add(lead);
            if (!letter.isEmpty()) {
                JLabel l = new JLabel(letter);
                l.setFont(AppTheme.FONT_BOLD);
                west.add(l);
            }
            add(west, BorderLayout.WEST);
            add(field, BorderLayout.CENTER);
        }

        void setCorrect(boolean correct) {
            setFill(correct ? CORRECT_BG : Color.WHITE);
            setOutline(correct ? AppTheme.CORRECT : AppTheme.BORDER);
        }
    }
}
