package quizgame.server.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceValidationTest {
    @Test
    void acceptsOnlyProtocolUsernameAndPasswordLengths() {
        assertTrue(AuthService.validUsername("Player_01"));
        assertFalse(AuthService.validUsername("ab"));
        assertFalse(AuthService.validUsername("bad-name"));
        assertTrue(AuthService.validPassword("123456"));
        assertFalse(AuthService.validPassword("12345"));
        assertFalse(AuthService.validPassword("x".repeat(65)));
    }
}
