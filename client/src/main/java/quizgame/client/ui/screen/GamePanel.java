package quizgame.client.ui.screen;

import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Dialogs;
import quizgame.client.ui.Format;
import quizgame.client.ui.MainFrame;
import quizgame.client.ui.Screen;
import quizgame.client.ui.component.AnswerButton;
import quizgame.client.ui.component.AppButton;
import quizgame.client.ui.component.CardPanel;
import quizgame.client.ui.component.GameScoreList;
import quizgame.client.ui.component.ScoreWindow;
import quizgame.client.ui.component.TimerRing;
import quizgame.client.ui.component.WidthTrackingPanel;
import quizgame.client.ui.component.WrappingText;
import quizgame.client.util.Async;
import quizgame.client.util.ImageUtil;
import quizgame.client.util.SoundPlayer;
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
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.GridLayout;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Màn chơi nền tối (không có NavBar): câu hỏi, 4 ô đáp án lớn, đồng hồ tròn, khung kết quả và bảng xếp hạng. */
public class GamePanel extends JPanel implements Screen {
    private static final int TICK_MS = 100;
    private static final long DANGER_MS = 3000;
    private static final int MAX_POINTS = 1000;
    private static final int BANNER_MIN_H = 76;

    private final MainFrame frame;

    // Thanh trên
    private final JLabel questionNo = whiteLabel("CÂU 0/0", 22f);
    private final ProgressBar progressBar = new ProgressBar();
    private final TimerRing timerRing = new TimerRing();
    private final JLabel scoreValue = new JLabel("0");

    // Giữa
    private final JLabel kindLabel = new JLabel(" ");
    private final WrappingText questionText = new WrappingText("");
    private final CardPanel questionCard = new CardPanel(new BorderLayout(0, 8));
    private final JLabel imageLabel = new JLabel("", SwingConstants.CENTER);
    private final CardLayout answerLayout = new CardLayout();
    private final JPanel answerArea = new JPanel(answerLayout);
    private final AnswerButton[] singleButtons = new AnswerButton[4];
    private final JPanel singlePanel = new JPanel(new GridLayout(2, 2, 20, 20));
    private final JCheckBox[] multiChecks = new JCheckBox[4];
    private final JPanel[] multiRows = new JPanel[4];
    private final AppButton multiConfirm = goldButton();
    private final JTextField typeField = new JTextField();
    private final AppButton typeConfirm = goldButton();
    private final ResultBanner banner = new ResultBanner();
    private final JLabel waiting = new JLabel(" ");
    private JScrollPane questionScroll;

    // Phải
    private final GameScoreList scoreList = new GameScoreList();
    private final JLabel notices = new JLabel(" ");
    private final List<String> noticeLines = new ArrayList<>();

    private final Timer countdown = new Timer(TICK_MS, e -> tick());
    private Map<String, BufferedImage> images = new HashMap<>();
    private Question current;
    private boolean active;
    private boolean answered;
    private int total;
    private long deadlineNanos;
    private long limitMs = 1;
    private List<Integer> chosen = List.of();
    private int renderedImageWidth = -1;

    public GamePanel(MainFrame frame) {
        super(new BorderLayout());
        this.frame = frame;
        setOpaque(true);
        add(buildTopBar(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(AppTheme.GAP_L + 4, 0));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(AppTheme.GAP_L, AppTheme.GAP_L + 8, AppTheme.GAP_L,
                AppTheme.GAP_L + 8));
        questionScroll = new JScrollPane(buildQuestionSide(),
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        questionScroll.setBorder(null);
        questionScroll.setOpaque(false);
        questionScroll.getViewport().setOpaque(false);
        questionScroll.getVerticalScrollBar().setUnitIncrement(20);
        // Thanh cuộn tối, mảnh để hợp nền (chỉ hiện khi câu hỏi/đáp án quá dài)
        questionScroll.getVerticalScrollBar().setOpaque(false);
        questionScroll.getVerticalScrollBar().putClientProperty("FlatLaf.style",
                "width: 8; trackArc: 999; thumbArc: 999; track: #00000000; thumb: #FFFFFF40; hoverThumbColor: #FFFFFF66");
        center.add(questionScroll, BorderLayout.CENTER);
        center.add(buildScoreSide(), BorderLayout.EAST);
        add(center, BorderLayout.CENTER);
    }

    /** Nền tối có vầng sáng tím phía trên giữa. */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        int w = getWidth();
        int h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, AppTheme.GAME_BG_TOP, 0, h, AppTheme.GAME_BG_BOTTOM));
        g2.fillRect(0, 0, w, h);
        float radius = Math.max(1, w * 0.55f);
        g2.setPaint(new RadialGradientPaint(w / 2f, 0, radius, new float[]{0f, 1f},
                new Color[]{new Color(AppTheme.GAME_GLOW.getRed(), AppTheme.GAME_GLOW.getGreen(),
                        AppTheme.GAME_GLOW.getBlue(), 150), new Color(0, 0, 0, 0)}));
        g2.fillRect(0, 0, w, h);
        g2.dispose();
    }

    // ---------------------------------------------------------------- Dựng giao diện

    private static JLabel whiteLabel(String text, float size) {
        JLabel l = new JLabel(text);
        l.setFont(AppTheme.FONT_TITLE.deriveFont(size));
        l.setForeground(Color.WHITE);
        return l;
    }

    private static AppButton goldButton() {
        AppButton b = new AppButton("XÁC NHẬN", AppTheme.GOLD, AppTheme.TITLE);
        b.setFont(AppTheme.FONT_BOLD.deriveFont(15f));
        return b;
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(AppTheme.GAP_L + 4, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(AppTheme.GAME_BAR.getRed(), AppTheme.GAME_BAR.getGreen(),
                        AppTheme.GAME_BAR.getBlue(), 200));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(AppTheme.GAME_LINE);
                g2.fillRect(0, getHeight() - 1, getWidth(), 1);
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(12, AppTheme.GAP_L + 8, 12, AppTheme.GAP_L + 8));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, AppTheme.GAP_L, 0));
        right.setOpaque(false);
        right.add(timerRing);
        JPanel score = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        score.setOpaque(false);
        JLabel scoreTitle = whiteLabel("ĐIỂM", 19f);
        scoreTitle.setFont(AppTheme.FONT_BASE.deriveFont(19f));
        scoreValue.setFont(AppTheme.FONT_TITLE.deriveFont(28f));
        scoreValue.setForeground(AppTheme.GOLD);
        score.add(scoreTitle);
        score.add(scoreValue);
        right.add(score);
        AppButton quit = new AppButton("Thoát", new Color(0x2C2852), Color.WHITE);
        quit.setPreferredSize(new Dimension(108, 42));
        quit.setFont(AppTheme.FONT_BOLD.deriveFont(15f));
        quit.addActionListener(e -> quit());
        right.add(quit);
        // Đẩy khối điểm xuống cho thẳng hàng với tâm đồng hồ tròn
        score.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel progressWrap = new JPanel(new BorderLayout());
        progressWrap.setOpaque(false);
        progressWrap.add(progressBar, BorderLayout.CENTER);
        bar.add(questionNo, BorderLayout.WEST);
        bar.add(progressWrap, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildQuestionSide() {
        JPanel side = new WidthTrackingPanel(null) {
            @Override
            public Dimension getPreferredSize() {
                updateQuestionLayout(getWidth() > 0 ? getWidth() : 560);
                ((BoxLayout) getLayout()).invalidateLayout(this);
                return super.getPreferredSize();
            }

            @Override
            public void doLayout() {
                updateQuestionLayout(getWidth());
                ((BoxLayout) getLayout()).invalidateLayout(this);
                super.doLayout();
            }
        };
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));

        CardPanel card = questionCard;
        card.setFill(AppTheme.GAME_QUESTION_BG);
        card.setBorder(BorderFactory.createEmptyBorder(22, 30, 24, 30));
        kindLabel.setFont(AppTheme.FONT_BOLD.deriveFont(14f));
        kindLabel.setForeground(AppTheme.GAME_KIND);
        questionText.setEditable(false);
        questionText.setFocusable(false);
        questionText.setLineWrap(true);
        questionText.setWrapStyleWord(true);
        questionText.setOpaque(false);
        questionText.setForeground(AppTheme.TITLE);
        questionText.setFont(AppTheme.FONT_TITLE.deriveFont(27f));
        card.add(kindLabel, BorderLayout.NORTH);
        card.add(questionText, BorderLayout.CENTER);
        card.setAlignmentX(LEFT_ALIGNMENT);

        side.add(card);
        side.add(Box.createVerticalStrut(AppTheme.GAP_M + 4));

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
        JPanel none = new JPanel();
        none.setOpaque(false);
        answerArea.add(none, "NONE");
        answerLayout.show(answerArea, "NONE");
        answerArea.setAlignmentX(LEFT_ALIGNMENT);

        side.add(answerArea);
        side.add(Box.createVerticalStrut(AppTheme.GAP_M + 4));

        banner.setAlignmentX(LEFT_ALIGNMENT);
        banner.setVisible(false);
        side.add(banner);
        waiting.setFont(AppTheme.FONT_BOLD.deriveFont(17f));
        waiting.setForeground(AppTheme.GAME_MUTED);
        waiting.setBorder(BorderFactory.createEmptyBorder(10, 4, 0, 16));
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
            multiChecks[i].setFont(AppTheme.FONT_BOLD.deriveFont(19f));
            multiChecks[i].setForeground(AppTheme.TITLE);
            multiChecks[i].addActionListener(e -> SoundPlayer.click());
            multiChecks[i].addActionListener(e -> multiConfirm.setEnabled(!ticked().isEmpty() && !answered));
            JPanel row = new RoundedRow();
            row.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            row.setBackground(AppTheme.CARD);
            row.add(multiChecks[i], BorderLayout.CENTER);
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
            multiRows[i] = row;
            panel.add(row);
            panel.add(Box.createVerticalStrut(10));
        }
        multiConfirm.setEnabled(false);
        multiConfirm.setAlignmentX(LEFT_ALIGNMENT);
        multiConfirm.setMaximumSize(new Dimension(180, 46));
        multiConfirm.setPreferredSize(new Dimension(180, 46));
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
        typeField.setFont(AppTheme.FONT_BASE.deriveFont(20f));
        typeField.setMargin(new java.awt.Insets(10, 16, 10, 16));
        typeConfirm.setPreferredSize(new Dimension(150, 52));
        Runnable send = () -> {
            String text = typeField.getText().trim();
            if (!text.isEmpty() && !answered) {
                submit(new Answer(List.of(), text));
            }
        };
        typeField.addActionListener(e -> send.run());
        typeConfirm.addActionListener(e -> send.run());
        JPanel row = new JPanel(new BorderLayout(AppTheme.GAP_M, 0));
        row.setOpaque(false);
        row.add(typeField, BorderLayout.CENTER);
        row.add(typeConfirm, BorderLayout.EAST);
        panel.add(row, BorderLayout.NORTH);
        return panel;
    }

    private JPanel buildScoreSide() {
        JPanel side = new JPanel(new BorderLayout(0, AppTheme.GAP_M)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppTheme.GAME_PANEL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 28, 28);
                g2.dispose();
            }
        };
        side.setOpaque(false);
        side.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        side.setPreferredSize(new Dimension(380, 100));
        JLabel title = new JLabel("XẾP HẠNG");
        title.setFont(AppTheme.FONT_BOLD.deriveFont(18f));
        title.setForeground(AppTheme.GAME_MUTED);
        side.add(title, BorderLayout.NORTH);

        notices.setFont(AppTheme.FONT_BASE.deriveFont(14f));
        notices.setForeground(AppTheme.GAME_WRONG_TEXT);
        notices.setVerticalAlignment(SwingConstants.TOP);
        notices.setBorder(BorderFactory.createEmptyBorder(8, 4, 0, 4));
        JPanel list = new JPanel(new BorderLayout());
        list.setOpaque(false);
        list.add(scoreList, BorderLayout.NORTH);
        list.add(notices, BorderLayout.CENTER);
        side.add(list, BorderLayout.CENTER);

        JLabel hint = new JLabel("<html>Cập nhật sau mỗi câu có người trả lời.</html>");
        hint.setFont(AppTheme.FONT_BASE.deriveFont(14f));
        hint.setForeground(AppTheme.GAME_MUTED);
        side.add(hint, BorderLayout.SOUTH);
        return side;
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
        SoundPlayer.stopMusic();
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
        scoreList.setRows(List.of());
        scoreValue.setText("0");
        questionNo.setText("CÂU 0/" + total);
        kindLabel.setText(" ");
        questionText.setText("Đang chờ câu hỏi đầu tiên...");
        imageLabel.setVisible(false);
        banner.setVisible(false);
        waiting.setText(" ");
        answerLayout.show(answerArea, "NONE");
        progressBar.setFraction(0f);
        timerRing.clear();
        countdown.stop();
        SoundPlayer.startMusic();
    }

    /** Kết thúc trận: dừng đồng hồ, tắt nhạc nền và không nhận thêm sự kiện. */
    public void end() {
        active = false;
        countdown.stop();
        SoundPlayer.stopMusic();
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
        progressBar.setFraction(q.total() <= 0 ? 0f : (q.qIndex() + 1f) / q.total());
        kindLabel.setText(kindText(q.type()) + " · " + Format.points(MAX_POINTS) + " ĐIỂM");
        questionText.setText(q.content());
        banner.setVisible(false);
        waiting.setText(" ");

        renderedImageWidth = -1;
        fitQuestionImage(imageLabel.getParent().getWidth());

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
                    multiRows[i].setBackground(AppTheme.CARD);
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
        imageLabel.getParent().revalidate();
        if (imageLabel.getParent().getParent() instanceof javax.swing.JViewport viewport) {
            viewport.setViewPosition(new java.awt.Point(0, 0));
        }
        startCountdown(q.timeLimitMs());
    }

    private static String kindText(QuestionType type) {
        return switch (type) {
            case SINGLE -> "CHỌN 1 ĐÁP ÁN";
            case MULTI -> "CHỌN NHIỀU ĐÁP ÁN";
            case TYPE -> "GÕ ĐÁP ÁN";
        };
    }

    private void fitQuestionImage(int width) {
        int available = Math.max(1, width);
        if (renderedImageWidth == available) return;
        renderedImageWidth = available;
        BufferedImage image = current == null || current.imageId() == null ? null : images.get(current.imageId());
        if (image == null) {
            imageLabel.setIcon(null);
            imageLabel.setVisible(false);
            return;
        }
        ImageIcon icon = new ImageIcon(ImageUtil.fit(image, available, 200));
        imageLabel.setIcon(icon);
        imageLabel.setPreferredSize(new Dimension(available, icon.getIconHeight()));
        imageLabel.setMinimumSize(new Dimension(1, icon.getIconHeight()));
        imageLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, icon.getIconHeight()));
        imageLabel.setVisible(true);
    }

    private void updateQuestionLayout(int width) {
        int available = Math.max(1, width);
        java.awt.Insets insets = questionCard.getInsets();
        questionText.setWrapWidth(available - insets.left - insets.right);
        int questionHeight = questionText.getPreferredSize().height + kindLabel.getPreferredSize().height + 8
                + insets.top + insets.bottom;
        fixedHeight(questionCard, available, questionHeight);
        fitQuestionImage(available);
        int bannerHeight = 0;
        if (banner.isVisible()) {
            bannerHeight = banner.layoutFor(available);
            fixedHeight(banner, available, bannerHeight);
        }
        int answerHeight = 190;
        if (current != null) {
            answerHeight = switch (current.type()) {
                case SINGLE -> singleAnswerHeight(available, spareHeight(questionHeight, bannerHeight));
                case MULTI -> multiAnswerHeight(available);
                case TYPE -> 64;
            };
        }
        fixedHeight(answerArea, available, answerHeight);
    }

    /** Chiều cao còn trống trong khung nhìn để 4 ô đáp án giãn ra lấp đầy (giống ảnh mẫu). */
    private int spareHeight(int questionHeight, int bannerHeight) {
        if (questionScroll == null) return 0;
        int view = questionScroll.getViewport().getHeight();
        int image = imageLabel.isVisible() ? imageLabel.getPreferredSize().height : 0;
        int gaps = 2 * (AppTheme.GAP_M + 4);
        // Dòng "đang chờ" luôn chiếm chỗ kể cả khi trống, phải trừ ra để không sinh thanh cuộn
        int wait = waiting.getPreferredSize().height;
        return view - questionHeight - image - gaps - bannerHeight - wait - 4;
    }

    private int multiAnswerHeight(int width) {
        for (int i = 0; i < multiChecks.length; i++) {
            String option = i < current.options().size() ? current.options().get(i) : "";
            String html = "<html>" + (char) ('A' + i) + ". " + escape(option) + "</html>";
            if (!html.equals(multiChecks[i].getText())) multiChecks[i].setText(html);
            Object renderer = multiChecks[i].getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
            if (renderer instanceof javax.swing.text.View view) {
                view.setSize(Math.max(1, width - 90), 0);
            }
            fixedHeight(multiRows[i], width, Math.max(62, multiChecks[i].getPreferredSize().height + 20));
        }
        JPanel panel = (JPanel) multiConfirm.getParent();
        ((BoxLayout) panel.getLayout()).invalidateLayout(panel);
        return Math.max(250, panel.getPreferredSize().height);
    }

    private int singleAnswerHeight(int width, int spare) {
        int columns = width >= 600 ? 2 : 1;
        GridLayout layout = (GridLayout) singlePanel.getLayout();
        layout.setRows(0);
        layout.setColumns(columns);
        int cellWidth = Math.max(1, (width - layout.getHgap() * (columns - 1)) / columns);
        int cellHeight = 96;
        for (AnswerButton button : singleButtons) {
            cellHeight = Math.max(cellHeight, button.preferredHeightForWidth(cellWidth));
        }
        int rows = 4 / columns;
        // Còn chỗ thì giãn ô cho to (tối đa 190px/ô); chữ dài vẫn giữ chiều cao tối thiểu để đọc đủ
        int stretched = (spare - layout.getVgap() * (rows - 1)) / rows;
        cellHeight = Math.max(cellHeight, Math.min(stretched, 190));
        return rows * cellHeight + layout.getVgap() * (rows - 1);
    }

    private static void fixedHeight(JPanel panel, int width, int height) {
        panel.setPreferredSize(new Dimension(width, height));
        panel.setMinimumSize(new Dimension(1, height));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
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
        scoreValue.setText(Format.points(r.totalScore()));
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
                        multiRows[i].setBackground(AppTheme.CORRECT_SOFT);
                    } else if (chosen.contains(i)) {
                        multiRows[i].setBackground(AppTheme.WRONG_SOFT);
                    }
                }
            }
            case TYPE -> { }
        }
        showBanner(r, hadAnswered);
        if (r.correct()) {
            SoundPlayer.correct();
        } else {
            SoundPlayer.wrong(); // sai hoặc hết giờ
        }
        if (r.qIndex() + 1 >= current.total()) {
            waiting.setText("Đã xong, đang chờ người khác...");
        }
        revalidate();
    }

    private void showBanner(AnswerResult r, boolean hadAnswered) {
        String correctText = switch (current.type()) {
            case TYPE -> String.join(" / ", r.acceptedAnswers());
            default -> optionsText(r.correctIndexes());
        };
        if (r.correct()) {
            String text = "ĐÚNG!";
            if (current.type() != QuestionType.TYPE) {
                text += " Bạn chọn " + optionsText(chosen);
            }
            banner.show(ResultBanner.Kind.CORRECT, text, r.points());
        } else {
            String text = (hadAnswered ? "SAI!" : "HẾT GIỜ!") + " Đáp án "
                    + (current.type() == QuestionType.TYPE ? "chấp nhận: " : "đúng: ") + correctText;
            banner.show(hadAnswered ? ResultBanner.Kind.WRONG : ResultBanner.Kind.TIMEOUT, text, r.points());
        }
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
        String me = frame == null ? "" : frame.username();
        List<GameScoreList.Row> data = new ArrayList<>();
        for (ScoreRow r : ScoreWindow.around(rows, me)) {
            int done = r.finished() ? total : r.currentQuestion();
            data.add(new GameScoreList.Row(r.rank(), r.username(), r.score(), done + "/" + total,
                    r.username().equals(me)));
        }
        scoreList.setRows(data);
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
        if (current.type() == QuestionType.SINGLE) {
            for (int i = 0; i < singleButtons.length; i++) singleButtons[i].setSelected(chosen.contains(i));
        }
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
        timerRing.set((remaining + 999) / 1000, (float) remaining / limitMs, remaining < DANGER_MS);
        if (remaining == 0) {
            countdown.stop();
        }
    }

    // ---------------------------------------------------------------- Thành phần nhỏ

    /** Thanh tiến độ câu (vàng–cam) trên nền tối. */
    private static final class ProgressBar extends JPanel {
        private float fraction;

        ProgressBar() {
            setOpaque(false);
            setPreferredSize(new Dimension(100, 20));
        }

        void setFraction(float fraction) {
            this.fraction = Math.max(0f, Math.min(1f, fraction));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = 16;
            int y = (getHeight() - h) / 2;
            g2.setColor(AppTheme.GAME_LINE);
            g2.fillRoundRect(0, y, getWidth(), h, h, h);
            int w = Math.round(getWidth() * fraction);
            if (w > 0) {
                g2.setPaint(new GradientPaint(0, 0, AppTheme.GOLD, Math.max(1, w), 0, AppTheme.ORANGE));
                g2.fillRoundRect(0, y, w, h, h, h);
            }
            g2.dispose();
        }
    }

    /** Dòng đáp án Checkboxes bo góc; màu nền vẫn lấy từ getBackground() để báo đúng/sai. */
    private static final class RoundedRow extends JPanel {
        RoundedRow() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.dispose();
        }
    }

    /** Khung kết quả câu: biểu tượng + chữ bên trái, điểm cộng to màu vàng bên phải. */
    private static final class ResultBanner extends JPanel {
        enum Kind { CORRECT, WRONG, TIMEOUT }

        private final JLabel message = new JLabel();
        private final JLabel points = new JLabel();
        private Kind kind = Kind.CORRECT;
        private String text = "";

        ResultBanner() {
            super(new BorderLayout(AppTheme.GAP_M, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(14, 66, 14, 28));
            message.setFont(AppTheme.FONT_TITLE.deriveFont(25f));
            points.setFont(AppTheme.FONT_TITLE.deriveFont(38f));
            points.setForeground(AppTheme.GOLD);
            add(message, BorderLayout.CENTER);
            add(points, BorderLayout.EAST);
        }

        void show(Kind kind, String text, int earned) {
            this.kind = kind;
            this.text = text;
            message.setForeground(kind == Kind.CORRECT ? AppTheme.GAME_CORRECT_TEXT : AppTheme.GAME_WRONG_TEXT);
            points.setText("+" + Format.points(earned));
            message.setText("<html>" + escape(text) + "</html>");
            setVisible(true);
        }

        /** Đặt độ rộng xuống dòng cho chữ và trả chiều cao cần có. */
        int layoutFor(int width) {
            java.awt.Insets in = getInsets();
            int textWidth = Math.max(80, width - in.left - in.right - points.getPreferredSize().width - AppTheme.GAP_M);
            message.setText("<html><div style='width:" + textWidth + "px'>" + escape(text) + "</div></html>");
            return Math.max(BANNER_MIN_H, message.getPreferredSize().height + in.top + in.bottom);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean ok = kind == Kind.CORRECT;
            Color accent = ok ? AppTheme.GAME_CORRECT_TEXT : AppTheme.GAME_WRONG_TEXT;
            g2.setColor(ok ? AppTheme.GAME_CORRECT_BG : AppTheme.GAME_WRONG_BG);
            g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 22, 22);
            g2.setColor(accent);
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 22, 22);
            // Biểu tượng vẽ bằng nét: ✓ đúng, ✗ sai, đồng hồ cát khi hết giờ
            int cx = 36;
            int cy = getHeight() / 2;
            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (kind) {
                case CORRECT -> g2.drawPolyline(new int[]{cx - 11, cx - 3, cx + 12}, new int[]{cy, cy + 8, cy - 9}, 3);
                case WRONG -> {
                    g2.drawLine(cx - 9, cy - 9, cx + 9, cy + 9);
                    g2.drawLine(cx - 9, cy + 9, cx + 9, cy - 9);
                }
                case TIMEOUT -> {
                    g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawOval(cx - 12, cy - 12, 24, 24);
                    g2.drawLine(cx, cy, cx, cy - 7);
                    g2.drawLine(cx, cy, cx + 6, cy + 3);
                }
            }
            g2.dispose();
        }
    }
}
