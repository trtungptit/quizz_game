package quizgame.server.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quizgame.common.model.Invite;
import quizgame.common.model.InviteResult;
import quizgame.common.model.RoomState;
import quizgame.common.model.UserStatus;
import quizgame.server.dao.AccountDao;
import quizgame.server.dao.Database;
import quizgame.server.dao.InvitationDao;
import quizgame.server.dao.RoomDao;
import quizgame.server.net.ClientSession;
import quizgame.server.net.ProtocolMessages;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

/** Hàng đợi lời mời theo người nhận; timeout bắt đầu sau khi frame được ghi ra socket. */
public final class InviteManager implements AutoCloseable {
    private static final Gson GSON=new Gson();
    private final Database database; private final AccountDao accounts; private final InvitationDao invitations;
    private final RoomDao rooms; private final OnlineRegistry online; private final RoomManager roomManager;
    private final Map<Long,ArrayDeque<Pending>> queues=new HashMap<>();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"quiz-invite-timeouts");t.setDaemon(true);return t;});
    private boolean closed;
    public InviteManager(Database db,AccountDao a,InvitationDao i,RoomDao r,OnlineRegistry o,RoomManager rm){
        database=db;accounts=a;invitations=i;rooms=r;online=o;roomManager=rm;
        rm.attachInvites(this);
        timer.scheduleWithFixedDelay(()->{synchronized(online){
            if(closed)return;
            try{roomManager.retryCleanup();reconcileQueues();}catch(RuntimeException ignored){}
        }},1,1,TimeUnit.SECONDS);
    }

    public void invite(ClientSession sender,String requestedUsername){ synchronized (online) {
        long senderId=online.userId(sender);if(senderId<0)throw new ServiceException("NOT_LOGGED_IN","Bạn cần đăng nhập trước");
        if(online.status(sender)!=UserStatus.IN_ROOM)throw new ServiceException("NOT_IN_ROOM","Bạn cần ở trong phòng để mời");
        if(requestedUsername==null||requestedUsername.isBlank())throw new ServiceException("INVALID_INPUT","Tên người chơi không hợp lệ");
        try{
            Setup setup=database.transaction(c->{
                long roomId=rooms.activeRoomForUser(c,senderId).orElseThrow(()->new ServiceException("NOT_IN_ROOM","Bạn cần ở trong phòng để mời"));
                RoomDao.RoomRow room=rooms.findById(c,roomId,true).orElseThrow();
                if(!"waiting".equals(room.status()))throw new ServiceException("ROOM_PLAYING","Phòng không nhận lời mời lúc này");
                var target=accounts.findByUsername(c,requestedUsername).orElseThrow(()->new ServiceException("USER_NOT_FOUND","Không tìm thấy người chơi"));
                ClientSession receiver=online.sessionByUsername(target.username());
                if(receiver==null||online.status(receiver)!=UserStatus.IDLE||rooms.activeRoomForUser(c,target.id()).isPresent())throw new ServiceException("USER_BUSY","Người chơi hiện không rảnh");
                if(rooms.activeMemberCount(c,roomId)>=5)throw new ServiceException("ROOM_FULL","Phòng đã đủ 5 người");
                long id=invitations.create(c,roomId,senderId,target.id());
                return new Setup(invitations.find(c,id,false).orElseThrow(),rooms.snapshot(c,rooms.findById(c,roomId,false).orElseThrow()));
            });
            enqueue(setup.row());roomManager.publish(setup.state());
        }catch(ServiceException ex){throw ex;}catch(SQLException ex){
            if(ex.getSQLState()!=null&&ex.getSQLState().startsWith("23"))throw new ServiceException("ALREADY_INVITED","Lời mời này đang chờ phản hồi");
            throw new ServiceException("INTERNAL","Không thể gửi lời mời lúc này");
        }
    } }
    public ReplyResult reply(ClientSession receiver,long inviteId,boolean accept){ synchronized (online) {
        long receiverId=online.userId(receiver);if(receiverId<0)throw new ServiceException("NOT_LOGGED_IN","Bạn cần đăng nhập trước");
        ArrayDeque<Pending> q=queues.get(receiverId);Pending head=q==null?null:q.peekFirst();
        if(head==null||head.row.id()!=inviteId)throw new ServiceException("INVITE_EXPIRED","Lời mời không còn hiệu lực");
        // Reply có thể tới ngay sau flush, trước khi callback writer lấy được monitor.
        if(head.sending&&!head.delivered)delivered(receiverId,head);
        if(head.cancelled){finishQueue(receiverId,head);showHead(receiverId);throw new ServiceException("INVITE_EXPIRED","Lời mời không còn hiệu lực");}
        if(!head.delivered)throw new ServiceException("INVITE_EXPIRED","Lời mời không còn hiệu lực");
        if(head.expiresAtNanos-System.nanoTime()<=0){expire(receiverId,head);throw new ServiceException("INVITE_EXPIRED","Lời mời đã hết hạn");}
        try{
            ReplyResult result=database.transaction(c->{
                InvitationDao.InvitationRow row=invitations.find(c,inviteId,true).orElseThrow(()->new ServiceException("INVITE_EXPIRED","Lời mời không còn hiệu lực"));
                if(row.receiverId()!=receiverId||!"pending".equals(row.status())||row.deliveredAt()==null)throw new ServiceException("INVITE_EXPIRED","Lời mời đã hết hạn");
                RoomDao.RoomRow room=rooms.findById(c,row.roomId(),true).orElseThrow(()->new ServiceException("INVITE_EXPIRED","Phòng đã đóng"));
                if(!"waiting".equals(room.status())){invitations.finish(c,inviteId,"cancelled");return new ReplyResult("ROOM_PLAYING",null,row,null);}
                if(rooms.activeRoomForUser(c,row.senderId()).orElse(-1L)!=row.roomId()){invitations.finish(c,inviteId,"cancelled");return new ReplyResult("EXPIRED",null,row,null);}
                if(!accept){invitations.finish(c,inviteId,"rejected");return new ReplyResult("REJECTED",rooms.snapshot(c,room),row,List.of());}
                if(online.status(receiver)!=UserStatus.IDLE||rooms.activeRoomForUser(c,receiverId).isPresent()){invitations.finish(c,inviteId,"cancelled");return new ReplyResult("EXPIRED",null,row,null);}
                if(rooms.activeMemberCount(c,row.roomId())>=5){invitations.finish(c,inviteId,"room_full");return new ReplyResult("ROOM_FULL",rooms.snapshot(c,room),row,List.of());}
                List<InvitationDao.InvitationRow> other=invitations.pendingForReceiver(c,receiverId).stream().filter(i->i.id()!=inviteId).toList();
                rooms.join(c,row.roomId(),receiverId);
                if(!invitations.finish(c,inviteId,"accepted"))throw new ServiceException("INVITE_EXPIRED","Lời mời không còn hiệu lực");
                invitations.cancelOtherPending(c,receiverId,inviteId);
                return new ReplyResult("ACCEPTED",rooms.snapshot(c,rooms.findById(c,row.roomId(),false).orElseThrow()),row,other);
            });
            finishQueue(receiverId,head);sendResult(result.row(),"ROOM_PLAYING".equals(result.result())?InviteResult.EXPIRED:InviteResult.valueOf(result.result()));
            if("ACCEPTED".equals(result.result()))online.changeStatus(receiver,UserStatus.IN_ROOM);
            if(result.other()!=null&&!result.other().isEmpty()){cancelQueued(receiverId,result.other());for(var other:result.other()){sendResult(other,InviteResult.JOINED_OTHER);roomManager.publishRoom(other.roomId());}}
            roomManager.publishRoom(result.row().roomId());showHead(receiverId);return result;
        }catch(ServiceException ex){throw ex;}catch(SQLException ex){throw new ServiceException("INTERNAL","Không thể xử lý lời mời lúc này");}
    } }
    private void enqueue(InvitationDao.InvitationRow row){ synchronized (online) {ArrayDeque<Pending> q=queues.computeIfAbsent(row.receiverId(),k->new ArrayDeque<>());q.addLast(new Pending(row));if(q.size()==1)showHead(row.receiverId());} }
    private void showHead(long id){ synchronized (online) {
        ArrayDeque<Pending> q=queues.get(id);if(closed||q==null||q.isEmpty())return;
        Pending p=q.peekFirst();if(p.sending||p.delivered||p.cancelled)return;
        ClientSession s=online.sessionByUsername(p.row.receiver());
        if(s==null||!s.isOpen()||online.status(s)!=UserStatus.IDLE)return;
        p.sending=true;
        Invite invite=new Invite(p.row.id(),p.row.sender(),p.row.roomCode(),p.row.roomName(),5000);
        s.send(ProtocolMessages.event("INVITE_RECEIVED",GSON.toJsonTree(invite)),()->{
            // Ghi mốc trước khi đợi monitor, không cộng thêm thời gian server đang bận.
            p.writtenAtNanos.compareAndSet(0,System.nanoTime());delivered(id,p);
        });
    } }
    private void delivered(long id,Pending p){ synchronized (online) {
        ArrayDeque<Pending> q=queues.get(id);if(closed||q==null||q.peekFirst()!=p||p.delivered)return;
        try{
            boolean changed=database.transaction(c->invitations.markDelivered(c,p.row.id()));
            if(!changed)p.cancelled=true;
            p.writtenAtNanos.compareAndSet(0,System.nanoTime());p.delivered=true;arm(id,p);
        }catch(SQLException ignored){
            timer.schedule(()->delivered(id,p),500,TimeUnit.MILLISECONDS);
        }
    } }
    private void arm(long id,Pending p){
        if(p.timeout!=null)p.timeout.cancel(false);
        long written=p.writtenAtNanos.get();
        p.expiresAtNanos=(written==0?System.nanoTime():written)+TimeUnit.SECONDS.toNanos(5);
        p.timeout=timer.schedule(()->{synchronized(online){expire(id,p);}},Math.max(0,p.expiresAtNanos-System.nanoTime()),TimeUnit.NANOSECONDS);
    }
    private void expire(long id,Pending p){ synchronized (online) {
        ArrayDeque<Pending> q=queues.get(id);if(closed||q==null||q.peekFirst()!=p)return;
        try{
            boolean changed=!p.cancelled&&database.transaction(c->invitations.finish(c,p.row.id(),"expired"));
            if(changed)sendResult(p.row,InviteResult.EXPIRED);
            finishQueue(id,p);roomManager.publishRoom(p.row.roomId());showHead(id);
        }catch(SQLException ignored){p.timeout=timer.schedule(()->{synchronized(online){expire(id,p);}},500,TimeUnit.MILLISECONDS);}
    } }
    private void finishQueue(long id,Pending p){ArrayDeque<Pending> q=queues.get(id);if(q==null)return;if(q.peekFirst()==p)q.removeFirst();else q.remove(p);if(p.timeout!=null)p.timeout.cancel(false);if(q.isEmpty())queues.remove(id);}
    private void cancelQueued(long id,List<InvitationDao.InvitationRow> rows){ArrayDeque<Pending> q=queues.get(id);if(q==null)return;for(var row:rows){Pending p=q.stream().filter(x->x.row.id()==row.id()).findFirst().orElse(null);if(p!=null){q.remove(p);if(p.timeout!=null)p.timeout.cancel(false);}}if(q.isEmpty())queues.remove(id);}
    private void sendResult(InvitationDao.InvitationRow row,InviteResult result){ClientSession s=online.sessionByUsername(row.sender());if(s==null)return;JsonObject data=new JsonObject();data.addProperty("toUser",row.receiver());data.addProperty("result",result.name());s.send(ProtocolMessages.event("INVITE_RESULT",data));}
    /** Hủy lời mời không còn hợp lệ, kể cả receiver tự tạo/vào phòng bằng mã. */
    public void reconcileQueues(){synchronized(online){
        if(closed)return;
        Set<Long> changedRooms=new HashSet<>();
        for(var queue:List.copyOf(queues.values()))for(Pending p:List.copyOf(queue)){
            if(p.cancelled)continue;
            try{
                String terminal=database.transaction(c->{
                    var row=invitations.find(c,p.row.id(),true).orElse(null);
                    if(row==null||!"pending".equals(row.status()))return row==null?"cancelled":row.status();
                    ClientSession receiver=online.sessionByUsername(row.receiver());
                    String status=null;
                    if(receiver==null||!receiver.isOpen())status="cancelled";
                    else if(online.status(receiver)!=UserStatus.IDLE||rooms.activeRoomForUser(c,row.receiverId()).isPresent())status="joined_other";
                    else{
                        var room=rooms.findById(c,row.roomId(),false).orElse(null);
                        if(room==null||!"waiting".equals(room.status())||rooms.activeRoomForUser(c,row.senderId()).orElse(-1L)!=row.roomId())status="cancelled";
                    }
                    if(status!=null)invitations.finish(c,row.id(),status);
                    return status;
                });
                if(terminal==null)continue;
                if("joined_other".equals(terminal))sendResult(p.row,InviteResult.JOINED_OTHER);
                changedRooms.add(p.row.roomId());
                var q=queues.get(p.row.receiverId());
                ClientSession receiver=online.sessionByUsername(p.row.receiver());
                if(q!=null&&q.peekFirst()==p&&(p.sending||p.delivered)&&receiver!=null&&receiver.isOpen()&&online.status(receiver)==UserStatus.IDLE){
                    // Protocol không có CANCELLED: chờ hết slot dialog cũ trước khi gửi lời kế tiếp.
                    p.cancelled=true;if(p.timeout==null)arm(p.row.receiverId(),p);
                }else finishQueue(p.row.receiverId(),p);
            }catch(SQLException ignored){ /* Giữ queue để maintenance thử lại, không báo thành công giả. */ }
        }
        for(long id:changedRooms)roomManager.publishRoom(id);
        for(long id:List.copyOf(queues.keySet()))showHead(id);
    }}

    public void disconnected(ClientSession session){synchronized(online){
        long userId=online.userId(session);if(userId<0)return;
        try{
            Set<Long> changed=database.transaction(c->{
                Set<Long> ids=new HashSet<>();
                for(var row:invitations.pendingForReceiver(c,userId)){
                    invitations.finish(c,row.id(),"cancelled");ids.add(row.roomId());
                }
                return ids;
            });
            var q=queues.remove(userId);if(q!=null)for(Pending p:q)if(p.timeout!=null)p.timeout.cancel(false);
            for(long id:changed)roomManager.publishRoom(id);
        }catch(SQLException ignored){ /* Maintenance xử lý sau khi session bị bỏ khỏi registry. */ }
    }}

    @Override public void close(){synchronized(online){closed=true;timer.shutdownNow();for(var q:queues.values())for(var p:q)if(p.timeout!=null)p.timeout.cancel(false);queues.clear();}}
    public record ReplyResult(String result,RoomState state,InvitationDao.InvitationRow row,List<InvitationDao.InvitationRow> other){}
    private record Setup(InvitationDao.InvitationRow row,RoomState state){}
    private static final class Pending{final InvitationDao.InvitationRow row;final java.util.concurrent.atomic.AtomicLong writtenAtNanos=new java.util.concurrent.atomic.AtomicLong();boolean sending,delivered,cancelled;long expiresAtNanos;ScheduledFuture<?> timeout;Pending(InvitationDao.InvitationRow r){row=r;}}
}
