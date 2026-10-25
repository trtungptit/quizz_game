package quizgame.common.protocol;

import java.io.IOException;

/** Vi phạm giao thức (ví dụ khung vượt quá 2 MB). */
public class ProtocolException extends IOException {
    public ProtocolException(String message) {
        super(message);
    }
}
