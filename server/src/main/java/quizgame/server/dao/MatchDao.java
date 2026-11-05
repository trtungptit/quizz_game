package quizgame.server.dao;

import quizgame.common.model.*;
import quizgame.server.service.ServiceException;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** SQL của trận; caller quản lý transaction, không publish event từ DAO. */
public final class MatchDao {
    public record GameQuestion(long id,QuestionDraft draft,List<Long> optionIds,String imageRef){}
    public record Participant(long id,String username){}
    public record Started(long id,long roundId,List<Long> roundQuestions){}
    public List<GameQuestion> load(Connection c,long setId,int count)throws SQLException{
        // Giữ phiên bản bộ ổn định từ lúc đọc câu tới khi tạo round.
        try(var s=c.prepareStatement("SELECT set_id FROM question_sets WHERE set_id=? AND deleted_at IS NULL FOR UPDATE")){
            s.setLong(1,setId);try(var r=s.executeQuery()){
                if(!r.next())throw new ServiceException("SET_NOT_FOUND","Không tìm thấy bộ câu hỏi");
            }
        }
        var stored=new QuestionSetDao().get(c,setId,true).orElseThrow(()->new ServiceException("SET_NOT_FOUND","Không tìm thấy bộ câu hỏi"));
        if(count<1||count>stored.value().questions().size())throw new ServiceException("INVALID_INPUT","Số câu không hợp lệ");
        List<GameQuestion> out=new ArrayList<>();List<Long> ids=new ArrayList<>();
        try(var s=c.prepareStatement("SELECT question_id FROM questions WHERE set_id=? ORDER BY position,question_id LIMIT ? FOR UPDATE")){
            s.setLong(1,setId);s.setInt(2,count);try(var r=s.executeQuery()){while(r.next())ids.add(r.getLong(1));}
        }
        for(int i=0;i<ids.size();i++){
            List<Long> options=new ArrayList<>();
            try(var s=c.prepareStatement("SELECT option_id FROM question_options WHERE question_id=? ORDER BY option_index FOR UPDATE")){
                s.setLong(1,ids.get(i));try(var r=s.executeQuery()){while(r.next())options.add(r.getLong(1));}
            }
            out.add(new GameQuestion(ids.get(i),stored.value().questions().get(i),List.copyOf(options),stored.imageRefs().get(i)));
        }
        return List.copyOf(out);
    }
    public Started start(Connection c,long roomId,long setId,List<GameQuestion> questions,List<Participant> players)throws SQLException{
        long match=insert(c,"INSERT INTO matches(room_id,status,started_at) VALUES(?,'in_progress',CURRENT_TIMESTAMP)",roomId);
        for(var p:players)update(c,"INSERT INTO match_players(match_id,user_id) VALUES(?,?)",match,p.id());
        long round=insert(c,"INSERT INTO rounds(match_id,set_id,round_number,status,started_at) VALUES(?,?,1,'in_progress',CURRENT_TIMESTAMP)",match,setId);
        List<Long> rq=new ArrayList<>();
        for(int i=0;i<questions.size();i++)rq.add(insert(c,"INSERT INTO round_questions(round_id,question_id,position) VALUES(?,?,?)",round,questions.get(i).id(),i+1));
        update(c,"UPDATE rooms SET status='playing' WHERE room_id=? AND status='waiting'",roomId);
        update(c,"UPDATE invitations SET status='cancelled',responded_at=CURRENT_TIMESTAMP(3) WHERE room_id=? AND status='pending'",roomId);
        return new Started(match,round,List.copyOf(rq));
    }
    public long slot(Connection c,long match,long round,long roundQuestion,long question,long user)throws SQLException{
        return insert(c,"INSERT INTO submissions(round_question_id,user_id,question_id,match_id,round_id) VALUES(?,?,?,?,?)",roundQuestion,user,question,match,round);
    }
    public void sent(Connection c,long id,Instant at,long limit)throws SQLException{
        update(c,"UPDATE submissions SET question_sent_at=?,deadline_at=? WHERE submission_id=? AND question_sent_at IS NULL",Timestamp.from(at),Timestamp.from(at.plusMillis(limit)),id);
    }
    public void answer(Connection c,long id,GameQuestion question,Answer answer,boolean correct,int points)throws SQLException{
        if(update(c,"UPDATE submissions SET answer_text=?,submitted_at=CURRENT_TIMESTAMP(3),is_correct=?,awarded_points=? WHERE submission_id=? AND submitted_at IS NULL",
                answer==null?null:answer.text(),correct,points,id)!=1)throw new ServiceException("ALREADY_ANSWERED","Câu này đã trả lời");
        if(answer!=null&&answer.choices()!=null)for(int index:answer.choices())
            update(c,"INSERT INTO submission_options(submission_id,question_id,option_id) VALUES(?,?,?)",id,question.id(),question.optionIds().get(index));
    }
    public void depart(Connection c,long match,long user,PlayerStatus status)throws SQLException{
        update(c,"UPDATE match_players SET player_status=? WHERE match_id=? AND user_id=?",status.name(),match,user);
        update(c,"UPDATE submissions SET submitted_at=CURRENT_TIMESTAMP(3),is_correct=FALSE,awarded_points=0 WHERE match_id=? AND user_id=? AND submitted_at IS NULL",match,user);
    }
    public void finish(Connection c,long roomId,MatchResult result,Map<String,Long> userIds)throws SQLException{
        String state=result.rows().stream().noneMatch(r->r.status()==PlayerStatus.FINISHED)?"cancelled":result.draw()?"draw":"completed";
        Long winner=result.winner()==null?null:userIds.get(result.winner());
        int changed=update(c,"UPDATE matches SET status=?,winner_id=?,ended_at=CURRENT_TIMESTAMP WHERE match_id=? AND status='in_progress'",state,winner,result.matchId());
        if(changed==0){
            try(var s=c.prepareStatement("SELECT status FROM matches WHERE match_id=?")){
                s.setLong(1,result.matchId());try(var r=s.executeQuery()){
                    if(!r.next()||!state.equals(r.getString(1)))throw new SQLException("Trận không ở trạng thái kết thúc mong đợi");
                }
            }return;
        }
        for(var row:result.rows())update(c,"UPDATE match_players SET final_score=?,correct_count=?,finish_time_ms=?,player_status=? WHERE match_id=? AND user_id=?",
                row.score(),row.correctCount(),row.finishTimeMs(),row.status().name(),result.matchId(),userIds.get(row.username()));
        update(c,"UPDATE submissions SET submitted_at=CURRENT_TIMESTAMP(3),is_correct=FALSE,awarded_points=0 WHERE match_id=? AND submitted_at IS NULL",result.matchId());
        update(c,"UPDATE rounds SET status='completed',ended_at=CURRENT_TIMESTAMP WHERE match_id=?",result.matchId());
        if(winner!=null)update(c,"UPDATE users SET total_wins=total_wins+1 WHERE user_id=?",winner);
        update(c,"UPDATE rooms SET status='waiting' WHERE room_id=? AND status='playing'",roomId);
        update(c,"UPDATE room_members SET ready=FALSE WHERE room_id=? AND left_at IS NULL",roomId);
    }
    public void cancel(Connection c,long match)throws SQLException{
        update(c,"UPDATE matches SET status='cancelled',ended_at=CURRENT_TIMESTAMP WHERE match_id=? AND status IN ('pending','in_progress')",match);
        update(c,"UPDATE rounds SET status='completed',ended_at=CURRENT_TIMESTAMP WHERE match_id=? AND status IN ('pending','in_progress')",match);
        update(c,"UPDATE submissions SET submitted_at=CURRENT_TIMESTAMP(3),is_correct=FALSE,awarded_points=0 WHERE match_id=? AND submitted_at IS NULL",match);
    }
    private static int update(Connection c,String sql,Object...args)throws SQLException{
        try(var s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s.executeUpdate();}
    }
    private static long insert(Connection c,String sql,Object...args)throws SQLException{
        try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();
            try(var r=s.getGeneratedKeys()){if(!r.next())throw new SQLException("Không lấy được ID");return r.getLong(1);}
        }
    }
}
