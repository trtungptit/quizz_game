package quizgame.server.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import quizgame.common.model.ChangeType;
import quizgame.common.model.UserInfo;
import quizgame.common.model.UserStatus;
import quizgame.common.protocol.ErrorInfo;
import quizgame.server.dao.Account;
import quizgame.server.net.ClientSession;
import quizgame.server.net.ProtocolMessages;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Trạng thái online thuần RAM; chỉ session hiện tại mới được phép xóa người chơi. */
public final class OnlineRegistry {
    private static final Gson GSON = new Gson();
    private final Map<String, Entry> online = new HashMap<>();

    public synchronized boolean login(Account account, ClientSession session, long reqId) {
        String key = key(account.username());
        if (online.containsKey(key)) {
            session.send(ProtocolMessages.response("LOGIN", reqId, null,
                    new ErrorInfo("ALREADY_ONLINE", "Tài khoản này đang đăng nhập ở nơi khác")));
            return false;
        }
        Entry entry = new Entry(new Player(account.id(), account.username(), account.totalWins()), session, UserStatus.IDLE);
        online.put(key, entry);
        session.send(ProtocolMessages.response("LOGIN", reqId,
                GSON.toJsonTree(new LoginData(account.username(), account.totalWins())), null));
        session.send(ProtocolMessages.event("ONLINE_LIST", GSON.toJsonTree(snapshotExcept(session))));
        JsonElement add = updateData(entry.info(), ChangeType.ADD);
        for (Entry other : List.copyOf(online.values())) {
            if (other.session() != session) other.session().send(ProtocolMessages.event("USER_UPDATE", add));
        }
        return true;
    }

    public synchronized void logout(ClientSession session, long reqId) {
        Entry removed = remove(session);
        session.send(ProtocolMessages.response("LOGOUT", reqId, null, null));
        if (removed != null) broadcastRemove(removed);
    }

    public synchronized void disconnected(ClientSession session) {
        Entry removed = remove(session);
        if (removed != null) broadcastRemove(removed);
    }

    public synchronized boolean isAuthenticated(ClientSession session) { return find(session) != null; }
    public synchronized String username(ClientSession session) {
        Entry entry = find(session);
        return entry == null ? null : entry.player().username();
    }
    public synchronized long userId(ClientSession session) {
        Entry entry = find(session);
        return entry == null ? -1 : entry.player().id();
    }
    public synchronized UserStatus status(ClientSession session) {
        Entry entry = find(session);
        return entry == null ? null : entry.status();
    }
    public synchronized ClientSession sessionByUsername(String username) {
        Entry entry = online.get(key(username));
        return entry == null ? null : entry.session();
    }
    public synchronized UserStatus statusByUsername(String username) {
        Entry entry = online.get(key(username));
        return entry == null ? null : entry.status();
    }
    public synchronized long userIdByUsername(String username) {
        Entry entry = online.get(key(username));
        return entry == null ? -1 : entry.player().id();
    }

    public synchronized void changeStatus(ClientSession session, UserStatus status) {
        Entry current = find(session);
        if (current == null || current.status() == status) return;
        Entry updated = new Entry(current.player(), current.session(), status);
        online.put(key(current.player().username()), updated);
        broadcast(ProtocolMessages.event("USER_UPDATE", updateData(updated.info(), ChangeType.UPDATE)));
    }

    public synchronized void refreshWins(String username, int totalWins) {
        Entry current = online.get(key(username));
        if (current == null) return;
        Entry updated = new Entry(new Player(current.player().id(), current.player().username(), totalWins), current.session(), current.status());
        online.put(key(current.player().username()), updated);
        broadcast(ProtocolMessages.event("USER_UPDATE", updateData(updated.info(), ChangeType.UPDATE)));
    }

    public synchronized List<UserInfo> snapshotExcept(ClientSession excluded) {
        return online.values().stream().filter(e -> e.session() != excluded)
                .map(Entry::info).sorted(Comparator.comparing(UserInfo::username, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private Entry remove(ClientSession session) {
        for (var iterator = online.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (entry.getValue().session() == session) {
                iterator.remove();
                return entry.getValue();
            }
        }
        return null;
    }

    private Entry find(ClientSession session) {
        return online.values().stream().filter(e -> e.session() == session).findFirst().orElse(null);
    }

    private void broadcastRemove(Entry entry) {
        broadcast(ProtocolMessages.event("USER_UPDATE", updateData(entry.info(), ChangeType.REMOVE)));
    }

    private void broadcast(String event) {
        for (Entry entry : List.copyOf(online.values())) entry.session().send(event);
    }

    private static JsonElement updateData(UserInfo info, ChangeType change) {
        JsonObject data = new JsonObject();
        data.add("user", GSON.toJsonTree(info));
        data.addProperty("change", change.name());
        return data;
    }

    private static String key(String username) { return username.toLowerCase(Locale.ROOT); }
    private record Player(long id, String username, int totalWins) { }
    private record Entry(Player player, ClientSession session, UserStatus status) {
        UserInfo info() { return new UserInfo(player.username(), player.totalWins(), status); }
    }
    private record LoginData(String username, int totalWins) { }
}
