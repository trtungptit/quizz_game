package quizgame.client.ui.screen;

import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Dialogs;
import quizgame.client.ui.MainFrame;
import quizgame.client.ui.Screen;
import quizgame.client.ui.component.AnswerButton;
import quizgame.client.ui.component.AppButton;
import quizgame.client.ui.component.CardPanel;
import quizgame.client.ui.component.GradientPanel;
import quizgame.client.ui.component.ScoreWindow;
import quizgame.client.ui.component.StyledTable;
import quizgame.client.util.Async;
import quizgame.client.util.ImageUtil;
import quizgame.common.model.Answer;
import quizgame.common.model.AnswerResult;
import quizgame.common.model.GameStart;
import quizgame.common.model.Question;
import quizgame.common.model.QuestionType;
import quizgame.common.model.ScoreRow;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Màn chơi (không có NavBar): câu hỏi, đồng hồ, vùng trả lời theo loại và bảng xếp hạng mini. */
public class GamePanel extends JPanel implements Screen {
    private static final int TICK_MS = 100;
    private static final long DANGER_MS = 3000;

    private final MainFrame frame;

    // Thanh trên
    private final JLabel questionNo = whiteLabel("CÂU 0/0", 20f);
    private final TimeBar timeBar = new TimeBar();
    private final JLabel secondsLabel = whiteLabel("00s", 18f);
    private final JLabel scoreLabel = whiteLabel("ĐIỂM: 0", 18f);

    // Giữa
    private final JTextArea questionText = new JTextArea();
    private final JLabel imageLabel = new JLabel("", SwingConstants.CENTER);
    private final CardLayout answerLayout = new CardLayout();
    private final JPanel answerArea = new JPanel(answerLayout);
    private final AnswerButton[] singleButtons = new AnswerButton[4];
    private final JPanel singlePanel = new JPanel(new GridLayout(2, 2, 14, 14));
    private final JCheckBox[] multiChecks = new JCheckBox[4];
    private final JPanel[] multiRows = new JPanel[4];
    private final AppButton multiConfirm = AppButton.primary("XÁC NHẬN");
    private final JTextField typeField = new JTextField();
    private final AppButton typeConfirm = AppButton.primary("XÁC NHẬN");
    private final JLabel banner = new JLabel(" ");
    private final JLabel waiting = new JLabel(" ");

    // Phải
    private final StyledTable scoreTable = new StyledTable("Hạng", "Tên", "Điểm", "Câu");
    private final JLabel notices = new JLabel(" ");
    private final List<String> noticeLines = new ArrayList<>();
    private List<ScoreRow> shownRows = List.of();

    private final Timer countdown = new Timer(TICK_MS, e -> tick());
    private Map<String, BufferedImage> images = new HashMap<>();
    private Question current;
    private boolean active;
    private boolean answered;
    private int total;
    private long deadlineNanos;
    private long limitMs = 1;
    private List<Integer> chosen = List.of();

    public GamePanel(MainFrame frame) {
        super(new BorderLayout());
        this.frame = frame;
        add(buildTopBar(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(AppTheme.GAP_L, 0));
        center.setBorder(BorderFactory.createEmptyBorder(AppTheme.GAP_M, AppTheme.GAP_L, AppTheme.GAP_L,
                AppTheme.GAP_L));
        center.add(buildQuestionSide(), BorderLayout.CENTER);
        center.add(buildScoreSide(), BorderLayout.EAST);
        add(center, BorderLayout.CENTER);
    }

    // ---------------------------------------------------------------- Dựng giao diện

    private static JLabel whiteLabel(String text, float size) {
        JLabel l = new JLabel(text);
        l.setFont(AppTheme.FONT_TITLE.deriveFont(size));
        l.setForeground(Color.WHITE);
        return l;
    }

    private JPanel buildTopBar() {
        JPanel bar = GradientPanel.horizontal(new BorderLayout(AppTheme.GAP_M, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(14, AppTheme.GAP_L, 14, AppTheme.GAP_L));
        AppButton quit = new AppButton("THOÁT", new Color(0x9B7BF7), Color.WHITE);
        quit.setPreferredSize(new Dimension(96, 36));
        quit.addActionListener(e -> quit());
        JPanel right = new JPanel(new BorderLayout(AppTheme.GAP_M, 0));
        right.setOpaque(false);
        JPanel stats = new JPanel(new BorderLayout(AppTheme.GAP_M, 0));
        stats.setOpaque(false);
        stats.add(secondsLabel, BorderLayout.WEST);
        stats.add(scoreLabel, BorderLayout.CENTER);
        right.add(stats, BorderLayout.CENTER);
        right.add(quit, BorderLayout.EAST);
        secondsLabel.setPreferredSize(new Dimension(56, 24));
        bar.add(questionNo, BorderLayout.WEST);
        bar.add(timeBar, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildQuestionSide() {
        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));

        CardPanel card = new CardPanel(new BorderLayout());
        questionText.setEditable(false);
        questionText.setFocusable(false);
        questionText.setLineWrap(true);
        questionText.setWrapStyleWord(true);
        questionText.setOpaque(false);
        questionText.setFont(AppTheme.FONT_BOLD.deriveFont(20f));
        card.add(questionText, BorderLayout.CENTER);
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        side.add(card);
        side.add(Box.createVerticalStrut(AppTheme.GAP_S));

        imageLabel.setAlignmentX(LEFT_ALIGNMENT);
        imageLabel.setVisible(false);
        side.add(imageLabel);

        answerArea.setOpaque(false);
        for (int i = 0; i < 4; i++) {
            singleButtons[i] = new AnswerButton(i, "");
            singlePanel.add(singleButtons[i]);
        }
        singlePanel.setOpaque(false);
        answerArea.add(singlePanel, QuestionType.SINGLE.name());
        answerArea.add(buildMultiPanel(), QuestionType.MULTI.name());
        answerArea.add(buildTypePanel(), QuestionType.TYPE.name());
        answerArea.add(new JPanel(), "NONE");
        answerLayout.show(answerArea, "NONE");
        answerArea.setAlignmentX(LEFT_ALIGNMENT);
        answerArea.setPreferredSize(new Dimension(100, 190));
        answerArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 230));
        side.add(answerArea);
        side.add(Box.createVerticalStrut(AppTheme.GAP_S));

        banner.setFont(AppTheme.FONT_TITLE.deriveFont(19f));
        banner.setOpaque(true);
        banner.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        banner.setAlignmentX(LEFT_ALIGNMENT);
        banner.setVisible(false);
        side.add(banner);
        waiting.setFont(AppTheme.FONT_BOLD.deriveFont(16f));
        waiting.setForeground(AppTheme.SOFT_TEXT);
        waiting.setBorder(BorderFactory.createEmptyBorder(8, 4, 0, 16));
        waiting.setAlignmentX(LEFT_ALIGNMENT);
        side.add(waiting);
        side.add(Box.createVerticalGlue());

        for (int i = 0; i < 4; i++) {
            final int index = i;
            singleButtons[i].addActionListener(e -> submit(new Answer(List.of(index), null)));
        }
        return side;
    }

    private JPanel buildMultiPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (int i = 0; i < 4; i++) {
            multiChecks[i] = new JCheckBox();
            multiChecks[i].setOpaque(false);
            multiChecks[i].setFont(AppTheme.FONT_BOLD.deriveFont(17f));
            multiChecks[i].addActionListener(e -> multiConfirm.setEnabled(!ticked().isEmpty() && !answered));
            JPanel row = new JPanel(new BorderLayout());
            row.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            row.setBackground(Color.WHITE);
            row.add(multiChecks[i], BorderLayout.CENTER);
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            multiRows[i] = row;
            panel.add(row);
            panel.add(Box.createVerticalStrut(6));
        }
        multiConfirm.setEnabled(false);
        multiConfirm.setAlignmentX(LEFT_ALIGNMENT);
        multiConfirm.setMaximumSize(new Dimension(160, 40));
        multiConfirm.addActionListener(e -> {
            List<Integer> t = ticked();
            if (!t.isEmpty()) {
                submit(new Answer(t, null));
            }
        });
        panel.add(multiConfirm);
        return panel;
    }

    private JPanel buildTypePanel() {
        JPanel panel = new JPanel(new BorderLayout(AppTheme.GAP_S, 0));
        panel.setOpaque(false);
        typeField.setFont(AppTheme.FONT_BASE.deriveFont(18f));
        typeField.setMargin(new java.awt.Insets(10, 14, 10, 14));
        typeConfirm.setPreferredSize(new Dimension(140, 48));
        Runnable send = () -> {
            String text = typeField.getText().trim();
            if (!text.isEmpty() && !answered) {
                submit(new Answer(List.of(), text));
            }
        };
        typeField.addActionListener(e -> send.run());
        typeConfirm.addActionListener(e -> send.run());
        JPanel row = new JPanel(new BorderLayout(AppTheme.GAP_S, 0));
        row.setOpaque(false);
        row.add(typeField, BorderLayout.CENTER);
        row.add(typeConfirm, BorderLayout.EAST);
        panel.add(row, BorderLayout.NORTH);
        return panel;
    }

    private JPanel buildScoreSide() {
        JPanel side = new JPanel(new BorderLayout(0, AppTheme.GAP_S));
        side.setOpaque(false);
        side.setPreferredSize(new Dimension(340, 100));
        JLabel title = new JLabel("XẾP HẠNG");
        title.setFont(AppTheme.FONT_BOLD.deriveFont(15f));
        side.add(title, BorderLayout.NORTH);

        scoreTable.setCellStyle(new StyledTable.CellStyle() {
            @Override
            public Color background(int row, int col) {
                return isMine(row) ? AppTheme.HIGHLIGHT : null;
            }

            @Override
            public boolean bold(int row, int col) {
                return isMine(row);
            }
        });
        scoreTable.setColumnWidths(64, 160, 64, 58);
        CardPanel card = new CardPanel(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder());
        JScrollPane scroll = scoreTable.inScrollPane();
        scroll.setPreferredSize(new Dimension(100, 34 + 5 * 36 + 2));
        card.add(scroll, BorderLayout.CENTER);
        JPanel stack = new JPanel(new BorderLayout(0, AppTheme.GAP_S));
        stack.setOpaque(false);
        stack.add(card, BorderLayout.NORTH);
        JLabel hint = new JLabel("<html>Tên bạn được tô đậm. Bảng cập nhật sau mỗi câu có người trả lời.</html>");
        hint.setFont(AppTheme.FONT_SMALL.deriveFont(13f));
        hint.setForeground(AppTheme.TEXT_MUTED);
        notices.setFont(AppTheme.FONT_SMALL.deriveFont(13f));
        notices.setForeground(AppTheme.TEXT_MUTED);
        notices.setVerticalAlignment(SwingConstants.TOP);
        JPanel below = new JPanel(new BorderLayout(0, AppTheme.GAP_S));
        below.setOpaque(false);
        below.add(hint, BorderLayout.NORTH);
        below.add(notices, BorderLayout.CENTER);
        stack.add(below, BorderLayout.CENTER);
        side.add(stack, BorderLayout.CENTER);
        return side;
    }

    private boolean isMine(int row) {
        return row < shownRows.size() && shownRows.get(row).username().equals(frame.username());
    }

    private List<Integer> ticked() {
        List<Integer> t = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (multiChecks[i].isSelected()) {
                t.add(i);
            }
        }
        return t;
    }

    // ---------------------------------------------------------------- Vòng đời

    @Override
    public void onHide() {
        countdown.stop();
    }

    /** Bắt đầu trận: giải mã ảnh một lần để dùng lại cho mọi câu. */
    public void begin(GameStart start) {
        active = true;
        total = start.numQuestions();
        current = null;
        answered = false;
        images = new HashMap<>();
        start.imagesBase64().forEach((id, base64) -> {
            try {
                images.put(id, ImageUtil.decode(base64));
            } catch (IOException | IllegalArgumentException e) {
                // ảnh hỏng: câu hỏi tương ứng hiện không có ảnh
            }
        });
        noticeLines.clear();
        notices.setText(" ");
        shownRows = List.of();
        scoreTable.setRows(List.of());
        scoreLabel.setText("ĐIỂM: 0");
        questionNo.setText("CÂU 0/" + total);
        questionText.setText("Đang chờ câu hỏi đầu tiên...");
        imageLabel.setVisible(false);
        banner.setVisible(false);
        waiting.setText(" ");
        answerLayout.show(answerArea, "NONE");
        timeBar.setFraction(1f, false);
        secondsLabel.setText("");
        countdown.stop();
    }

    /** Kết thúc trận: dừng đồng hồ và không nhận thêm sự kiện. */
    public void end() {
        active = false;
        countdown.stop();
    }

    public int totalQuestions() {
        return total;
    }

    // ---------------------------------------------------------------- Sự kiện chuyển tiếp từ MainFrame

    public void onQuestion(Question q) {
        if (!active) {
            return;
        }
        current = q;
        answered = false;
        chosen = List.of();
        total = q.total();
        questionNo.setText("CÂU " + (q.qIndex() + 1) + "/" + q.total());
        questionText.setText(q.content());
        banner.setVisible(false);
        waiting.setText(" ");

        BufferedImage image = q.imageId() == null ? null : images.get(q.imageId());
        if (image != null) {
            imageLabel.setIcon(new ImageIcon(ImageUtil.fit(image, 560, 200)));
            imageLabel.setVisible(true);
        } else {
            imageLabel.setIcon(null);
            imageLabel.setVisible(false);
        }

        switch (q.type()) {
            case SINGLE -> {
                for (int i = 0; i < 4; i++) {
                    singlePanel.remove(singleButtons[i]);
                    String text = i < q.options().size() ? q.options().get(i) : "";
                    singleButtons[i] = replaceButton(i, text);
                    singlePanel.add(singleButtons[i]);
                }
                singlePanel.revalidate();
                singlePanel.repaint();
            }
            case MULTI -> {
                for (int i = 0; i < 4; i++) {
                    multiChecks[i].setText((char) ('A' + i) + ". " + (i < q.options().size() ? q.options().get(i) : ""));
                    multiChecks[i].setSelected(false);
                    multiChecks[i].setEnabled(true);
                    multiRows[i].setBackground(Color.WHITE);
                }
                multiConfirm.setEnabled(false);
            }
            case TYPE -> {
                typeField.setText("");
                typeField.setEnabled(true);
                typeConfirm.setEnabled(true);
                typeField.requestFocusInWindow();
            }
        }
        answerLayout.show(answerArea, q.type().name());
        startCountdown(q.timeLimitMs());
    }

    private AnswerButton replaceButton(int index, String text) {
        AnswerButton b = new AnswerButton(index, text);
        b.addActionListener(e -> submit(new Answer(List.of(index), null)));
        return b;
    }

    public void onAnswerResult(AnswerResult r) {
        if (!active) {
            return;
        }
        scoreLabel.setText("ĐIỂM: " + r.totalScore());
        if (current == null || r.qIndex() != current.qIndex()) {
            return;
        }
        boolean hadAnswered = answered;
        lock();
        countdown.stop();
        List<Integer> correct = r.correctIndexes();
        switch (current.type()) {
            case SINGLE -> {
                for (int i = 0; i < 4; i++) {
                    AnswerButton.State s;
                    if (correct.contains(i)) {
                        s = AnswerButton.State.CORRECT;
                    } else if (chosen.contains(i)) {
                        s = AnswerButton.State.WRONG;
                    } else {
                        s = AnswerButton.State.DIM;
                    }
                    singleButtons[i].setState(s);
                }
            }
            case MULTI -> {
                for (int i = 0; i < 4; i++) {
                    if (correct.contains(i)) {
                        multiRows[i].setBackground(new Color(0xBBF7D0));
                    } else if (chosen.contains(i)) {
                        multiRows[i].setBackground(new Color(0xFECACA));
                    }
                }
            }
            case TYPE -> { }
        }
        showBanner(r, hadAnswered);
        if (r.qIndex() + 1 >= current.total()) {
            waiting.setText("Đã xong, đang chờ người khác");
        }
    }

    private void showBanner(AnswerResult r, boolean hadAnswered) {
        String correctText = switch (current.type()) {
            case TYPE -> String.join(" / ", r.acceptedAnswers());
            default -> optionsText(r.correctIndexes());
        };
        String text;
        Color bg;
        Color fg;
        if (r.correct()) {
            text = "ĐÚNG +" + r.points();
            if (current.type() != QuestionType.TYPE) {
                text += " — bạn đã chọn " + optionsText(chosen);
            }
            bg = new Color(0xDCFCE7);
            fg = new Color(0x15803D);
        } else {
            text = (hadAnswered ? "SAI" : "HẾT GIỜ") + " — đáp án "
                    + (current.type() == QuestionType.TYPE ? "chấp nhận: " : "đúng: ") + correctText;
            bg = new Color(0xFEE2E2);
            fg = new Color(0xB91C1C);
        }
        banner.setText("<html>" + escape(text) + "</html>");
        banner.setBackground(bg);
        banner.setForeground(fg);
        banner.setVisible(true);
    }

    private String optionsText(List<Integer> indexes) {
        List<String> parts = new ArrayList<>();
        for (int i : indexes) {
            String option = i >= 0 && i < current.options().size() ? current.options().get(i) : "";
            parts.add((char) ('A' + i) + ". " + option);
        }
        return String.join(", ", parts);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public void onScoreboard(List<ScoreRow> rows) {
        if (!active) {
            return;
        }
        shownRows = ScoreWindow.around(rows, frame.username());
        List<Object[]> data = new ArrayList<>();
        for (ScoreRow r : shownRows) {
            boolean mine = r.username().equals(frame.username());
            int done = r.finished() ? total : r.currentQuestion();
            data.add(new Object[]{r.rank(), r.username() + (mine ? " (bạn)" : ""), r.score(), done + "/" + total});
        }
        scoreTable.setRows(data);
    }

    public void onPlayerLeft(String username) {
        if (!active) {
            return;
        }
        noticeLines.add(username + " đã rời trận");
        int from = Math.max(0, noticeLines.size() - 3);
        notices.setText("<html>" + String.join("<br>", noticeLines.subList(from, noticeLines.size())
                .stream().map(GamePanel::escape).toList()) + "</html>");
    }

    // ---------------------------------------------------------------- Trả lời

    private void submit(Answer answer) {
        if (answered || current == null) {
            return;
        }
        chosen = answer.choices() == null ? List.of() : List.copyOf(answer.choices());
        lock();
        Async.ui(frame.client().answer(current.qIndex(), answer), v -> { }, error -> {
            // Lỗi như TIME_UP: kết quả vẫn đến từ Server qua ANSWER_RESULT, chỉ báo nhẹ ở dòng ghi chú
            notices.setText("<html>" + escape(error) + "</html>");
        });
    }

    /** Khóa mọi ô trả lời (đã gửi hoặc đã có kết quả). */
    private void lock() {
        answered = true;
        for (AnswerButton b : singleButtons) {
            b.setEnabled(false);
        }
        for (JCheckBox c : multiChecks) {
            c.setEnabled(false);
        }
        multiConfirm.setEnabled(false);
        typeField.setEnabled(false);
        typeConfirm.setEnabled(false);
    }

    private void quit() {
        if (Dialogs.confirm(frame, "Thoát sẽ bị xử thua. Bạn chắc chắn muốn thoát?")) {
            end();
            frame.leaveGame();
        }
    }

    // ---------------------------------------------------------------- Đồng hồ

    private void startCountdown(long ms) {
        limitMs = Math.max(1, ms);
        deadlineNanos = System.nanoTime() + ms * 1_000_000L;
        tick();
        countdown.restart();
    }

    /** Đồng hồ chỉ để hiển thị; hết giờ thì chờ Server gửi câu mới, không tự gửi gì. */
    private void tick() {
        long remaining = Math.max(0, (deadlineNanos - System.nanoTime()) / 1_000_000L);
        boolean danger = remaining < DANGER_MS;
        timeBar.setFraction((float) remaining / limitMs, danger);
        secondsLabel.setText(String.format("%02ds", (remaining + 999) / 1000));
        secondsLabel.setForeground(danger ? new Color(0xFCA5A5) : Color.WHITE);
        if (remaining == 0) {
            countdown.stop();
        }
    }

    /** Thanh thời gian: vàng cam, đỏ khi còn dưới 3 giây. */
    private static final class TimeBar extends JPanel {
        private float fraction = 1f;
        private boolean danger;

        TimeBar() {
            setOpaque(false);
            setPreferredSize(new Dimension(100, 20));
        }

        void setFraction(float fraction, boolean danger) {
            this.fraction = Math.max(0f, Math.min(1f, fraction));
            this.danger = danger;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = 18;
            int y = (getHeight() - h) / 2;
            g2.setColor(new Color(255, 255, 255, 60));
            g2.fillRoundRect(0, y, getWidth(), h, h, h);
            int w = Math.round(getWidth() * fraction);
            if (w > 0) {
                g2.setPaint(danger ? new GradientPaint(0, 0, AppTheme.WRONG, w, 0, new Color(0xB91C1C))
                        : new GradientPaint(0, 0, AppTheme.ACCENT, Math.max(1, w), 0, AppTheme.ORANGE));
                g2.fillRoundRect(0, y, w, h, h, h);
            }
            g2.dispose();
        }
    }
}
