package quizgame.client.util;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hiệu ứng âm thanh tự tổng hợp bằng code (không dùng file, không cần thư viện ngoài).
 * Mọi thao tác âm thanh chạy trên một luồng riêng để không làm đứng giao diện;
 * máy không có loa / thiết bị âm thanh thì im lặng, không báo lỗi.
 */
public final class SoundPlayer {
    static final int RATE = 44_100;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);

    private static final ExecutorService AUDIO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "quiz-sound");
        t.setDaemon(true);
        return t;
    });

    // Chỉ được đọc/ghi trên luồng AUDIO
    private static boolean initialized;
    private static boolean disabled;
    private static Clip click;
    private static Clip correct;
    private static Clip wrong;
    private static Clip music;

    private SoundPlayer() {
    }

    /** Tiếng "tách" ngắn khi bấm nút. */
    public static void click() {
        run(() -> replay(click));
    }

    /** Chuỗi nốt đi lên khi trả lời đúng. */
    public static void correct() {
        run(() -> replay(correct));
    }

    /** Hai nốt trầm đi xuống khi trả lời sai hoặc hết giờ. */
    public static void wrong() {
        run(() -> replay(wrong));
    }

    /** Bật nhạc nền lặp liên tục trong trận (gọi lại khi đang phát thì phát lại từ đầu). */
    public static void startMusic() {
        run(() -> {
            if (music == null) {
                return;
            }
            music.stop();
            music.setFramePosition(0);
            music.loop(Clip.LOOP_CONTINUOUSLY);
        });
    }

    /** Tắt nhạc nền (gọi nhiều lần không sao). */
    public static void stopMusic() {
        run(() -> {
            if (music != null) {
                music.stop();
            }
        });
    }

    private static void run(Runnable action) {
        AUDIO.execute(() -> {
            if (!initialized) {
                init();
            }
            if (disabled) {
                return;
            }
            try {
                action.run();
            } catch (RuntimeException e) {
                // lỗi thiết bị âm thanh không được làm hỏng game
            }
        });
    }

    private static void init() {
        initialized = true;
        try {
            click = open(clickTone());
            correct = open(correctTone());
            wrong = open(wrongTone());
            music = open(musicLoop());
        } catch (Exception | LinkageError e) {
            disabled = true;
        }
    }

    private static Clip open(short[] samples) throws Exception {
        Clip clip = AudioSystem.getClip();
        byte[] bytes = toBytes(samples);
        clip.open(FORMAT, bytes, 0, bytes.length);
        return clip;
    }

    private static void replay(Clip clip) {
        if (clip == null) {
            return;
        }
        clip.stop();
        clip.flush();
        clip.setFramePosition(0);
        clip.start();
    }

    // ---------------------------------------------------------------- Tổng hợp âm

    static short[] clickTone() {
        double[] buf = new double[ms(40)];
        add(buf, 0, 1400, 40, 0.25, 60);
        return toPcm(buf);
    }

    static short[] correctTone() {
        // Đô - Mi - Son - Đô cao, nốt cuối ngân dài hơn
        double[] buf = new double[ms(90 * 3 + 260)];
        double[] notes = {523.25, 659.25, 783.99, 1046.50};
        for (int i = 0; i < notes.length; i++) {
            int len = i == notes.length - 1 ? 260 : 90;
            add(buf, ms(i * 90), notes[i], len, 0.32, i == notes.length - 1 ? 6 : 14);
        }
        return toPcm(buf);
    }

    static short[] wrongTone() {
        // Hai nốt trầm đi xuống, thêm họa âm bậc 3 cho tiếng "rè" dễ nhận ra
        double[] buf = new double[ms(400)];
        add(buf, 0, 233.08, 180, 0.30, 8);
        add(buf, ms(180), 174.61, 220, 0.30, 6);
        add(buf, 0, 233.08 * 3, 180, 0.08, 8);
        add(buf, ms(180), 174.61 * 3, 220, 0.08, 6);
        return toPcm(buf);
    }

    /** Giai điệu ngắn tự sáng tác, 16 phách ở 132 BPM, nốt cuối tắt hẳn để lặp không bị vấp. */
    static short[] musicLoop() {
        int beat = 60_000 / 132 / 2; // độ dài nốt móc đơn (ms)
        double[] melody = {
                523.25, 659.25, 783.99, 659.25, 587.33, 698.46, 880.00, 698.46,
                659.25, 783.99, 1046.50, 783.99, 698.46, 587.33, 493.88, 392.00,
                523.25, 659.25, 783.99, 880.00, 783.99, 659.25, 587.33, 659.25,
                698.46, 659.25, 587.33, 493.88, 523.25, 0, 392.00, 0};
        double[] bass = {130.81, 174.61, 164.81, 196.00, 130.81, 174.61, 146.83, 130.81};
        double[] buf = new double[ms(beat * melody.length)];
        for (int i = 0; i < melody.length; i++) {
            if (melody[i] > 0) {
                add(buf, ms(i * beat), melody[i], beat, 0.11, 7);
            }
        }
        int bassLen = beat * 4;
        for (int i = 0; i < bass.length; i++) {
            add(buf, ms(i * bassLen), bass[i], bassLen, 0.09, 3);
        }
        return toPcm(buf);
    }

    /** Cộng một nốt (sóng sin, vào nhanh rồi tắt dần) vào buf từ mẫu start. */
    private static void add(double[] buf, int start, double freq, int lenMs, double volume, double decay) {
        int len = ms(lenMs);
        int attack = Math.max(1, ms(5));
        int release = Math.max(1, ms(12));
        for (int i = 0; i < len && start + i < buf.length; i++) {
            double t = (double) i / RATE;
            double env = Math.exp(-decay * t);
            if (i < attack) {
                env *= (double) i / attack;
            }
            if (i > len - release) {
                env *= (double) (len - i) / release; // về 0 ở cuối nốt để không nghe tiếng "lụp bụp"
            }
            buf[start + i] += volume * env * Math.sin(2 * Math.PI * freq * t);
        }
    }

    private static int ms(int millis) {
        return RATE * millis / 1000;
    }

    private static short[] toPcm(double[] buf) {
        short[] out = new short[buf.length];
        for (int i = 0; i < buf.length; i++) {
            double v = Math.max(-1, Math.min(1, buf[i]));
            out[i] = (short) Math.round(v * Short.MAX_VALUE);
        }
        return out;
    }

    private static byte[] toBytes(short[] samples) {
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            bytes[2 * i] = (byte) samples[i];
            bytes[2 * i + 1] = (byte) (samples[i] >> 8);
        }
        return bytes;
    }
}
