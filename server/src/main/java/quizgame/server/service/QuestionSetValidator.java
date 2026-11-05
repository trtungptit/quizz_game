package quizgame.server.service;

import quizgame.common.model.QuestionSet;
import quizgame.common.model.QuestionType;

import java.util.HashSet;

/** Kiểm tra bản nháp ở Server, không phụ thuộc kiểm tra của Client. */
public final class QuestionSetValidator {
    private QuestionSetValidator() { }

    public static void validate(QuestionSet set) {
        if (set == null) fail("Thiếu bộ câu hỏi");
        if (set.id() != 0) fail("Bộ mới phải có id bằng 0");
        validateContent(set);
    }

    public static void validateUpdate(QuestionSet set) {
        if (set == null || set.id() < 1) fail("Mã bộ câu hỏi cần sửa không hợp lệ");
        validateContent(set);
    }

    private static void validateContent(QuestionSet set) {
        if (set.name() == null || set.name().isBlank() || set.name().length() > 255)
            fail("Tên bộ câu hỏi phải có từ 1 đến 255 ký tự");
        if (set.topic() == null || set.topic().length() > 100) fail("Chủ đề tối đa 100 ký tự");
        if (set.description() == null || set.description().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65535)
            fail("Mô tả không hợp lệ hoặc vượt quá 65535 byte UTF-8");
        if (set.questions() == null || set.questions().isEmpty() || set.questions().size() > 65535)
            fail("Bộ câu hỏi cần có từ 1 đến 65535 câu");
        for (int i = 0; i < set.questions().size(); i++) {
            var q = set.questions().get(i);
            String prefix = "Câu " + (i + 1) + ": ";
            if (q == null || q.type() == null) fail(prefix + "loại câu hỏi không hợp lệ");
            if (!text(q.content(), 500)) fail(prefix + "nội dung phải có từ 1 đến 500 ký tự");
            if (q.timeSeconds() < 5 || q.timeSeconds() > 120) fail(prefix + "thời gian phải từ 5 đến 120 giây");
            if (q.options() == null || q.correctIndexes() == null || q.acceptedAnswers() == null)
                fail(prefix + "thiếu danh sách đáp án");
            if (q.type() == QuestionType.TYPE) {
                if (!q.options().isEmpty() || !q.correctIndexes().isEmpty())
                    fail(prefix + "câu nhập chữ không có lựa chọn A-D");
                if (q.acceptedAnswers().isEmpty()) fail(prefix + "chưa nhập đáp án chấp nhận");
                for (String answer : q.acceptedAnswers()) {
                    if (!text(answer, 200) || Scoring.normalize(answer).isEmpty()
                            || Scoring.normalize(answer).length() > 200)
                        fail(prefix + "đáp án chấp nhận phải có từ 1 đến 200 ký tự");
                }
            } else {
                if (q.options().size() != 4) fail(prefix + "cần đúng 4 lựa chọn A-D");
                for (String option : q.options())
                    if (!text(option, 200)) fail(prefix + "mỗi lựa chọn phải có từ 1 đến 200 ký tự");
                if (!q.acceptedAnswers().isEmpty()) fail(prefix + "câu lựa chọn không có đáp án nhập chữ");
                var correct = q.correctIndexes();
                if (correct.isEmpty() || new HashSet<>(correct).size() != correct.size()
                        || correct.stream().anyMatch(n -> n == null || n < 0 || n > 3))
                    fail(prefix + "đáp án đúng phải nằm trong A-D và không trùng");
                if (q.type() == QuestionType.SINGLE && correct.size() != 1)
                    fail(prefix + "câu một lựa chọn cần đúng 1 đáp án đúng");
            }
        }
    }

    private static boolean text(String text, int max) {
        return text != null && !text.isBlank() && text.length() <= max;
    }

    private static void fail(String message) { throw new ServiceException("INVALID_SET", message); }
}
