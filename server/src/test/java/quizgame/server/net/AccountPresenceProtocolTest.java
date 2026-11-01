package quizgame.server.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import quizgame.common.protocol.FramedIO;
import quizgame.server.config.ServerConfig;
import quizgame.server.dao.AccountDao;
import quizgame.server.dao.Database;
import quizgame.server.dao.RoomDao;
import quizgame.server.dao.InvitationDao;
import quizgame.server.service.AuthService;
import quizgame.server.service.OnlineRegistry;
import quizgame.server.service.PasswordHasher;
import quizgame.server.service.RoomManager;
import quizgame.server.service.InviteManager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "QUIZ_TEST_DB_URL", matches = ".+")
class AccountPresenceProtocolTest {
    @Test
    void registerLoginOnlineSnapshotAndLogoutFollowWireOrder() throws Exception {
        ServerConfig config = ServerConfig.from(Map.of(
                "QUIZ_DB_URL", System.getenv("QUIZ_TEST_DB_URL"),
                "QUIZ_DB_USER", System.getenv("QUIZ_TEST_DB_USER"),
                "QUIZ_DB_PASSWORD", System.getenv("QUIZ_TEST_DB_PASSWORD")));
        Database database = new Database(config);
        var auth = new AuthService(database, new AccountDao(), new PasswordHasher());
        var online = new OnlineRegistry();
        var rooms = new RoomManager(database, new RoomDao(), online);
        var invites = new InviteManager(database,new AccountDao(),new InvitationDao(),new RoomDao(),online,rooms);
        try (QuizServer server = new QuizServer("127.0.0.1", 0, auth, online, rooms, invites)) {
            server.start();
            try (Peer alice = new Peer(server.port()); Peer bob = new Peer(server.port())) {
                alice.send("REGISTER", 1, "{\"username\":\"test_alice\",\"password\":\"secret1\"}");
                JsonObject registerAlice = alice.read();
                assertTrue(registerAlice.get("ok").getAsBoolean(), registerAlice.toString());
                bob.send("REGISTER", 1, "{\"username\":\"test_bob\",\"password\":\"secret2\"}");
                assertTrue(bob.read().get("ok").getAsBoolean());
                alice.send("LOGIN", 2, "{\"username\":\"test_alice\",\"password\":\"secret1\"}");
                assertEquals("LOGIN", alice.read().get("type").getAsString());
                assertEquals("ONLINE_LIST", alice.read().get("type").getAsString());
                bob.send("LOGIN", 2, "{\"username\":\"test_bob\",\"password\":\"secret2\"}");
                assertEquals("LOGIN", bob.read().get("type").getAsString());
                assertEquals("ONLINE_LIST", bob.read().get("type").getAsString());
                JsonObject add = alice.read();
                assertEquals("USER_UPDATE", add.get("type").getAsString());
                assertEquals("ADD", add.getAsJsonObject("data").get("change").getAsString());
                assertEquals("test_bob", add.getAsJsonObject("data").getAsJsonObject("user").get("username").getAsString());

                alice.send("CREATE_ROOM", 3, "{\"name\":\"Test room\"}");
                JsonObject created = alice.readType("CREATE_ROOM");
                String roomCode = created.getAsJsonObject("data").get("code").getAsString();
                assertTrue(roomCode.matches("[A-HJ-NP-Z2-9]{6}"));
                alice.send("INVITE", 4, "{\"username\":\"missing_player\"}");
                JsonObject missingInvite = alice.readType("INVITE");
                assertFalse(missingInvite.get("ok").getAsBoolean());
                assertEquals("USER_NOT_FOUND", missingInvite.getAsJsonObject("error").get("code").getAsString());
                alice.send("INVITE", 5, "{\"username\":\"test_bob\"}");
                assertTrue(alice.readType("INVITE").get("ok").getAsBoolean());
                JsonObject received = bob.readType("INVITE_RECEIVED");
                long invitationId = received.getAsJsonObject("data").get("id").getAsLong();
                bob.send("INVITE_REPLY", 4, "{\"inviteId\":" + invitationId + ",\"accept\":true}");
                JsonObject accepted = bob.readType("INVITE_REPLY");
                assertTrue(accepted.get("ok").getAsBoolean(), accepted.toString());
                assertEquals(2, accepted.getAsJsonObject("data").getAsJsonArray("members").size());
                assertEquals("ACCEPTED", alice.readType("INVITE_RESULT").getAsJsonObject("data").get("result").getAsString());

                bob.send("LOGOUT", 5, "null");
                assertEquals("LOGOUT", bob.readType("LOGOUT").get("type").getAsString());
                JsonObject remove = alice.readType("USER_UPDATE");
                while (!"REMOVE".equals(remove.getAsJsonObject("data").get("change").getAsString())) remove = alice.readType("USER_UPDATE");
                assertEquals("REMOVE", remove.getAsJsonObject("data").get("change").getAsString());
            }
        }
    }

    private static final class Peer implements AutoCloseable {
        private final Socket socket;
        private final DataInputStream input;
        private final DataOutputStream output;
        Peer(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(5_000);
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
        }
        void send(String type, long reqId, String data) throws Exception {
            FramedIO.write(output, "{\"type\":\"" + type + "\",\"reqId\":" + reqId + ",\"data\":" + data + "}");
        }
        JsonObject read() throws Exception { return JsonParser.parseString(FramedIO.read(input)).getAsJsonObject(); }
        JsonObject readType(String type) throws Exception {
            for (int i=0;i<6;i++) { JsonObject value=read(); if(type.equals(value.get("type").getAsString()))return value; }
            fail("Không nhận được message type " + type); return null;
        }
        @Override public void close() throws Exception { socket.close(); }
    }
}
