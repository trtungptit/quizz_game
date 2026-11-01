package quizgame.server.net;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolMessagesTest {
    @Test
    void responsesIncludeNullDataAndEventsOmitResponseFields() {
        var response = JsonParser.parseString(ProtocolMessages.response("LOGOUT", 4L, null, null)).getAsJsonObject();
        assertTrue(response.get("data").isJsonNull());
        assertEquals(4, response.get("reqId").getAsLong());
        assertFalse(response.has("error"));
        var event = JsonParser.parseString(ProtocolMessages.event("ONLINE_LIST", "[]")).getAsJsonObject();
        assertTrue(event.has("data"));
        assertFalse(event.has("reqId"));
        assertFalse(event.has("ok"));
        assertFalse(event.has("error"));
    }
}
