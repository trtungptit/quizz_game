# QUIZ Server — tài khoản, hiện diện, bộ câu hỏi, phòng và lời mời

## Phạm vi hiện tại

Đã triển khai tài khoản/hiện diện, đọc bộ câu hỏi (`SET_LIST` / `SET_GET`) và luồng tạo/vào/rời phòng + lời mời. Luồng TCP/DB được kiểm thử với **MySQL tạm**; chưa kiểm tra trực tiếp giao diện Swing cho lượt phòng/lời mời này. Chưa triển khai tạo/sửa/xóa bộ qua ứng dụng, ready/remind/kick/config hoặc gameplay.

`ServerApp` chạy `StartupRecovery` sau khi lấy named lock: hủy các phòng waiting/playing, kết thúc membership còn active và hủy lời mời pending còn sót từ lần chạy trước. Hành vi này đã được kiểm thử trên DB tạm, không xóa tài khoản. Chỉ chạy trên DB demo đã sao lưu/chấp nhận việc reset trạng thái runtime; không dùng bản này trên DB production.

## Yêu cầu cấu hình

- Java 17; MySQL 8.0.16+ cho schema hiện tại.
- Tạo cấu hình lần đầu: copy `.env.example` thành `.env` ở thư mục gốc repository, rồi điền user/password MySQL của máy đang chạy. Không ghi đè `.env` nếu đã cấu hình.
- Điền cấu hình local trong file `.env` ở thư mục gốc repository; `ServerApp` đọc file theo working directory. Biến môi trường của tiến trình được ưu tiên nếu trùng tên.
- `.env` bị Git ignore; file này không được mã hóa. Không chia sẻ hoặc commit file chứa mật khẩu.
- `database/schema.sql` là schema cho DB cài mới, **không phải migration**; không chạy lại trên DB có dữ liệu và không DROP DB thật.
- Không nhúng credentials vào JDBC URL, source, lệnh được lưu trong shell history hoặc log.

| Biến môi trường | Bắt buộc | Mặc định / ràng buộc |
|---|---:|---|
| `QUIZ_SERVER_HOST` | Không | `127.0.0.1` |
| `QUIZ_SERVER_PORT` | Không | `9090`; khoảng 1–65535 |
| `QUIZ_DB_URL` | Có | JDBC MySQL; đúng DB `quiz_game`; không chứa user/password |
| `QUIZ_DB_USER` | Có | User MySQL |
| `QUIZ_DB_PASSWORD` | Có | Cấp ngoài source/chat |

TCP hiện chưa bật TLS; không mở server ra Internet.

## Build và kiểm thử (PowerShell, ở thư mục gốc repo)

```powershell
$mvn = 'C:/Users/admin/.m2/wrapper/dists/apache-maven-3.8.2-bin/1um3pu5poco57pibs3g92d6vrf/apache-maven-3.8.2/bin/mvn.cmd'
& $mvn -q test
& $mvn -q -DskipTests package
python database/tests/test_schema.py --mysql-bin 'C:/Program Files/MySQL/MySQL Server 8.0/bin'
python database/tests/run_backend_tests.py
```

`run_backend_tests.py` tạo MySQL disposable để chạy unit và TCP/DB integration tests; không dùng DB thật. Kết quả gần nhất đã xác nhận: `Disposable MySQL: 8.0.46` và `Backend unit + TCP/DB integration tests passed.`

## Chạy thủ công

Điền `QUIZ_DB_USER` và `QUIZ_DB_PASSWORD` trong `.env` ở thư mục gốc. Chạy lệnh từ chính thư mục gốc sau khi đã chuẩn bị **DB dùng thử**:

```powershell
& $mvn -q -pl server -am exec:java
```

Server kiểm tra schema và lấy MySQL named lock trước khi bind cổng. Nếu có instance khác dùng cùng DB, startup sẽ thất bại. Dừng bằng `Ctrl+C`.

## Luồng tài khoản/hiện diện được kiểm thử

- `PING` → `PONG`; session bị dọn nếu không có traffic đọc được trong 15 giây.
- `REGISTER`: tạo tài khoản, username theo `[A-Za-z0-9_]{3,20}`, mật khẩu 6–64 ký tự; username trùng không phân biệt hoa/thường bị từ chối.
- `LOGIN`: gửi username canonical và `totalWins`; một tài khoản chỉ có một session online. Lần đăng nhập sai trả chung `WRONG_CREDENTIALS`.
- Sau login, client nhận `ONLINE_LIST` (không gồm chính client); các session khác nhận `USER_UPDATE` với `ADD`.
- `LOGOUT`, disconnect và timeout dọn presence idempotent; người online khác nhận `USER_UPDATE` với `REMOVE`.
- Snapshot/update mang username, `totalWins` và trạng thái. Thay đổi trạng thái hoặc số thắng được phát bằng `UPDATE` qua API nội bộ.
- Password được hash PBKDF2, không gửi qua protocol. Presence chỉ giữ dữ liệu người chơi cần thiết, không giữ password hash.

## Đọc bộ câu hỏi

- Cần đăng nhập trước khi gửi `SET_LIST` (`data: null`) hoặc `SET_GET` (`data: {"id": ...}`).
- `SET_LIST` trả `QuestionSetSummary[]`: tên, chủ đề, username người tạo, số câu, loại câu không lặp và cờ có ảnh. Sắp xếp bộ mới nhất trước theo ID. Không gửi đáp án trong danh sách.
- `SET_GET` trả `QuestionSet` gồm câu theo `position`, đáp án theo `option_index` (0 = A), đáp án đúng/đáp án nhập chữ và thời gian từng câu. Đây là dữ liệu bộ để xem/soạn, không phải sự kiện câu hỏi trong trận đấu.
- Bộ công khai đọc được bởi mọi người đã đăng nhập. Bộ xóa mềm không xuất hiện; ID không tồn tại hoặc đã xóa trả `SET_NOT_FOUND`. Danh sách rỗng trả `[]`.
- Ảnh trong `image_ref` là tên file tương đối dưới thư mục `images/` ở working directory; có thể dùng `pixel.png` hoặc `images/pixel.png`. Server chuyển bytes sang `imageBase64`, giới hạn mỗi file 1 MiB, chặn đường dẫn ra ngoài thư mục và không tải URL. Ảnh thiếu/lỗi trả `INTERNAL` thay vì bỏ ảnh im lặng.
- Giữ nguyên giới hạn frame 2 MiB của giao thức. Phản hồi vượt giới hạn trả lỗi `INTERNAL` rõ ràng, không làm rớt kết nối.
- Không thay đổi schema và không tự thêm câu hỏi vào DB thật. Nếu đã nhập SQL mẫu, dừng backend cũ rồi chạy lại `ServerApp`, đăng nhập lại client và tải lại danh sách ở trang chủ. Không cần nhập lại SQL.

## Phòng và lời mời

- Hỗ trợ `CREATE_ROOM`, `JOIN_ROOM`, `LEAVE_ROOM`, `INVITE`, `INVITE_REPLY`. Giữ nguyên protocol/common/client và schema.
- Mã phòng ngẫu nhiên 6 ký tự, tên 1–30 ký tự, tối đa 5 thành viên. Một người chỉ ở một phòng; có thể rời rồi vào lại. Phòng đóng trả `ROOM_NOT_FOUND`; phòng playing trả `ROOM_PLAYING`.
- Chủ rời/mất kết nối: chuyển chủ cho thành viên vào sớm nhất; người cuối rời: đóng hàng phòng, giữ lịch sử DB. Presence đổi IDLE/IN_ROOM sau commit.
- Mỗi người nhận có queue riêng. Chỉ lời đầu hàng được gửi; 5 giây tính theo mốc writer gửi xong frame, không theo lúc xếp hàng và không cộng thêm thời gian chờ coordinator.
- Accept/reject/expire chỉ kết thúc một lần. Accept hoặc tự tạo/vào phòng bằng mã sẽ hủy các lời khác bằng `JOINED_OTHER`, cập nhật cả pending list ở các phòng liên quan.
- Sender rời/phòng đóng và receiver logout/disconnect: hủy lời không còn hợp lệ. Vì protocol không có `CANCELLED`, dialog cũ có thể còn đến hết 5 giây; server giữ slot trước khi gửi lời kế tiếp, hoặc giải phóng khi client trả lời ID cũ (`INVITE_EXPIRED`). Không thêm enum/event ngoài protocol.
- Room/presence/invitation mutation và timer cùng monitor điều phối, writer FIFO riêng. Server xử lý tối đa 32 kết nối đồng thời; không để kết nối thứ 9 bị xếp sau 8 phiên còn mở.
- SQL thất bại khi accept: rollback membership/trạng thái lời mời, không công bố ACCEPTED. Cleanup disconnect thất bại được ghi nhớ và thử lại; không giữ user online giả. Shutdown vẫn dựa vào startup recovery nếu DB không phục hồi kịp.
- `RoomState.set=null`, `numQuestions=0`, chưa có cấu hình trận. Các nút ready, nhắc sẵn sàng, kick, chọn bộ/cấu hình và bắt đầu chơi vẫn trả lỗi chưa hỗ trợ; không phải toàn bộ gameplay đã xong.
- Kiểm chứng: 14 integration test mới dùng TCP thật/MySQL tạm cho lifecycle, queue, deadline lúc coordinator bận, capacity race, duplicate invite, rollback, cleanup retry, recovery/instance lock và 10 client ở hai phòng. Không đọc hoặc sửa DB demo của Hải.

### Kiểm tra bằng hai client

1. Dừng backend cũ, build và chạy lại `ServerApp` từ thư mục gốc (NetBeans: `exec.workingdir=D:/Code/Java/LTM/quizz_game`).
2. Mở hai client, đăng nhập hai tài khoản khác nhau; cả hai ban đầu IDLE.
3. A tạo phòng, mời B. B thử từ chối, chờ hết hạn, rồi nhận lời mời mới và đồng ý.
4. Kiểm tra thành viên và trạng thái cả hai phía; thử B rời rồi vào lại bằng mã.
5. A đóng client/đăng xuất: B nhận quyền chủ; B rời: mã phòng cũ không còn tham gia được.
6. Không nhập lại schema hoặc SQL câu hỏi; không commit `.env`. Chưa commit/push tự động.
