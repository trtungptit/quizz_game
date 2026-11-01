package quizgame.server.net;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import static quizgame.server.net.RoomInviteWorkflowIntegrationTest.*;

/** Fixtures chỉ được tạo trong MySQL disposable do runner cung cấp. */
final class GameplaySupport implements AutoCloseable {
    final Fixture f;
    final List<Long> sets=new ArrayList<>();
    GameplaySupport(int players)throws Exception{f=new Fixture(players);}
    long seed(int count)throws Exception{
        long id=f.db.transaction(c->{
            long owner;
            try(var s=c.prepareStatement("SELECT user_id FROM users WHERE username=?")){
                s.setString(1,f.peers.get(0).username);try(var r=s.executeQuery()){r.next();owner=r.getLong(1);}
            }
            long set;
            try(var s=c.prepareStatement("INSERT INTO question_sets(owner_id,title,topic,description) VALUES(?,'Toán thử','Toán','Fixture')",Statement.RETURN_GENERATED_KEYS)){
                s.setLong(1,owner);s.executeUpdate();try(var k=s.getGeneratedKeys()){k.next();set=k.getLong(1);}
            }
            for(int i=0;i<count;i++){
                long q;
                try(var s=c.prepareStatement("INSERT INTO questions(set_id,type,prompt,time_limit_seconds,position) VALUES(?,'SINGLE','1 + 1 = ?',5,?)",Statement.RETURN_GENERATED_KEYS)){
                    s.setLong(1,set);s.setInt(2,i+1);s.executeUpdate();try(var k=s.getGeneratedKeys()){k.next();q=k.getLong(1);}
                }
                for(int j=0;j<4;j++)try(var s=c.prepareStatement("INSERT INTO question_options(question_id,option_index,option_text,is_correct) VALUES(?,?,?,?)")){
                    s.setLong(1,q);s.setInt(2,j);s.setString(3,String.valueOf(j+1));s.setBoolean(4,j==1);s.executeUpdate();
                }
            }
            return set;
        });sets.add(id);return id;
    }
    void joinAll()throws Exception{
        String code=f.peers.get(0).create();
        for(int i=1;i<f.peers.size();i++)f.peers.get(i).ok("JOIN_ROOM","{\"code\":\""+code+"\"}");
    }
    void configure(long id,int count)throws Exception{f.peers.get(0).ok("ROOM_CONFIG","{\"setId\":"+id+",\"numQuestions\":"+count+",\"timeOverrideSeconds\":null}");}
    void readyAll()throws Exception{for(Peer p:f.peers)p.ok("SET_READY","{\"ready\":true}");}
    @Override public void close()throws Exception{
        f.server.close();
        f.db.transaction(c->{
            for(long set:sets){
                List<Long> matches=new ArrayList<>();
                try(var s=c.prepareStatement("SELECT match_id FROM rounds WHERE set_id=?")){s.setLong(1,set);try(var r=s.executeQuery()){while(r.next())matches.add(r.getLong(1));}}
                for(long match:matches){
                    for(String sql:List.of("DELETE FROM submissions WHERE match_id=?","DELETE FROM round_questions WHERE round_id IN (SELECT round_id FROM rounds WHERE match_id=?)","DELETE FROM rounds WHERE match_id=?","DELETE FROM match_players WHERE match_id=?","DELETE FROM matches WHERE match_id=?")){
                        try(var s=c.prepareStatement(sql)){s.setLong(1,match);s.executeUpdate();}
                    }
                }
                try(var s=c.prepareStatement("UPDATE rooms SET set_id=NULL,num_questions=0 WHERE set_id=?")){s.setLong(1,set);s.executeUpdate();}
                try(var s=c.prepareStatement("DELETE FROM questions WHERE set_id=?")){s.setLong(1,set);s.executeUpdate();}
                try(var s=c.prepareStatement("DELETE FROM question_sets WHERE set_id=?")){s.setLong(1,set);s.executeUpdate();}
            }return null;
        });f.close();
    }
}
