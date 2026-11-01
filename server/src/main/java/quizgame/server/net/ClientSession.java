package quizgame.server.net;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Một writer FIFO riêng cho mỗi kết nối; không ghi socket trên luồng state coordinator. */
public final class ClientSession implements AutoCloseable {
    private static final int OUTBOUND_CAPACITY = 256;
    private final Socket socket;
    private record Outbound(String json, Runnable afterWrite) { }
    private final BlockingQueue<Outbound> outbound = new ArrayBlockingQueue<>(OUTBOUND_CAPACITY);
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final Runnable onClosed;
    private final Thread writer;

    public ClientSession(Socket socket, Runnable onClosed) throws IOException {
        this.socket = socket;
        this.onClosed = onClosed;
        this.writer = new Thread(this::writeLoop, "quiz-client-writer");
        this.writer.setDaemon(true);
        this.writer.start();
    }

    public boolean send(String json) { return send(json, null); }
    public boolean send(String json, Runnable afterWrite) {
        if (!open.get()) return false;
        if (outbound.offer(new Outbound(json, afterWrite))) return true;
        close();
        return false;
    }

    public boolean isOpen() { return open.get() && !socket.isClosed(); }
    public Socket socket() { return socket; }

    private void writeLoop() {
        try (var out = new DataOutputStream(socket.getOutputStream())) {
            while (open.get() || !outbound.isEmpty()) {
                Outbound item = outbound.poll(250, TimeUnit.MILLISECONDS);
                if (item != null) {
                    quizgame.common.protocol.FramedIO.write(out, item.json());
                    if (item.afterWrite() != null) {
                        try { item.afterWrite().run(); } catch (RuntimeException ignored) { }
                    }
                }
            }
        } catch (IOException ignored) {
            // Hàng gửi bị hủy cùng kết nối; cleanup idempotent do callback.
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            close();
        }
    }

    @Override public void close() {
        if (!open.compareAndSet(true, false)) return;
        try { socket.close(); } catch (IOException ignored) { }
        writer.interrupt();
        onClosed.run();
    }
}
