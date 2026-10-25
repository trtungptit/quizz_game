package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;

/** Một vùng dữ liệu với 4 trạng thái: nội dung, đang tải, rỗng (chữ hướng dẫn), lỗi + nút THỬ LẠI. */
public class StatePanel extends JPanel {
    private static final String CONTENT = "content";
    private static final String LOADING = "loading";
    private static final String EMPTY = "empty";
    private static final String ERROR = "error";

    private final CardLayout layout = new CardLayout();
    private final JLabel emptyLabel = centered("");
    private final JLabel errorLabel = centered("");
    private Runnable retry = () -> { };

    public StatePanel(JComponent content) {
        setLayout(layout);
        setOpaque(false);
        add(content, CONTENT);
        add(wrapCenter(centered("Đang tải...")), LOADING);
        add(wrapCenter(emptyLabel), EMPTY);

        AppButton retryButton = AppButton.primary("THỬ LẠI");
        retryButton.setPreferredSize(new Dimension(120, 40));
        retryButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        retryButton.addActionListener(e -> retry.run());
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        errorLabel.setForeground(AppTheme.WRONG);
        JPanel error = new JPanel();
        error.setOpaque(false);
        error.setLayout(new BoxLayout(error, BoxLayout.Y_AXIS));
        error.add(Box.createVerticalGlue());
        error.add(errorLabel);
        error.add(Box.createVerticalStrut(AppTheme.GAP_M));
        error.add(retryButton);
        error.add(Box.createVerticalGlue());
        add(error, ERROR);
        showContent();
    }

    private static JLabel centered(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(AppTheme.FONT_BASE.deriveFont(15f));
        label.setForeground(AppTheme.TEXT_MUTED);
        return label;
    }

    private static JPanel wrapCenter(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    public void showContent() {
        layout.show(this, CONTENT);
    }

    public void showLoading() {
        layout.show(this, LOADING);
    }

    public void showEmpty(String text) {
        emptyLabel.setText(text);
        layout.show(this, EMPTY);
    }

    /** Hiện lỗi và nút THỬ LẠI chạy retry. */
    public void showError(String message, Runnable retry) {
        errorLabel.setText("<html><div style='text-align:center;width:360px'>" + escape(message) + "</div></html>");
        this.retry = retry;
        layout.show(this, ERROR);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
