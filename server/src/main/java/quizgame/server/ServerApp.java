package quizgame.server;

import quizgame.server.config.EnvironmentFile;
import quizgame.server.config.ServerConfig;
import quizgame.server.dao.AccountDao;
import quizgame.server.dao.Database;
import quizgame.server.dao.RoomDao;
import quizgame.server.dao.InvitationDao;
import quizgame.server.dao.QuestionSetDao;
import quizgame.server.net.QuizServer;
import quizgame.server.service.AuthService;
import quizgame.server.service.OnlineRegistry;
import quizgame.server.service.RoomManager;
import quizgame.server.service.InviteManager;
import quizgame.server.service.PasswordHasher;
import quizgame.server.service.StartupRecovery;
import quizgame.server.service.QuestionSetService;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;

/** Khởi tạo cấu hình, DB và TCP server theo đúng thứ tự an toàn. */
public final class ServerApp {
    private ServerApp() { }

    public static void main(String[] args) throws InterruptedException {
        ServerConfig config;
        try {
            config = ServerConfig.from(EnvironmentFile.load(Path.of(".env"), System.getenv()));
        } catch (IOException ex) {
            throw new IllegalStateException("Không đọc được file .env; kiểm tra định dạng và quyền truy cập.");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(ex.getMessage());
        }

        Database database = new Database(config);
        QuizServer server = null;
        try {
            database.verifySchemaAndAcquireLock();
            new StartupRecovery(database).reconcile();
            AccountDao accountDao = new AccountDao();
            RoomDao roomDao = new RoomDao();
            AuthService auth = new AuthService(database, accountDao, new PasswordHasher());
            OnlineRegistry online = new OnlineRegistry();
            RoomManager rooms = new RoomManager(database, roomDao, online);
            InviteManager invites = new InviteManager(database, accountDao, new InvitationDao(), roomDao, online, rooms);
            QuestionSetService questionSets = new QuestionSetService(database, new QuestionSetDao());
            server = new QuizServer(config.host(), config.port(), auth, online, rooms, invites, questionSets);
            server.start();
            QuizServer running = server;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.close();
                database.releaseInstanceLock();
            }, "quiz-server-shutdown"));
            System.out.println("QUIZ Server đang lắng nghe tại " + config.host() + ":" + server.port());
            new CountDownLatch(1).await();
        } catch (SQLException ex) {
            database.releaseInstanceLock();
            throw new IllegalStateException("Không kết nối được DB hoặc schema quiz_game chưa sẵn sàng; "
                    + "kiểm tra cấu hình và cài schema trước khi chạy.");
        } catch (java.io.IOException ex) {
            database.releaseInstanceLock();
            throw new IllegalStateException("Không thể mở cổng Server; kiểm tra địa chỉ/cổng cấu hình.");
        } finally {
            if (server != null) server.close();
        }
    }
}
