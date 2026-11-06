# QUIZ Server — tài khoản, phòng, lời mời và chơi quiz

## Phạm vi hiện tại

Đã triển khai tài khoản/hiện diện, tạo/đọc/sửa/xóa mềm bộ câu hỏi, phòng/lời mời, sẵn sàng/nhắc/kick/cấu hình và chơi trọn trận (START_GAME, ANSWER, QUIT_GAME). Luồng TCP/DB được kiểm thử với **MySQL tạm**; chưa thao tác trực tiếp giao diện Swing cho giai đoạn gameplay này. Chưa triển khai leaderboard hoặc history API.

`ServerApp` chạy `StartupRecovery` sau khi lấy named lock: hủy phòng/lời mời/match còn dở, đóng submission chưa kết thúc và đánh dấu round hoàn tất trong match cancelled. Không xóa tài khoản hoặc cộng thắng cho trận bị hủy. Shutdown hủy trận trước khi lần lượt đóng sockets để không tạo người thắng giả. Chỉ chạy trên DB demo đã sao lưu/chấp nhận việc reset trạng thái runtime; không dùng bản này trên DB production.

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

## Bộ câu hỏi

### Tạo bộ câu hỏi

- `SET_CREATE {set: QuestionSet}` yêu cầu đăng nhập và `id=0`; trả `{id}` sau khi giao dịch đã commit. Chủ sở hữu lấy từ session, không tin trường `owner` do Client gửi.
- Server kiểm tra tên (1–255 ký tự), chủ đề (tối đa 100 ký tự), mô tả (tối đa 65535 byte UTF-8), ít nhất một câu, nội dung, lựa chọn, đáp án đúng và thời gian 5–120 giây theo PROTOCOL. Lỗi nghiệp vụ trả `INVALID_SET`, chỉ rõ câu bị lỗi; JSON sai kiểu trả `BAD_REQUEST`.
- Lưu đủ SINGLE/MULTI/TYPE, giữ thứ tự câu và lựa chọn. Đáp án TYPE được chuẩn hóa như khi chấm; các đáp án tương đương chỉ lưu một lần, giữ dấu tiếng Việt.
- Ảnh phải là Base64 PNG/JPG hợp lệ, tối đa 1 MiB sau giải mã và 16 triệu điểm ảnh. Server sinh tên file dưới `images/`, không nhận đường dẫn từ Client. Tài khoản chạy server cần quyền ghi thư mục này.
- Kiểm tra toàn bộ bản nháp và ảnh trước khi ghi; dữ liệu bộ/câu/lựa chọn/đáp án nằm trong một giao dịch. Nếu lưu file hoặc SQL thất bại, trả `INTERNAL`, rollback dữ liệu và dọn ảnh đã tạo. Không tự retry yêu cầu tạo khi đã mất kết nối sau commit để tránh tạo trùng.
- Bộ mới công khai và dùng ngay qua SET_LIST/SET_GET/chọn bộ cho phòng. Giới hạn frame 2 MiB vẫn giữ nguyên; bộ quá lớn bị từ chối trước khi lưu.
- Kiểm tra thủ công: build và khởi động lại server, mở ClientApp, đăng nhập → TẠO BỘ CÂU HỎI → điền tên, thêm câu thuộc ba loại và ảnh → hoàn tất lưu → kiểm tra thẻ bộ mới ở Home, mở lại để xem nội dung và chọn bộ trong phòng. Không cần nhập lại schema.
- Kiểm thử tự động riêng chức năng tạo/đọc/sửa/xóa (19 test, MySQL tạm; không dùng `.env`):

```powershell
python database/tests/run_backend_tests.py --mvn 'C:/Program Files/Apache NetBeans/java/maven/bin/mvn.cmd' --tests 'QuestionSetMutationIntegrationTest,QuestionSetCreateIntegrationTest,QuestionSetReadIntegrationTest,QuestionSetRequestTest,QuestionSetValidatorTest'
```

Runner cho phép `--timeout` (giây), mặc định 900 để đủ thời gian chạy toàn bộ integration suite trên máy chậm.

### Sửa và xóa bộ câu hỏi

- `SET_UPDATE {set: QuestionSet}` trả `data:null`; `set.id` phải là ID dương của bộ đang tồn tại. Chỉ chủ sở hữu trong session được sửa; trường `owner` từ Client không thay đổi quyền sở hữu. Áp dụng toàn bộ validation nội dung/ảnh như khi tạo mới.
- `SET_DELETE {id}` trả `data:null`, đánh dấu `deleted_at` và ẩn bộ khỏi SET_LIST/SET_GET/chọn bộ để chơi. Giữ câu hỏi, đáp án, ảnh và các tham chiếu lịch sử.
- Chưa đăng nhập: `NOT_LOGGED_IN`; không phải chủ bộ: `NOT_OWNER`; bộ không tồn tại/đã xóa: `SET_NOT_FOUND`; bộ đang thuộc match `pending`/`in_progress`: `SET_IN_USE`. Các yêu cầu lỗi không làm đóng socket.
- Khi sửa, chuyển phiên bản cũ (metadata, toàn bộ câu/đáp án/ảnh và các round đã dùng) sang một bản lưu ẩn; ID công khai của bộ vẫn giữ nguyên. Các ID câu/đáp án/submission cũ không đổi và dữ liệu lịch sử giữ tên/nội dung/điểm cũ. Không sửa schema; các phiên bản và ảnh cũ được giữ lại trong DB/ổ đĩa.
- Một transaction gồm kiểm tra quyền/trận đang dùng, lưu phiên bản cũ, ghi nội dung mới và cập nhật các phòng chờ. SQL thất bại rollback toàn bộ và dọn ảnh mới tạo. Khóa bộ cũng được giữ khi đọc câu/tạo round để tránh trộn hai phiên bản khi bắt đầu trận.
- Phòng chờ chọn bộ vừa sửa: giữ lựa chọn, giảm số câu xuống số còn có nếu cần, reset ready và phát ROOM_STATE mới sau commit. Bộ bị xóa: bỏ lựa chọn, đặt số câu về 0, xóa thời gian ghi đè và reset ready. ACK của người sửa/xóa được gửi trước ROOM_STATE.
- Kiểm tra tay: đăng nhập bằng chủ bộ → bấm SỬA trên thẻ → đổi tên/nội dung/đáp án/ảnh → HOÀN THÀNH → mở lại để đối chiếu. Bấm XÓA → xác nhận → thẻ biến mất. Thử trong lúc một phòng chờ đang chọn bộ để thấy cập nhật; thử khi trận đang chạy để thấy lỗi SET_IN_USE. Khởi động lại server cũ trước khi thử bản mới, chỉ chạy một server.

### Đọc bộ câu hỏi

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
- Phòng mới chưa chọn bộ (`set=null`, `numQuestions=0`). Sau `ROOM_CONFIG`, ROOM_STATE mang tóm tắt bộ thật; xem các thao tác quản lý/chơi bên dưới.
- Kiểm chứng: 14 integration test mới dùng TCP thật/MySQL tạm cho lifecycle, queue, deadline lúc coordinator bận, capacity race, duplicate invite, rollback, cleanup retry, recovery/instance lock và 10 client ở hai phòng. Không đọc hoặc sửa DB demo của Hải.

### Kiểm tra bằng hai client

1. Dừng backend cũ, build và chạy lại `ServerApp` từ thư mục gốc (NetBeans: `exec.workingdir=D:/Code/Java/LTM/quizz_game`).
2. Mở hai client, đăng nhập hai tài khoản khác nhau; cả hai ban đầu IDLE.
3. A tạo phòng, mời B. B thử từ chối, chờ hết hạn, rồi nhận lời mời mới và đồng ý.
4. Kiểm tra thành viên và trạng thái cả hai phía; thử B rời rồi vào lại bằng mã.
5. A đóng client/đăng xuất: B nhận quyền chủ; B rời: mã phòng cũ không còn tham gia được.
6. Không nhập lại schema hoặc SQL câu hỏi; không commit `.env`. Chưa commit/push tự động.

## Sẵn sàng, nhắc, kick và cấu hình

- `SET_READY {ready}`: boolean đúng kiểu, chỉ thành viên phòng WAITING. ACK sau commit rồi ROOM_STATE tới cả phòng.
- `REMIND_READY`: người gọi phải ready; chỉ những người chưa ready nhận READY_REMINDER. Giới hạn một lần/5 giây theo session trong phòng; state được dọn khi rời/kick/kết thúc trận.
- `KICK {username}`: chỉ host khi WAITING; không tự kick. Kết thúc membership và lời mời đã gửi của target, đưa target về IDLE và gửi KICKED; cả phòng nhận snapshot mới.
- `ROOM_CONFIG {setId,numQuestions,timeOverrideSeconds}`: chỉ host, WAITING; count 1..số câu, time null hoặc 5..60. Bỏ bộ dùng setId=null,count=0. Bộ xóa/rỗng bị từ chối.
- Thay đổi cấu hình thật sẽ reset ready của cả phòng. Gửi lại cấu hình y hệt không reset. Không cần ALTER schema.

## Luồng chơi

1. Host START_GAME khi có bộ hợp lệ, ít nhất hai người và mọi người ready. Server đóng băng N câu đầu theo position, kiểm tra ảnh/frame trước khi ghi match; một transaction tạo match/participants/round/questions và chuyển phòng PLAYING.
2. ACK START_GAME -> GAME_START (ảnh dùng chung) -> QUESTION đầu cho từng người. QUESTION không có đáp án đúng. Đổi presence PLAYING, hủy lời mời chưa hết hạn của phòng.
3. Mỗi người có submission slot/deadline riêng; mốc actual-write được ghi trước khi đợi state monitor. Client không gửi thời gian/điểm để server tin theo.
4. ANSWER được validate theo SINGLE/MULTI/TYPE; chỉ đáp án đầu tiên được lưu. SINGLE một lựa chọn, MULTI so tập chính xác, TYPE chuẩn hóa whitespace/case nhưng giữ dấu tiếng Việt.
5. ACK ANSWER -> ANSWER_RESULT -> SCOREBOARD. Đúng được điểm theo công thức PROTOCOL; sai/timeout bằng 0. Timer xử lý ở hạn +500 ms theo grace của protocol, thời gian tính điểm clamp tại hạn gốc.
6. Khoảng 1500 ms sau khi gửi ANSWER_RESULT, server gửi câu tiếp theo cho riêng người đó. Người xong câu cuối chờ những người còn lại; không gửi thêm câu.
7. Mọi người xong hoặc chỉ còn một người không rời: transaction finalize lưu kết quả, tăng total_wins đúng một lần nếu có người thắng, đưa phòng WAITING và reset ready. Sau commit mới gửi GAME_END -> ROOM_STATE -> USER_UPDATE. Bằng điểm cao nhất là hòa, không cộng thắng.
8. QUIT_GAME ghi QUIT, nhưng giữ membership để tương thích client gọi LEAVE_ROOM tiếp theo. EOF/logout ghi DISCONNECTED. Người rời xếp sau người ở lại; người sống sót duy nhất thắng. Hủy server khác với quit, không tự cộng thắng.

### Lưu kết quả và xử lý lỗi

- SQL lỗi khi start/answer: rollback, không ACK thành công và không đổi score trong RAM trước commit.
- Finalize lỗi: giữ kết quả đã đóng băng và retry idempotent, không phát GAME_END giả/không cộng thắng hai lần. Player rời trong lúc retry được tách khỏi session trận cũ để event/callback cũ không thay đổi trận mới.
- Mặc định JDBC có connectTimeout/socketTimeout=5000 ms; tham số do người vận hành thêm vào JDBC URL có thể thay mặc định của driver. Không log URL/credentials.
- Ảnh phải ở images/, mỗi file <=1 MiB. Tổng GAME_START/QUESTION phải vừa frame 2 MiB; lỗi ảnh/frame chặn START trước commit, không làm rớt kết nối.
- Test dùng SQL trigger chỉ trong DB disposable để chứng minh rollback/start/answer/finalize/cleanup; không cài trigger lên DB người dùng.

### Demo một trận bằng hai client

1. Dừng backend cũ, build/chạy lại ServerApp bằng working directory đã cấu hình. Mở hai client và đăng nhập hai tài khoản khác nhau.
2. A tạo phòng, B vào bằng mã hoặc lời mời. A chọn bộ đã nhập SQL, chọn số câu/thời gian.
3. A bấm sẵn sàng, thử nhắc B; B sẵn sàng. A bấm Bắt đầu.
4. Trả lời theo tốc độ riêng; kiểm tra kết quả từng câu, bảng điểm, kết quả trận và số trận thắng.
5. Cả hai sẵn sàng lại để chơi trận mới; thử một người thoát giữa trận ở lượt khác.
6. Không nhập lại SQL/schema. Chưa commit/push giai đoạn này; API xếp hạng và lịch sử được nối tại mục Thống kê bên dưới.

## Sửa lỗi về Home nhưng server vẫn giữ trong phòng

- Client hiện gửi LEAVE_ROOM và chờ server xác nhận trước khi đi từ phòng/kết quả sang Home hoặc các trang ngoài phòng. Bấm lặp lúc đang chờ không gửi nhiều lệnh rời.
- QUIT_GAME/LEAVE_ROOM báo lỗi thật: giữ thông tin phòng và báo lỗi để thử lại; không xóa state local rồi giả vờ đã ra ngoài. NOT_PLAYING khi trận đã kết thúc và NOT_IN_ROOM khi server xác nhận đã ra ngoài được xử lý idempotent.
- Chỉ sau xác nhận rời phòng mới chuyển self về IDLE. Bỏ snapshot phòng vừa rời và GAME_END đến muộn khi không còn ở màn chơi; vẫn cho phép vào lại phòng bằng lời mời mới.
- Đã kiểm tra MainFrame/Swing và SocketGameClient thật qua TCP: chờ ACK, lỗi và retry, bấm điều hướng lặp, sự kiện muộn và nhận lời mời mới. Kiểm tra end-to-end riêng với backend thật trên MySQL disposable xác nhận membership được dọn, tạo phòng mới và accept lời mời đều thành công.
- Để dùng bản sửa: build lại và đóng/mở lại client. Phiên cũ đang kẹt nên đăng xuất hoặc đóng client để server dọn session; không cần xóa DB/schema hoặc khởi động lại server chỉ vì lỗi điều hướng này.

## Thống kê và tích hợp quản lý bộ câu hỏi

Đã nối lại LEADERBOARD, HISTORY, HISTORY_DETAIL sau khi tích hợp quản lý bộ câu hỏi.
Lấy danh tính từ phiên đăng nhập, chỉ đọc trận đã chốt và chỉ cho người tham gia xem chi tiết.
StatisticsDao đã có từ PR #2 và tiếp tục được sử dụng.

| File sửa trong lần khôi phục | Nội dung |
| --- | --- |
| server/src/main/java/quizgame/server/ServerApp.java | Khởi tạo StatisticsDao khi bật Server. |
| server/src/main/java/quizgame/server/net/QuizServer.java | Truyền StatisticsDao cho ClientHandler. |
| server/src/main/java/quizgame/server/net/ClientHandler.java | Phục hồi ba yêu cầu Thống kê cùng xử lý lỗi SQL. Các yêu cầu SET_CREATE, SET_UPDATE, SET_DELETE cùng hoạt động. |
| server/README.md | Ghi lại phạm vi khôi phục và kết quả kiểm tra. |

Kiểm tra hồi quy qua ServerApp và TCP thật với MySQL 8.0.45 tạm: lịch sử rỗng, top 50 kèm hạng 51, điểm và thứ hạng trận, quyền xem chi tiết, tạo/sửa/xóa bộ câu hỏi và tra cứu thống kê sau các thao tác đó đều đạt. Biên dịch Server bằng JDK 17 đạt. Maven package còn thiếu thư viện plugin trong cache ngoại tuyến.

Chạy thử bằng IntelliJ: đóng Client, dừng Server, Build > Rebuild Project, chạy Quiz Server rồi Client. Mở Xếp hạng và Lịch sử, nháy đúp trận cũ để xem chi tiết. Có thể tạo một bộ câu hỏi mới từ giao diện để thử phần quản lý câu hỏi đã tích hợp.
