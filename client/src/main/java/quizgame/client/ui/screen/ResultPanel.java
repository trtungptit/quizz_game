package quizgame.client.ui.screen;

import quizgame.client.ui.AppTheme;
import quizgame.client.ui.MainFrame;
import quizgame.client.ui.component.AppButton;
import quizgame.client.ui.component.CardPanel;
import quizgame.client.ui.component.GradientPanel;
import quizgame.client.ui.component.MatchResultTable;
import quizgame.client.ui.component.SectionTitle;
import quizgame.common.model.MatchResult;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Locale;

/** Kết quả trận: biểu ngữ người thắng, bảng kết quả, nút VỀ PHÒNG CHỜ / RỜI PHÒNG. */
public class ResultPanel extends JPanel {
    private final MainFrame frame;
    private final JLabel bannerText = new JLabel(" ", SwingConstants.CENTER);
    private final CardPanel tableCard = new CardPanel(new BorderLayout());

    public ResultPanel(MainFrame frame) {
        super(new BorderLayout(0, AppTheme.GAP_M));
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(AppTheme.GAP_L, AppTheme.GAP_L, AppTheme.GAP_L, AppTheme.GAP_L));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        SectionTitle title = new SectionTitle("Kết quả trận đấu");
        title.setAlignmentX(LEFT_ALIGNMENT);
        top.add(title);
        top.add(Box.createVerticalStrut(AppTheme.GAP_M));
        JPanel banner = GradientPanel.accent(new BorderLayout(), 36);
        banner.setBorder(BorderFactory.createEmptyBorder(22, 16, 22, 16));
        bannerText.setFont(AppTheme.FONT_TITLE.deriveFont(30f));
        bannerText.setForeground(new java.awt.Color(0x422006));
        banner.add(bannerText, BorderLayout.CENTER);
        banner.setAlignmentX(LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        top.add(banner);
        add(top, BorderLayout.NORTH);

        tableCard.setBorder(BorderFactory.createEmptyBorder());
        add(tableCard, BorderLayout.CENTER);

        AppButton leave = AppButton.soft("RỜI PHÒNG");
        AppButton back = AppButton.primary("VỀ PHÒNG CHỜ");
        leave.addActionListener(e -> frame.leaveRoom());
        back.addActionListener(e -> frame.showScreen(MainFrame.ROOM));
        JPanel buttons = new JPanel(new GridLayout(1, 2, AppTheme.GAP_M, 0));
        buttons.setOpaque(false);
        buttons.add(leave);
        buttons.add(back);
        buttons.setPreferredSize(new Dimension(100, 44));
        add(buttons, BorderLayout.SOUTH);
    }

    /** Dựng màn từ kết quả trận; totalQuestions = số câu của trận. */
    public void show(MatchResult result, int totalQuestions) {
        bannerText.setText(bannerFor(result, frame.username()));
        // Bảng cần biết tên của mình để tô vàng nên dựng mới cho đúng người dùng hiện tại
        MatchResultTable table = new MatchResultTable(frame.username());
        table.setResult(result, totalQuestions > 0 ? totalQuestions : null);
        tableCard.removeAll();
        tableCard.add(table, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /** Chữ trên biểu ngữ: hòa, mình thắng, hoặc tên người thắng. */
    public static String bannerFor(MatchResult result, String me) {
        if (result.draw()) {
            return "HÒA, KHÔNG AI ĐƯỢC TÍNH THẮNG";
        }
        if (result.winner() == null) {
            return "TRẬN ĐẤU KẾT THÚC";
        }
        if (result.winner().equals(me)) {
            return "BẠN THẮNG!";
        }
        return result.winner().toUpperCase(Locale.ROOT) + " THẮNG!";
    }
}
