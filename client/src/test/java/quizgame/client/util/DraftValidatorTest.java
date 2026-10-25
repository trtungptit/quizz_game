package quizgame.client.util;

import org.junit.jupiter.api.Test;
import quizgame.common.model.QuestionDraft;
import quizgame.common.model.QuestionType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DraftValidatorTest {
    private static QuestionDraft single(List<String> options, List<Integer> correct) {
        return new QuestionDraft(QuestionType.SINGLE, "Q", options, correct, List.of(), 20, null);
    }

    @Test
    void validSingleMultiType() {
        assertNull(DraftValidator.validate(single(List.of("a", "b", "c", "d"), List.of(1))));
        assertNull(DraftValidator.validate(new QuestionDraft(QuestionType.MULTI, "Q", List.of("a", "b", "c", "d"),
                List.of(0, 2), List.of(), 5, null)));
        assertNull(DraftValidator.validate(new QuestionDraft(QuestionType.TYPE, "Q", List.of(), List.of(),
                List.of("x"), 120, null)));
    }

    @Test
    void invalidCases() {
        assertNotNull(DraftValidator.validate(new QuestionDraft(QuestionType.SINGLE, " ", List.of("a", "b", "c", "d"),
                List.of(0), List.of(), 20, null)));
        assertNotNull(DraftValidator.validate(single(List.of("a", "b", "", "d"), List.of(0))));
        assertNotNull(DraftValidator.validate(single(List.of("a", "b", "c", "d"), List.of())));
        assertNotNull(DraftValidator.validate(single(List.of("a", "b", "c", "d"), List.of(0, 1))));
        assertNotNull(DraftValidator.validate(new QuestionDraft(QuestionType.MULTI, "Q", List.of("a", "b", "c", "d"),
                List.of(1, 1), List.of(), 20, null)));
        assertNotNull(DraftValidator.validate(new QuestionDraft(QuestionType.TYPE, "Q", List.of(), List.of(),
                List.of(), 20, null)));
        assertNotNull(DraftValidator.validate(new QuestionDraft(QuestionType.TYPE, "Q", List.of(), List.of(),
                List.of(" "), 20, null)));
        assertNotNull(DraftValidator.validate(new QuestionDraft(QuestionType.SINGLE, "Q", List.of("a", "b", "c", "d"),
                List.of(0), List.of(), 4, null)));
    }
}
