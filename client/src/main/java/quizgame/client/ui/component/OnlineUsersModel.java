package quizgame.client.ui.component;

import quizgame.common.model.ChangeType;
import quizgame.common.model.UserInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Danh sách người chơi online và thông tin của chính mình, do MainFrame nuôi từ sự kiện Server.
 * Chỉ truy cập trên luồng giao diện. Giữ ở mức ứng dụng vì ONLINE_LIST đến ngay sau đăng nhập,
 * trước khi bất kỳ màn nào kịp hiện.
 */
public class OnlineUsersModel {
    /** Người nghe thay đổi (luôn chạy trên luồng giao diện). */
    public interface Listener {
        /** Cả danh sách bị thay mới. */
        default void onReplaced() {
        }

        /** Đúng một dòng thêm/sửa/xóa. */
        default void onChanged(UserInfo user, ChangeType change) {
        }

        /** Thông tin của chính mình đổi (trạng thái, số trận thắng). */
        default void onSelfChanged() {
        }

        /** Trạng thái 'ĐÃ MỜI' của ai đó đổi. */
        default void onInvitedChanged(String username) {
        }
    }

    private final Map<String, UserInfo> users = new LinkedHashMap<>();
    private final Set<String> invited = new HashSet<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private UserInfo self;
    private boolean loaded;

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Thông tin của mình (null nếu chưa đăng nhập). */
    public UserInfo self() {
        return self;
    }

    public void setSelf(UserInfo info) {
        self = info;
        if (info != null) {
            users.remove(info.username());
        }
        listeners.forEach(Listener::onSelfChanged);
    }

    public List<UserInfo> snapshot() {
        return new ArrayList<>(users.values());
    }

    public void replaceAll(List<UserInfo> list) {
        loaded = true;
        users.clear();
        for (UserInfo u : list) {
            if (!isSelf(u.username())) {
                users.put(u.username(), u);
            }
        }
        listeners.forEach(Listener::onReplaced);
    }

    public void apply(UserInfo user, ChangeType change) {
        if (isSelf(user.username())) {
            setSelf(user);
            return;
        }
        if (change == ChangeType.REMOVE) {
            users.remove(user.username());
            invited.remove(user.username());
        } else {
            users.put(user.username(), user);
        }
        listeners.forEach(l -> l.onChanged(user, change));
    }

    private boolean isSelf(String username) {
        return self != null && self.username().equals(username);
    }

    public boolean isInvited(String username) {
        return invited.contains(username);
    }

    /** Đánh dấu đã mời / hết chờ phản hồi. */
    public void setInvited(String username, boolean value) {
        boolean changed = value ? invited.add(username) : invited.remove(username);
        if (changed) {
            listeners.forEach(l -> l.onInvitedChanged(username));
        }
    }

    /** Đã nhận danh sách online từ Server chưa (false = đang tải). */
    public boolean isLoaded() {
        return loaded;
    }

    /** Bỏ trạng thái 'ĐÃ MỜI' của mọi người (khi rời phòng hoặc vào trận). */
    public void clearInvited() {
        List<String> names = new ArrayList<>(invited);
        invited.clear();
        names.forEach(n -> listeners.forEach(l -> l.onInvitedChanged(n)));
    }

    /** Xóa sạch khi đăng xuất hoặc mất kết nối. */
    public void clear() {
        loaded = false;
        users.clear();
        invited.clear();
        self = null;
        listeners.forEach(Listener::onReplaced);
        listeners.forEach(Listener::onSelfChanged);
    }
}
