package quizgame.client.ui.dialog;

import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Dialogs;
import quizgame.client.ui.MainFrame;
import quizgame.client.ui.component.AppButton;
import quizgame.client.util.Async;
import quizgame.common.model.Invite;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Hộp thoại lời mời không modal. Lời mời đến nhiều thì xếp hàng, hiện lần lượt;
 * mỗi lời mời đếm ngược theo expiresInMs rồi tự đóng.
 */
public class InviteDialog extends JDialog {
    private static final int TICK_MS = 100;

    private final MainFrame frame;
    private final Deque<Pending> queue = new ArrayDeque<>();
    private final JLabel heading = new JLabel();
    private final JLabel message = new JLabel();
    private final JProgressBar bar = new JProgressBar();
    private final Timer timer;
    private int total;
    private Pending current;

    /** Một lời mời và mốc hết hạn tính từ lúc nhận. */
    private record Pending(Invite invite, long deadlineNanos) {
    }

    public InviteDialog(MainFrame frame) {
        super(frame, "Lời mời thi đấu", false);
        this.frame = frame;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(0, AppTheme.GAP_S));
        content.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        content.setBackground(AppTheme.CARD);
        heading.setFont(AppTheme.FONT_TITLE.deriveFont(17f));
        heading.setForeground(AppTheme.TITLE);
        message.setFont(AppTheme.FONT_BASE);
        bar.setStringPainted(true);
        bar.setForeground(AppTheme.ORANGE);
        bar.setPreferredSize(new Dimension(100, 18));

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        top.add(heading, BorderLayout.NORTH);
        top.add(message, BorderLayout.CENTER);
        top.add(bar, BorderLayout.SOUTH);
        content.add(top, BorderLayout.CENTER);

        AppButton decline = AppButton.soft("TỪ CHỐI");
        AppButton accept = AppButton.primary("ĐỒNG Ý");
        decline.addActionListener(e -> reply(false));
        accept.addActionListener(e -> reply(true));
        JPanel buttons = new JPanel(new GridLayout(1, 2, AppTheme.GAP_M, 0));
        buttons.setOpaque(false);
        buttons.add(decline);
        buttons.add(accept);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        setPreferredSize(new Dimension(400, 200));
        pack();

        timer = new Timer(TICK_MS, e -> tick());
    }

    /** Thêm một lời mời mới vào hàng đợi (gọi trên luồng giao diện). */
    public void enqueue(Invite invite) {
        queue.add(new Pending(invite, System.nanoTime() + invite.expiresInMs() * 1_000_000L));
        total++;
        if (current == null) {
            next();
        } else {
            updateHeading();
        }
    }

    /** Hủy hết và đóng (đăng xuất, mất kết nối, vào phòng). */
    public void clear() {
        queue.clear();
        current = null;
        total = 0;
        timer.stop();
        setVisible(false);
    }

    private void next() {
        current = queue.poll();
        if (current == null) {
            total = 0;
            timer.stop();
            setVisible(false);
            return;
        }
        Invite i = current.invite();
        message.setText(i.fromUser() + " mời bạn vào phòng " + i.roomCode());
        bar.setMaximum((int) Math.max(1, i.expiresInMs()));
        updateHeading();
        tick();
        timer.start();
        if (!isVisible()) {
            setLocationRelativeTo(frame);
            setVisible(true);
        }
    }

    private void updateHeading() {
        int shown = total - queue.size();
        heading.setText("LỜI MỜI THI ĐẤU (" + shown + "/" + total + ")");
    }

    private void tick() {
        if (current == null) {
            return;
        }
        long remainingMs = (current.deadlineNanos() - System.nanoTime()) / 1_000_000L;
        if (remainingMs <= 0) {
            next(); // hết giờ: Server tự hủy lời mời, chỉ cần đóng và hiện cái kế tiếp
            return;
        }
        bar.setValue((int) remainingMs);
        bar.setString((remainingMs + 999) / 1000 + "s");
    }

    private void reply(boolean accept) {
        if (current == null) {
            return;
        }
        Invite invite = current.invite();
        if (accept) {
            clear(); // chấp nhận 1 lời thì các lời còn lại bị hủy
        } else {
            next();
        }
        Async.ui(frame.client().replyInvite(invite.id(), accept), room -> {
            if (accept && room != null) {
                frame.showRoom(room);
            }
        }, error -> Dialogs.error(frame, error));
    }
}
