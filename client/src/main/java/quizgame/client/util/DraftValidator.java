package quizgame.client.util;

import quizgame.common.model.QuestionDraft;
import quizgame.common.model.QuestionType;

import java.util.HashSet;
import java.util.List;

/** Kiểm tra một câu hỏi theo quy tắc QuestionDraft trong PROTOCOL.md mục 3. */
public final class DraftValidator {
    public static final int MAX_CONTENT = 500;
    public static final int MAX_ANSWER = 200;
    public static final int MIN_TIME = 5;
    public static final int MAX_TIME = 120;

    private DraftValidator() {
    }

    /** Trả về câu lỗi tiếng Việt (nói rõ thiếu gì) hoặc null nếu câu hợp lệ. */
    public static String validate(QuestionDraft d) {
        if (d.content() == null || d.content().isBlank()) {
            return "chưa nhập nội dung câu hỏi";
        }
        if (d.content().length() > MAX_CONTENT) {
            return "nội dung quá " + MAX_CONTENT + " ký tự";
        }
        if (d.timeSeconds() < MIN_TIME || d.timeSeconds() > MAX_TIME) {
            return "thời gian phải từ " + MIN_TIME + " đến " + MAX_TIME + " giây";
        }
        if (d.type() == QuestionType.TYPE) {
            return validateType(d);
        }
        List<String> options = d.options();
        if (options == null || options.size() != 4) {
            return "cần đúng 4 đáp án";
        }
        for (int i = 0; i < 4; i++) {
            String o = options.get(i);
            if (o == null || o.isBlank()) {
                return "chưa nhập đáp án " + (char) ('A' + i);
            }
            if (o.length() > MAX_ANSWER) {
                return "đáp án " + (char) ('A' + i) + " quá " + MAX_ANSWER + " ký tự";
            }
        }
        List<Integer> correct = d.correctIndexes();
        if (correct == null || correct.isEmpty()) {
            return "chưa chọn đáp án đúng";
        }
        if (new HashSet<>(correct).size() != correct.size()) {
            return "đáp án đúng bị trùng";
        }
        for (int c : correct) {
            if (c < 0 || c > 3) {
                return "đáp án đúng ngoài A-D";
            }
        }
        if (d.type() == QuestionType.SINGLE && correct.size() != 1) {
            return "câu Buttons chỉ có 1 đáp án đúng";
        }
        return null;
    }

    private static String validateType(QuestionDraft d) {
        if (d.options() != null && !d.options().isEmpty()) {
            return "câu Type answer không có đáp án A-D";
        }
        List<String> accepted = d.acceptedAnswers();
        if (accepted == null || accepted.isEmpty()) {
            return "chưa nhập đáp án chấp nhận";
        }
        for (String a : accepted) {
            if (a == null || a.isBlank()) {
                return "có đáp án chấp nhận bị để trống";
            }
            if (a.length() > MAX_ANSWER) {
                return "đáp án chấp nhận quá " + MAX_ANSWER + " ký tự";
            }
        }
        return null;
    }
}
