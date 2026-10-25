package quizgame.client.ui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.geom.Point2D;

/** Màu, font, khoảng cách dùng chung và hàm cài FlatLaf theo phong cách của spec. */
public final class AppTheme {
    public static final String APP_NAME = "QUIZ";

    // Màu
    public static final Color BACKGROUND = new Color(0xF4F1FF);
    public static final Color GRADIENT_START = new Color(0x3B1FD1);
    public static final Color GRADIENT_MID = new Color(0x7C3AED);
    public static final Color GRADIENT_END = new Color(0xC026D3);
    public static final Color PRIMARY = new Color(0x5B3DF5);
    public static final Color ACCENT = new Color(0xFFC83D);
    public static final Color CORRECT = new Color(0x22C55E);
    public static final Color WRONG = new Color(0xEF4444);
    public static final Color CARD = Color.WHITE;
    public static final Color TEXT = new Color(0x1E1B4B);
    public static final Color TEXT_MUTED = new Color(0x6B7280);
    public static final Color BORDER = new Color(0xDDD6FE);
    public static final Color SOFT = new Color(0xEDE9FE);
    public static final Color SOFT_TEXT = new Color(0x4C1D95);

    public static final Color TITLE = new Color(0x2E1065);
    public static final Color ORANGE = new Color(0xFF9F1C);
    public static final Color HIGHLIGHT = new Color(0xFFF1C2);
    public static final Color GOLD = new Color(0xFFD54A);
    public static final Color SILVER = new Color(0xD9DCE3);
    public static final Color BRONZE = new Color(0xF4B27A);
    public static final Color ROW_BG = new Color(0xF8F6FF);
    // Dải màu trên đầu các thẻ
    public static final Color[] STRIPES = {
            new Color(0x7C3AED), new Color(0xEC4899), new Color(0xF59E0B), new Color(0x10B981), new Color(0x3B82F6)
    };

    // Bốn màu nút đáp án: A đỏ, B xanh dương, C cam, D xanh lá
    public static final Color ANSWER_A = new Color(0xEF4444);
    public static final Color ANSWER_B = new Color(0x3B82F6);
    public static final Color ANSWER_C = new Color(0xF59E0B);
    public static final Color ANSWER_D = new Color(0x22C55E);

    // Font
    public static final String FONT_FAMILY = "Segoe UI";
    public static final Font FONT_BASE = new Font(FONT_FAMILY, Font.PLAIN, 14);
    public static final Font FONT_BOLD = new Font(FONT_FAMILY, Font.BOLD, 14);
    public static final Font FONT_SMALL = new Font(FONT_FAMILY, Font.PLAIN, 12);
    public static final Font FONT_TITLE = new Font(FONT_FAMILY, Font.BOLD, 20);
    public static final Font FONT_LOGO = new Font(FONT_FAMILY, Font.BOLD, 44);

    // Khoảng cách và bo góc
    public static final int GAP_S = 8;
    public static final int GAP_M = 16;
    public static final int GAP_L = 24;
    public static final int ARC_CARD = 18;
    public static final int ARC_BUTTON = 12;
    public static final int BUTTON_DEPTH = 3;

    private AppTheme() {
    }

    /** Cài FlatLaf sáng và các thuộc tính UIManager cho phong cách của ứng dụng. */
    public static void install() {
        FlatLightLaf.setup();
        UIManager.put("Component.arc", 16);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("Component.borderWidth", 2);
        UIManager.put("Component.borderColor", BORDER);
        UIManager.put("Component.focusedBorderColor", PRIMARY);
        UIManager.put("Component.focusColor", PRIMARY);
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("defaultFont", FONT_BASE);
        localize();
    }

    /** Việt hóa nút và nhãn của JOptionPane và JFileChooser (mặc định theo ngôn ngữ hệ thống). */
    private static void localize() {
        String[][] texts = {
                {"OptionPane.okButtonText", "Đồng ý"}, {"OptionPane.cancelButtonText", "Hủy"},
                {"OptionPane.yesButtonText", "Có"}, {"OptionPane.noButtonText", "Không"},
                {"FileChooser.openButtonText", "Mở"}, {"FileChooser.saveButtonText", "Lưu"},
                {"FileChooser.cancelButtonText", "Hủy"}, {"FileChooser.openDialogTitleText", "Mở"},
                {"FileChooser.saveDialogTitleText", "Lưu"}, {"FileChooser.lookInLabelText", "Tìm trong:"},
                {"FileChooser.saveInLabelText", "Lưu vào:"}, {"FileChooser.fileNameLabelText", "Tên tệp:"},
                {"FileChooser.filesOfTypeLabelText", "Loại tệp:"}, {"FileChooser.upFolderToolTipText", "Lên một cấp"},
                {"FileChooser.homeFolderToolTipText", "Thư mục chính"},
                {"FileChooser.newFolderToolTipText", "Thư mục mới"},
                {"FileChooser.listViewButtonToolTipText", "Danh sách"},
                {"FileChooser.detailsViewButtonToolTipText", "Chi tiết"},
                {"FileChooser.openButtonToolTipText", "Mở tệp đã chọn"},
                {"FileChooser.saveButtonToolTipText", "Lưu tệp"}, {"FileChooser.cancelButtonToolTipText", "Hủy"},
                {"FileChooser.refreshActionLabelText", "Làm mới"},
                {"FileChooser.newFolderActionLabelText", "Thư mục mới"},
                {"FileChooser.viewMenuLabelText", "Xem"}, {"FileChooser.listViewActionLabelText", "Danh sách"},
                {"FileChooser.detailsViewActionLabelText", "Chi tiết"},
                {"FileChooser.fileNameHeaderText", "Tên"}, {"FileChooser.fileSizeHeaderText", "Kích thước"},
                {"FileChooser.fileTypeHeaderText", "Loại"}, {"FileChooser.fileDateHeaderText", "Ngày sửa"},
                {"FileChooser.acceptAllFileFilterText", "Tất cả tệp"},
        };
        for (String[] t : texts) {
            UIManager.put(t[0], t[1]);
        }
    }

    /** Gradient ngang tím -> tím sáng -> hồng cho thanh điều hướng. */
    public static Paint horizontalGradient(int width) {
        return gradient(new Point2D.Float(0, 0), new Point2D.Float(Math.max(width, 1), 0));
    }

    /** Gradient chéo cho nền màn đăng nhập. */
    public static Paint diagonalGradient(int width, int height) {
        return gradient(new Point2D.Float(0, 0), new Point2D.Float(Math.max(width, 1), Math.max(height, 1)));
    }

    private static Paint gradient(Point2D start, Point2D end) {
        return new LinearGradientPaint(start, end, new float[]{0f, 0.5f, 1f},
                new Color[]{GRADIENT_START, GRADIENT_MID, GRADIENT_END});
    }
}
