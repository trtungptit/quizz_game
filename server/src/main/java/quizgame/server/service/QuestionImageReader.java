package quizgame.server.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Base64;

/** Đọc/lưu ảnh dưới images/; không truy cập URL hay đường dẫn tùy ý từ Client. */
public final class QuestionImageReader {
    private static final int MAX_IMAGE = 1024 * 1024;
    private final Path root;

    public QuestionImageReader(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** Kiểm tra toàn bộ ảnh trước khi tạo file hay ghi DB. */
    public byte[] decodeUpload(String base64, int questionNumber) {
        if (base64 == null) return null;
        String error = "Câu " + questionNumber + ": ảnh phải là Base64 PNG/JPG hợp lệ, tối đa 1 MiB";
        try {
            if (base64.length() > ((MAX_IMAGE + 2) / 3) * 4) throw new IllegalArgumentException();
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length == 0 || bytes.length > MAX_IMAGE) throw new IllegalArgumentException();
            try (var input = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
                var readers = javax.imageio.ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException();
                var reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    if (!format.equalsIgnoreCase("PNG") && !format.equalsIgnoreCase("JPEG"))
                        throw new IllegalArgumentException();
                    reader.setInput(input, true, true);
                    // Chặn ảnh nén nhỏ nhưng kích thước giải nén quá lớn.
                    if ((long) reader.getWidth(0) * reader.getHeight(0) > 16_000_000L)
                        throw new ServiceException("INVALID_SET", "Câu " + questionNumber + ": ảnh vượt quá 16 triệu điểm ảnh");
                    if (reader.read(0) == null) throw new IllegalArgumentException();
                } finally { reader.dispose(); }
            }
            return bytes;
        } catch (IOException | IllegalArgumentException ex) {
            throw new ServiceException("INVALID_SET", error);
        }
    }

    public String saveUpload(byte[] bytes) throws IOException {
        Files.createDirectories(root);
        Path file = Files.createTempFile(root, "quiz-", ".img");
        try {
            Files.write(file, bytes);
            return file.getFileName().toString();
        } catch (IOException ex) {
            Files.deleteIfExists(file);
            throw ex;
        }
    }

    public void deleteUpload(String reference) {
        try { Files.deleteIfExists(root.resolve(reference)); }
        catch (IOException | SecurityException ex) {
            System.err.println("Không dọn được ảnh tạm của bộ câu hỏi thất bại");
        }
    }

    public String read(String reference) {
        if (reference == null || reference.isBlank()) return null;
        try {
            String normalized = reference.replace('\\', '/');
            if (normalized.startsWith("images/")) normalized = normalized.substring("images/".length());
            if (normalized.contains(":")) throw new IOException();
            Path relative = Path.of(normalized);
            if (relative.isAbsolute()) throw new IOException();
            Path candidate = root.resolve(relative).normalize();
            if (!candidate.startsWith(root)) throw new IOException();
            Path realRoot = root.toRealPath();
            Path realFile = candidate.toRealPath();
            if (!realFile.startsWith(realRoot) || !Files.isRegularFile(realFile)) throw new IOException();
            try (var input = Files.newInputStream(realFile)) {
                byte[] data = input.readNBytes(MAX_IMAGE + 1);
                if (data.length == 0 || data.length > MAX_IMAGE) throw new IOException();
                return Base64.getEncoder().encodeToString(data);
            }
        } catch (IOException | InvalidPathException | SecurityException ex) {
            throw new ServiceException("INTERNAL", "Không thể tải ảnh của bộ câu hỏi");
        }
    }
}
