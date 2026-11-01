package quizgame.server.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import static quizgame.server.net.RoomInviteWorkflowIntegrationTest.*;

@EnabledIfEnvironmentVariable(named="QUIZ_TEST_DB_URL",matches=".+")
class RoomControlsIntegrationTest {
    @Test void configAndKickRespectHostAndResetReadiness() throws Exception {
        try(GameplaySupport g=new GameplaySupport(3)){
            var f=g.f;Peer host=f.peers.get(0),member=f.peers.get(1),target=f.peers.get(2);g.joinAll();
            long id=g.seed(2);
            assertCode(member.request("ROOM_CONFIG","{\"setId\":"+id+",\"numQuestions\":1,\"timeOverrideSeconds\":null}"),"NOT_HOST");
            g.readyAll();g.configure(id,2);
            var state=member.eventWhere("ROOM_STATE",x->x.getAsJsonObject("data").has("set"));
            assertEquals(id,state.getAsJsonObject("data").getAsJsonObject("set").get("id").getAsLong());
            assertTrue(state.getAsJsonObject("data").getAsJsonArray("members").asList().stream().noneMatch(x->x.getAsJsonObject().get("ready").getAsBoolean()));
            for(String data:java.util.List.of("{\"setId\":"+id+",\"numQuestions\":3,\"timeOverrideSeconds\":null}","{\"setId\":"+id+",\"numQuestions\":1,\"timeOverrideSeconds\":4}"))assertCode(host.request("ROOM_CONFIG",data),"INVALID_INPUT");
            assertCode(member.request("KICK",user(target)),"NOT_HOST");
            assertCode(host.request("KICK",user(host)),"BAD_REQUEST");
            host.ok("KICK",user(target));target.event("KICKED");
            assertEquals("IDLE",f.online.statusByUsername(target.username).name());
            assertCode(target.request("SET_READY","{\"ready\":true}"),"NOT_IN_ROOM");
            host.ok("ROOM_CONFIG","{\"setId\":null,\"numQuestions\":0,\"timeOverrideSeconds\":null}");
            assertNull(f.value("SELECT set_id FROM rooms WHERE host_id=(SELECT user_id FROM users WHERE username=?) AND status='waiting'",host.username));
        }
    }
    @Test void readyAndReminderEnforceMembershipAndCooldown() throws Exception {
        try(Fixture f=new Fixture(3)){
            Peer host=f.peers.get(0),member=f.peers.get(1),outside=f.peers.get(2);
            assertCode(outside.request("SET_READY","{\"ready\":true}"),"NOT_IN_ROOM");
            String code=host.create();member.ok("JOIN_ROOM","{\"code\":\""+code+"\"}");
            assertCode(host.request("SET_READY","{\"ready\":\"true\"}"),"BAD_REQUEST");
            assertCode(host.request("REMIND_READY","null"),"NOT_READY_YOURSELF");
            host.ok("SET_READY","{\"ready\":true}");
            member.eventWhere("ROOM_STATE",x->x.getAsJsonObject("data").getAsJsonArray("members").get(0).getAsJsonObject().get("ready").getAsBoolean());
            host.ok("REMIND_READY","null");
            assertEquals(host.username,member.event("READY_REMINDER").getAsJsonObject("data").get("fromUser").getAsString());
            assertCode(host.request("REMIND_READY","null"),"TOO_FAST");
            host.ok("SET_READY","{\"ready\":false}");
            assertCode(host.request("REMIND_READY","null"),"NOT_READY_YOURSELF");
        }
    }
}
