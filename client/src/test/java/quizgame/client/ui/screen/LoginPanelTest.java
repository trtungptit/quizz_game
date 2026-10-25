package quizgame.client.ui.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoginPanelTest {
    @Test
    void validRegistration() {
        assertNull(LoginPanel.validateRegister("an_01", "123456", "123456"));
        assertNull(LoginPanel.validateRegister("abc", "x".repeat(64), "x".repeat(64)));
    }

    @Test
    void badUsername() {
        assertNotNull(LoginPanel.validateRegister("ab", "123456", "123456"));
        assertNotNull(LoginPanel.validateRegister("a".repeat(21), "123456", "123456"));
        assertNotNull(LoginPanel.validateRegister("an binh", "123456", "123456"));
        assertNotNull(LoginPanel.validateRegister("bình", "123456", "123456"));
    }

    @Test
    void badPassword() {
        assertNotNull(LoginPanel.validateRegister("an", "12345", "12345"));
        assertNotNull(LoginPanel.validateRegister("anbinh", "12345", "12345"));
        assertNotNull(LoginPanel.validateRegister("anbinh", "x".repeat(65), "x".repeat(65)));
    }

    @Test
    void passwordsMustMatch() {
        assertNotNull(LoginPanel.validateRegister("anbinh", "123456", "654321"));
    }
}
