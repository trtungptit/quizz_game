package quizgame.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashesAreSaltedAndVerifyOnlyTheirPassword() {
        String first = hasher.hash("correct-horse".toCharArray());
        String second = hasher.hash("correct-horse".toCharArray());

        assertNotEquals(first, second);
        assertTrue(hasher.verify("correct-horse".toCharArray(), first));
        assertFalse(hasher.verify("wrong-password".toCharArray(), first));
        assertFalse(first.contains("correct-horse"));
    }

    @Test
    void malformedHashesFailClosed() {
        assertFalse(hasher.verify("password".toCharArray(), "not-a-password-hash"));
        assertFalse(hasher.verify("password".toCharArray(), null));
    }
}
