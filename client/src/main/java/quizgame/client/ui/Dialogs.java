package quizgame.client.ui;

import javax.swing.JOptionPane;
import java.awt.Component;

/** Hộp thoại thông báo, xác nhận và nhập chữ dùng chung. */
public final class Dialogs {
    private Dialogs() {
    }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, AppTheme.APP_NAME, JOptionPane.ERROR_MESSAGE);
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, AppTheme.APP_NAME, JOptionPane.INFORMATION_MESSAGE);
    }

    /** Hỏi Có/Không; true nếu chọn Có. */
    public static boolean confirm(Component parent, String message) {
        Object[] options = {"Đồng ý", "Hủy"};
        return JOptionPane.showOptionDialog(parent, message, AppTheme.APP_NAME, JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[1]) == 0;
    }

    /** Hỏi một dòng chữ; null nếu hủy hoặc để trống. */
    public static String ask(Component parent, String message, String initial) {
        Object value = JOptionPane.showInputDialog(parent, message, AppTheme.APP_NAME, JOptionPane.QUESTION_MESSAGE,
                null, null, initial);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString().trim();
    }
}
