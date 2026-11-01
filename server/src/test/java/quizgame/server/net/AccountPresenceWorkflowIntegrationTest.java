package quizgame.server.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import quizgame.common.protocol.FramedIO;
import quizgame.server.config.ServerConfig;
import quizgame.server.dao.AccountDao;
import quizgame.server.dao.Database;
import quizgame.server.service.AuthService;
import quizgame.server.service.OnlineRegistry;
import quizgame.server.service.PasswordHasher;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "QUIZ_TEST_DB_URL", matches = ".+")
class AccountPresenceWorkflowIntegrationTest {
    @Test
    void accountAndPresenceFlowIsCanonicalOrderedAndSessionSafe() throws Exception {
        ServerConfig config=ServerConfig.from(Map.of("QUIZ_DB_URL",System.getenv("QUIZ_TEST_DB_URL"),"QUIZ_DB_USER",System.getenv("QUIZ_TEST_DB_USER"),"QUIZ_DB_PASSWORD",System.getenv("QUIZ_TEST_DB_PASSWORD")));
        Database db=new Database(config);OnlineRegistry online=new OnlineRegistry();
        AuthService auth=new AuthService(db,new AccountDao(),new PasswordHasher());
        try(QuizServer server=new QuizServer("127.0.0.1",0,auth,online)){
            server.start();
            try(Peer alice=new Peer(server.port());Peer bob=new Peer(server.port());Peer rival=new Peer(server.port());Peer replacement=new Peer(server.port())){
                alice.sendRaw("{\"type\":\"PING\",\"reqId\":1,\"data\":null}");
                assertEquals("PONG",alice.read().get("type").getAsString());
                alice.sendRaw("{\"type\":\"PING\",\"reqId\":2}");
                assertCode(alice.read(),"BAD_REQUEST");
                alice.send("SET_LIST",3,"null");
                assertCode(alice.read(),"NOT_LOGGED_IN");
                alice.send("REGISTER",4,"{\"username\":\"CaseUser\",\"password\":\"secret1\"}");
                assertTrue(alice.read().get("ok").getAsBoolean());
                bob.send("REGISTER",1,"{\"username\":\"bob_user\",\"password\":\"secret2\"}");
                assertTrue(bob.read().get("ok").getAsBoolean());
                rival.send("REGISTER",1,"{\"username\":\"caseuser\",\"password\":\"secret3\"}");
                assertCode(rival.read(),"USERNAME_TAKEN");
                db.transaction(c->{try(PreparedStatement s=c.prepareStatement("UPDATE users SET total_wins=11 WHERE username=?")){s.setString(1,"CaseUser");s.executeUpdate();}return null;});
                alice.send("LOGIN",5,"{\"username\":\"caseuser\",\"password\":\"secret1\"}");
                JsonObject login=alice.readType("LOGIN");
                assertTrue(login.get("ok").getAsBoolean(),login.toString());
                assertEquals("CaseUser",login.getAsJsonObject("data").get("username").getAsString());
                assertEquals(11,login.getAsJsonObject("data").get("totalWins").getAsInt());
                assertFalse(login.toString().contains("passwordHash"));
                assertTrue(alice.readType("ONLINE_LIST").getAsJsonArray("data").isEmpty());
                alice.send("REGISTER",6,"{\"username\":\"late_user\",\"password\":\"secret8\"}");
                assertCode(alice.read(),"BAD_REQUEST");
                rival.send("LOGIN",2,"{\"username\":\"CASEUSER\",\"password\":\"wrongpass\"}");
                assertCode(rival.read(),"WRONG_CREDENTIALS");
                rival.send("LOGIN",3,"{\"username\":\"CASEUSER\",\"password\":\"secret1\"}");
                assertCode(rival.read(),"ALREADY_ONLINE");
                bob.send("LOGIN",2,"{\"username\":\"bob_user\",\"password\":\"secret2\"}");
                assertTrue(bob.readType("LOGIN").get("ok").getAsBoolean());
                JsonArray snapshot=bob.readType("ONLINE_LIST").getAsJsonArray("data");
                assertEquals(1,snapshot.size());
                assertEquals("CaseUser",snapshot.get(0).getAsJsonObject().get("username").getAsString());
                assertEquals("IDLE",snapshot.get(0).getAsJsonObject().get("status").getAsString());
                JsonObject add=alice.readType("USER_UPDATE");
                assertEquals("ADD",add.getAsJsonObject("data").get("change").getAsString());
                var staleSession=online.sessionByUsername("bob_user");
                online.changeStatus(staleSession,quizgame.common.model.UserStatus.IN_ROOM);
                JsonObject statusUpdate=alice.readType("USER_UPDATE");
                assertEquals("UPDATE",statusUpdate.getAsJsonObject("data").get("change").getAsString());
                assertEquals("IN_ROOM",statusUpdate.getAsJsonObject("data").getAsJsonObject("user").get("status").getAsString());
                online.refreshWins("BOB_USER",4);
                JsonObject winsUpdate=alice.readType("USER_UPDATE");
                assertEquals(4,winsUpdate.getAsJsonObject("data").getAsJsonObject("user").get("totalWins").getAsInt());
                bob.send("LOGOUT",3,"null");
                assertEquals("LOGOUT",bob.readType("LOGOUT").get("type").getAsString());
                JsonObject remove=alice.readType("USER_UPDATE");
                assertEquals("REMOVE",remove.getAsJsonObject("data").get("change").getAsString());
                replacement.send("LOGIN",1,"{\"username\":\"bob_user\",\"password\":\"secret2\"}");
                assertTrue(replacement.readType("LOGIN").get("ok").getAsBoolean());
                replacement.readType("ONLINE_LIST");
                var newSession=online.sessionByUsername("bob_user");
                online.disconnected(staleSession);
                assertTrue(online.isAuthenticated(newSession));
                assertEquals("bob_user",online.username(newSession));
            }
        }
    }
    @Test
    void concurrentLoginsForOneAccountAtomicallyClaimExactlyOneSession() throws Exception {
        ServerConfig config=ServerConfig.from(Map.of("QUIZ_DB_URL",System.getenv("QUIZ_TEST_DB_URL"),"QUIZ_DB_USER",System.getenv("QUIZ_TEST_DB_USER"),"QUIZ_DB_PASSWORD",System.getenv("QUIZ_TEST_DB_PASSWORD")));
        Database db=new Database(config);OnlineRegistry online=new OnlineRegistry();
        AuthService auth=new AuthService(db,new AccountDao(),new PasswordHasher());
        try(QuizServer server=new QuizServer("127.0.0.1",0,auth,online);
            Peer registrar=new Peer(server.port());Peer one=new Peer(server.port());Peer two=new Peer(server.port())){
            server.start();
            registrar.send("REGISTER",1,"{\"username\":\"race_user\",\"password\":\"secret7\"}");
            assertTrue(registrar.read().get("ok").getAsBoolean());
            CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
            ExecutorService pool=Executors.newFixedThreadPool(2);
            try{
                Future<?> a=pool.submit(()->{ready.countDown();start.await();one.send("LOGIN",1,"{\"username\":\"race_user\",\"password\":\"secret7\"}");return null;});
                Future<?> b=pool.submit(()->{ready.countDown();start.await();two.send("LOGIN",1,"{\"username\":\"race_user\",\"password\":\"secret7\"}");return null;});
                ready.await();start.countDown();a.get();b.get();
                JsonObject responseOne=one.readType("LOGIN"),responseTwo=two.readType("LOGIN");
                assertNotEquals(responseOne.get("ok").getAsBoolean(),responseTwo.get("ok").getAsBoolean());
                JsonObject success=responseOne.get("ok").getAsBoolean()?responseOne:responseTwo;
                JsonObject rejected=responseOne.get("ok").getAsBoolean()?responseTwo:responseOne;
                assertEquals("race_user",success.getAsJsonObject("data").get("username").getAsString());
                assertEquals("ALREADY_ONLINE",rejected.getAsJsonObject("error").get("code").getAsString());
                Peer winner=responseOne.get("ok").getAsBoolean()?one:two;
                winner.readType("ONLINE_LIST");
                winner.send("LOGOUT",2,"null");winner.readType("LOGOUT");
            }finally{pool.shutdownNow();}
        }
    }

    private static void assertCode(JsonObject response,String code){assertFalse(response.get("ok").getAsBoolean(),response.toString());assertEquals(code,response.getAsJsonObject("error").get("code").getAsString());}
    private static final class Peer implements AutoCloseable{
        final Socket socket;final DataInputStream in;final DataOutputStream out;
        Peer(int port)throws Exception{socket=new Socket("127.0.0.1",port);socket.setSoTimeout(5000);in=new DataInputStream(socket.getInputStream());out=new DataOutputStream(socket.getOutputStream());}
        void send(String type,long id,String data)throws Exception{sendRaw("{\"type\":\""+type+"\",\"reqId\":"+id+",\"data\":"+data+"}");}
        void sendRaw(String json)throws Exception{FramedIO.write(out,json);}
        JsonObject read()throws Exception{return JsonParser.parseString(FramedIO.read(in)).getAsJsonObject();}
        JsonObject readType(String type)throws Exception{for(int i=0;i<10;i++){JsonObject x=read();if(type.equals(x.get("type").getAsString()))return x;}fail("Missing protocol message "+type);return null;}
        @Override public void close()throws Exception{socket.close();}
    }
}
