package quizgame.client.ui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.geom.Point2D;

/** Token giao diện: nền trung tính, tím nhận diện và màu trạng thái có ý nghĩa. */
public final class AppTheme {
    public static final String APP_NAME = "QUIZ";

    // Màu
    public static final Color BACKGROUND = new Color(0xF7F8FC);
    public static final Color GRADIENT_START = new Color(0x4C1D95);
    public static final Color GRADIENT_MID = new Color(0x5B21B6);
    public static final Color GRADIENT_END = new Color(0x6D28D9);
    public static final Color PRIMARY = new Color(0x6D28D9);
    public static final Color ACCENT = new Color(0xFFC83D);
    public static final Color CORRECT = new Color(0x15803D);
    public static final Color WRONG = new Color(0xB91C1C);
    public static final Color CORRECT_SOFT = new Color(0xEDF9F0);
    public static final Color WRONG_SOFT = new Color(0xFFF1F2);
    public static final Color NEUTRAL = new Color(0xF1F3F7);
    public static final Color CARD = Color.WHITE;
    public static final Color TEXT = new Color(0x1F2937);
    public static final Color TEXT_MUTED = new Color(0x627086);
    public static final Color BORDER = new Color(0xE5E7EB);
    public static final Color SOFT = new Color(0xF3E8FF);
    public static final Color SOFT_TEXT = new Color(0x4C1D95);

    public static final Color TITLE = TEXT;
    public static final Color ORANGE = new Color(0xA16207);
    public static final Color HIGHLIGHT = new Color(0xFFF1C2);
    public static final Color GOLD = new Color(0xFFD54A);
    public static final Color SILVER = new Color(0xD9DCE3);
    public static final Color BRONZE = new Color(0xF4B27A);
    public static final Color ROW_BG = new Color(0xF8FAFC);
    // Điểm màu nhỏ cho ký hiệu đáp án; không tô cả khối bằng màu này.
    public static final Color ANSWER_A = new Color(0xBE3455);
    public static final Color ANSWER_B = new Color(0x2563EB);
    public static final Color ANSWER_C = new Color(0xA16207);
    public static final Color ANSWER_D = new Color(0x15803D);

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
    public static final int ARC_CARD = 14;
    public static final int ARC_BUTTON = 9;
    public static final int BUTTON_DEPTH = 0;

    private AppTheme() {
    }

    /** Cài FlatLaf sáng và các thuộc tính UIManager cho phong cách của ứng dụng. */
    public static void install() {
        FlatLightLaf.setup();
        UIManager.put("Component.arc", 18);
        UIManager.put("Button.arc", 18);
        UIManager.put("TextComponent.arc", 18);
        UIManager.put("Component.borderWidth", 1);
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

    /** Thanh điều hướng dùng tím phẳng; giữ API để không đổi bố cục các màn. */
    public static Paint horizontalGradient(int width) {
        return PRIMARY;
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
