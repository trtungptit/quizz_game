package quizgame.client.ui.component;

import org.junit.jupiter.api.Test;
import quizgame.client.ui.AppTheme;
import quizgame.client.util.ImageUtil;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ImagePickerTest {
    @Test
    void emptyPickerShowsCenteredUploadArtworkWithoutCreatingQuestionImageData() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            ImagePicker picker = new ImagePicker();
            JFrame host = new JFrame(); host.setUndecorated(true); host.setContentPane(picker); host.pack();
            try {
                for (Dimension size : List.of(new Dimension(380, 260), new Dimension(310, 230))) {
                    host.setSize(size); host.validate(); layout(picker);
                    JLabel artwork = labels(picker).stream().filter(l -> l.getIcon() != null).findFirst()
                            .orElseThrow(() -> new AssertionError("Khung tải ảnh đang thiếu hình minh họa"));
                    assertTrue(artwork.getIcon().getIconWidth() >= 80);
                    assertTrue(artwork.getIcon().getIconHeight() >= 60);
                    Rectangle bounds = SwingUtilities.convertRectangle(artwork.getParent(), artwork.getBounds(), picker);
                    assertEquals(picker.getWidth() / 2.0, bounds.getCenterX(), 1.0,
                            "art=" + bounds + " parent=" + artwork.getParent().getBounds() + " insets=" + picker.getInsets());
                    assertTrue(new Rectangle(picker.getSize()).contains(bounds));
                    for (JLabel label : labels(picker)) assertEquals(SwingConstants.CENTER, label.getHorizontalAlignment());
                    assertNull(picker.getImageBase64(), "Minh họa không phải ảnh nội dung và không được lưu vào bản nháp");
                }
            } finally { host.dispose(); }
        });
    }

    @Test
    void loadingAndRemovingRealImagePreservesDataAndRestoresUploadArtwork() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            ImagePicker picker = new ImagePicker();
            AtomicInteger changes = new AtomicInteger(); picker.setOnChange(changes::incrementAndGet);
            try {
                String data = ImageUtil.toBase64(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB));
                picker.setImageBase64(data);
                assertEquals(data, picker.getImageBase64());
                assertEquals(0, changes.get(), "Nạp ảnh có sẵn không đánh dấu bản nháp bị sửa");
                JLabel preview = labels(picker).stream().filter(l -> l.getIcon() != null).findFirst().orElseThrow();
                assertInstanceOf(ImageIcon.class, preview.getIcon());
                findButton(picker, "GỠ ẢNH").doClick(0);
                assertNull(picker.getImageBase64());
                assertEquals(1, changes.get());
                assertTrue(labels(picker).stream().anyMatch(l -> l.getIcon() != null),
                        "Gỡ ảnh phải trở về trạng thái rỗng có minh họa");
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }

    private static List<JLabel> labels(Component c) {
        List<JLabel> result = new ArrayList<>();
        if (c instanceof JLabel label) result.add(label);
        if (c instanceof Container p) for (Component child : p.getComponents()) result.addAll(labels(child));
        return result;
    }
    private static AbstractButton findButton(Component c, String text) {
        if (c instanceof AbstractButton b && text.equals(b.getText())) return b;
        if (c instanceof Container p) for (Component child : p.getComponents()) {
            AbstractButton button = findButton(child, text); if (button != null) return button;
        }
        return null;
    }
    private static void layout(Component c) {
        if (c instanceof Container p) { p.doLayout(); for (Component child : p.getComponents()) layout(child); }
    }
}
