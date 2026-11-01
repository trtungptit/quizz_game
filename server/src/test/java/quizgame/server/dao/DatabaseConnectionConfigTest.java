package quizgame.server.dao;
import org.junit.jupiter.api.Test;
import quizgame.server.config.ServerConfig;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class DatabaseConnectionConfigTest {
    @Test void connectionDefaultsBoundConnectAndSocketWaits(){
        var config=ServerConfig.from(Map.of("QUIZ_DB_URL","jdbc:mysql://127.0.0.1/quiz_game","QUIZ_DB_USER","fixture","QUIZ_DB_PASSWORD","fixture-only"));
        var properties=Database.connectionProperties(config);
        assertEquals("5000",properties.getProperty("connectTimeout"));
        assertEquals("5000",properties.getProperty("socketTimeout"));
    }
}
