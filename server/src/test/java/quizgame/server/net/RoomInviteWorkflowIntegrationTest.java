package quizgame.server.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import quizgame.common.protocol.FramedIO;
import quizgame.server.config.ServerConfig;
import quizgame.server.dao.*;
import quizgame.server.service.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "QUIZ_TEST_DB_URL", matches = ".+")
class RoomInviteWorkflowIntegrationTest {
    @Test
    void expiryPublishesCleanPendingListAndAdvancesQueueOnlyAfterDelivery() throws Exception {
        try (Fixture f = new Fixture(3)) {
            Peer a=f.peers.get(0), b=f.peers.get(1), target=f.peers.get(2);
            a.create(); b.create();
            a.ok("INVITE", user(target));
            long first=target.event("INVITE_RECEIVED").getAsJsonObject("data").get("id").getAsLong();
            a.eventWhere("ROOM_STATE", x->x.getAsJsonObject("data").getAsJsonArray("pendingInvitees").size()==1);
            b.ok("INVITE", user(target));
            assertNull(f.value("SELECT delivered_at FROM invitations WHERE receiver_id=(SELECT user_id FROM users WHERE username=?) AND invitation_id<>?",target.username,first));
            assertEquals("EXPIRED", a.event("INVITE_RESULT").getAsJsonObject("data").get("result").getAsString());
            // Phải có snapshot mới sau timeout, không chỉ cập nhật bảng DB.
            a.eventWhere("ROOM_STATE", x->x.getAsJsonObject("data").getAsJsonArray("pendingInvitees").isEmpty());
            long second=target.event("INVITE_RECEIVED").getAsJsonObject("data").get("id").getAsLong();
            assertNotEquals(first,second);
            target.ok("INVITE_REPLY",reply(second,false));
            assertEquals("REJECTED", b.event("INVITE_RESULT").getAsJsonObject("data").get("result").getAsString());
            assertEquals("expired",f.value("SELECT status FROM invitations WHERE invitation_id=?",first));
            assertEquals("rejected",f.value("SELECT status FROM invitations WHERE invitation_id=?",second));
        }
    }

    @Test
    void joiningByCodeCancelsOtherInvitesAndNotifiesTheirSenders() throws Exception {
        try (Fixture f = new Fixture(3)) {
            Peer a=f.peers.get(0), b=f.peers.get(1), target=f.peers.get(2);
            String code=a.create(); b.create();
            b.ok("INVITE",user(target));
            long id=target.event("INVITE_RECEIVED").getAsJsonObject("data").get("id").getAsLong();
            target.ok("JOIN_ROOM","{\"code\":\""+code+"\"}");
            assertEquals("JOINED_OTHER",b.event("INVITE_RESULT").getAsJsonObject("data").get("result").getAsString());
            assertEquals("joined_other",f.value("SELECT status FROM invitations WHERE invitation_id=?",id));
            assertCode(target.request("INVITE_REPLY",reply(id,true)),"INVITE_EXPIRED");
        }
    }

    @Test
    void roomLifecycleValidatesNamesTransfersHostAndClosesEmptyRoom() throws Exception {
        try(Fixture f=new Fixture(3)){
            Peer host=f.peers.get(0), next=f.peers.get(1), third=f.peers.get(2);
            for(String name:List.of("", "   ", "x".repeat(31))){
                assertCode(host.request("CREATE_ROOM","{\"name\":\""+name+"\"}"),"INVALID_INPUT");
            }
            String code=host.create();assertTrue(code.matches("[A-HJ-NP-Z2-9]{6}"));
            assertCode(host.request("CREATE_ROOM","{\"name\":\"Khác\"}"),"ALREADY_IN_ROOM");
            assertCode(next.request("JOIN_ROOM","{\"code\":\"invalid\"}"),"ROOM_NOT_FOUND");
            next.ok("JOIN_ROOM","{\"code\":\""+code+"\"}");
            third.ok("JOIN_ROOM","{\"code\":\""+code+"\"}");
            host.ok("LEAVE_ROOM","null");
            JsonObject transferred=next.eventWhere("ROOM_STATE",x->next.username.equals(x.getAsJsonObject("data").get("host").getAsString()));
            assertEquals(2,transferred.getAsJsonObject("data").getAsJsonArray("members").size());
            host.ok("JOIN_ROOM","{\"code\":\""+code+"\"}");
            assertEquals("IN_ROOM",f.online.statusByUsername(host.username).name());
            host.ok("LEAVE_ROOM","null");next.ok("LEAVE_ROOM","null");third.ok("LEAVE_ROOM","null");
            assertEquals("cancelled",f.value("SELECT status FROM rooms WHERE room_code=?",code));
            assertEquals(0L,((Number)f.value("SELECT COUNT(*) FROM room_members m JOIN rooms r ON r.room_id=m.room_id WHERE r.room_code=? AND m.left_at IS NULL",code)).longValue());
            assertCode(host.request("JOIN_ROOM","{\"code\":\""+code+"\"}"),"ROOM_NOT_FOUND");
            assertEquals("IDLE",f.online.statusByUsername(host.username).name());
        }
    }

    @Test
    void concurrentJoinAndAcceptForLastSeatCannotOverfillTheRoom() throws Exception {
        try(Fixture f=new Fixture(6)){
            Peer host=f.peers.get(0), invited=f.peers.get(4), byCode=f.peers.get(5);
            String code=host.create();
            for(int i=1;i<4;i++)f.peers.get(i).ok("JOIN_ROOM","{\"code\":\""+code+"\"}");
            host.ok("INVITE",user(invited));
            long id=invited.event("INVITE_RECEIVED").getAsJsonObject("data").get("id").getAsLong();
            ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
            try{
                Future<JsonObject> a=pool.submit(()->{start.await();return invited.request("INVITE_REPLY",reply(id,true));});
                Future<JsonObject> b=pool.submit(()->{start.await();return byCode.request("JOIN_ROOM","{\"code\":\""+code+"\"}");});
                start.countDown();JsonObject x=a.get(8,TimeUnit.SECONDS),y=b.get(8,TimeUnit.SECONDS);
                assertNotEquals(x.get("ok").getAsBoolean(),y.get("ok").getAsBoolean());
                assertCode(x.get("ok").getAsBoolean()?y:x,"ROOM_FULL");
                assertEquals(5L,((Number)f.value("SELECT COUNT(*) FROM room_members m JOIN rooms r ON r.room_id=m.room_id WHERE r.room_code=? AND m.left_at IS NULL",code)).longValue());
                assertEquals(1L,((Number)f.value("SELECT COUNT(*) FROM invitations WHERE invitation_id=? AND status IN ('accepted','room_full')",id)).longValue());
            }finally{pool.shutdownNow();}
        }
    }

    @Test
    void acceptsOneInviteCancelsOtherRoomsAndRejectsForeignOrRepeatedReply() throws Exception {
        try(Fixture f=new Fixture(4)){
            Peer a=f.peers.get(0), b=f.peers.get(1), receiver=f.peers.get(2), stranger=f.peers.get(3);
            String code=a.create();b.create();
            a.ok("INVITE",user(receiver));
            long id=receiver.event("INVITE_RECEIVED").getAsJsonObject("data").get("id").getAsLong();
            b.ok("INVITE",user(receiver));
            assertCode(stranger.request("INVITE_REPLY",reply(id,true)),"INVITE_EXPIRED");
            assertCode(a.request("INVITE",user(receiver)),"ALREADY_INVITED");
            receiver.ok("INVITE_REPLY",reply(id,true));
            assertEquals("ACCEPTED",a.event("INVITE_RESULT").getAsJsonObject("data").get("result").getAsString());
            assertEquals("JOINED_OTHER",b.event("INVITE_RESULT").getAsJsonObject("data").get("result").getAsString());
            assertEquals("IN_ROOM",f.online.statusByUsername(receiver.username).name());
            assertEquals(code,f.value("SELECT r.room_code FROM rooms r JOIN room_members m ON m.room_id=r.room_id JOIN users u ON u.user_id=m.user_id WHERE u.username=? AND m.left_at IS NULL",receiver.username));
            assertCode(receiver.request("INVITE_REPLY",reply(id,true)),"INVITE_EXPIRED");
            assertCode(b.request("INVITE",user(receiver)),"USER_BUSY");
            b.eventWhere("ROOM_STATE",x->x.getAsJsonObject("data").getAsJsonArray("pendingInvitees").isEmpty());
        }
    }

    @Test
    void senderLeaveCancelsCurrentDialogSlotAndNextInviteStillWorks() throws Exception {
        try(Fixture f=new Fixture(3)){
            Peer a=f.peers.get(0), b=f.peers.get(1), receiver=f.peers.get(2);
            a.create();String code=b.create();a.ok("INVITE",user(receiver));
            long old=receiver.event("INVITE_RECEIVED").getAsJsonObject("data").get("id").getAsLong();
            b.ok("INVITE",user(receiver));a.ok("LEAVE_ROOM","null");
            assertEquals("cancelled",f.value("SELECT status FROM invitations WHERE invitation_id=?",old));
            assertCode(receiver.request("INVITE_REPLY",reply(old,true)),"INVITE_EXPIRED");
            long next=receiver.event("INVITE_RECEIVED").getAsJsonObject("data").get("id").getAsLong();
            assertNotEquals(old,next);receiver.ok("INVITE_REPLY",reply(next,true));
            assertEquals(code,f.value("SELECT r.room_code FROM rooms r JOIN room_members m ON m.room_id=r.room_id JOIN users u ON u.user_id=m.user_id WHERE u.username=? AND m.left_at IS NULL",receiver.username));
        }
    }

    @Test
    void receiverLogoutCancelsPendingAndHostDisconnectTransfersOwnership() throws Exception {
        try(Fixture f=new Fixture(3)){
            Peer host=f.peers.get(0), member=f.peers.get(1), receiver=f.peers.get(2);
            String code=host.create();member.ok("JOIN_ROOM","{\"code\":\""+code+"\"}");
            host.ok("INVITE",user(receiver));
            long id=receiver.event("INVITE_RECEIVED").getAsJsonObject("data").get("id").getAsLong();
            receiver.ok("LOGOUT","null");
            assertEquals("cancelled",f.value("SELECT status FROM invitations WHERE invitation_id=?",id));
            host.close();
            member.eventWhere("ROOM_STATE",x->member.username.equals(x.getAsJsonObject("data").get("host").getAsString()));
            assertEquals(member.username,f.value("SELECT u.username FROM rooms r JOIN users u ON u.user_id=r.host_id WHERE r.room_code=?",code));
        }
    }

    @Test
    void duplicateInvitesFromTwoMembersCreateExactlyOnePendingInvitation() throws Exception {
        try(Fixture f=new Fixture(3)){
            Peer host=f.peers.get(0),member=f.peers.get(1),receiver=f.peers.get(2);
            String code=host.create();member.ok("JOIN_ROOM","{\"code\":\""+code+"\"}");
            ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
            try{
                Future<JsonObject> a=pool.submit(()->{start.await();return host.request("INVITE",user(receiver));});
                Future<JsonObject> b=pool.submit(()->{start.await();return member.request("INVITE",user(receiver));});
                start.countDown();JsonObject x=a.get(8,TimeUnit.SECONDS),y=b.get(8,TimeUnit.SECONDS);
                assertNotEquals(x.get("ok").getAsBoolean(),y.get("ok").getAsBoolean());
                assertCode(x.get("ok").getAsBoolean()?y:x,"ALREADY_INVITED");
                assertEquals(1L,((Number)f.value("SELECT COUNT(*) FROM invitations WHERE receiver_id=(SELECT user_id FROM users WHERE username=?) AND status='pending'",receiver.username)).longValue());
            }finally{pool.shutdownNow();}
        }
    }

    @Test
    void failedAcceptRollsBackMembershipAndCanBeRetriedWithoutLosingInvitation() throws Exception {
        try(Fixture f=new Fixture(2)){
            Peer host=f.peers.get(0),receiver=f.peers.get(1);host.create();host.ok("INVITE",user(receiver));
            long id=receiver.event("INVITE_RECEIVED").getAsJsonObject("data").get("id").getAsLong();
            try(Connection c=f.db.openConnection();var s=c.createStatement()){
                s.execute("CREATE TRIGGER fail_test_join BEFORE INSERT ON room_members FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture-only failure'");
                try{
                    assertCode(receiver.request("INVITE_REPLY",reply(id,true)),"INTERNAL");
                    assertEquals("pending",f.value("SELECT status FROM invitations WHERE invitation_id=?",id));
                    assertEquals("IDLE",f.online.statusByUsername(receiver.username).name());
                    assertEquals(0L,((Number)f.value("SELECT COUNT(*) FROM room_members WHERE user_id=(SELECT user_id FROM users WHERE username=?) AND left_at IS NULL",receiver.username)).longValue());
                }finally{s.execute("DROP TRIGGER IF EXISTS fail_test_join");}
            }
            receiver.ok("INVITE_REPLY",reply(id,true));
            assertEquals("accepted",f.value("SELECT status FROM invitations WHERE invitation_id=?",id));
        }
    }

    @Test
    void disconnectCleanupRetriesAfterSqlFailureAndDoesNotKeepUserOnline() throws Exception {
        try(Fixture f=new Fixture(2)){
            Peer host=f.peers.get(0),member=f.peers.get(1);String code=host.create();member.ok("JOIN_ROOM","{\"code\":\""+code+"\"}");
            try(Connection c=f.db.openConnection();var s=c.createStatement()){
                s.execute("CREATE TRIGGER fail_test_leave BEFORE UPDATE ON room_members FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture-only failure'");
                try{
                    host.close();
                    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                    while(f.online.sessionByUsername(host.username)!=null&&System.nanoTime()<end)Thread.sleep(20);
                    assertNull(f.online.sessionByUsername(host.username));
                    assertEquals(1L,((Number)f.value("SELECT COUNT(*) FROM room_members WHERE user_id=(SELECT user_id FROM users WHERE username=?) AND left_at IS NULL",host.username)).longValue());
                }finally{s.execute("DROP TRIGGER IF EXISTS fail_test_leave");}
            }
            member.eventWhere("ROOM_STATE",x->member.username.equals(x.getAsJsonObject("data").get("host").getAsString()));
            assertEquals(0L,((Number)f.value("SELECT COUNT(*) FROM room_members WHERE user_id=(SELECT user_id FROM users WHERE username=?) AND left_at IS NULL",host.username)).longValue());
        }
    }

    @Test
    void moreThanEightClientsCanLoginAndUseTwoRooms() throws Exception {
        try(Fixture f=new Fixture(10)){
            String a=f.peers.get(0).create(),b=f.peers.get(5).create();
            assertNotEquals(a,b);
            for(int i=1;i<5;i++)f.peers.get(i).ok("JOIN_ROOM","{\"code\":\""+a+"\"}");
            for(int i=6;i<10;i++)f.peers.get(i).ok("JOIN_ROOM","{\"code\":\""+b+"\"}");
            assertEquals(10,f.online.snapshotExcept(null).stream().filter(x->x.username().startsWith(f.names.get(0).substring(0,11))).count());
        }
    }

    @Test
    void startupRecoveryCancelsRuntimeRowsWithoutDeletingAccountsAndEnforcesInstanceLock() throws Exception {
        try(Fixture f=new Fixture(2)){
            Peer host=f.peers.get(0),receiver=f.peers.get(1);String code=host.create();host.ok("INVITE",user(receiver));
            receiver.event("INVITE_RECEIVED");
            Database other=new Database(ServerConfig.from(Map.of("QUIZ_DB_URL",System.getenv("QUIZ_TEST_DB_URL"),"QUIZ_DB_USER",System.getenv("QUIZ_TEST_DB_USER"),"QUIZ_DB_PASSWORD",System.getenv("QUIZ_TEST_DB_PASSWORD"))));
            try{
                f.db.verifySchemaAndAcquireLock();
                assertThrows(java.sql.SQLException.class,other::verifySchemaAndAcquireLock);
                new StartupRecovery(f.db).reconcile();
                assertEquals("cancelled",f.value("SELECT status FROM rooms WHERE room_code=?",code));
                assertEquals(0L,((Number)f.value("SELECT COUNT(*) FROM room_members m JOIN rooms r ON r.room_id=m.room_id WHERE r.room_code=? AND m.left_at IS NULL",code)).longValue());
                assertEquals(0L,((Number)f.value("SELECT COUNT(*) FROM invitations i JOIN rooms r ON r.room_id=i.room_id WHERE r.room_code=? AND i.status='pending'",code)).longValue());
                assertNotNull(f.value("SELECT user_id FROM users WHERE username=?",host.username));
            }finally{other.releaseInstanceLock();f.db.releaseInstanceLock();}
        }
    }

    @Test
    void joiningInvitingRoomByCodeReturnsSnapshotWithoutOwnPendingInvite() throws Exception {
        try(Fixture f=new Fixture(2)){
            Peer host=f.peers.get(0),receiver=f.peers.get(1);String code=host.create();
            host.ok("INVITE",user(receiver));receiver.event("INVITE_RECEIVED");
            JsonObject joined=receiver.ok("JOIN_ROOM","{\"code\":\""+code+"\"}");
            assertTrue(joined.getAsJsonObject("data").getAsJsonArray("pendingInvitees").isEmpty());
            host.eventWhere("ROOM_STATE",x->x.getAsJsonObject("data").getAsJsonArray("members").size()==2
                    &&x.getAsJsonObject("data").getAsJsonArray("pendingInvitees").isEmpty());
        }
    }

    @Test
    void expiryUsesActualWriteTimeEvenWhenCoordinatorWasBusy() throws Exception {
        try(Fixture f=new Fixture(2)){
            Peer host=f.peers.get(0),receiver=f.peers.get(1);host.create();
            long id;
            synchronized(f.online){
                f.invites.invite(f.online.sessionByUsername(host.username),receiver.username);
                id=receiver.event("INVITE_RECEIVED").getAsJsonObject("data").get("id").getAsLong();
                Thread.sleep(5200);
            }
            assertCode(receiver.request("INVITE_REPLY",reply(id,true)),"INVITE_EXPIRED");
            assertEquals("expired",f.value("SELECT status FROM invitations WHERE invitation_id=?",id));
        }
    }

    static String user(Peer p){return "{\"username\":\""+p.username+"\"}";}
    static String reply(long id,boolean accept){return "{\"inviteId\":"+id+",\"accept\":"+accept+"}";}
    static void assertCode(JsonObject value,String code){assertFalse(value.get("ok").getAsBoolean(),value.toString());assertEquals(code,value.getAsJsonObject("error").get("code").getAsString());}

    static final class Fixture implements AutoCloseable {
        final Database db;
        final QuizServer server;
        final OnlineRegistry online=new OnlineRegistry();
        final InviteManager invites;
        final List<Peer> peers=new ArrayList<>();
        final List<String> names=new ArrayList<>();
        Fixture(int count)throws Exception{
            db=new Database(ServerConfig.from(Map.of("QUIZ_DB_URL",System.getenv("QUIZ_TEST_DB_URL"),"QUIZ_DB_USER",System.getenv("QUIZ_TEST_DB_USER"),"QUIZ_DB_PASSWORD",System.getenv("QUIZ_TEST_DB_PASSWORD"))));
            AccountDao accounts=new AccountDao();RoomDao dao=new RoomDao();
            AuthService auth=new AuthService(db,accounts,new PasswordHasher());
            RoomManager rooms=new RoomManager(db,dao,online);
            invites=new InviteManager(db,accounts,new InvitationDao(),dao,online,rooms);
            server=new QuizServer("127.0.0.1",0,auth,online,rooms,invites);server.start();
            String prefix="ri_"+UUID.randomUUID().toString().substring(0,8);
            for(int i=0;i<count;i++){
                String name=prefix+"_"+i;names.add(name);auth.register(name,"fixture-only".toCharArray());
                Peer p=new Peer(server.port(),name);peers.add(p);
                p.ok("LOGIN","{\"username\":\""+name+"\",\"password\":\"fixture-only\"}");
                p.event("ONLINE_LIST");
            }
        }
        Object value(String sql,Object... args)throws Exception{
            try(Connection c=db.openConnection();var s=c.prepareStatement(sql)){
                for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);
                try(var r=s.executeQuery()){return r.next()?r.getObject(1):null;}
            }
        }
        @Override public void close()throws Exception{
            for(Peer p:peers)p.close();server.close();
            db.transaction(c->{
                for(String name:names){
                    try(var s=c.prepareStatement("DELETE FROM invitations WHERE sender_id=(SELECT user_id FROM users WHERE username=?) OR receiver_id=(SELECT user_id FROM users WHERE username=?)")){s.setString(1,name);s.setString(2,name);s.executeUpdate();}
                }
                for(String name:names){
                    try(var s=c.prepareStatement("DELETE FROM room_members WHERE room_id IN (SELECT room_id FROM rooms WHERE host_id=(SELECT user_id FROM users WHERE username=?))")){s.setString(1,name);s.executeUpdate();}
                    try(var s=c.prepareStatement("DELETE FROM rooms WHERE host_id=(SELECT user_id FROM users WHERE username=?)")){s.setString(1,name);s.executeUpdate();}
                }
                for(String name:names){try(var s=c.prepareStatement("DELETE FROM users WHERE username=?")){s.setString(1,name);s.executeUpdate();}}
                return null;
            });
        }
    }

    static final class Peer implements AutoCloseable {
        final String username;final Socket socket;final DataInputStream in;final DataOutputStream out;
        final List<JsonObject> events=new ArrayList<>();long id;
        Peer(int port,String username)throws Exception{this.username=username;socket=new Socket("127.0.0.1",port);socket.setSoTimeout(8000);in=new DataInputStream(socket.getInputStream());out=new DataOutputStream(socket.getOutputStream());}
        void send(String type,String data)throws Exception{FramedIO.write(out,"{\"type\":\""+type+"\",\"reqId\":"+(++id)+",\"data\":"+data+"}");}
        JsonObject request(String type,String data)throws Exception{
            send(type,data);
            for(int i=0;i<200;i++){JsonObject x=read();if(x.has("reqId")){assertEquals(id,x.get("reqId").getAsLong());assertEquals(type.equals("PING")?"PONG":type,x.get("type").getAsString());return x;}events.add(x);}
            throw new AssertionError("Không nhận response "+type);
        }
        JsonObject ok(String type,String data)throws Exception{JsonObject x=request(type,data);assertTrue(x.get("ok").getAsBoolean(),x.toString());return x;}
        String create()throws Exception{return ok("CREATE_ROOM","{\"name\":\"Phòng thử\"}").getAsJsonObject("data").get("code").getAsString();}
        JsonObject read()throws Exception{return JsonParser.parseString(FramedIO.read(in)).getAsJsonObject();}
        JsonObject event(String type)throws Exception{return eventWhere(type,x->true);}
        JsonObject eventWhere(String type,Predicate<JsonObject> check)throws Exception{
            for(var it=events.iterator();it.hasNext();){JsonObject x=it.next();if(type.equals(x.get("type").getAsString())){it.remove();if(check.test(x))return x;}}
            for(int i=0;i<200;i++){JsonObject x=read();if(type.equals(x.get("type").getAsString())){if(check.test(x))return x;}else events.add(x);}
            throw new AssertionError("Không nhận event "+type);
        }
        @Override public void close()throws Exception{socket.close();}
    }
}
