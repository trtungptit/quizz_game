package quizgame.server.net;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import static quizgame.server.net.RoomInviteWorkflowIntegrationTest.*;

@EnabledIfEnvironmentVariable(named="QUIZ_TEST_DB_URL",matches=".+")
class GameplayIntegrationTest {
    static String answer(int index,int choice){return "{\"qIndex\":"+index+",\"answer\":{\"choices\":["+choice+"],\"text\":null}}";}
    @Test void multiAndTextAnswersPersistTheirOwnOptionsAndNormalizedText()throws Exception{
        try(GameplaySupport g=new GameplaySupport(2)){
            g.joinAll();long set=g.seed(2);
            g.f.db.transaction(c->{
                try(var s=c.prepareStatement("UPDATE questions SET type='MULTI' WHERE set_id=? AND position=1")){s.setLong(1,set);s.executeUpdate();}
                try(var s=c.prepareStatement("UPDATE question_options o JOIN questions q ON q.question_id=o.question_id SET o.is_correct=(o.option_index IN (1,3)) WHERE q.set_id=? AND q.position=1")){s.setLong(1,set);s.executeUpdate();}
                try(var s=c.prepareStatement("DELETE o FROM question_options o JOIN questions q ON q.question_id=o.question_id WHERE q.set_id=? AND q.position=2")){s.setLong(1,set);s.executeUpdate();}
                try(var s=c.prepareStatement("UPDATE questions SET type='TYPE' WHERE set_id=? AND position=2")){s.setLong(1,set);s.executeUpdate();}
                try(var s=c.prepareStatement("INSERT INTO accepted_answers(question_id,answer_text,normalized_text) SELECT question_id,'Hà Nội','hà nội' FROM questions WHERE set_id=? AND position=2")){s.setLong(1,set);s.executeUpdate();}
                return null;
            });
            g.configure(set,2);g.readyAll();var a=g.f.peers.get(0);var b=g.f.peers.get(1);a.ok("START_GAME","null");
            for(Peer p:g.f.peers){p.event("GAME_START");assertEquals("MULTI",p.event("QUESTION").getAsJsonObject("data").get("type").getAsString());}
            assertCode(a.request("ANSWER","{\"qIndex\":0,\"answer\":{\"choices\":[1,1],\"text\":null}}"),"BAD_REQUEST");
            a.ok("ANSWER","{\"qIndex\":0,\"answer\":{\"choices\":[3,1],\"text\":null}}");b.ok("ANSWER",answer(0,0));
            for(Peer p:g.f.peers){p.event("ANSWER_RESULT");assertEquals("TYPE",p.event("QUESTION").getAsJsonObject("data").get("type").getAsString());}
            a.ok("ANSWER","{\"qIndex\":1,\"answer\":{\"choices\":[],\"text\":\"  HÀ   NỘI \"}}");b.ok("ANSWER","{\"qIndex\":1,\"answer\":{\"choices\":[],\"text\":\"Ha Noi\"}}");
            assertTrue(a.event("ANSWER_RESULT").getAsJsonObject("data").get("correct").getAsBoolean());
            assertFalse(b.event("ANSWER_RESULT").getAsJsonObject("data").get("correct").getAsBoolean());
            long match=a.event("GAME_END").getAsJsonObject("data").get("matchId").getAsLong();
            assertEquals(2L,((Number)g.f.value("SELECT COUNT(*) FROM submission_options o JOIN submissions s ON s.submission_id=o.submission_id JOIN users u ON u.user_id=s.user_id WHERE s.match_id=? AND u.username=?",match,a.username)).longValue());
        }
    }
    @Test void invalidImageFailsBeforeCreatingMatchAndCanBeCorrected()throws Exception{
        try(GameplaySupport g=new GameplaySupport(2)){
            g.joinAll();long set=g.seed(1);g.configure(set,1);g.readyAll();var a=g.f.peers.get(0);
            g.f.db.transaction(c->{try(var s=c.prepareStatement("UPDATE questions SET image_ref='../not-an-image.png' WHERE set_id=?")){s.setLong(1,set);s.executeUpdate();}return null;});
            assertCode(a.request("START_GAME","null"),"INTERNAL");
            assertEquals(0L,((Number)g.f.value("SELECT COUNT(*) FROM matches")).longValue());
            assertEquals("IN_ROOM",g.f.online.statusByUsername(a.username).name());
            g.f.db.transaction(c->{try(var s=c.prepareStatement("UPDATE questions SET image_ref=NULL WHERE set_id=?")){s.setLong(1,set);s.executeUpdate();}return null;});
            a.ok("START_GAME","null");assertTrue(a.event("GAME_START").getAsJsonObject("data").getAsJsonObject("imagesBase64").isEmpty());
            assertTrue(a.event("QUESTION").getAsJsonObject("data").get("imageId").isJsonNull());
        }
    }
    @Test void startsAndFinishesTwoQuestionsAtIndependentPlayerSpeeds()throws Exception{
        try(GameplaySupport g=new GameplaySupport(2)){
            var f=g.f;Peer a=f.peers.get(0),b=f.peers.get(1);g.joinAll();
            assertCode(a.request("START_GAME","null"),"NO_SET");
            long set=g.seed(2);g.configure(set,2);
            assertCode(b.request("START_GAME","null"),"NOT_HOST");
            assertCode(a.request("START_GAME","null"),"NOT_ALL_READY");
            g.readyAll();a.ok("START_GAME","null");
            a.event("GAME_START");b.event("GAME_START");
            var first=a.event("QUESTION").getAsJsonObject("data");b.event("QUESTION");
            assertEquals(0,first.get("qIndex").getAsInt());assertFalse(first.has("correctIndexes"));assertFalse(first.has("acceptedAnswers"));
            assertCode(a.request("SET_READY","{\"ready\":false}"),"ROOM_PLAYING");
            a.ok("ANSWER",answer(0,1));a.event("ANSWER_RESULT");
            assertCode(a.request("ANSWER",answer(0,1)),"ALREADY_ANSWERED");
            assertEquals(1,a.event("QUESTION").getAsJsonObject("data").get("qIndex").getAsInt());
            a.ok("ANSWER",answer(1,1));a.event("ANSWER_RESULT");
            b.ok("ANSWER",answer(0,0));b.event("ANSWER_RESULT");
            assertEquals(1,b.event("QUESTION").getAsJsonObject("data").get("qIndex").getAsInt());
            b.ok("ANSWER",answer(1,0));b.event("ANSWER_RESULT");
            var end=a.event("GAME_END").getAsJsonObject("data");b.event("GAME_END");
            assertEquals(a.username,end.get("winner").getAsString());assertFalse(end.get("draw").getAsBoolean());
            long match=end.get("matchId").getAsLong();
            assertEquals("completed",f.value("SELECT status FROM matches WHERE match_id=?",match));
            assertEquals(4L,((Number)f.value("SELECT COUNT(*) FROM submissions WHERE match_id=? AND submitted_at IS NOT NULL",match)).longValue());
            assertEquals(1L,((Number)f.value("SELECT total_wins FROM users WHERE username=?",a.username)).longValue());
            a.eventWhere("ROOM_STATE",x->"WAITING".equals(x.getAsJsonObject("data").get("phase").getAsString()));
            assertEquals("IN_ROOM",f.online.statusByUsername(a.username).name());
            assertCode(a.request("START_GAME","null"),"NOT_ALL_READY");
        }
    }
}
