package quizgame.server.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class QuestionImageReaderTest {
    @TempDir Path temp;

    @Test
    void returnsBase64OnlyForFilesInsideImageRoot() throws Exception {
        Path root = Files.createDirectory(temp.resolve("images"));
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6S0AAAAASUVORK5CYII=");
        Files.write(root.resolve("pixel.png"), png);
        QuestionImageReader reader = new QuestionImageReader(root);
        assertEquals(Base64.getEncoder().encodeToString(png), reader.read("pixel.png"));
        assertEquals(Base64.getEncoder().encodeToString(png), reader.read("images/pixel.png"));
        assertNull(reader.read(null));
        assertNull(reader.read(" "));
    }

    @Test
    void rejectsOutsidePathsMissingFilesAndOversizedFilesWithoutEchoingReferences() throws Exception {
        Path root = Files.createDirectory(temp.resolve("images"));
        Files.write(root.resolve("large.png"), new byte[1024 * 1024 + 1]);
        QuestionImageReader reader = new QuestionImageReader(root);
        for (String ref : java.util.List.of("../outside.png", temp.resolve("outside.png").toString(),
                "missing.png", "large.png", "https://example.invalid/image.png")) {
            ServiceException error = assertThrows(ServiceException.class, () -> reader.read(ref));
            assertEquals("INTERNAL", error.code());
            assertFalse(error.getMessage().contains(ref));
        }
    }
}
