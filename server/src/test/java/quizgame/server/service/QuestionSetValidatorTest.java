package quizgame.server.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import quizgame.common.model.QuestionDraft;
import quizgame.common.model.QuestionSet;
import quizgame.common.model.QuestionType;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestionSetValidatorTest {
    @TempDir Path images;

    private static QuestionDraft single() {
        return new QuestionDraft(QuestionType.SINGLE, "Câu hỏi", List.of("A", "B", "C", "D"),
                List.of(0), List.of(), 20, null);
    }

    private static QuestionSet set(QuestionDraft q) {
        return new QuestionSet(0, "Bộ mới", "Toán", "", "ignored", List.of(q));
    }

    @Test void validatesEveryQuestionAndMetadata() {
        assertDoesNotThrow(() -> QuestionSetValidator.validate(set(single())));
        var invalid = List.of(
                new QuestionDraft(null, "Câu", List.of(), List.of(), List.of(), 20, null),
                new QuestionDraft(QuestionType.SINGLE, " ", List.of("A", "B", "C", "D"), List.of(0), List.of(), 20, null),
                new QuestionDraft(QuestionType.SINGLE, "x".repeat(501), List.of("A", "B", "C", "D"), List.of(0), List.of(), 20, null),
                new QuestionDraft(QuestionType.SINGLE, "Câu", List.of("A", "B", "C", "D"), List.of(0, 1), List.of(), 20, null),
                new QuestionDraft(QuestionType.MULTI, "Câu", List.of("A", "B", "C", "D"), List.of(0, 0), List.of(), 20, null),
                new QuestionDraft(QuestionType.MULTI, "Câu", List.of("A", "B", "C", "D"), List.of(4), List.of(), 20, null),
                new QuestionDraft(QuestionType.MULTI, "Câu", List.of("A", "B", "C", "D"), List.of(), List.of(), 20, null),
                new QuestionDraft(QuestionType.SINGLE, "Câu", List.of("A", "B", "C"), List.of(0), List.of(), 20, null),
                new QuestionDraft(QuestionType.SINGLE, "Câu", List.of("A", " ", "C", "D"), List.of(0), List.of(), 20, null),
                new QuestionDraft(QuestionType.SINGLE, "Câu", List.of("A".repeat(201), "B", "C", "D"), List.of(0), List.of(), 20, null),
                new QuestionDraft(QuestionType.TYPE, "Câu", List.of(), List.of(0), List.of("Hà Nội"), 20, null),
                new QuestionDraft(QuestionType.TYPE, "Câu", List.of(), List.of(), List.of(), 20, null),
                new QuestionDraft(QuestionType.TYPE, "Câu", List.of(), List.of(), List.of(" "), 20, null),
                new QuestionDraft(QuestionType.TYPE, "Câu", List.of(), List.of(), List.of("x".repeat(201)), 20, null),
                new QuestionDraft(QuestionType.TYPE, "Câu", List.of(), List.of(), List.of("Hà Nội"), 4, null),
                new QuestionDraft(QuestionType.TYPE, "Câu", List.of(), List.of(), List.of("Hà Nội"), 121, null));
        for (var q : invalid) {
            var ex = assertThrows(ServiceException.class, () -> QuestionSetValidator.validate(
                    new QuestionSet(0, "Bộ mới", "", "", null, List.of(single(), q))));
            assertEquals("INVALID_SET", ex.code());
            assertTrue(ex.getMessage().startsWith("Câu 2:"), ex.getMessage());
        }
        for (var bad : List.of(new QuestionSet(1, "Tên", "", "", null, List.of(single())),
                new QuestionSet(0, " ", "", "", null, List.of(single())),
                new QuestionSet(0, "x".repeat(256), "", "", null, List.of(single())),
                new QuestionSet(0, "Tên", "x".repeat(101), "", null, List.of(single())),
                new QuestionSet(0, "Tên", "", "ả".repeat(22000), null, List.of(single())),
                new QuestionSet(0, "Tên", "", "", null, List.of()))) {
            assertEquals("INVALID_SET", assertThrows(ServiceException.class,
                    () -> QuestionSetValidator.validate(bad)).code());
        }
        for (int seconds : List.of(5, 120)) assertDoesNotThrow(() -> QuestionSetValidator.validate(set(
                new QuestionDraft(QuestionType.TYPE, "Câu", List.of(), List.of(), List.of("Hà Nội"), seconds, null))));
    }

    @Test void validatesAndStoresOnlyRealImages() throws Exception {
        var storage = new QuestionImageReader(images);
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", out);
        String base64 = java.util.Base64.getEncoder().encodeToString(out.toByteArray());
        byte[] bytes = storage.decodeUpload(base64, 1);
        String reference = storage.saveUpload(bytes);
        assertEquals(base64, storage.read(reference));
        storage.deleteUpload(reference);
        assertFalse(java.nio.file.Files.exists(images.resolve(reference)));
        for (String invalid : List.of("", "not-base64", "AQID", "A".repeat(1398105),
                java.util.Base64.getEncoder().encodeToString(new byte[1024 * 1024 + 1]))) {
            var ex = assertThrows(ServiceException.class, () -> storage.decodeUpload(invalid, 3));
            assertEquals("INVALID_SET", ex.code());
            assertTrue(ex.getMessage().startsWith("Câu 3:"));
        }
        assertNull(storage.decodeUpload(null, 1));
    }
}
