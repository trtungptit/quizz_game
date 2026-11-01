package quizgame.server.dao;

import java.sql.*;
import java.util.Optional;

/** DAO lưu lời mời và thực hiện chuyển trạng thái có điều kiện. */
public final class InvitationDao {
    public record InvitationRow(long id,long roomId,long senderId,long receiverId,String sender,String receiver,
                                String roomCode,String roomName,String status,Timestamp deliveredAt,Timestamp expiresAt) { }
    public long create(Connection c,long roomId,long senderId,long receiverId) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("INSERT INTO invitations(room_id,sender_id,receiver_id,status) VALUES(?,?,?,'pending')",Statement.RETURN_GENERATED_KEYS)) {
            s.setLong(1,roomId);s.setLong(2,senderId);s.setLong(3,receiverId);s.executeUpdate();
            try(ResultSet r=s.getGeneratedKeys()){if(!r.next())throw new SQLException("Không lấy được mã lời mời");return r.getLong(1);}
        }
    }
    public Optional<InvitationRow> find(Connection c,long id,boolean lock) throws SQLException {
        String sql="SELECT i.invitation_id,i.room_id,i.sender_id,i.receiver_id,s.username AS sender,u.username AS receiver,r.room_code,r.name AS room_name,i.status,i.delivered_at,i.expires_at FROM invitations i JOIN users s ON s.user_id=i.sender_id JOIN users u ON u.user_id=i.receiver_id JOIN rooms r ON r.room_id=i.room_id WHERE i.invitation_id=?"+(lock?" FOR UPDATE":"");
        try(PreparedStatement s=c.prepareStatement(sql)){s.setLong(1,id);try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(read(r)):Optional.empty();}}
    }
    public boolean finish(Connection c,long id,String status) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("UPDATE invitations SET status=?,responded_at=CURRENT_TIMESTAMP(3) WHERE invitation_id=? AND status='pending'")){s.setString(1,status);s.setLong(2,id);return s.executeUpdate()==1;}
    }
    public int cancelOtherPending(Connection c,long receiverId,long exceptId) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("UPDATE invitations SET status='joined_other',responded_at=CURRENT_TIMESTAMP(3) WHERE receiver_id=? AND status='pending' AND invitation_id<>?")){s.setLong(1,receiverId);s.setLong(2,exceptId);return s.executeUpdate();}
    }
    public Optional<InvitationRow> nextPending(Connection c,long receiverId) throws SQLException {
        String sql="SELECT i.invitation_id,i.room_id,i.sender_id,i.receiver_id,s.username AS sender,u.username AS receiver,r.room_code,r.name AS room_name,i.status,i.delivered_at,i.expires_at FROM invitations i JOIN users s ON s.user_id=i.sender_id JOIN users u ON u.user_id=i.receiver_id JOIN rooms r ON r.room_id=i.room_id WHERE i.receiver_id=? AND i.status='pending' ORDER BY i.sent_at,i.invitation_id LIMIT 1";
        try(PreparedStatement s=c.prepareStatement(sql)){s.setLong(1,receiverId);try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(read(r)):Optional.empty();}}
    }
    public java.util.List<InvitationRow> pendingForReceiver(Connection c,long receiverId) throws SQLException {
        java.util.List<InvitationRow> rows=new java.util.ArrayList<>();
        String sql="SELECT i.invitation_id,i.room_id,i.sender_id,i.receiver_id,s.username AS sender,u.username AS receiver,r.room_code,r.name AS room_name,i.status,i.delivered_at,i.expires_at FROM invitations i JOIN users s ON s.user_id=i.sender_id JOIN users u ON u.user_id=i.receiver_id JOIN rooms r ON r.room_id=i.room_id WHERE i.receiver_id=? AND i.status='pending' ORDER BY i.sent_at,i.invitation_id";
        try(PreparedStatement s=c.prepareStatement(sql)){s.setLong(1,receiverId);try(ResultSet r=s.executeQuery()){while(r.next())rows.add(read(r));}}return java.util.List.copyOf(rows);
    }
    public boolean markDelivered(Connection c,long id) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("UPDATE invitations SET delivered_at=CURRENT_TIMESTAMP(3),expires_at=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 5 SECOND) WHERE invitation_id=? AND status='pending' AND delivered_at IS NULL")){s.setLong(1,id);return s.executeUpdate()==1;}
    }
    public int cancelRoomSender(Connection c,long roomId,long senderId) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("UPDATE invitations SET status='cancelled',responded_at=CURRENT_TIMESTAMP(3) WHERE room_id=? AND sender_id=? AND status='pending'")){s.setLong(1,roomId);s.setLong(2,senderId);return s.executeUpdate();}
    }
    private static InvitationRow read(ResultSet r)throws SQLException{return new InvitationRow(r.getLong("invitation_id"),r.getLong("room_id"),r.getLong("sender_id"),r.getLong("receiver_id"),r.getString("sender"),r.getString("receiver"),r.getString("room_code"),r.getString("room_name"),r.getString("status"),r.getTimestamp("delivered_at"),r.getTimestamp("expires_at"));}
}
