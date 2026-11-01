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
