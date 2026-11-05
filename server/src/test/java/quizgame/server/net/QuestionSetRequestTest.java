package quizgame.server.net;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import quizgame.server.service.ServiceException;

import static org.junit.jupiter.api.Assertions.*;

class QuestionSetRequestTest {
    private static final String DRAFT = """
            {"id":0,"name":"Bộ mới","topic":"","description":"","owner":null,"questions":[
              {"type":"SINGLE","content":"Câu hỏi","options":["A","B","C","D"],
               "correctIndexes":[0],"acceptedAnswers":[],"timeSeconds":20,"imageBase64":null}]}
            """;

    @Test void acceptsClientPayloadAndRejectsTypeCoercionAndMissingFields() {
        assertEquals(1, QuestionSetRequest.parse(JsonParser.parseString(DRAFT)).questions().size());
        for (String invalid : java.util.List.of(DRAFT.replace("\"id\":0", "\"id\":\"0\""),
                DRAFT.replace("\"timeSeconds\":20", "\"timeSeconds\":20.5"),
                DRAFT.replace("\"correctIndexes\":[0]", "\"correctIndexes\":[true]"),
                DRAFT.replace("\"options\":[\"A\",\"B\",\"C\",\"D\"]", "\"options\":null"),
                DRAFT.replace("\"content\":\"Câu hỏi\",", ""))) {
            assertThrows(IllegalArgumentException.class, () -> QuestionSetRequest.parse(JsonParser.parseString(invalid)));
        }
    }

    @Test void reportsQuestionNumberForUnknownType() {
        var error = assertThrows(ServiceException.class, () -> QuestionSetRequest.parse(
                JsonParser.parseString(DRAFT.replace("SINGLE", "UNKNOWN"))));
        assertEquals("INVALID_SET", error.code());
        assertTrue(error.getMessage().startsWith("Câu 1:"));
    }
}
