package quizgame.common.model;

import java.util.Map;

/** Thông báo trận bắt đầu, kèm ảnh của các câu hỏi theo imageId. */
public record GameStart(int numQuestions, Map<String, String> imagesBase64) {
}
