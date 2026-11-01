package quizgame.server.config;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigSecretTest {
    @Test
    void diagnosticsRedactDatabasePassword() {
        ServerConfig config = ServerConfig.from(Map.of(
                "QUIZ_DB_URL", "jdbc:mysql://localhost/quiz_game",
                "QUIZ_DB_USER", "quiz",
                "QUIZ_DB_PASSWORD", "top-secret-test-value"));
        assertFalse(config.toString().contains("top-secret-test-value"));
        assertTrue(config.toString().contains("<redacted>"));
    }
}
