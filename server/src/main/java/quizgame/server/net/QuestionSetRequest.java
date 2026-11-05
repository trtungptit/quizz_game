package quizgame.server.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import quizgame.common.model.QuestionDraft;
import quizgame.common.model.QuestionSet;
import quizgame.common.model.QuestionType;
import quizgame.server.service.ServiceException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Đọc JSON đúng kiểu, không để Gson tự đổi chuỗi/số thành dữ liệu hợp lệ. */
final class QuestionSetRequest {
    private QuestionSetRequest() { }

    static QuestionSet parse(JsonElement value) {
        JsonObject set = object(value);
        long id;
        try { id = number(set.get("id")).longValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException(); }
        int[] index = {0};
        List<QuestionDraft> questions = array(set.get("questions"), item -> {
            int questionNumber = ++index[0];
            JsonObject q = object(item);
            String typeName = string(q.get("type"));
            QuestionType type;
            try { type = QuestionType.valueOf(typeName); }
            catch (IllegalArgumentException ex) {
                throw new ServiceException("INVALID_SET", "Câu " + questionNumber
                        + ": loại câu hỏi không hợp lệ, chỉ nhận SINGLE, MULTI, TYPE");
            }
            return new QuestionDraft(type, string(q.get("content")), array(q.get("options"), QuestionSetRequest::string),
                    array(q.get("correctIndexes"), QuestionSetRequest::integer),
                    array(q.get("acceptedAnswers"), QuestionSetRequest::string), integer(q.get("timeSeconds")),
                    nullableString(q.get("imageBase64")));
        });
        return new QuestionSet(id, string(set.get("name")), string(set.get("topic")),
                string(set.get("description")), nullableString(set.get("owner")), questions);
    }

    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException();
        return value.getAsJsonObject();
    }

    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException();
        return value.getAsString();
    }

    private static String nullableString(JsonElement value) {
        return value == null || value.isJsonNull() ? null : string(value);
    }

    private static java.math.BigDecimal number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException();
        try { return value.getAsBigDecimal(); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(); }
    }

    private static int integer(JsonElement value) {
        try { return number(value).intValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException(); }
    }

    private static <T> List<T> array(JsonElement value, Function<JsonElement, T> reader) {
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException();
        List<T> result = new ArrayList<>();
        for (var element : value.getAsJsonArray()) result.add(reader.apply(element));
        return List.copyOf(result);
    }
}
