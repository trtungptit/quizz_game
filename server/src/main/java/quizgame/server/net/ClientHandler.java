package quizgame.server.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import quizgame.common.protocol.ErrorInfo;
import quizgame.server.dao.StatisticsDao;
import quizgame.server.service.AuthService;
import quizgame.server.service.InviteManager;
import quizgame.server.service.OnlineRegistry;
import quizgame.server.service.RoomManager;
import quizgame.server.service.ServiceException;
import quizgame.server.service.QuestionSetService;
import quizgame.server.service.GameManager;
import quizgame.common.model.Answer;

import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/** Xử lý yêu cầu tuần tự của một kết nối TCP. */
public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final AuthService auth;
    private final OnlineRegistry online;
    private final RoomManager rooms;
    private final InviteManager invites;
    private final QuestionSetService questionSets;
    private final StatisticsDao statistics;
    private final GameManager games;
    private final AtomicReference<ClientSession> sessionRef = new AtomicReference<>();

    public ClientHandler(Socket socket, AuthService auth, OnlineRegistry online) { this(socket, auth, online, null, null); }
    public ClientHandler(Socket socket, AuthService auth, OnlineRegistry online, RoomManager rooms) { this(socket, auth, online, rooms, null); }
    public ClientHandler(Socket socket, AuthService auth, OnlineRegistry online, RoomManager rooms, InviteManager invites) {
        this(socket, auth, online, rooms, invites, null);
    }

    public ClientHandler(Socket socket, AuthService auth, OnlineRegistry online, RoomManager rooms,
                         InviteManager invites, QuestionSetService questionSets) {
        this(socket, auth, online, rooms, invites, questionSets, null);
    }

    public ClientHandler(Socket socket, AuthService auth, OnlineRegistry online, RoomManager rooms,
                         InviteManager invites, QuestionSetService questionSets, StatisticsDao statistics) {
        this.statistics = statistics;
        this.socket = socket;
        this.auth = auth;
        this.online = online;
        this.rooms = rooms;
        this.invites = invites;
        this.questionSets = questionSets;
        this.games=rooms==null?null:rooms.games();
    }

    @Override public void run() {
        try {
            socket.setSoTimeout(15_000);
            ClientSession session = new ClientSession(socket, () -> {
                ClientSession current = sessionRef.get();
                synchronized (online) {
                if (current != null) {
                    if (invites != null) invites.disconnected(current);
                    if (rooms != null) rooms.leaveForDisconnect(current);
                    online.disconnected(current);
                    if (invites != null) invites.reconcileQueues();
                }
                }
            });
            sessionRef.set(session);
            try (DataInputStream input = new DataInputStream(socket.getInputStream())) {
                while (session.isOpen()) handle(JsonParser.parseString(quizgame.common.protocol.FramedIO.read(input)), session);
            }
        } catch (SocketTimeoutException ignored) {
            // Không có traffic trong thời hạn protocol; finally sẽ cleanup hiện diện.
        } catch (IOException | RuntimeException ignored) {
            // Không ghi nội dung request/exception để tránh lộ thông tin đăng nhập.
        } finally {
            ClientSession session = sessionRef.get();
            if (session != null) session.close();
            else try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private void handle(JsonElement message, ClientSession session) {
        Long reqId = null;
        String type = "";
        try {
            JsonObject request = requireObject(message);
            type = requireString(request, "type");
            reqId = requireRequestId(request.get("reqId"));
            if (!request.has("data")) throw new IllegalArgumentException();
            JsonElement data = request.get("data");
            if ("PING".equals(type)) {
                requireNull(data);
                session.send(ProtocolMessages.response("PONG", reqId, null, null));
                return;
            }
            if ("REGISTER".equals(type)) {
                if (online.isAuthenticated(session)) throw new ServiceException("BAD_REQUEST", "Hãy đăng xuất trước khi đăng ký tài khoản khác");
                JsonObject fields = requireFields(data, "username", "password");
                String username = requireString(fields, "username");
                char[] password = requireString(fields, "password").toCharArray();
                try { auth.register(username, password); }
                finally { Arrays.fill(password, '\0'); }
                session.send(ProtocolMessages.response(type, reqId, null, null));
                return;
            }
            if ("LOGIN".equals(type)) {
                if (online.isAuthenticated(session)) throw new ServiceException("ALREADY_ONLINE", "Kết nối này đã đăng nhập");
                JsonObject fields = requireFields(data, "username", "password");
                String username = requireString(fields, "username");
                char[] password = requireString(fields, "password").toCharArray();
                try { online.login(auth.authenticate(username, password), session, reqId); }
                finally { Arrays.fill(password, '\0'); }
                return;
            }
            if (statistics != null && ("LEADERBOARD".equals(type) || "HISTORY".equals(type)
                    || "HISTORY_DETAIL".equals(type))) {
                if (!online.isAuthenticated(session)) {
                    replyError(session, type, reqId, "NOT_LOGGED_IN", "Bạn cần đăng nhập trước");
                    return;
                }
                long userId = online.userId(session);
                Object result;
                if ("HISTORY_DETAIL".equals(type)) {
                    var fields = requireFields(data, "matchId");
                    result = statistics.detail(userId, requireRequestId(fields.get("matchId")));
                } else {
                    requireNull(data);
                    result = "HISTORY".equals(type) ? statistics.history(userId) : statistics.leaderboard(userId);
                }
                sendSetResponse(session, type, reqId, result);
                return;
            }
            synchronized (online) {
            if (!online.isAuthenticated(session)) {
                replyError(session, type, reqId, "NOT_LOGGED_IN", "Bạn cần đăng nhập trước");
                return;
            }
            if ("LOGOUT".equals(type)) {
                requireNull(data);
                if (invites != null) invites.disconnected(session);
                if (rooms != null) rooms.leaveForDisconnect(session);
                online.logout(session, reqId);
                if (invites != null) invites.reconcileQueues();
                return;
            }
            if (questionSets != null && "SET_LIST".equals(type)) {
                requireNull(data);
                sendSetResponse(session, type, reqId, questionSets.list());
                return;
            }
            if (questionSets != null && "SET_GET".equals(type)) {
                JsonObject fields = requireFields(data, "id");
                long id = requireRequestId(fields.get("id"));
                sendSetResponse(session, type, reqId, questionSets.get(id));
                return;
            }
            if (questionSets != null && "SET_CREATE".equals(type)) {
                var fields = requireFields(data, "set");
                var set = QuestionSetRequest.parse(fields.get("set"));
                long id = questionSets.create(online.userId(session), online.username(session), set);
                sendSetResponse(session, type, reqId, java.util.Map.of("id", id));
                return;
            }
            if (questionSets != null && "SET_UPDATE".equals(type)) {
                var fields = requireFields(data, "set");
                var set = QuestionSetRequest.parse(fields.get("set"));
                var changed = questionSets.update(online.userId(session), online.username(session), set);
                session.send(ProtocolMessages.response(type, reqId, null, null));
                if (rooms != null) changed.forEach(rooms::publish);
                return;
            }
            if (questionSets != null && "SET_DELETE".equals(type)) {
                var fields = requireFields(data, "id");
                long id = requireRequestId(fields.get("id"));
                var changed = questionSets.delete(online.userId(session), id);
                session.send(ProtocolMessages.response(type, reqId, null, null));
                if (rooms != null) changed.forEach(rooms::publish);
                return;
            }
            if(games!=null&&"START_GAME".equals(type)){
                requireNull(data);games.start(session,reqId);return;
            }
            if(games!=null&&"QUIT_GAME".equals(type)){
                requireNull(data);games.quit(session,false);session.send(ProtocolMessages.response(type,reqId,null,null));return;
            }
            if(games!=null&&"ANSWER".equals(type)){
                var fields=requireFields(data,"qIndex","answer");int index=requireInt(fields.get("qIndex"));
                if(index<0)throw new IllegalArgumentException();
                var answer=requireFields(fields.get("answer"),"choices","text");
                java.util.List<Integer> choices=new java.util.ArrayList<>();
                if(!answer.get("choices").isJsonNull()){
                    if(!answer.get("choices").isJsonArray()||answer.getAsJsonArray("choices").size()>4)throw new IllegalArgumentException();
                    for(var choice:answer.getAsJsonArray("choices"))choices.add(requireInt(choice));
                }
                String text=answer.get("text").isJsonNull()?null:requireString(answer,"text");
                games.answer(session,index,new Answer(java.util.List.copyOf(choices),text),reqId);return;
            }
            if (rooms != null && "SET_READY".equals(type)) {
                var fields=requireFields(data,"ready");var value=fields.get("ready");
                if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();
                var state=rooms.ready(session,value.getAsBoolean());
                session.send(ProtocolMessages.response(type,reqId,null,null));rooms.publish(state);return;
            }
            if (rooms != null && "REMIND_READY".equals(type)) {
                requireNull(data);rooms.remind(session);session.send(ProtocolMessages.response(type,reqId,null,null));return;
            }
            if(rooms!=null&&"KICK".equals(type)){
                var fields=requireFields(data,"username");var state=rooms.kick(session,requireString(fields,"username"));
                session.send(ProtocolMessages.response(type,reqId,null,null));rooms.publish(state);return;
            }
            if(rooms!=null&&"ROOM_CONFIG".equals(type)){
                var fields=requireFields(data,"setId","numQuestions","timeOverrideSeconds");
                Long setId=fields.get("setId").isJsonNull()?null:requireRequestId(fields.get("setId"));
                int count=requireInt(fields.get("numQuestions"));
                Integer seconds=fields.get("timeOverrideSeconds").isJsonNull()?null:requireInt(fields.get("timeOverrideSeconds"));
                var state=rooms.configure(session,setId,count,seconds);
                session.send(ProtocolMessages.response(type,reqId,null,null));rooms.publish(state);return;
            }
            if (rooms != null && "CREATE_ROOM".equals(type)) {
                JsonObject fields = requireFields(data, "name");
                var state = rooms.create(session, requireString(fields, "name"));
                session.send(ProtocolMessages.response(type, reqId, new com.google.gson.Gson().toJsonTree(state), null));
                rooms.publish(state);
                return;
            }
            if (rooms != null && "JOIN_ROOM".equals(type)) {
                JsonObject fields = requireFields(data, "code");
                var state = rooms.join(session, requireString(fields, "code"));
                session.send(ProtocolMessages.response(type, reqId, new com.google.gson.Gson().toJsonTree(state), null));
                rooms.publish(state);
                return;
            }
            if (rooms != null && "LEAVE_ROOM".equals(type)) {
                requireNull(data);
                var state = rooms.leave(session);
                session.send(ProtocolMessages.response(type, reqId, null, null));
                rooms.publish(state);
                return;
            }
            if (invites != null && "INVITE".equals(type)) {
                JsonObject fields = requireFields(data, "username");
                invites.invite(session, requireString(fields, "username"));
                session.send(ProtocolMessages.response(type, reqId, null, null));
                return;
            }
            if (invites != null && "INVITE_REPLY".equals(type)) {
                JsonObject fields = requireFields(data, "inviteId", "accept");
                long inviteId = requireRequestId(fields.get("inviteId"));
                JsonElement acceptElement = fields.get("accept");
                if (acceptElement == null || !acceptElement.isJsonPrimitive() || !acceptElement.getAsJsonPrimitive().isBoolean())
                    throw new IllegalArgumentException();
                var outcome = invites.reply(session, inviteId, acceptElement.getAsBoolean());
                if ("ROOM_FULL".equals(outcome.result())) {
                    replyError(session, type, reqId, "ROOM_FULL", "Phòng đã đủ 5 người");
                } else if ("ROOM_PLAYING".equals(outcome.result())) {
                    replyError(session, type, reqId, "ROOM_PLAYING", "Phòng đang chơi hoặc đã đóng");
                } else if ("EXPIRED".equals(outcome.result())) {
                    replyError(session, type, reqId, "INVITE_EXPIRED", "Lời mời không còn hiệu lực");
                } else {
                    JsonElement replyData = "ACCEPTED".equals(outcome.result())
                            ? new com.google.gson.Gson().toJsonTree(outcome.state()) : null;
                    session.send(ProtocolMessages.response(type, reqId, replyData, null));
                }
                return;
            }
            replyError(session, type, reqId, "BAD_REQUEST", "Chức năng này chưa được triển khai ở Server");
            }
        } catch (java.sql.SQLException ex) {
            replyError(session, type, reqId, "INTERNAL", "Không thể tải dữ liệu thống kê");
        } catch (ServiceException ex) {
            replyError(session, type, reqId, ex.code(), ex.getMessage());
        } catch (IllegalArgumentException ex) {
            replyError(session, type, reqId, "BAD_REQUEST", "Yêu cầu không đúng định dạng");
        }
    }

    private static JsonObject requireObject(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException();
        return value.getAsJsonObject();
    }

    private static JsonObject requireFields(JsonElement value, String... names) {
        JsonObject object = requireObject(value);
        if (object.size() != names.length) throw new IllegalArgumentException();
        for (String name : names) if (!object.has(name)) throw new IllegalArgumentException();
        return object;
    }

    private static String requireString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException();
        return value.getAsString();
    }

    private static long requireRequestId(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException();
        try {
            BigDecimal number = value.getAsBigDecimal();
            long result = number.longValueExact();
            if (result < 1) throw new IllegalArgumentException();
            return result;
        } catch (ArithmeticException ex) { throw new IllegalArgumentException(); }
    }

    private static int requireInt(JsonElement value){
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();
        try{return value.getAsBigDecimal().intValueExact();}catch(ArithmeticException ex){throw new IllegalArgumentException();}
    }
    private static void requireNull(JsonElement value) {
        if (value != null && !value.isJsonNull()) throw new IllegalArgumentException();
    }

    private static void sendSetResponse(ClientSession session, String type, long reqId, Object data) {
        String response = ProtocolMessages.response(type, reqId, new com.google.gson.Gson().toJsonTree(data), null);
        if (response.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > quizgame.common.protocol.FramedIO.MAX_FRAME) {
            throw new ServiceException("INTERNAL", "Bộ câu hỏi hoặc danh sách quá lớn để tải qua kết nối hiện tại");
        }
        session.send(response);
    }

    private static void replyError(ClientSession session, String type, Long reqId, String code, String message) {
        session.send(ProtocolMessages.response(type, reqId, null, new ErrorInfo(code, message)));
    }
}
