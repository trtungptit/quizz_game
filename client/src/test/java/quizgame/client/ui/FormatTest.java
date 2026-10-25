package quizgame.client.ui;

import org.junit.jupiter.api.Test;
import quizgame.common.model.QuestionSetSummary;
import quizgame.common.model.QuestionType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatTest {
    @Test
    void duration() {
        assertEquals("00:00", Format.duration(0));
        assertEquals("00:59", Format.duration(59_999));
        assertEquals("03:12", Format.duration(192_000));
        assertEquals("61:01", Format.duration(3_661_000));
        assertEquals("00:00", Format.duration(-5));
    }

    @Test
    void dateTime() {
        assertEquals("25/10/2026 09:41", Format.dateTime("2026-10-25T09:41:00"));
        assertEquals("25/10/2026 09:41", Format.dateTime("2026-10-25T09:41:00+07:00"));
        assertEquals("không phải ngày", Format.dateTime("không phải ngày"));
    }

    @Test
    void types() {
        assertEquals("Buttons / Type answer",
                Format.types(List.of(QuestionType.TYPE, QuestionType.SINGLE, QuestionType.TYPE), false));
        assertEquals("Buttons / Hình ảnh", Format.types(List.of(QuestionType.SINGLE), true));
    }

    @Test
    void setLine() {
        QuestionSetSummary s = new QuestionSetSummary(1, "T", "C", "an", 18,
                List.of(QuestionType.SINGLE, QuestionType.MULTI, QuestionType.TYPE), false);
        assertEquals("18 câu • Buttons / Checkboxes / Type answer", Format.setLine(s));
    }
}
