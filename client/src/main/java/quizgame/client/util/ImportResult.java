package quizgame.client.util;

import quizgame.common.model.QuestionDraft;

import java.util.List;

/** Kết quả import CSV: các câu hợp lệ và danh sách dòng lỗi dạng 'Dòng 7: thiếu đáp án đúng'. */
public record ImportResult(List<QuestionDraft> questions, List<String> errors) {
}
