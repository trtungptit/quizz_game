package quizgame.client.ui.screen;

import quizgame.client.net.GameClient;
import quizgame.client.ui.AppTheme;
import quizgame.client.ui.component.AppButton;
import quizgame.client.ui.component.GradientPanel;
import quizgame.client.ui.component.RoundPanel;
import quizgame.client.util.Async;
import quizgame.common.model.UserInfo;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

/** Màn đăng nhập / đăng ký: hai tab, ô máy chủ (nhớ lần trước), dòng lỗi đỏ. */
public class LoginPanel extends GradientPanel {
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "9090";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,20}");
    private static final int CARD_WIDTH = 440;
    private static final int INNER_WIDTH = CARD_WIDTH - 2 * 32;

    private final GameClient client;
    private final Consumer<UserInfo> onLoggedIn;
    private final Preferences prefs;

    private final AppButton tabLogin = AppButton.primary("ĐĂNG NHẬP");
    private final AppButton tabRegister = AppButton.soft("ĐĂNG KÝ");
    private final JTextField hostField = new JTextField();
    private final JTextField portField = new JTextField();
    private final JTextField userField = new JTextField();
    private final JPasswordField passField = new JPasswordField();
    private final JPasswordField pass2Field = new JPasswordField();
    private final JLabel pass2Label = fieldLabel("Nhập lại mật khẩu");
    private final JComponent pass2Row = wrap(pass2Field);
    private final JLabel messageLabel = new JLabel(" ");
    private final AppButton submitButton = AppButton.primary("ĐĂNG NHẬP");

    private boolean registerMode;

    public LoginPanel(GameClient client, Consumer<UserInfo> onLoggedIn, Preferences prefs) {
        super(new GridBagLayout(), AppTheme::diagonalGradient, 0);
        this.client = client;
        this.onLoggedIn = onLoggedIn;
        this.prefs = prefs;
        hostField.setText(prefs.get("host", DEFAULT_HOST));
        portField.setText(prefs.get("port", DEFAULT_PORT));
        add(buildCard());
        setMode(false);
    }

    // ---------------------------------------------------------------- Giao diện

    private JPanel buildCard() {
        JPanel card = new RoundPanel(null, AppTheme.CARD, AppTheme.ARC_CARD * 2);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));

        card.add(wrap(buildLogo()));
        card.add(Box.createVerticalStrut(AppTheme.GAP_M));

        JPanel tabs = new JPanel(new GridLayout(1, 2, AppTheme.GAP_M, 0));
        tabs.setOpaque(false);
        tabs.add(tabLogin);
        tabs.add(tabRegister);
        tabLogin.addActionListener(e -> setMode(false));
        tabRegister.addActionListener(e -> setMode(true));
        card.add(wrap(tabs));
        card.add(Box.createVerticalStrut(AppTheme.GAP_S));

        card.add(wrap(fieldLabel("Máy chủ")));
        JPanel server = new JPanel(new BorderLayout(AppTheme.GAP_S, 0));
        server.setOpaque(false);
        portField.setPreferredSize(new Dimension(90, 40));
        hostField.setPreferredSize(new Dimension(0, 40));
        styleField(hostField);
        styleField(portField);
        server.add(hostField, BorderLayout.CENTER);
        server.add(portField, BorderLayout.EAST);
        card.add(wrap(server));
        card.add(Box.createVerticalStrut(AppTheme.GAP_S));

        card.add(wrap(fieldLabel("Tên đăng nhập")));
        card.add(wrap(userField));
        card.add(Box.createVerticalStrut(AppTheme.GAP_S));

        card.add(wrap(fieldLabel("Mật khẩu")));
        card.add(wrap(passField));
        card.add(Box.createVerticalStrut(AppTheme.GAP_S));

        card.add(wrap(pass2Label));
        card.add(pass2Row);
        card.add(Box.createVerticalStrut(AppTheme.GAP_S));

        messageLabel.setFont(AppTheme.FONT_SMALL.deriveFont(13f));
        messageLabel.setVerticalAlignment(SwingConstants.TOP);
        messageLabel.setPreferredSize(new Dimension(INNER_WIDTH, 36));
        card.add(wrap(messageLabel));
        card.add(Box.createVerticalStrut(AppTheme.GAP_S));

        submitButton.setPreferredSize(new Dimension(INNER_WIDTH, 40));
        submitButton.addActionListener(e -> submit());
        card.add(wrap(submitButton));

        for (JTextField f : new JTextField[]{userField, passField, pass2Field, hostField, portField}) {
            styleField(f);
            f.addActionListener(e -> submit());
        }
        return card;
    }

    private JComponent buildLogo() {
        JLabel logo = new JLabel(AppTheme.APP_NAME, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                int tw = g2.getFontMetrics().stringWidth(getText());
                int x = (getWidth() - tw) / 2;
                int y = (getHeight() - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent();
                g2.setPaint(new GradientPaint(x, 0, AppTheme.GRADIENT_START, x + tw, 0, AppTheme.GRADIENT_END));
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        logo.setFont(AppTheme.FONT_LOGO);
        logo.setPreferredSize(new Dimension(INNER_WIDTH, 56));
        return logo;
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.FONT_BOLD);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return label;
    }

    private static void styleField(JTextField field) {
        field.setMargin(new Insets(6, 12, 6, 12));
        field.setFont(AppTheme.FONT_BASE);
    }

    /** Bọc thành phần vào một hàng kéo hết bề rộng thẻ, căn trái. */
    private static JComponent wrap(JComponent inner) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(inner, BorderLayout.CENTER);
        row.setAlignmentX(LEFT_ALIGNMENT);
        if (inner instanceof JTextField) {
            inner.setPreferredSize(new Dimension(INNER_WIDTH, 40));
        }
        Dimension pref = row.getPreferredSize();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        return row;
    }

    private void setMode(boolean register) {
        registerMode = register;
        tabLogin.setColors(register ? AppTheme.SOFT : AppTheme.PRIMARY, register ? AppTheme.SOFT_TEXT : Color.WHITE);
        tabRegister.setColors(register ? AppTheme.PRIMARY : AppTheme.SOFT, register ? Color.WHITE : AppTheme.SOFT_TEXT);
        pass2Label.setVisible(register);
        pass2Row.setVisible(register);
        submitButton.setText(register ? "ĐĂNG KÝ" : "ĐĂNG NHẬP");
        clearMessage();
        revalidate();
        repaint();
    }

    // ---------------------------------------------------------------- Thông báo

    private void showError(String text) {
        messageLabel.setForeground(AppTheme.WRONG);
        messageLabel.setText(html(text));
    }

    private void showInfo(String text) {
        messageLabel.setForeground(AppTheme.TEXT_MUTED);
        messageLabel.setText(html(text));
    }

    private void clearMessage() {
        messageLabel.setText(" ");
    }

    private static String html(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><div style='width:" + (INNER_WIDTH - 8) + "px'>" + escaped + "</div></html>";
    }

    private void setBusy(boolean busy) {
        submitButton.setEnabled(!busy);
        tabLogin.setEnabled(!busy);
        tabRegister.setEnabled(!busy);
    }

    /** Đưa màn về trạng thái ban đầu (khi quay lại từ đăng xuất hoặc mất kết nối). */
    public void reset() {
        passField.setText("");
        pass2Field.setText("");
        clearMessage();
        setBusy(false);
    }

    // ---------------------------------------------------------------- Kiểm tra dữ liệu

    /** Kiểm tra thông tin đăng ký; trả về câu lỗi tiếng Việt hoặc null nếu hợp lệ. */
    public static String validateRegister(String username, String password, String password2) {
        if (!USERNAME.matcher(username).matches()) {
            return "Tên đăng nhập gồm 3-20 ký tự chữ, số hoặc dấu _";
        }
        if (password.length() < 6 || password.length() > 64) {
            return "Mật khẩu phải từ 6 đến 64 ký tự";
        }
        if (!password.equals(password2)) {
            return "Hai mật khẩu không khớp";
        }
        return null;
    }

    // ---------------------------------------------------------------- Gửi yêu cầu

    private void submit() {
        if (!submitButton.isEnabled()) {
            return;
        }
        String host = hostField.getText().trim();
        String username = userField.getText().trim();
        String password = new String(passField.getPassword());
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            showError("Cổng không hợp lệ (1-65535)");
            return;
        }
        if (host.isEmpty()) {
            showError("Hãy nhập địa chỉ máy chủ");
            return;
        }
        if (registerMode) {
            String error = validateRegister(username, password, new String(pass2Field.getPassword()));
            if (error != null) {
                showError(error);
                return;
            }
        } else if (username.isEmpty() || password.isEmpty()) {
            showError("Hãy nhập tên đăng nhập và mật khẩu");
            return;
        }

        prefs.put("host", host);
        prefs.put("port", String.valueOf(port));
        setBusy(true);
        showInfo("Đang kết nối...");

        boolean register = registerMode;
        client.connect(host, port).whenComplete((v, connectError) -> {
            if (connectError != null) {
                SwingUtilities.invokeLater(() -> fail(Async.message(connectError)));
                return;
            }
            var chain = register
                    ? client.register(username, password).thenCompose(x -> client.login(username, password))
                    : client.login(username, password);
            chain.whenComplete((user, error) -> {
                if (error != null) {
                    client.close();
                    SwingUtilities.invokeLater(() -> fail(Async.message(error)));
                } else {
                    SwingUtilities.invokeLater(() -> {
                        setBusy(false);
                        clearMessage();
                        onLoggedIn.accept(user);
                    });
                }
            });
        });
    }

    private void fail(String message) {
        setBusy(false);
        showError(message);
    }

}
