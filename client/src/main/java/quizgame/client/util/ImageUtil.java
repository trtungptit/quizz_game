package quizgame.client.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;

/** Đọc, thu nhỏ và mã hóa Base64 ảnh câu hỏi. */
public final class ImageUtil {
    public static final int MAX_WIDTH = 800;
    public static final int MAX_BYTES = 1024 * 1024;

    private ImageUtil() {
    }

    /** Đọc file ảnh, thu nhỏ về rộng tối đa 800 px rồi mã hóa Base64; ném IOException (câu tiếng Việt) nếu lỗi. */
    public static String fileToBase64(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Không đọc được file ảnh (chỉ hỗ trợ PNG/JPG)");
        }
        return toBase64(scaleToWidth(image, MAX_WIDTH));
    }

    /** PNG nếu ảnh có nền trong suốt, ngược lại JPG; lỗi nếu vượt 1 MB. */
    public static String toBase64(BufferedImage image) throws IOException {
        boolean alpha = image.getColorModel().hasAlpha();
        BufferedImage out = image;
        if (!alpha && image.getType() != BufferedImage.TYPE_INT_RGB) {
            out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(out, alpha ? "png" : "jpg", bytes)) {
            throw new IOException("Không mã hóa được ảnh");
        }
        if (bytes.size() > MAX_BYTES) {
            throw new IOException("Ảnh vẫn lớn hơn 1 MB sau khi thu nhỏ");
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    /** Giải mã chuỗi Base64 thành ảnh. */
    public static BufferedImage decode(String base64) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        if (image == null) {
            throw new IOException("Ảnh không hợp lệ");
        }
        return image;
    }

    /** Thu nhỏ theo chiều rộng, giữ tỉ lệ; không phóng to. */
    public static BufferedImage scaleToWidth(BufferedImage image, int maxWidth) {
        if (image.getWidth() <= maxWidth) {
            return image;
        }
        int h = Math.max(1, Math.round((float) image.getHeight() * maxWidth / image.getWidth()));
        return resize(image, maxWidth, h);
    }

    /** Co ảnh vừa khung w x h, giữ tỉ lệ; không phóng to. */
    public static Image fit(BufferedImage image, int w, int h) {
        double ratio = Math.min(1.0, Math.min((double) w / image.getWidth(), (double) h / image.getHeight()));
        int nw = Math.max(1, (int) Math.round(image.getWidth() * ratio));
        int nh = Math.max(1, (int) Math.round(image.getHeight() * ratio));
        return ratio >= 1.0 ? image : resize(image, nw, nh);
    }

    private static BufferedImage resize(BufferedImage image, int w, int h) {
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage out = new BufferedImage(w, h, type);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
