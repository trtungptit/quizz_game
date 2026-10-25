package quizgame.common.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FramedIOTest {
    private static byte[] frame(String json) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        FramedIO.write(new DataOutputStream(bytes), json);
        return bytes.toByteArray();
    }

    private static String read(byte[] data) throws IOException {
        return FramedIO.read(new DataInputStream(new ByteArrayInputStream(data)));
    }

    @Test
    void roundTripVietnamese() throws IOException {
        String json = "{\"msg\":\"Mất kết nối tới Server\"}";
        assertEquals(json, read(frame(json)));
    }

    @Test
    void twoFramesInARow() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        FramedIO.write(out, "a");
        FramedIO.write(out, "bb");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals("a", FramedIO.read(in));
        assertEquals("bb", FramedIO.read(in));
    }

    @Test
    void oneMegabyte() throws IOException {
        String json = "x".repeat(1024 * 1024);
        assertEquals(json, read(frame(json)));
    }

    @Test
    void writeOverLimitThrows() {
        String json = "x".repeat(FramedIO.MAX_FRAME + 1);
        assertThrows(ProtocolException.class, () -> frame(json));
    }

    @Test
    void readOverLimitThrows() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeInt(FramedIO.MAX_FRAME + 1);
        assertThrows(ProtocolException.class, () -> read(bytes.toByteArray()));
    }

    @Test
    void negativeLengthThrows() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeInt(-1);
        assertThrows(ProtocolException.class, () -> read(bytes.toByteArray()));
    }

    @Test
    void truncatedFrameThrowsEof() throws IOException {
        byte[] full = frame("hello world");
        byte[] cut = Arrays.copyOf(full, full.length - 3);
        assertThrows(EOFException.class, () -> read(cut));
    }

    @Test
    void emptyStreamThrowsEof() {
        assertThrows(EOFException.class, () -> read(new byte[0]));
    }
}
