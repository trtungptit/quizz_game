package quizgame.server.dao;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountTest {
    @Test
    void stringRepresentationNeverRevealsPasswordHash() {
        Account account = new Account(7, "player_7", "pbkdf2-secret-material", 3);
        assertFalse(account.toString().contains("pbkdf2-secret-material"));
        assertTrue(account.toString().contains("<redacted>"));
    }
}
