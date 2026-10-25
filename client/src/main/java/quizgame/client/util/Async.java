package quizgame.client.util;

import quizgame.client.net.GameException;

import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Nối CompletableFuture với giao diện: kết quả luôn được xử lý trên luồng giao diện. */
public final class Async {
    private Async() {
    }

    /** Khi future xong: gọi ok hoặc err (với câu lỗi tiếng Việt) trên luồng giao diện. */
    public static <T> void ui(CompletableFuture<T> future, Consumer<T> ok, Consumer<String> err) {
        future.whenComplete((value, error) -> SwingUtilities.invokeLater(() -> {
            if (error == null) {
                ok.accept(value);
            } else {
                err.accept(message(error));
            }
        }));
    }

    /** Câu lỗi hiện cho người dùng: message của Server nếu là GameException. */
    public static String message(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof GameException) {
            return cause.getMessage();
        }
        return "Có lỗi xảy ra: " + cause.getMessage();
    }
}
