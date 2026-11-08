package quizgame.server.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import quizgame.common.model.*;
import quizgame.common.protocol.FramedIO;
import quizgame.server.dao.*;
import quizgame.server.net.ClientSession;
import quizgame.server.net.ProtocolMessages;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Điều phối trận thật, mọi mutation dùng cùng monitor online với phòng/lời mời. */
public final class GameManager implements AutoCloseable {
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private final Database db;private final RoomDao roomDao;private final RoomManager rooms;private final OnlineRegistry online;
    private final MatchDao dao=new MatchDao();
    private final Random random=new java.security.SecureRandom(); // xếp hộp quà, chọn hộp khi hết giờ
    private final Map<Long,GameSession> games=new HashMap<>();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"quiz-game-timers");t.setDaemon(true);return t;});
    private boolean closed;
    public GameManager(Database db,RoomDao roomDao,OnlineRegistry online,RoomManager rooms){this.db=db;this.roomDao=roomDao;this.online=online;this.rooms=rooms;}

    public void start(ClientSession host,long reqId){synchronized(online){
        if(closed)throw error("INTERNAL","Server đang dừng");
        try{
            Prepared prepared=db.transaction(c->{
                var room=rooms.current(c,host,true,true);
                if(games.containsKey(room.id()))throw error("ROOM_PLAYING","Trận trước chưa kết thúc");
                if(room.setId()==null)throw error("NO_SET","Hãy chọn bộ câu hỏi");
                var members=roomDao.members(c,room.id());
                if(members.size()<2)throw error("NOT_ENOUGH_PLAYERS","Cần ít nhất hai người chơi");
                if(members.stream().anyMatch(m->!m.ready()))throw error("NOT_ALL_READY","Mọi người cần sẵn sàng");
                var questions=dao.load(c,room.setId(),room.numQuestions());
                Map<String,String> images=new LinkedHashMap<>();
                var reader=new QuestionImageReader(Path.of("images"));
                for(int i=0;i<questions.size();i++){
                    var q=questions.get(i);validate(q.draft());
                    if(q.imageRef()!=null&&!q.imageRef().isBlank())images.put("q"+i,reader.read(q.imageRef()));
                    requireFrame(event("QUESTION",wireQuestion(q,i,questions.size(),room.timeOverrideSeconds())));
                }
                String startFrame=event("GAME_START",new GameStart(questions.size(),Map.copyOf(images)));requireFrame(startFrame);
                List<GameSession.Player> players=new ArrayList<>();List<MatchDao.Participant> participants=new ArrayList<>();
                for(var member:members){
                    ClientSession peer=online.sessionByUsername(member.username());
                    if(peer==null||!peer.isOpen())throw error("NOT_ALL_READY","Một người chơi đã mất kết nối");
                    long id=online.userId(peer);players.add(new GameSession.Player(id,member.username(),peer));participants.add(new MatchDao.Participant(id,member.username()));
                }
                var started=dao.start(c,room.id(),room.setId(),questions,participants);
                var boxAfter=BoxRules.positions(questions.size());
                List<List<BoxEffect>> layouts=new ArrayList<>();for(int i=0;i<boxAfter.size();i++)layouts.add(BoxRules.shuffle(random));
                var game=new GameSession(started.id(),started.roundId(),room.id(),questions,started.roundQuestions(),players,room.timeOverrideSeconds(),boxAfter,layouts);
                return new Prepared(game,startFrame);
            });
            var g=prepared.game();games.put(g.roomId,g);
            host.send(ProtocolMessages.response("START_GAME",reqId,null,null));
            for(var p:g.players){online.changeStatus(p.session,UserStatus.PLAYING);p.session.send(prepared.frame());}
            rooms.invitationsChanged();rooms.publishRoom(g.roomId);
            for(var p:g.players)sendQuestion(g,p);
        }catch(SQLException ex){throw error("INTERNAL","Không thể bắt đầu trận lúc này");}
    }}

    private static void validate(QuestionDraft q){
        if(q.type()==null||q.content()==null||q.content().isBlank()||q.content().length()>500||q.timeSeconds()<5||q.timeSeconds()>120)throw error("INVALID_INPUT","Bộ câu hỏi có câu không hợp lệ");
        if(q.type()==QuestionType.TYPE){if(!q.options().isEmpty()||q.acceptedAnswers().isEmpty()||q.acceptedAnswers().stream().anyMatch(a->a.isBlank()||a.length()>200))throw error("INVALID_INPUT","Câu nhập chữ thiếu đáp án");}
        else if(q.options().size()!=4||q.options().stream().anyMatch(o->o.isBlank()||o.length()>200)||q.correctIndexes().isEmpty()||new HashSet<>(q.correctIndexes()).size()!=q.correctIndexes().size()||q.correctIndexes().stream().anyMatch(i->i<0||i>3)||(q.type()==QuestionType.SINGLE&&q.correctIndexes().size()!=1))throw error("INVALID_INPUT","Câu trắc nghiệm không hợp lệ");
    }
    private static Question wireQuestion(MatchDao.GameQuestion q,int i,int total,Integer override){
        return new Question(i,total,q.draft().type(),q.draft().content(),q.draft().options(),q.imageRef()==null||q.imageRef().isBlank()?null:"q"+i,(override==null?q.draft().timeSeconds():override)*1000L);
    }
    private boolean active(GameSession g){return !closed&&!g.ended&&games.get(g.roomId)==g&&g.result==null;}
    private void sendQuestion(GameSession g,GameSession.Player p){
        if(!active(g)||p.left()||p.done)return;
        try{
            var question=g.questions.get(p.index);
            Question wire=wireQuestion(question,p.index,g.questions.size(),g.override);
            long slot=db.transaction(c->dao.slot(c,g.id,g.roundId,g.roundQuestions.get(p.index),question.id(),p.id));
            var state=new GameSession.Slot(slot,p.index,wire.timeLimitMs());p.slot=state;
            // Hiệu lực hộp quà dành cho câu này (chỉ dùng một lần)
            state.noLate=p.nextNoLate;state.doubled=p.nextDouble;p.nextNoLate=false;p.nextDouble=false;
            p.session.send(event("QUESTION",wire),()->{
                state.written();
                schedule(()->{
                    if(!active(g)||p.left()||p.slot!=state||state.answered)return;
                    try{ensureSent(g,p,state);}catch(SQLException ex){schedule(()->persistSent(g,p,state),500);}
                },0);
            });
        }catch(SQLException ex){schedule(()->sendQuestion(g,p),500);}
    }
    private void persistSent(GameSession g,GameSession.Player p,GameSession.Slot slot){
        if(!active(g)||p.left()||p.slot!=slot||slot.answered)return;
        try{ensureSent(g,p,slot);}catch(SQLException ex){schedule(()->persistSent(g,p,slot),500);}
    }
    private void ensureSent(GameSession g,GameSession.Player p,GameSession.Slot slot)throws SQLException{
        if(slot.persisted||slot.sentNanos.get()==0)return;
        Instant at=slot.sentWall.get();if(at==null)at=Instant.now();final Instant sent=at;
        db.transaction(c->{dao.sent(c,slot.id,sent,slot.limitMs);return null;});slot.persisted=true;
        long remaining=TimeUnit.MILLISECONDS.toNanos(slot.limitMs+500)-(System.nanoTime()-slot.sentNanos.get());
        slot.timeout=schedule(()->timeout(g,p,slot),Math.max(0,TimeUnit.NANOSECONDS.toMillis(remaining)+1));
    }
    public void answer(ClientSession session,int index,Answer answer,long reqId){synchronized(online){
        GameSession g=find(session);var p=player(g,session);
        if(g.result!=null||p.done||p.left())throw error("NOT_PLAYING","Bạn không còn chơi trận này");
        var slot=p.slot;
        if(slot==null||index!=p.index)throw error("WRONG_QUESTION","Không đúng câu đang chơi");
        if(slot.answered)throw error("ALREADY_ANSWERED","Câu này đã trả lời");
        if(slot.sentNanos.get()==0)throw error("WRONG_QUESTION","Câu hỏi chưa được gửi");
        try{
            ensureSent(g,p,slot);
            if(System.nanoTime()-slot.sentNanos.get()>TimeUnit.MILLISECONDS.toNanos(slot.limitMs+500)){
                complete(g,p,slot,null,null);throw error("TIME_UP","Câu hỏi đã hết thời gian");
            }
            complete(g,p,slot,answer,()->session.send(ProtocolMessages.response("ANSWER",reqId,null,null)));
        }catch(SQLException ex){throw error("INTERNAL","Không thể lưu đáp án lúc này");}
    }}
    private void complete(GameSession g,GameSession.Player p,GameSession.Slot slot,Answer answer,Runnable ack)throws SQLException{
        if(slot.answered||p.left()||!active(g))return;
        var question=g.questions.get(slot.index);boolean correct=answer!=null&&Scoring.correct(question.draft(),answer);
        long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-slot.sentNanos.get());
        int points=BoxRules.points(Scoring.points(correct,elapsed,slot.limitMs),correct,slot.noLate,slot.doubled);
        db.transaction(c->{dao.answer(c,slot.id,question,answer,correct,points);return null;});
        slot.answered=true;if(slot.timeout!=null)slot.timeout.cancel(false);p.score+=points;if(correct)p.correct++;
        if(slot.index==g.questions.size()-1){p.done=true;p.finishMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-g.startedNanos);}
        if(ack!=null)ack.run();
        var result=new AnswerResult(slot.index,correct,question.draft().correctIndexes(),question.draft().acceptedAnswers(),points,p.score);
        p.session.send(event("ANSWER_RESULT",result),()->{
            long at=System.nanoTime();
            schedule(()->{
                if(!active(g)||p.done||p.left()||p.slot!=slot)return;
                long remaining=1500-TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-at);
                p.next=schedule(()->{
                    if(!active(g)||p.done||p.left()||p.slot!=slot)return;
                    // Câu mốc thì mời mở hộp quà trước, ngược lại sang câu kế tiếp
                    int box=g.boxRoundAfter(slot.index);
                    if(box>0&&!p.boxes.containsKey(box))offerBox(g,p,box);else{p.index++;sendQuestion(g,p);}
                },Math.max(0,remaining));
            },0);
        });
        scoreboard(g);maybeFinish(g);
    }

    // ---------------------------------------------------------------- Hộp quà bí ẩn

    private void offerBox(GameSession g,GameSession.Player p,int round){
        if(!active(g)||p.done||p.left())return;
        p.boxRound=round;
        p.boxes.put(round,new BoxStatus(p.username,round,BoxState.CHOOSING,null,null,0,null,0,null));
        p.session.send(event("BOX_OFFER",new BoxOffer(round,g.boxAfter.get(round-1),BoxRules.PICK_TIMEOUT_MS)));
        // Hết giờ mà chưa chọn thì Server chọn ngẫu nhiên
        p.boxTimer=schedule(()->{if(active(g)&&!p.left()&&p.boxRound==round)resolveBox(g,p,random.nextInt(4),true);},
                BoxRules.PICK_TIMEOUT_MS+BoxRules.PICK_GRACE_MS);
        boxStatus(g);
    }

    public void pickBox(ClientSession session,int round,int box,long reqId){synchronized(online){
        GameSession g=find(session);var p=player(g,session);
        if(g.result!=null||p.done||p.left())throw error("NOT_PLAYING","Bạn không còn chơi trận này");
        if(p.boxRound==0||p.boxRound!=round)throw error("NO_BOX","Không có hộp quà đang chờ chọn");
        if(box<0||box>3)throw new IllegalArgumentException();
        session.send(ProtocolMessages.response("BOX_PICK",reqId,null,null));
        resolveBox(g,p,box,false);
    }}

    /** Mở hộp: áp hiệu ứng (Server quyết định), báo kết quả, rồi sau REVEAL_MS gửi câu kế tiếp. */
    private void resolveBox(GameSession g,GameSession.Player p,int box,boolean auto){
        int round=p.boxRound;if(round==0)return;
        p.boxRound=0;if(p.boxTimer!=null)p.boxTimer.cancel(false);
        var layout=g.boxLayouts.get(round-1);var effect=layout.get(box);
        int delta=0,targetDelta=0;String target=null;Integer apply=null;
        switch(effect){
            case STEAL->{
                // Người còn chơi đang dẫn đầu, không tính mình
                var leader=g.players.stream().filter(x->x!=p&&!x.left()).sorted(order()).findFirst();
                if(leader.isPresent()){
                    var t=leader.get();int amount=BoxRules.stealAmount(t.score);
                    t.score-=amount;p.score+=amount;delta=amount;targetDelta=-amount;target=t.username;
                }
            }
            case LOSE->{int amount=BoxRules.loseAmount(p.score);p.score-=amount;delta=-amount;}
            case DOUBLE->{p.nextDouble=true;apply=p.index+1;}
            case NO_LATE->{p.nextNoLate=true;apply=p.index+1;}
        }
        p.boxes.put(round,new BoxStatus(p.username,round,BoxState.CHOSEN,box,effect,delta,target,targetDelta,apply));
        p.session.send(event("BOX_RESULT",new BoxResult(round,box,auto,layout,effect,delta,target,targetDelta,apply,p.score,BoxRules.REVEAL_MS)));
        boxStatus(g);
        if(delta!=0||targetDelta!=0)scoreboard(g);
        p.next=schedule(()->{
            if(!active(g)||p.done||p.left()||p.boxRound!=0)return;
            p.index++;sendQuestion(g,p);
        },BoxRules.REVEAL_MS);
    }

    /** Gửi riêng từng người danh sách lượt hộp; ẩn vị trí hộp của người khác ở lượt người nhận chưa chọn. */
    private void boxStatus(GameSession g){
        for(var q:g.players){
            if(q.left()||q.detached||!q.session.isOpen()||online.sessionByUsername(q.username)!=q.session)continue;
            List<BoxStatus> rows=new ArrayList<>();
            for(var x:g.players)for(var s:x.boxes.values()){
                var mine=q.boxes.get(s.round());
                boolean seen=x==q||mine!=null&&mine.state()==BoxState.CHOSEN;
                rows.add(seen?s:new BoxStatus(s.username(),s.round(),s.state(),null,s.effect(),s.delta(),s.target(),s.targetDelta(),s.applyQIndex()));
            }
            rows.sort(Comparator.comparingInt(BoxStatus::round).thenComparing(BoxStatus::username));
            q.session.send(event("BOX_STATUS",List.copyOf(rows)));
        }
    }
    private void timeout(GameSession g,GameSession.Player p,GameSession.Slot slot){
        if(!active(g)||p.left()||p.slot!=slot||slot.answered)return;
        long remaining=TimeUnit.MILLISECONDS.toNanos(slot.limitMs+500)-(System.nanoTime()-slot.sentNanos.get());
        if(remaining>0){slot.timeout=schedule(()->timeout(g,p,slot),TimeUnit.NANOSECONDS.toMillis(remaining)+1);return;}
        try{ensureSent(g,p,slot);complete(g,p,slot,null,null);}catch(SQLException ex){slot.timeout=schedule(()->timeout(g,p,slot),500);}
    }
    public void quit(ClientSession session,boolean disconnected){synchronized(online){
        GameSession g=null;for(var game:games.values())if(game.players.stream().anyMatch(p->p.session==session&&!p.left()&&!p.detached)){g=game;break;}
        if(g==null){if(!disconnected)throw error("NOT_PLAYING","Bạn không ở trong trận đấu");return;}
        var p=player(g,session);
        if(g.result!=null){p.detached=true;p.cancelTimers();online.changeStatus(session,UserStatus.IN_ROOM);return;}
        PlayerStatus status=disconnected?PlayerStatus.DISCONNECTED:PlayerStatus.QUIT;
        final GameSession game=g;
        try{db.transaction(c->{dao.depart(c,game.id,p.id,status);return null;});}
        catch(SQLException ex){
            if(!disconnected)throw error("INTERNAL","Không thể lưu trạng thái rời trận");
            schedule(()->persistDeparture(game,p,status),500);
        }
        p.status=status;p.done=true;p.finishMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-g.startedNanos);p.cancelTimers();
        online.changeStatus(session,UserStatus.IN_ROOM);
        broadcast(g,"PLAYER_LEFT",Map.of("username",p.username));scoreboard(g);maybeFinish(g);
    }}
    private void persistDeparture(GameSession g,GameSession.Player p,PlayerStatus status){
        if(g.ended)return;
        try{db.transaction(c->{dao.depart(c,g.id,p.id,status);return null;});}catch(SQLException ex){schedule(()->persistDeparture(g,p,status),500);}
    }
    public void leaving(ClientSession session,boolean disconnected){synchronized(online){
        if(closed)return;
        boolean playing=games.values().stream().anyMatch(g->g.players.stream().anyMatch(p->p.session==session&&!p.left()&&!p.detached));
        if(playing)quit(session,disconnected);
    }}
    private GameSession find(ClientSession session){
        for(var g:games.values())if(g.players.stream().anyMatch(p->p.session==session&&!p.left()&&!p.detached))return g;
        throw error("NOT_PLAYING","Bạn không ở trong trận đấu");
    }
    private static GameSession.Player player(GameSession g,ClientSession session){return g.players.stream().filter(p->p.session==session).findFirst().orElseThrow();}
    private Comparator<GameSession.Player> order(){return Comparator.comparing(GameSession.Player::left).thenComparing(Comparator.comparingInt((GameSession.Player p)->p.score).reversed()).thenComparingLong(p->p.finishMs==0?Long.MAX_VALUE:p.finishMs).thenComparing(p->p.username);}
    private void scoreboard(GameSession g){
        var sorted=g.players.stream().sorted(order()).toList();List<ScoreRow> rows=new ArrayList<>();
        for(int i=0;i<sorted.size();i++){var p=sorted.get(i);rows.add(new ScoreRow(i+1,p.username,p.score,Math.min(p.index+1,g.questions.size()),p.done));}
        broadcast(g,"SCOREBOARD",List.copyOf(rows));
    }
    private void maybeFinish(GameSession g){
        if(!active(g))return;var remaining=g.players.stream().filter(p->!p.left()).toList();
        if(remaining.size()>1&&remaining.stream().anyMatch(p->!p.done))return;
        for(var p:g.players){p.cancelTimers();if(p.finishMs==0)p.finishMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-g.startedNanos);}
        String winner=null;boolean draw=false;
        if(remaining.size()==1)winner=remaining.get(0).username;
        else if(!remaining.isEmpty()){
            int best=remaining.stream().mapToInt(p->p.score).max().orElse(0);var top=remaining.stream().filter(p->p.score==best).toList();
            if(top.size()==1)winner=top.get(0).username;else draw=true;
        }
        var sorted=g.players.stream().sorted(order()).toList();List<MatchRow> rows=new ArrayList<>();
        for(int i=0;i<sorted.size();i++){var p=sorted.get(i);rows.add(new MatchRow(i+1,p.username,p.score,p.correct,p.finishMs,p.status));}
        g.result=new MatchResult(g.id,winner,draw,List.copyOf(rows));persistFinish(g);
    }
    private void persistFinish(GameSession g){
        if(closed||g.ended||games.get(g.roomId)!=g)return;
        Map<String,Long> ids=new HashMap<>();for(var p:g.players)ids.put(p.username,p.id);
        try{
            Integer wins=db.transaction(c->{
                dao.finish(c,g.roomId,g.result,ids);
                if(g.result.winner()==null)return null;
                try(var s=c.prepareStatement("SELECT total_wins FROM users WHERE username=?")){
                    s.setString(1,g.result.winner());try(var r=s.executeQuery()){if(!r.next())throw new SQLException("Không tìm thấy người thắng");return r.getInt(1);}
                }
            });
            g.ended=true;games.remove(g.roomId);for(var p:g.players)p.cancelTimers();
            broadcast(g,"GAME_END",g.result);
            rooms.publishRoom(g.roomId);
            for(var p:g.players)if(!p.detached){rooms.forgetReminder(p.session);if(online.sessionByUsername(p.username)==p.session&&online.status(p.session)==UserStatus.PLAYING)online.changeStatus(p.session,UserStatus.IN_ROOM);}
            if(wins!=null)online.refreshWins(g.result.winner(),wins);
        }catch(SQLException ex){schedule(()->persistFinish(g),1000);}
    }
    private void broadcast(GameSession g,String type,Object data){String frame=event(type,data);for(var p:g.players)if(!p.left()&&!p.detached&&p.session.isOpen()&&online.sessionByUsername(p.username)==p.session)p.session.send(frame);}
    private ScheduledFuture<?> schedule(Runnable task,long delayMs){
        if(closed)return null;
        return timer.schedule(()->{synchronized(online){if(!closed)task.run();}},Math.max(0,delayMs),TimeUnit.MILLISECONDS);
    }
    private static String event(String type,Object data){return ProtocolMessages.event(type,JSON.toJsonTree(data));}
    private static void requireFrame(String frame){if(frame.getBytes(StandardCharsets.UTF_8).length>FramedIO.MAX_FRAME)throw error("INVALID_INPUT","Dữ liệu trận vượt giới hạn 2 MiB");}
    private static ServiceException error(String code,String message){return new ServiceException(code,message);}
    @Override public void close(){synchronized(online){
        if(closed)return;closed=true;timer.shutdownNow();
        for(var g:List.copyOf(games.values())){g.ended=true;for(var p:g.players)p.cancelTimers();try{db.transaction(c->{dao.cancel(c,g.id);return null;});}catch(SQLException ignored){}}
        games.clear();
    }}
    private record Prepared(GameSession game,String frame){}
}
