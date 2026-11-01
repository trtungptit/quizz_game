package quizgame.server.config;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {
    @Test
    void appliesLoopbackAndDefaultPortButRequiresDatabaseSettings() {
        ServerConfig config = ServerConfig.from(Map.of(
                "QUIZ_DB_URL", "jdbc:mysql://127.0.0.1:3306/quiz_game",
                "QUIZ_DB_USER", "quiz",
                "QUIZ_DB_PASSWORD", "not-used-in-this-test"));
        assertEquals("127.0.0.1", config.host());
        assertEquals(9090, config.port());
        assertEquals("quiz_game", config.databaseName());
    }

    @Test
    void rejectsMissingDatabasePasswordWithoutEchoingInput() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ServerConfig.from(Map.of("QUIZ_DB_URL", "jdbc:mysql://localhost/quiz_game",
                        "QUIZ_DB_USER", "quiz")));
        assertTrue(error.getMessage().contains("QUIZ_DB_PASSWORD"));
        assertFalse(error.getMessage().contains("super-secret"));
    }

    @Test
    void rejectsCredentialsEmbeddedInJdbcUrl() {
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.from(Map.of(
                "QUIZ_DB_URL", "jdbc:mysql://u:p@localhost/quiz_game", "QUIZ_DB_USER", "u",
                "QUIZ_DB_PASSWORD", "p")));
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.from(Map.of(
                "QUIZ_DB_URL", "jdbc:mysql://localhost/quiz_game?password%3Dembedded", "QUIZ_DB_USER", "u",
                "QUIZ_DB_PASSWORD", "p")));
    }

    @Test
    void rejectsInvalidPortAndNonQuizDatabase() {
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.from(Map.of(
                "QUIZ_DB_URL", "jdbc:mysql://localhost/quiz_game", "QUIZ_DB_USER", "u",
                "QUIZ_DB_PASSWORD", "p", "QUIZ_SERVER_PORT", "70000")));
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.from(Map.of(
                "QUIZ_DB_URL", "jdbc:mysql://localhost/other", "QUIZ_DB_USER", "u",
                "QUIZ_DB_PASSWORD", "p")));
    }
}
