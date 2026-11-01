package quizgame.server.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Base64;

/** Chỉ đọc ảnh lưu dưới images/; không truy cập URL hay đường dẫn tùy ý trong DB. */
public final class QuestionImageReader {
    private static final int MAX_IMAGE = 1024 * 1024;
    private final Path root;

    public QuestionImageReader(Path root) {
        this.root = root.toAbsolutePath().normalize();
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
