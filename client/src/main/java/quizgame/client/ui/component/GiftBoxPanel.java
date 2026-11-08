package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Format;
import quizgame.client.util.SoundPlayer;
import quizgame.common.model.BoxEffect;
import quizgame.common.model.BoxOffer;
import quizgame.common.model.BoxResult;
import quizgame.common.model.BoxState;
import quizgame.common.model.BoxStatus;
import quizgame.common.model.ScoreRow;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Màn hộp quà bí ẩn trong trận (nền tối): 4 hộp để chọn, đếm ngược 6 giây, lật kết quả và bảng người chơi.
 * Chỉ hiển thị dữ liệu Server gửi (BOX_OFFER / BOX_RESULT / BOX_STATUS); không tự quyết định hiệu ứng.
 */
public class GiftBoxPanel extends JPanel {
    private static final Color[] COLORS = {AppTheme.ANSWER_A, AppTheme.ANSWER_B, AppTheme.ANSWER_C, AppTheme.ANSWER_D};
    private static final int TICK_MS = 100;

    private final BiConsumer<Integer, Integer> onPick; // (round, box)
    private final Title title = new Title();
    private final JLabel subtitle = new JLabel(" ", SwingConstants.CENTER);
    private final Card[] cards = new Card[4];
    private final PlayerList players = new PlayerList();
    private final JLabel playersTitle = new JLabel("Người chơi");
    private final TimerRing ring = new TimerRing();
    private final Bar bar = new Bar();
    private final JLabel hint = new JLabel("Hết giờ chưa chọn: hệ thống chọn ngẫu nhiên");
    private final JLabel nextPill = new Pill();
    private final JPanel footer = new JPanel(new java.awt.CardLayout());
    private final Timer timer = new Timer(TICK_MS, e -> tick());

    private BoxOffer offer;
    private BoxResult result;
    private int picked = -1;
    private long deadlineNanos;
    private long revealEndNanos;
    private List<BoxStatus> statuses = List.of();
    private List<ScoreRow> scores = List.of();
    private String me = "";

    public GiftBoxPanel(BiConsumer<Integer, Integer> onPick) {
        super(new BorderLayout(0, AppTheme.GAP_L));
        this.onPick = onPick;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(AppTheme.GAP_M, AppTheme.GAP_L + 8, AppTheme.GAP_L, AppTheme.GAP_L + 8));

        JPanel head = new JPanel(new BorderLayout(0, 6));
        head.setOpaque(false);
        subtitle.setFont(AppTheme.FONT_BASE.deriveFont(20f));
        subtitle.setForeground(new Color(0xE9E6FF));
        head.add(title, BorderLayout.CENTER);
        head.add(subtitle, BorderLayout.SOUTH);
        add(head, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(1, 4, AppTheme.GAP_L, 0));
        grid.setOpaque(false);
        for (int i = 0; i < 4; i++) {
            final int index = i;
            cards[i] = new Card(i);
            cards[i].addActionListener(e -> choose(index));
            grid.add(cards[i]);
        }
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
        side.setPreferredSize(new Dimension(360, 100));
        playersTitle.setFont(AppTheme.FONT_BOLD.deriveFont(19f));
        playersTitle.setForeground(new Color(0xE9E6FF));
        side.add(playersTitle, BorderLayout.NORTH);
        JPanel listWrap = new JPanel(new BorderLayout());
        listWrap.setOpaque(false);
        listWrap.add(players, BorderLayout.NORTH);
        side.add(listWrap, BorderLayout.CENTER);
        JPanel middle = new JPanel(new BorderLayout(AppTheme.GAP_L + 4, 0));
        middle.setOpaque(false);
        middle.add(grid, BorderLayout.CENTER);
        middle.add(side, BorderLayout.EAST);
        add(middle, BorderLayout.CENTER);

        JPanel countdown = new JPanel(new BorderLayout(AppTheme.GAP_L, 0));
        countdown.setOpaque(false);
        countdown.setBorder(BorderFactory.createEmptyBorder(0, 200, 0, 200));
        ring.setPreferredSize(new Dimension(76, 76));
        ring.setFont(AppTheme.FONT_TITLE.deriveFont(30f));
        hint.setFont(AppTheme.FONT_BASE.deriveFont(18f));
        hint.setForeground(new Color(0xE9E6FF));
        countdown.add(ring, BorderLayout.WEST);
        countdown.add(bar, BorderLayout.CENTER);
        countdown.add(hint, BorderLayout.EAST);
        JPanel pillRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 14));
        pillRow.setOpaque(false);
        pillRow.add(nextPill);
        footer.setOpaque(false);
        footer.add(countdown, "COUNTDOWN");
        footer.add(pillRow, "NEXT");
        add(footer, BorderLayout.SOUTH);
    }

    // ---------------------------------------------------------------- Dữ liệu từ GamePanel

    /** Server mời chọn hộp: đặt lại 4 hộp úp và bắt đầu đếm ngược. */
    public void offer(BoxOffer o) {
        offer = o;
        result = null;
        picked = -1;
        title.set(false);
        subtitle.setText("Chọn 1 trong 4 hộp quà. Mỗi hộp giấu một hiệu ứng, có hộp may và có hộp rủi!");
        playersTitle.setText("Người chơi");
        for (Card c : cards) c.reset();
        deadlineNanos = System.nanoTime() + o.timeoutMs() * 1_000_000L;
        showFooter("COUNTDOWN");
        refreshPlayers();
        tick();
        timer.restart();
    }

    /** Gửi chọn hộp thất bại (ví dụ hết giờ): mở khoá lại nếu vẫn chưa có kết quả. */
    public void pickFailed() {
        if (result == null && offer != null) {
            picked = -1;
            for (Card c : cards) c.reset();
        }
    }

    /** Kết quả mở hộp của mình: lật cả 4 hộp, phát âm thanh, đếm ngược tới câu kế tiếp. */
    public void reveal(BoxResult r) {
        if (offer == null || r.round() != offer.round()) return;
        result = r;
        picked = r.chosen();
        title.set(true);
        subtitle.setText("<html><div style='text-align:center'>" + revealText(r) + "</div></html>");
        playersTitle.setText("Kết quả mở quà");
        for (int i = 0; i < 4; i++) {
            BoxEffect e = i < r.boxes().size() ? r.boxes().get(i) : null;
            cards[i].reveal(e, i == r.chosen());
        }
        refreshCards();
        revealEndNanos = System.nanoTime() + r.nextInMs() * 1_000_000L;
        showFooter("NEXT");
        refreshPlayers();
        tick();
        timer.restart();
        boolean bad = r.effect() == BoxEffect.LOSE || r.effect() == BoxEffect.STEAL && r.delta() <= 0;
        if (bad) SoundPlayer.wrong(); else SoundPlayer.correct();
    }

    /** Trạng thái hộp của mọi người + bảng điểm mới nhất để vẽ cột bên phải và ghi chú trên thẻ. */
    public void update(List<BoxStatus> statuses, List<ScoreRow> scores, String me) {
        this.statuses = statuses == null ? List.of() : List.copyOf(statuses);
        this.scores = scores == null ? List.of() : List.copyOf(scores);
        this.me = me == null ? "" : me;
        refreshCards();
        refreshPlayers();
    }

    /** Dừng đồng hồ khi rời màn. */
    public void stop() {
        timer.stop();
    }

    public int round() {
        return offer == null ? 0 : offer.round();
    }

    // ---------------------------------------------------------------- Nội bộ

    private void choose(int box) {
        if (offer == null || result != null || picked >= 0) return;
        picked = box;
        for (int i = 0; i < 4; i++) cards[i].pending(i == box);
        onPick.accept(offer.round(), box);
    }

    private void showFooter(String key) {
        ((java.awt.CardLayout) footer.getLayout()).show(footer, key);
    }

    private void tick() {
        long now = System.nanoTime();
        if (result == null && offer != null) {
            long remaining = Math.max(0, (deadlineNanos - now) / 1_000_000L);
            ring.set((remaining + 999) / 1000, (float) remaining / Math.max(1, offer.timeoutMs()), remaining < 2000);
            bar.setFraction((float) remaining / Math.max(1, offer.timeoutMs()));
            if (remaining == 0) timer.stop();
        } else if (result != null) {
            long remaining = Math.max(0, (revealEndNanos - now) / 1_000_000L);
            int next = (result.applyQIndex() != null ? result.applyQIndex() : offer.afterQIndex() + 1) + 1;
            nextPill.setText("Câu " + next + " bắt đầu sau " + (remaining + 999) / 1000 + " giây...");
            if (remaining == 0) timer.stop();
        }
    }

    private static String effectName(BoxEffect e) {
        return switch (e) {
            case STEAL -> "Cướp điểm";
            case LOSE -> "Mất điểm";
            case DOUBLE -> "Nhân đôi";
            case NO_LATE -> "Không sợ muộn";
        };
    }

    private static String effectDescription(BoxEffect e) {
        return switch (e) {
            case STEAL -> "Lấy 15% điểm của người dẫn đầu";
            case LOSE -> "Bị trừ 20% số điểm đang có";
            case DOUBLE -> "Câu sau đúng được x2 điểm";
            case NO_LATE -> "Câu sau đúng là được điểm tối đa";
        };
    }

    private String revealText(BoxResult r) {
        String gold = "<b style='color:#FFD54A'>" + effectName(r.effect()).toUpperCase() + "</b>";
        String prefix = r.auto() ? "Hết giờ, hệ thống chọn giúp Hộp " + (r.chosen() + 1) + ". " : "";
        int next = r.applyQIndex() == null ? 0 : r.applyQIndex() + 1;
        String body = switch (r.effect()) {
            case STEAL -> r.target() == null || r.delta() == 0
                    ? "Bạn nhận " + gold + " nhưng không có điểm nào để lấy."
                    : "Bạn nhận " + gold + ": lấy " + Format.points(r.delta()) + " điểm của " + escape(r.target()) + ".";
            case LOSE -> r.delta() == 0
                    ? "Bạn nhận " + gold + " nhưng chưa có điểm nào để mất."
                    : "Bạn nhận " + gold + ": bị trừ " + Format.points(-r.delta()) + " điểm.";
            case DOUBLE -> "Bạn nhận " + gold + ": câu " + next + " đúng sẽ được gấp đôi điểm.";
            case NO_LATE -> "Bạn nhận " + gold + ": câu " + next + " đúng là được trọn 1.000 điểm.";
        };
        return prefix + body;
    }

    /** Ghi chú dưới mỗi thẻ đã lật: ai đã chọn hộp này và được/mất gì. */
    private void refreshCards() {
        if (result == null) return;
        Map<Integer, List<String>> notes = new HashMap<>();
        for (BoxStatus s : statuses) {
            if (s.round() != result.round() || s.state() != BoxState.CHOSEN || s.box() == null || s.effect() == null) continue;
            boolean mine = s.username().equals(me);
            String who = mine ? "Bạn" : s.username();
            String line = switch (s.effect()) {
                case STEAL -> s.target() == null || s.delta() == 0 ? who + ": không cướp được điểm"
                        : who + " lấy " + Format.points(s.delta()) + " điểm của " + s.target();
                case LOSE -> who + ": −" + Format.points(-s.delta()) + " điểm";
                case DOUBLE, NO_LATE -> mine ? "Áp dụng ở câu "
                        + (s.applyQIndex() == null ? "sau" : String.valueOf(s.applyQIndex() + 1)) : s.username() + " chọn";
            };
            notes.computeIfAbsent(s.box(), k -> new ArrayList<>()).add(line);
        }
        for (int i = 0; i < 4; i++) cards[i].setNotes(notes.getOrDefault(i, List.of()));
    }

    /** Cột bên phải: trước khi mở = trạng thái chọn; sau khi mở = mỗi người mở được gì. */
    private void refreshPlayers() {
        int round = round();
        Map<String, BoxStatus> byUser = new HashMap<>();
        for (BoxStatus s : statuses) if (s.round() == round) byUser.put(s.username(), s);
        List<PlayerList.Row> rows = new ArrayList<>();
        for (ScoreRow r : scores) {
            BoxStatus s = byUser.get(r.username());
            String chip;
            String detail;
            boolean chosen = s != null && s.state() == BoxState.CHOSEN;
            if (chosen) chip = "đã chọn";
            else if (s != null) chip = "đang chọn";
            else chip = r.finished() ? "đã xong" : "đang trả lời";
            if (chosen && s.effect() != null) {
                String what = effectName(s.effect());
                if (s.effect() == BoxEffect.STEAL && s.delta() > 0) what = "Cướp điểm (+" + Format.points(s.delta()) + ")";
                if (s.effect() == BoxEffect.LOSE && s.delta() < 0) what = "Mất điểm (−" + Format.points(-s.delta()) + ")";
                detail = (s.box() != null ? "Hộp " + (s.box() + 1) + " · " : "") + what;
            } else {
                detail = s != null ? "Đang chọn hộp..." : "Chưa tới hộp quà";
            }
            rows.add(new PlayerList.Row(r.rank(), r.username(), r.score(), r.username().equals(me), chip, chosen, detail));
        }
        players.setRows(rows, result != null);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Color mix(Color a, Color b, float ratio) {
        return new Color(Math.round(a.getRed() * (1 - ratio) + b.getRed() * ratio),
                Math.round(a.getGreen() * (1 - ratio) + b.getGreen() * ratio),
                Math.round(a.getBlue() * (1 - ratio) + b.getBlue() * ratio));
    }

    // ---------------------------------------------------------------- Thành phần vẽ

    /** Tiêu đề vàng phát sáng: "LẬT NGƯỢC TÌNH THẾ" kèm tia sét, hoặc "MỞ QUÀ!" kèm hộp quà nhỏ. */
    private static final class Title extends JComponent {
        private boolean revealed;

        Title() {
            setPreferredSize(new Dimension(600, 74));
            setFont(AppTheme.FONT_TITLE.deriveFont(50f));
        }

        void set(boolean revealed) {
            this.revealed = revealed;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            String text = revealed ? "MỞ QUÀ!" : "LẬT NGƯỢC TÌNH THẾ";
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(text);
            int x = (getWidth() - tw) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            // Quầng sáng: vẽ chữ mờ lệch nhiều phía
            g2.setColor(new Color(255, 200, 60, 40));
            for (int d = 4; d >= 1; d--) {
                g2.drawString(text, x - d, y);
                g2.drawString(text, x + d, y);
                g2.drawString(text, x, y - d);
                g2.drawString(text, x, y + d);
            }
            g2.setColor(new Color(0xB45309));
            g2.drawString(text, x, y + 3);
            g2.setPaint(new GradientPaint(0, y - fm.getAscent(), new Color(0xFFE27A), 0, y, AppTheme.GOLD));
            g2.drawString(text, x, y);
            int cy = getHeight() / 2;
            if (revealed) {
                drawGift(g2, x - 70, cy - 28, 56, AppTheme.ANSWER_A, 1f);
            } else {
                drawBolt(g2, x - 60, cy, 30);
                drawBolt(g2, x + tw + 30, cy, 30);
            }
            g2.dispose();
        }

        private static void drawBolt(Graphics2D g2, int x, int cy, int h) {
            Polygon p = new Polygon();
            p.addPoint(x + h * 6 / 10, cy - h);
            p.addPoint(x, cy + h / 6);
            p.addPoint(x + h * 4 / 10, cy + h / 6);
            p.addPoint(x + h * 2 / 10, cy + h);
            p.addPoint(x + h * 8 / 10, cy - h / 6);
            p.addPoint(x + h * 4 / 10, cy - h / 6);
            g2.setColor(AppTheme.GOLD);
            g2.fillPolygon(p);
            g2.setColor(new Color(0xB45309));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawPolygon(p);
        }
    }

    /** Vẽ hộp quà trắng có ruy băng màu (dùng cho thẻ và tiêu đề). */
    static void drawGift(Graphics2D g2, int x, int y, int size, Color ribbon, float alpha) {
        int w = size;
        int lidH = size / 5;
        int bodyY = y + size / 4 + lidH;
        int bodyH = size - size / 4 - lidH;
        Color white = new Color(255, 255, 255, Math.round(255 * alpha));
        Color body = new Color(255, 255, 255, Math.round(235 * alpha));
        Color rib = new Color(ribbon.getRed(), ribbon.getGreen(), ribbon.getBlue(), Math.round(255 * alpha));
        // Nơ
        g2.setStroke(new BasicStroke(Math.max(3f, size / 14f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(white);
        g2.drawOval(x + w / 2 - size * 32 / 100, y, size * 30 / 100, size / 4);
        g2.drawOval(x + w / 2 + size * 2 / 100, y, size * 30 / 100, size / 4);
        // Nắp và thân
        g2.setColor(white);
        g2.fillRoundRect(x, y + size / 4, w, lidH, 10, 10);
        g2.setColor(body);
        g2.fillRoundRect(x + size / 16, bodyY, w - size / 8, bodyH, 12, 12);
        // Ruy băng dọc
        g2.setColor(rib);
        int rw = Math.max(6, size / 7);
        g2.fillRect(x + w / 2 - rw / 2, y + size / 4, rw, lidH + bodyH);
    }

    /** Một hộp quà: úp (chọn được), đã chọn chờ mở, hoặc đã lật với hiệu ứng. */
    private static final class Card extends JButton {
        private final int index;
        private boolean pending;
        private boolean chosen;
        private BoxEffect effect;
        private List<String> notes = List.of();

        Card(int index) {
            this.index = index;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(200, 360));
            getAccessibleContext().setAccessibleName("Hộp " + (index + 1));
            addActionListener(e -> SoundPlayer.click());
        }

        void reset() {
            pending = false;
            chosen = false;
            effect = null;
            notes = List.of();
            setEnabled(true);
            repaint();
        }

        void pending(boolean mine) {
            pending = mine;
            setEnabled(false);
            repaint();
        }

        void reveal(BoxEffect effect, boolean chosen) {
            this.effect = effect;
            this.chosen = chosen;
            pending = false;
            setEnabled(false);
            repaint();
        }

        void setNotes(List<String> notes) {
            this.notes = List.copyOf(notes);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            boolean revealed = effect != null;
            boolean dim = revealed && !chosen;
            boolean ring = pending || chosen || !revealed && isEnabled() && (getModel().isRollover() || hasFocus());
            Color base = COLORS[index];
            Color fill = dim ? mix(base, AppTheme.GAME_BG_TOP, .45f) : base;
            int top = ring ? 0 : 12; // thẻ được chọn nhô lên một chút
            int inset = 0;
            if (ring) {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, top, w, h - top, 34, 34);
                g2.setColor(AppTheme.GAME_BG_TOP);
                g2.fillRoundRect(6, top + 6, w - 12, h - top - 12, 28, 28);
                inset = 10;
            } else {
                g2.setColor(mix(fill, Color.BLACK, .4f));
                g2.fillRoundRect(0, top + 6, w, h - top - 6, 28, 28);
            }
            g2.setColor(fill);
            g2.fillRoundRect(inset, top + inset, w - 2 * inset, h - top - 2 * inset - (ring ? 0 : 6), 26, 26);

            Color text = dim ? new Color(255, 255, 255, 150) : Color.WHITE;
            Font label = AppTheme.FONT_BOLD.deriveFont(20f);
            centered(g2, "Hộp " + (index + 1), label, text, top + inset + 48);
            int iconCy = top + (h - top) * 36 / 100;
            if (!revealed) {
                int size = Math.min(w - 2 * inset - 50, (h - top) * 36 / 100);
                drawGift(g2, (w - size) / 2, iconCy - size / 2, size, base, 1f);
                if (pending) {
                    centered(g2, "Đã chọn", AppTheme.FONT_TITLE.deriveFont(22f), Color.WHITE, h - inset - 64);
                    centered(g2, "Đang mở hộp...", AppTheme.FONT_BASE.deriveFont(16f), new Color(255, 255, 255, 220), h - inset - 36);
                } else {
                    centered(g2, "Mở hộp này", AppTheme.FONT_TITLE.deriveFont(22f), Color.WHITE, h - inset - 64);
                    centered(g2, "Bấm để chọn", AppTheme.FONT_BASE.deriveFont(16f), new Color(255, 255, 255, 220), h - inset - 36);
                }
            } else {
                drawEffect(g2, effect, w / 2, iconCy, Math.min(w, h) / 4, dim ? .55f : 1f);
                int y = top + (h - top) * 57 / 100;
                centered(g2, effectName(effect), AppTheme.FONT_TITLE.deriveFont(23f), text, y);
                y = wrapCentered(g2, effectDescription(effect), AppTheme.FONT_BASE.deriveFont(14f),
                        dim ? new Color(255, 255, 255, 130) : new Color(255, 255, 255, 230), y + 28, w - 2 * inset - 24, 2);
                if (!notes.isEmpty()) {
                    drawChip(g2, notes, inset + 14, y + 6, w - 2 * inset - 28, h - inset - 14, mix(fill, Color.BLACK, .25f), text);
                }
                if (chosen) drawBadge(g2, w, top);
            }
            g2.dispose();
        }

        /** Vẽ một dòng chữ căn giữa theo chiều ngang của thẻ. */
        private void centered(Graphics2D g2, String s, Font f, Color c, int baseline) {
            g2.setFont(f);
            g2.setColor(c);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(s, (getWidth() - fm.stringWidth(s)) / 2, baseline);
        }

        /** Vẽ chữ căn giữa, tự xuống dòng tối đa maxLines; trả về baseline dòng cuối. */
        private int wrapCentered(Graphics2D g2, String s, Font f, Color c, int baseline, int width, int maxLines) {
            g2.setFont(f);
            g2.setColor(c);
            FontMetrics fm = g2.getFontMetrics();
            List<String> lines = TextLines.wrap(s, fm, Math.max(40, width));
            int y = baseline;
            for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
                String line = lines.get(i);
                g2.drawString(line, (getWidth() - fm.stringWidth(line)) / 2, y);
                y += fm.getHeight();
            }
            return y - fm.getHeight() + fm.getDescent();
        }

        private void drawChip(Graphics2D g2, List<String> lines, int x, int y, int w, int maxBottom, Color bg, Color fg) {
            g2.setFont(AppTheme.FONT_BASE.deriveFont(13f));
            FontMetrics fm = g2.getFontMetrics();
            List<String> wrapped = new ArrayList<>();
            for (String l : lines) wrapped.addAll(TextLines.wrap(l, fm, w - 20));
            int lineH = fm.getHeight();
            int count = Math.max(1, Math.min(Math.min(3, wrapped.size()), (maxBottom - y - 12) / lineH));
            int h = count * lineH + 12;
            if (y + h > maxBottom) y = maxBottom - h;
            g2.setColor(bg);
            g2.fillRoundRect(x, y, w, h, 14, 14);
            g2.setColor(fg);
            int ty = y + 6 + fm.getAscent();
            for (int i = 0; i < count; i++) {
                String s = wrapped.get(i);
                g2.drawString(s, x + (w - fm.stringWidth(s)) / 2, ty);
                ty += lineH;
            }
        }

        private static void drawBadge(Graphics2D g2, int w, int top) {
            String s = "BẠN CHỌN";
            g2.setFont(AppTheme.FONT_BOLD.deriveFont(15f));
            FontMetrics fm = g2.getFontMetrics();
            int bw = fm.stringWidth(s) + 28;
            int bh = 30;
            int x = (w - bw) / 2;
            g2.setColor(AppTheme.GOLD);
            g2.fillRoundRect(x, top - 2, bw, bh, bh, bh);
            g2.setColor(AppTheme.TITLE);
            g2.drawString(s, x + 14, top - 2 + (bh - fm.getHeight()) / 2 + fm.getAscent());
        }
    }

    /** Biểu tượng hiệu ứng vẽ bằng hình học (không phụ thuộc font emoji). */
    static void drawEffect(Graphics2D g2, BoxEffect e, int cx, int cy, int r, float alpha) {
        int a = Math.round(255 * alpha);
        switch (e) {
            case STEAL -> {
                Color[] rings = {new Color(220, 38, 38, a), new Color(255, 255, 255, a), new Color(220, 38, 38, a),
                        new Color(255, 255, 255, a)};
                for (int i = 0; i < rings.length; i++) {
                    int rr = r - i * r / 4;
                    g2.setColor(rings[i]);
                    g2.fillOval(cx - rr, cy - rr, 2 * rr, 2 * rr);
                }
                g2.setColor(new Color(30, 58, 138, a));
                g2.setStroke(new BasicStroke(Math.max(3f, r / 9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(cx, cy, cx + r, cy - r * 6 / 10);
                g2.setColor(new Color(59, 130, 246, a));
                g2.fillPolygon(new int[]{cx + r, cx + r + r / 3, cx + r * 9 / 10},
                        new int[]{cy - r * 6 / 10, cy - r, cy - r * 3 / 10}, 3);
            }
            case LOSE -> {
                g2.setColor(new Color(31, 41, 55, a));
                g2.fillOval(cx - r * 8 / 10, cy - r * 6 / 10, r * 16 / 10, r * 16 / 10);
                g2.setColor(new Color(75, 85, 99, a));
                g2.fillOval(cx - r * 5 / 10, cy - r * 3 / 10, r * 4 / 10, r * 4 / 10);
                g2.setColor(new Color(120, 113, 108, a));
                g2.setStroke(new BasicStroke(Math.max(3f, r / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D fuse = new Path2D.Float();
                fuse.moveTo(cx + r * 4 / 10, cy - r * 5 / 10);
                fuse.quadTo(cx + r * 6 / 10, cy - r, cx + r * 8 / 10, cy - r * 9 / 10);
                g2.draw(fuse);
                g2.setColor(new Color(251, 146, 60, a));
                int sx = cx + r * 85 / 100;
                int sy = cy - r * 95 / 100;
                int sr = Math.max(6, r / 4);
                Polygon star = new Polygon();
                for (int i = 0; i < 10; i++) {
                    double ang = Math.PI / 5 * i;
                    int rad = i % 2 == 0 ? sr : sr / 2;
                    star.addPoint(sx + (int) (Math.cos(ang) * rad), sy + (int) (Math.sin(ang) * rad));
                }
                g2.fillPolygon(star);
            }
            case DOUBLE -> {
                String s = "×2";
                g2.setFont(AppTheme.FONT_TITLE.deriveFont((float) r * 1.6f));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(new Color(255, 255, 255, a));
                g2.drawString(s, cx - fm.stringWidth(s) / 2, cy - fm.getHeight() / 2 + fm.getAscent());
            }
            case NO_LATE -> {
                int w = r * 9 / 10;
                int h = r;
                g2.setColor(new Color(120, 53, 15, a));
                g2.fillRoundRect(cx - w, cy - h - 6, 2 * w, 10, 6, 6);
                g2.fillRoundRect(cx - w, cy + h - 4, 2 * w, 10, 6, 6);
                g2.setColor(new Color(219, 234, 254, a));
                g2.fillPolygon(new int[]{cx - w + 6, cx + w - 6, cx}, new int[]{cy - h + 4, cy - h + 4, cy}, 3);
                g2.fillPolygon(new int[]{cx - w + 6, cx + w - 6, cx}, new int[]{cy + h - 4, cy + h - 4, cy}, 3);
                g2.setColor(new Color(245, 158, 11, a));
                g2.fillPolygon(new int[]{cx - w / 2, cx + w / 2, cx}, new int[]{cy - h / 2, cy - h / 2, cy}, 3);
                g2.fillPolygon(new int[]{cx - w + 10, cx + w - 10, cx}, new int[]{cy + h - 4, cy + h - 4, cy + h / 3}, 3);
            }
        }
    }

    /** Cột người chơi: trước khi mở = điểm + chip trạng thái; sau khi mở = 2 dòng "tên · điểm" và "Hộp N · hiệu ứng". */
    private static final class PlayerList extends JComponent {
        record Row(int rank, String name, int score, boolean mine, String chip, boolean chosen, String detail) {
        }

        private List<Row> rows = List.of();
        private boolean detailed;

        PlayerList() {
            setOpaque(false);
        }

        void setRows(List<Row> rows, boolean detailed) {
            this.rows = List.copyOf(rows);
            this.detailed = detailed;
            revalidate();
            repaint();
        }

        private int rowHeight() {
            return detailed ? 64 : 56;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(300, Math.max(1, rows.size()) * rowHeight());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int y = 0;
            int rh = rowHeight();
            Font name = AppTheme.FONT_BASE.deriveFont(18f);
            Font nameBold = AppTheme.FONT_BOLD.deriveFont(18f);
            Font score = AppTheme.FONT_BOLD.deriveFont(18f);
            Font small = AppTheme.FONT_BASE.deriveFont(14f);
            for (Row r : rows) {
                if (r.mine()) {
                    g2.setColor(new Color(255, 255, 255, 26));
                    g2.fillRoundRect(0, y + 2, w, rh - 4, 14, 14);
                }
                g2.setColor(AppTheme.GAME_LINE);
                g2.fillRect(4, y + rh - 1, w - 8, 1);
                String left = r.rank() + ". " + r.name() + (r.mine() ? " (bạn)" : "");
                if (!detailed) {
                    int cy = y + rh / 2;
                    // Chip trạng thái ở phải
                    g2.setFont(small);
                    FontMetrics fm = g2.getFontMetrics();
                    int cw = fm.stringWidth(r.chip()) + 22;
                    int ch = 28;
                    int cx = w - cw - 6;
                    g2.setColor(r.chosen() ? new Color(34, 197, 94, 60) : new Color(255, 255, 255, 34));
                    g2.fillRoundRect(cx, cy - ch / 2, cw, ch, ch, ch);
                    g2.setColor(r.chosen() ? AppTheme.GAME_CORRECT_TEXT : new Color(0xE9E6FF));
                    g2.drawString(r.chip(), cx + 11, cy - fm.getHeight() / 2 + fm.getAscent());
                    g2.setFont(score);
                    fm = g2.getFontMetrics();
                    String sc = Format.points(r.score());
                    int sx = cx - 12 - fm.stringWidth(sc);
                    g2.setColor(AppTheme.GOLD);
                    g2.drawString(sc, sx, cy - fm.getHeight() / 2 + fm.getAscent());
                    g2.setFont(r.mine() ? nameBold : name);
                    fm = g2.getFontMetrics();
                    g2.setColor(Color.WHITE);
                    g2.drawString(clip(left, fm, sx - 20), 10, cy - fm.getHeight() / 2 + fm.getAscent());
                } else {
                    g2.setFont(r.mine() ? nameBold : name);
                    FontMetrics fm = g2.getFontMetrics();
                    int base1 = y + 8 + fm.getAscent();
                    String first = clip(left, fm, w - 110) + " · ";
                    g2.setColor(Color.WHITE);
                    g2.drawString(first, 10, base1);
                    int fx = 10 + fm.stringWidth(first);
                    g2.setFont(score);
                    g2.setColor(AppTheme.GOLD);
                    g2.drawString(Format.points(r.score()), fx, base1);
                    g2.setFont(small);
                    fm = g2.getFontMetrics();
                    g2.setColor(r.chosen() ? new Color(0xD6D2F5) : AppTheme.GAME_MUTED);
                    g2.drawString(clip(r.detail(), fm, w - 20), 10, base1 + 4 + fm.getHeight());
                }
                y += rh;
            }
            g2.dispose();
        }

        private static String clip(String s, FontMetrics fm, int max) {
            if (fm.stringWidth(s) <= max) return s;
            while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
            return s + "…";
        }
    }

    /** Thanh đếm ngược vàng. */
    private static final class Bar extends JComponent {
        private float fraction = 1f;

        Bar() {
            setPreferredSize(new Dimension(300, 76));
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
                g2.setColor(AppTheme.GOLD);
                g2.fillRoundRect(0, y, w, h, h, h);
            }
            g2.dispose();
        }
    }

    /** Viên thuốc "Câu N bắt đầu sau 3 giây...". */
    private static final class Pill extends JLabel {
        Pill() {
            super(" ", SwingConstants.CENTER);
            setFont(AppTheme.FONT_BASE.deriveFont(19f));
            setForeground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(12, 26, 12, 26));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(255, 255, 255, 30));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
