package quizgame.server.dao;

import quizgame.common.model.RoomMember;
import quizgame.common.model.RoomPhase;
import quizgame.common.model.RoomState;
import java.sql.*;
import java.util.*;

/** SQL thao tác phòng và membership; caller sở hữu transaction Connection. */
public final class RoomDao {
    public record RoomRow(long id, String code, String name, long hostId, String host, String status,
                          int numQuestions, Integer timeOverrideSeconds, Long setId) { }

    public long create(Connection c, String code, String name, long hostId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("INSERT INTO rooms(room_code,name,host_id,status,num_questions) VALUES(?,?,?,'waiting',0)", Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, code); s.setString(2, name); s.setLong(3, hostId); s.executeUpdate();
            try (ResultSet keys = s.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Không lấy được mã phòng");
                long id = keys.getLong(1);
                try (PreparedStatement m = c.prepareStatement("INSERT INTO room_members(room_id,user_id,ready) VALUES(?,?,FALSE)")) {
                    m.setLong(1,id); m.setLong(2,hostId); m.executeUpdate();
                }
                return id;
            }
        }
    }

    public Optional<RoomRow> findByCode(Connection c, String code, boolean lock) throws SQLException {
        return find(c, "r.room_code=?", code, lock);
    }
    public Optional<RoomRow> findById(Connection c, long id, boolean lock) throws SQLException {
        return find(c, "r.room_id=?", id, lock);
    }
    private Optional<RoomRow> find(Connection c, String condition, Object value, boolean lock) throws SQLException {
        String sql = "SELECT r.room_id,r.room_code,r.name,r.host_id,u.username,r.status,r.num_questions,r.time_override_seconds,r.set_id "
                + "FROM rooms r JOIN users u ON u.user_id=r.host_id WHERE " + condition + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement s = c.prepareStatement(sql)) {
            if (value instanceof String text) s.setString(1,text); else s.setLong(1,(Long)value);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                int seconds=rs.getInt("time_override_seconds");
                Integer override=rs.wasNull()?null:seconds;
                Long setId=rs.getObject("set_id")==null?null:rs.getLong("set_id");
                return Optional.of(new RoomRow(rs.getLong("room_id"),rs.getString("room_code"),rs.getString("name"),rs.getLong("host_id"),rs.getString("username"),rs.getString("status"),rs.getInt("num_questions"),override,setId));
            }
        }
    }
    public Optional<Long> activeRoomForUser(Connection c,long userId) throws SQLException {
        try (PreparedStatement s=c.prepareStatement("SELECT room_id FROM room_members WHERE user_id=? AND left_at IS NULL")) {
            s.setLong(1,userId); try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(r.getLong(1)):Optional.empty();}
        }
    }
    public int activeMemberCount(Connection c,long roomId) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM room_members WHERE room_id=? AND left_at IS NULL")) {
            s.setLong(1,roomId);try(ResultSet r=s.executeQuery()){r.next();return r.getInt(1);}
        }
    }
    public void join(Connection c,long roomId,long userId) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("INSERT INTO room_members(room_id,user_id,ready,left_at) VALUES(?,?,FALSE,NULL) ON DUPLICATE KEY UPDATE joined_at=CURRENT_TIMESTAMP(3),ready=FALSE,left_at=NULL")) {
            s.setLong(1,roomId);s.setLong(2,userId);s.executeUpdate();
        }
    }
    public void leave(Connection c,long roomId,long userId) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("UPDATE room_members SET left_at=CURRENT_TIMESTAMP(3),ready=FALSE WHERE room_id=? AND user_id=? AND left_at IS NULL")) {
            s.setLong(1,roomId);s.setLong(2,userId);s.executeUpdate();
        }
        RoomRow room=findById(c,roomId,true).orElse(null); if(room==null)return;
        if(room.hostId()==userId){Long next=earliestActive(c,roomId);if(next!=null)try(PreparedStatement s=c.prepareStatement("UPDATE rooms SET host_id=? WHERE room_id=?")){s.setLong(1,next);s.setLong(2,roomId);s.executeUpdate();}}
        int remaining=activeMemberCount(c,roomId);
        String inviteSql=remaining==0
                ? "UPDATE invitations SET status='cancelled',responded_at=CURRENT_TIMESTAMP(3) WHERE room_id=? AND status='pending'"
                : "UPDATE invitations SET status='cancelled',responded_at=CURRENT_TIMESTAMP(3) WHERE room_id=? AND sender_id=? AND status='pending'";
        try(PreparedStatement s=c.prepareStatement(inviteSql)){s.setLong(1,roomId);if(remaining!=0)s.setLong(2,userId);s.executeUpdate();}
        if(remaining==0)try(PreparedStatement s=c.prepareStatement("UPDATE rooms SET status='cancelled' WHERE room_id=?")){s.setLong(1,roomId);s.executeUpdate();}
    }
    public List<RoomMember> members(Connection c,long roomId) throws SQLException {
        List<RoomMember> out=new ArrayList<>();
        try(PreparedStatement s=c.prepareStatement("SELECT u.username,rm.ready FROM room_members rm JOIN users u ON u.user_id=rm.user_id WHERE rm.room_id=? AND rm.left_at IS NULL ORDER BY rm.joined_at,rm.user_id")) {
            s.setLong(1,roomId);try(ResultSet r=s.executeQuery()){while(r.next())out.add(new RoomMember(r.getString(1),r.getBoolean(2)));}
        } return List.copyOf(out);
    }
    public List<String> pendingInvitees(Connection c,long roomId) throws SQLException {
        List<String> out=new ArrayList<>();
        try(PreparedStatement s=c.prepareStatement("SELECT u.username FROM invitations i JOIN users u ON u.user_id=i.receiver_id WHERE i.room_id=? AND i.status='pending' ORDER BY i.sent_at,i.invitation_id")) {
            s.setLong(1,roomId);try(ResultSet r=s.executeQuery()){while(r.next())out.add(r.getString(1));}
        }return List.copyOf(out);
    }
    public void setReady(Connection c,long roomId,long userId,boolean ready) throws SQLException {
        try(var s=c.prepareStatement("UPDATE room_members SET ready=? WHERE room_id=? AND user_id=? AND left_at IS NULL")){
            s.setBoolean(1,ready);s.setLong(2,roomId);s.setLong(3,userId);s.executeUpdate();
        }
    }
    public RoomState snapshot(Connection c,RoomRow room) throws SQLException {
        var set=room.setId()==null?null:new QuestionSetDao().summary(c,room.setId()).orElse(null);
        return new RoomState(room.code(),room.name(),room.host(),members(c,room.id()),pendingInvitees(c,room.id()),set,room.numQuestions(),room.timeOverrideSeconds(),"playing".equals(room.status())?RoomPhase.PLAYING:RoomPhase.WAITING);
    }
    private Long earliestActive(Connection c,long roomId) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("SELECT user_id FROM room_members WHERE room_id=? AND left_at IS NULL ORDER BY joined_at,user_id LIMIT 1")) {
            s.setLong(1,roomId);try(ResultSet r=s.executeQuery()){return r.next()?r.getLong(1):null;}
        }
    }
}
