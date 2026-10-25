package quizgame.common.protocol;

import com.google.gson.JsonElement;

/** Khung thông điệp: yêu cầu, phản hồi (có reqId, ok) hoặc sự kiện đẩy (không có reqId). */
public record Envelope(String type, Long reqId, Boolean ok, JsonElement data, ErrorInfo error) {
}
