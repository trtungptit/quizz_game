package quizgame.server.service;

import quizgame.common.model.*;
import quizgame.server.dao.MatchDao;
import quizgame.server.net.ClientSession;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** State nội bộ, chỉ được đổi dưới monitor OnlineRegistry. Không serialize lớp này ra wire. */
final class GameSession {
    final long id,roundId,roomId,startedNanos=System.nanoTime();
    final List<MatchDao.GameQuestion> questions;
    final List<Long> roundQuestions;
    final List<Player> players;
    final Integer override;
    MatchResult result;
    boolean ended;
    GameSession(long id,long roundId,long roomId,List<MatchDao.GameQuestion> questions,List<Long> rq,List<Player> players,Integer override){
        this.id=id;this.roundId=roundId;this.roomId=roomId;this.questions=List.copyOf(questions);roundQuestions=List.copyOf(rq);this.players=List.copyOf(players);this.override=override;
    }
    static final class Player {
        final long id;final String username;final ClientSession session;
        int index,score,correct;boolean done,detached;long finishMs;PlayerStatus status=PlayerStatus.FINISHED;
        Slot slot;ScheduledFuture<?> next;
        Player(long id,String username,ClientSession session){this.id=id;this.username=username;this.session=session;}
        boolean left(){return status!=PlayerStatus.FINISHED;}
        void cancelTimers(){if(slot!=null&&slot.timeout!=null)slot.timeout.cancel(false);if(next!=null)next.cancel(false);}
    }
    static final class Slot {
        final long id,limitMs;final int index;
        final AtomicLong sentNanos=new AtomicLong();
        final AtomicReference<Instant> sentWall=new AtomicReference<>();
        boolean persisted,answered;ScheduledFuture<?> timeout;
        Slot(long id,int index,long limitMs){this.id=id;this.index=index;this.limitMs=limitMs;}
        void written(){sentNanos.compareAndSet(0,System.nanoTime());sentWall.compareAndSet(null,Instant.now());}
    }
}
