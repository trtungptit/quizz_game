package quizgame.common.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Đóng khung thông điệp: 4 byte độ dài big-endian + JSON UTF-8, tối đa 2 MB. */
public final class FramedIO {
    public static final int MAX_FRAME = 2 * 1024 * 1024;

    private FramedIO() {
    }

    /** Ghi một khung; ném ProtocolException nếu vượt giới hạn (chưa ghi byte nào). */
    public static void write(DataOutputStream out, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FRAME) {
            throw new ProtocolException("Thông điệp quá lớn: " + bytes.length + " byte");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
        out.flush();
    }

    /** Đọc một khung; EOFException nếu kết nối đóng hoặc khung bị cắt giữa chừng. */
    public static String read(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_FRAME) {
            throw new ProtocolException("Độ dài khung không hợp lệ: " + length);
        }
        byte[] buf = new byte[length];
        in.readFully(buf);
        return new String(buf, StandardCharsets.UTF_8);
    }
}
