package quizgame.server.service;

import com.google.gson.Gson;
import quizgame.common.model.RoomState;
import quizgame.common.model.UserStatus;
import quizgame.server.dao.Database;
import quizgame.server.dao.RoomDao;
import quizgame.server.dao.InvitationDao;
import quizgame.server.net.ClientSession;
import quizgame.server.net.ProtocolMessages;
import java.sql.SQLException;
import java.security.SecureRandom;
import java.util.Optional;

/** Luật tạo phòng, tham gia, rời phòng và chuyển chủ; mọi ghi DB nằm trong transaction. */
public final class RoomManager {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new Gson();
    private final Database database;
    private final RoomDao rooms;
    private final OnlineRegistry online;
    private InviteManager invites;
    private final InvitationDao invitationDao=new InvitationDao();
    private final java.util.Set<Long> cleanupPending=new java.util.HashSet<>();
    private final java.util.Map<ClientSession,Long> lastReminder=new java.util.HashMap<>();
    private GameManager gameManager;
    public RoomManager(Database database, RoomDao rooms, OnlineRegistry online) { this.database=database; this.rooms=rooms; this.online=online; }

    public static boolean validRoomName(String name) {
        if (name == null) return false; String value=name.trim(); return !value.isEmpty() && value.length()<=30;
    }
    public static boolean validRoomCode(String code) { return code!=null && code.matches("[A-HJ-NP-Z2-9]{6}"); }

    public RoomState create(ClientSession session,String requestedName) { synchronized (online) {
        requireIdle(session);
        if(!validRoomName(requestedName))throw new ServiceException("INVALID_INPUT","Tên phòng phải dài 1-30 ký tự");
        String name=requestedName.trim(); long userId=online.userId(session);
        for(int attempt=0;attempt<10;attempt++){
            String code=newCode();
            try{
                RoomState state=database.transaction(c->{
                    if(rooms.activeRoomForUser(c,userId).isPresent())throw new ServiceException("ALREADY_IN_ROOM","Bạn đã ở trong một phòng");
                    long id=rooms.create(c,code,name,userId);
                    invitationDao.cancelOtherPending(c,userId,-1L);
                    return rooms.snapshot(c,rooms.findById(c,id,false).orElseThrow());
                });
                online.changeStatus(session,UserStatus.IN_ROOM); if(invites!=null)invites.reconcileQueues(); return state;
            }catch(ServiceException ex){throw ex;}catch(SQLException ex){
                if(ex.getSQLState()!=null&&ex.getSQLState().startsWith("23")&&attempt<9)continue;
                throw new ServiceException("INTERNAL","Không thể tạo phòng lúc này");
            }
        }
        throw new ServiceException("INTERNAL","Không thể tạo mã phòng duy nhất");
    } }

    public RoomState join(ClientSession session,String code) { synchronized (online) {
        requireIdle(session); if(!validRoomCode(code))throw new ServiceException("ROOM_NOT_FOUND","Không tìm thấy phòng");
        long userId=online.userId(session);
        try{
            RoomState state=database.transaction(c->{
                RoomDao.RoomRow room=rooms.findByCode(c,code,true).orElseThrow(()->new ServiceException("ROOM_NOT_FOUND","Không tìm thấy phòng"));
                if(!"waiting".equals(room.status())&&!"playing".equals(room.status()))throw new ServiceException("ROOM_NOT_FOUND","Không tìm thấy phòng");
                if("playing".equals(room.status()))throw new ServiceException("ROOM_PLAYING","Phòng đang chơi");
                if(rooms.activeRoomForUser(c,userId).isPresent())throw new ServiceException("ALREADY_IN_ROOM","Bạn đã ở trong một phòng");
                if(rooms.activeMemberCount(c,room.id())>=5)throw new ServiceException("ROOM_FULL","Phòng đã đủ 5 người");
                rooms.join(c,room.id(),userId);
                invitationDao.cancelOtherPending(c,userId,-1L);
                return rooms.snapshot(c,rooms.findById(c,room.id(),false).orElseThrow());
            });
            online.changeStatus(session,UserStatus.IN_ROOM); if(invites!=null)invites.reconcileQueues(); return state;
        }catch(ServiceException ex){throw ex;}catch(SQLException ex){
            if(ex.getSQLState()!=null&&ex.getSQLState().startsWith("23"))throw new ServiceException("ALREADY_IN_ROOM","Bạn đã ở trong một phòng");
            throw new ServiceException("INTERNAL","Không thể tham gia phòng lúc này");
        }
    } }

    public RoomState leave(ClientSession session) { synchronized (online) {
        if(gameManager!=null)gameManager.leaving(session,false);
        lastReminder.remove(session);
        long userId=online.userId(session); if(userId<0)throw new ServiceException("NOT_LOGGED_IN","Bạn cần đăng nhập trước");
        try{
            RoomState state=database.transaction(c->{
                Long id=rooms.activeRoomForUser(c,userId).orElseThrow(()->new ServiceException("NOT_IN_ROOM","Bạn hiện không ở trong phòng nào"));
                rooms.leave(c,id,userId);
                Optional<RoomDao.RoomRow> row=rooms.findById(c,id,false);
                if(row.isEmpty()||rooms.activeMemberCount(c,id)==0)return null;
                return rooms.snapshot(c,row.get());
            });
            online.changeStatus(session,UserStatus.IDLE); if(invites!=null)invites.reconcileQueues(); return state;
        }catch(ServiceException ex){throw ex;}catch(SQLException ex){throw new ServiceException("INTERNAL","Không thể rời phòng lúc này");}
    } }

    public void leaveForDisconnect(ClientSession session) { synchronized (online) {
        if(gameManager!=null)gameManager.leaving(session,true);
        lastReminder.remove(session);
        long userId=online.userId(session); if(userId<0)return;
        try{
            RoomState state=database.transaction(c->{
                Long id=rooms.activeRoomForUser(c,userId).orElse(null); if(id==null)return null;
                rooms.leave(c,id,userId);
                try(var s=c.prepareStatement("UPDATE invitations SET status='cancelled',responded_at=CURRENT_TIMESTAMP(3) WHERE room_id=? AND sender_id=? AND status='pending'")){s.setLong(1,id);s.setLong(2,userId);s.executeUpdate();}
                Optional<RoomDao.RoomRow> row=rooms.findById(c,id,false);
                return row.isEmpty()||rooms.activeMemberCount(c,id)==0?null:rooms.snapshot(c,row.get());
            });
            cleanupPending.remove(userId);
            online.changeStatus(session,UserStatus.IDLE); if(invites!=null)invites.reconcileQueues(); publish(state);
        }catch(SQLException ignored){cleanupPending.add(userId);}
    } }

    public void publish(RoomState state) { synchronized (online) {
        if(state==null)return; String event=ProtocolMessages.event("ROOM_STATE",GSON.toJsonTree(state));
        for(var member:state.members()){ClientSession receiver=online.sessionByUsername(member.username());if(receiver!=null)receiver.send(event);}
    } }
    private void requireIdle(ClientSession session) {
        retryCleanup();
        if(online.userId(session)<0)throw new ServiceException("NOT_LOGGED_IN","Bạn cần đăng nhập trước");
        if(online.status(session)!=UserStatus.IDLE)throw new ServiceException("ALREADY_IN_ROOM","Bạn cần rời phòng hiện tại trước");
    }
    public RoomDao.RoomRow current(java.sql.Connection c,ClientSession session,boolean waiting,boolean host) throws SQLException {
        long userId=online.userId(session);
        long roomId=rooms.activeRoomForUser(c,userId).orElseThrow(()->new ServiceException("NOT_IN_ROOM","Bạn chưa ở trong phòng"));
        var room=rooms.findById(c,roomId,true).orElseThrow(()->new ServiceException("NOT_IN_ROOM","Phòng đã đóng"));
        if(host&&room.hostId()!=userId)throw new ServiceException("NOT_HOST","Chỉ chủ phòng được thực hiện");
        if(waiting&&!"waiting".equals(room.status()))throw new ServiceException("ROOM_PLAYING","Phòng đang chơi");
        return room;
    }
    public RoomState ready(ClientSession session,boolean ready){synchronized(online){
        try{return database.transaction(c->{var room=current(c,session,true,false);
            rooms.setReady(c,room.id(),online.userId(session),ready);return rooms.snapshot(c,room);});
        }catch(SQLException ex){throw new ServiceException("INTERNAL","Không thể đổi sẵn sàng lúc này");}
    }}
    public void remind(ClientSession session){synchronized(online){
        try{
            var state=database.transaction(c->{var room=current(c,session,true,false);return rooms.snapshot(c,room);});
            String name=online.username(session);
            if(state.members().stream().noneMatch(m->m.username().equals(name)&&m.ready()))throw new ServiceException("NOT_READY_YOURSELF","Bạn cần sẵn sàng trước khi nhắc");
            long now=System.nanoTime();Long last=lastReminder.get(session);
            if(last!=null&&now-last<java.util.concurrent.TimeUnit.SECONDS.toNanos(5))throw new ServiceException("TOO_FAST","Chỉ được nhắc mỗi 5 giây");
            lastReminder.put(session,now);
            var data=new com.google.gson.JsonObject();data.addProperty("fromUser",name);
            for(var member:state.members())if(!member.ready()){
                var peer=online.sessionByUsername(member.username());if(peer!=null)peer.send(ProtocolMessages.event("READY_REMINDER",data));
            }
        }catch(SQLException ex){throw new ServiceException("INTERNAL","Không thể nhắc lúc này");}
    }}
    public RoomState configure(ClientSession session,Long setId,int count,Integer seconds){synchronized(online){
        try{return database.transaction(c->{
            var room=current(c,session,true,true);
            if(seconds!=null&&(seconds<5||seconds>60))throw new ServiceException("INVALID_INPUT","Thời gian phải từ 5 đến 60 giây");
            if(setId==null){if(count!=0)throw new ServiceException("INVALID_INPUT","Bỏ bộ câu hỏi phải đặt số câu bằng 0");}
            else{
                var set=new quizgame.server.dao.QuestionSetDao().summary(c,setId).orElseThrow(()->new ServiceException("SET_NOT_FOUND","Không tìm thấy bộ câu hỏi"));
                if(count<1||count>set.questionCount())throw new ServiceException("INVALID_INPUT","Số câu không hợp lệ");
            }
            boolean changed=!java.util.Objects.equals(room.setId(),setId)||room.numQuestions()!=count||!java.util.Objects.equals(room.timeOverrideSeconds(),seconds);
            try(var s=c.prepareStatement("UPDATE rooms SET set_id=?,num_questions=?,time_override_seconds=? WHERE room_id=?")){
                s.setObject(1,setId);s.setInt(2,count);s.setObject(3,seconds);s.setLong(4,room.id());s.executeUpdate();
            }
            if(changed)try(var s=c.prepareStatement("UPDATE room_members SET ready=FALSE WHERE room_id=? AND left_at IS NULL")){s.setLong(1,room.id());s.executeUpdate();}
            return rooms.snapshot(c,rooms.findById(c,room.id(),false).orElseThrow());
        });}catch(SQLException ex){throw new ServiceException("INTERNAL","Không thể lưu cấu hình phòng");}
    }}
    public RoomState kick(ClientSession session,String username){synchronized(online){
        try{
            var result=database.transaction(c->{
                var room=current(c,session,true,true);
                var target=new quizgame.server.dao.AccountDao().findByUsername(c,username).orElseThrow(()->new ServiceException("USER_NOT_IN_ROOM","Người chơi không ở trong phòng"));
                if(target.id()==online.userId(session))throw new ServiceException("BAD_REQUEST","Hãy dùng Rời phòng thay vì tự kick");
                if(rooms.activeRoomForUser(c,target.id()).orElse(-1L)!=room.id())throw new ServiceException("USER_NOT_IN_ROOM","Người chơi không ở trong phòng");
                rooms.leave(c,room.id(),target.id());
                return new KickResult(target.username(),rooms.snapshot(c,rooms.findById(c,room.id(),false).orElseThrow()));
            });
            var peer=online.sessionByUsername(result.username());if(peer!=null){lastReminder.remove(peer);online.changeStatus(peer,UserStatus.IDLE);peer.send(ProtocolMessages.event("KICKED",com.google.gson.JsonNull.INSTANCE));}
            if(invites!=null)invites.reconcileQueues();return result.state();
        }catch(SQLException ex){throw new ServiceException("INTERNAL","Không thể mời người chơi ra khỏi phòng");}
    }}
    private record KickResult(String username,RoomState state){}
    public GameManager games(){synchronized(online){if(gameManager==null)gameManager=new GameManager(database,rooms,online,this);return gameManager;}}
    public void forgetReminder(ClientSession session){synchronized(online){lastReminder.remove(session);}}
    public void invitationsChanged(){synchronized(online){if(invites!=null)invites.reconcileQueues();}}
    public void attachInvites(InviteManager manager){synchronized(online){
        if(invites!=null&&invites!=manager)throw new IllegalStateException("RoomManager đã có InviteManager");
        invites=manager;
    }}

    public void publishRoom(long roomId){synchronized(online){
        try{
            RoomState state=database.transaction(c->{
                var row=rooms.findById(c,roomId,false).orElse(null);
                return row==null||rooms.activeMemberCount(c,roomId)==0?null:rooms.snapshot(c,row);
            });
            publish(state);
        }catch(SQLException ignored){ /* Không phát snapshot chưa xác nhận; thao tác kế tiếp sẽ tải lại. */ }
    }}

    public void retryCleanup(){synchronized(online){
        for(long userId:java.util.List.copyOf(cleanupPending)){
            try{
                RoomState state=database.transaction(c->{
                    Long roomId=rooms.activeRoomForUser(c,userId).orElse(null);if(roomId==null)return null;
                    rooms.leave(c,roomId,userId);
                    var row=rooms.findById(c,roomId,false).orElse(null);
                    return row==null||rooms.activeMemberCount(c,roomId)==0?null:rooms.snapshot(c,row);
                });
                cleanupPending.remove(userId);publish(state);
            }catch(SQLException ignored){ /* Giữ ID để thử lại sau khi DB hồi phục. */ }
        }
    }}

    private static String newCode(){StringBuilder b=new StringBuilder(6);for(int i=0;i<6;i++)b.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));return b.toString();}
}
