package quizgame.server.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomManagerValidationTest {
    @Test
    void validatesRoomNamesAndSixCharacterCodes() {
        assertTrue(RoomManager.validRoomName("Đố vui"));
        assertFalse(RoomManager.validRoomName("   "));
        assertFalse(RoomManager.validRoomName("x".repeat(31)));
        assertTrue(RoomManager.validRoomCode("ABC234"));
        assertFalse(RoomManager.validRoomCode("ABO234"));
        assertFalse(RoomManager.validRoomCode("abc234"));
    }
}
