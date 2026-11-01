package quizgame.server.net;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import quizgame.common.model.MatchResult;
import quizgame.server.dao.MatchDao;
import quizgame.server.service.StartupRecovery;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static quizgame.server.net.RoomInviteWorkflowIntegrationTest.*;
import static quizgame.server.net.GameplayIntegrationTest.answer;

@EnabledIfEnvironmentVariable(named="QUIZ_TEST_DB_URL",matches=".+")
class GameplayConcurrencyIntegrationTest {
    static long begin(GameplaySupport g,int count)throws Exception{
        g.joinAll();long id=g.seed(count);g.configure(id,count);g.readyAll();g.f.peers.get(0).ok("START_GAME","null");
        for(Peer p:g.f.peers){p.event("GAME_START");p.event("QUESTION");}
        return ((Number)g.f.value("SELECT match_id FROM rounds WHERE set_id=?",id)).longValue();
    }
    @Test void quitThenLeaveAwardsOnlySurvivorAndClosesPendingSlots()throws Exception{
        try(GameplaySupport g=new GameplaySupport(2)){
            long id=begin(g,2);var a=g.f.peers.get(0);var b=g.f.peers.get(1);
            a.ok("QUIT_GAME","null");a.ok("LEAVE_ROOM","null");
            var result=b.event("GAME_END").getAsJsonObject("data");assertEquals(b.username,result.get("winner").getAsString());
            assertEquals("QUIT",g.f.value("SELECT player_status FROM match_players WHERE match_id=? AND user_id=(SELECT user_id FROM users WHERE username=?)",id,a.username));
            assertEquals(0L,((Number)g.f.value("SELECT COUNT(*) FROM submissions WHERE match_id=? AND submitted_at IS NULL",id)).longValue());
            assertEquals("IDLE",g.f.online.statusByUsername(a.username).name());
        }
    }
    @Test void timeoutsDrawAndReplayCreatesNewMatchWithoutOldTimers()throws Exception{
        try(GameplaySupport g=new GameplaySupport(2)){
            long first=begin(g,1);for(Peer p:g.f.peers){var r=p.event("ANSWER_RESULT").getAsJsonObject("data");assertFalse(r.get("correct").getAsBoolean());assertEquals(0,r.get("points").getAsInt());}
            var end=g.f.peers.get(0).event("GAME_END").getAsJsonObject("data");assertTrue(end.get("draw").getAsBoolean());
            assertEquals("draw",g.f.value("SELECT status FROM matches WHERE match_id=?",first));
            g.readyAll();g.f.peers.get(0).ok("START_GAME","null");
            for(Peer p:g.f.peers){p.event("GAME_START");p.event("QUESTION");}
            long second=((Number)g.f.value("SELECT MAX(match_id) FROM matches")).longValue();assertNotEquals(first,second);
            g.f.peers.get(0).ok("ANSWER",answer(0,1));g.f.peers.get(1).ok("ANSWER",answer(0,0));
            var replay=g.f.peers.get(0).eventWhere("GAME_END",x->x.getAsJsonObject("data").get("matchId").getAsLong()==second);
            assertEquals(g.f.peers.get(0).username,replay.getAsJsonObject("data").get("winner").getAsString());
        }
    }
    @Test void finalizeRetriesAfterSqlFailureAndNeverAwardsTwice()throws Exception{
        try(GameplaySupport g=new GameplaySupport(2)){
            long id=begin(g,1);var a=g.f.peers.get(0);var b=g.f.peers.get(1);
            try(var c=g.f.db.openConnection();var s=c.createStatement()){
                s.execute("CREATE TRIGGER fail_game_finish BEFORE UPDATE ON matches FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture-only failure'");
                try{
                    a.ok("ANSWER",answer(0,1));b.ok("ANSWER",answer(0,0));
                    assertEquals("in_progress",g.f.value("SELECT status FROM matches WHERE match_id=?",id));
                    assertEquals(0L,((Number)g.f.value("SELECT total_wins FROM users WHERE username=?",a.username)).longValue());
                }finally{s.execute("DROP TRIGGER IF EXISTS fail_game_finish");}
            }
            MatchResult result=new Gson().fromJson(a.event("GAME_END").get("data"),MatchResult.class);
            Map<String,Long> ids=Map.of(a.username,g.f.online.userIdByUsername(a.username),b.username,g.f.online.userIdByUsername(b.username));
            long room=((Number)g.f.value("SELECT room_id FROM matches WHERE match_id=?",id)).longValue();
            g.f.db.transaction(c->{new MatchDao().finish(c,room,result,ids);return null;});
            assertEquals(1L,((Number)g.f.value("SELECT total_wins FROM users WHERE username=?",a.username)).longValue());
        }
    }
    @Test void shutdownAndRecoveryCancelMatchWithoutFabricatingWinner()throws Exception{
        try(GameplaySupport g=new GameplaySupport(2)){
            long id=begin(g,2);g.f.server.close();
            assertEquals("cancelled",g.f.value("SELECT status FROM matches WHERE match_id=?",id));
            assertEquals(0L,((Number)g.f.value("SELECT total_wins FROM users WHERE username=?",g.f.names.get(0))).longValue());
        }
        try(GameplaySupport g=new GameplaySupport(2)){
            long id=begin(g,2);new StartupRecovery(g.f.db).reconcile();
            assertEquals("cancelled",g.f.value("SELECT status FROM matches WHERE match_id=?",id));
            assertEquals("completed",g.f.value("SELECT status FROM rounds WHERE match_id=?",id));
        }
    }
    @Test void busyCoordinatorDoesNotExtendQuestionDeadlineAndLateAnswerCannotScore()throws Exception{
        try(GameplaySupport g=new GameplaySupport(2)){
            g.joinAll();long set=g.seed(1);g.configure(set,1);g.readyAll();var a=g.f.peers.get(0);
            synchronized(g.f.online){
                g.f.games.start(g.f.online.sessionByUsername(a.username),++a.id);
                for(Peer p:g.f.peers){p.event("GAME_START");p.event("QUESTION");}
                Thread.sleep(5650);
            }
            var response=a.request("ANSWER",answer(0,1));assertFalse(response.get("ok").getAsBoolean());
            for(Peer p:g.f.peers){var r=p.event("ANSWER_RESULT").getAsJsonObject("data");assertEquals(0,r.get("points").getAsInt());}
            assertTrue(a.event("GAME_END").getAsJsonObject("data").get("draw").getAsBoolean());
            assertEquals(0L,((Number)g.f.value("SELECT COALESCE(SUM(awarded_points),0) FROM submissions")).longValue());
        }
    }
    @Test void failedAnswerRollsBackAndStartFailureDoesNotLeavePartialMatch()throws Exception{
        try(GameplaySupport g=new GameplaySupport(2)){
            g.joinAll();long set=g.seed(1);g.configure(set,1);g.readyAll();var a=g.f.peers.get(0);
            try(var c=g.f.db.openConnection();var s=c.createStatement()){
                s.execute("CREATE TRIGGER fail_start BEFORE INSERT ON match_players FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture-only failure'");
                try{assertCode(a.request("START_GAME","null"),"INTERNAL");assertEquals(0L,((Number)g.f.value("SELECT COUNT(*) FROM matches")).longValue());}
                finally{s.execute("DROP TRIGGER IF EXISTS fail_start");}
                a.ok("START_GAME","null");for(Peer p:g.f.peers){p.event("GAME_START");p.event("QUESTION");}
                s.execute("CREATE TRIGGER fail_answer BEFORE INSERT ON submission_options FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture-only failure'");
                try{assertCode(a.request("ANSWER",answer(0,1)),"INTERNAL");assertEquals(0L,((Number)g.f.value("SELECT COUNT(*) FROM submissions WHERE submitted_at IS NOT NULL")).longValue());}
                finally{s.execute("DROP TRIGGER IF EXISTS fail_answer");}
                a.ok("ANSWER",answer(0,1));g.f.peers.get(1).ok("ANSWER",answer(0,0));a.event("GAME_END");
            }
        }
    }
    @Test void disconnectFinishesWithSurvivorAndStartVersusLeaveStaysConsistent()throws Exception{
        try(GameplaySupport g=new GameplaySupport(2)){
            long id=begin(g,2);var a=g.f.peers.get(0);var b=g.f.peers.get(1);a.close();
            assertEquals(b.username,b.event("GAME_END").getAsJsonObject("data").get("winner").getAsString());
            assertEquals("DISCONNECTED",g.f.value("SELECT player_status FROM match_players WHERE match_id=? AND user_id=(SELECT user_id FROM users WHERE username=?)",id,a.username));
        }
        try(GameplaySupport g=new GameplaySupport(2)){
            g.joinAll();long set=g.seed(1);g.configure(set,1);g.readyAll();var a=g.f.peers.get(0);var b=g.f.peers.get(1);
            var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
            try{
                var x=pool.submit(()->{start.await();return a.request("START_GAME","null");});
                var y=pool.submit(()->{start.await();return b.request("LEAVE_ROOM","null");});start.countDown();
                var result=x.get(8,TimeUnit.SECONDS);assertTrue(y.get(8,TimeUnit.SECONDS).get("ok").getAsBoolean());
                if(result.get("ok").getAsBoolean()){a.event("GAME_END");assertEquals("IN_ROOM",g.f.online.statusByUsername(a.username).name());}
                else assertCode(result,"NOT_ENOUGH_PLAYERS");
                assertEquals("IDLE",g.f.online.statusByUsername(b.username).name());
            }finally{pool.shutdownNow();}
        }
    }
    @Test void leavingWhileFinalizationRetriesCannotDragPlayerOutOfTheirNextGame()throws Exception{
        try(GameplaySupport g=new GameplaySupport(3)){
            begin(g,1);var a=g.f.peers.get(0);var b=g.f.peers.get(1);var c=g.f.peers.get(2);long set=g.sets.get(0);
            try(var connection=g.f.db.openConnection();var statement=connection.createStatement()){
                statement.execute("CREATE TRIGGER fail_old_finish BEFORE UPDATE ON matches FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture-only failure'");
                long newer;
                try{
                    a.ok("ANSWER",answer(0,1));b.ok("ANSWER",answer(0,0));c.ok("ANSWER",answer(0,0));
                    a.ok("LEAVE_ROOM","null");String code=a.create();b.ok("LEAVE_ROOM","null");b.ok("JOIN_ROOM","{\"code\":\""+code+"\"}");
                    g.configure(set,1);a.ok("SET_READY","{\"ready\":true}");b.ok("SET_READY","{\"ready\":true}");a.ok("START_GAME","null");
                    for(Peer p:List.of(a,b)){p.event("GAME_START");p.event("QUESTION");}
                    newer=((Number)g.f.value("SELECT MAX(match_id) FROM matches")).longValue();
                }finally{statement.execute("DROP TRIGGER IF EXISTS fail_old_finish");}
                c.event("GAME_END");
                assertEquals("PLAYING",g.f.online.statusByUsername(a.username).name());
                a.ok("PING","null");assertTrue(a.events.stream().noneMatch(x->"GAME_END".equals(x.get("type").getAsString())));
                a.ok("ANSWER",answer(0,1));b.ok("ANSWER",answer(0,0));
                assertEquals(newer,a.event("GAME_END").getAsJsonObject("data").get("matchId").getAsLong());
            }
        }
    }
    @Test void simultaneousStartCreatesOnlyOneMatch()throws Exception{
        try(GameplaySupport g=new GameplaySupport(2)){
            g.joinAll();long set=g.seed(1);g.configure(set,1);g.readyAll();Peer host=g.f.peers.get(0);
            host.send("START_GAME","null");long first=host.id;host.send("START_GAME","null");long second=host.id;
            Map<Long,Boolean> responses=new HashMap<>();
            for(int i=0;i<100&&responses.size()<2;i++){var r=host.read();if(r.has("reqId"))responses.put(r.get("reqId").getAsLong(),r.get("ok").getAsBoolean());else host.events.add(r);}
            assertEquals(Boolean.TRUE,responses.get(first));assertEquals(Boolean.FALSE,responses.get(second));
            assertEquals(1L,((Number)g.f.value("SELECT COUNT(*) FROM rounds WHERE set_id=?",set)).longValue());
        }
    }
}
