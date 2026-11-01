# Database QUIZ

## Phạm vi

- Tên database: **`quiz_game`** (một chữ `z` trong `quiz`).
- `schema.sql` dành cho **cài mới**, MySQL **8.0.16 trở lên**, InnoDB, utf8mb4.
- Không phải migration. Nếu đã import bản cũ và có dữ liệu, cần viết migration riêng; không xóa database thật hoặc chạy lại file này để thay thế.
- Giữ nguyên class Java trong `common/model` và giao thức trong `docs/PROTOCOL.md`.
- Không có tài khoản mẫu/dữ liệu giả trong schema. Dữ liệu kiểm thử chỉ nằm trong MySQL tạm của test runner.

## Ánh xạ vào model hiện có

| Model / trường | Cách đọc / ghi |
|---|---|
| `UserInfo.username`, `totalWins` | `users.username`, `users.total_wins` |
| `UserInfo.status` | OnlineRegistry quản lý từ phiên socket; không đọc trạng thái online từ DB |
| `RoomState.code`, `name`, `host` | `rooms` JOIN `users` qua `host_id` |
| `RoomState.members` | `room_members` có `left_at IS NULL`, JOIN `users`; `ready` giữ nguyên |
| `RoomState.set`, `numQuestions`, `timeOverrideSeconds` | `rooms.set_id`, `num_questions`, `time_override_seconds` |
| `RoomState.phase` | `waiting` -> `WAITING`, `playing` -> `PLAYING`; phòng đã đóng không gửi thành room đang hoạt động |
| `RoomState.pendingInvitees` | Người nhận của mọi invitation `pending` trong phòng, kể cả đang xếp hàng |
| `Invite` | JOIN `invitations`, `users` người gửi, `rooms`; thời gian còn lại do server tính |
| `InviteResult` | Chuyển trạng thái lowercase ở DB sang enum uppercase; `cancelled` chỉ dùng nội bộ, không thêm enum mạng |
| `QuestionSet.name`, `owner` | `question_sets.title`, JOIN `users`; query danh sách luôn lọc `deleted_at IS NULL` |
| `QuestionDraft.type/content/timeSeconds` | `questions.type/prompt/time_limit_seconds` |
| `QuestionDraft.options/correctIndexes` | `question_options` sắp theo `option_index` 0..3; `is_correct` xác định đáp án |
| `QuestionDraft.acceptedAnswers` | `accepted_answers`; chuẩn hóa trim, gộp khoảng trắng, bỏ phân biệt hoa thường nhưng giữ dấu |
| `QuestionDraft.imageBase64` | Đọc ảnh theo `questions.image_ref` rồi encode; DB không chứa trực tiếp Base64 |
| `Answer.choices` | Đổi chỉ số lựa chọn sang `option_id` của đúng câu, lưu nhiều dòng trong `submission_options` |
| `Answer.text` | `submissions.answer_text`; TYPE không có dòng trong `submission_options` |
| `MatchRow` | `match_players.final_score/correct_count/finish_time_ms/player_status`, JOIN username; rank do server tính |
| `MatchSummary` | JOIN match, room, round, set, player; mỗi match có duy nhất một round/bộ câu hỏi |
| `LeaderboardRow.totalScore` | Tổng điểm từ các trận đã kết thúc; không cộng dữ liệu trận đang chạy |

## Quy ước trận / vòng

Mỗi `START_GAME` là một **match mới**. Một phòng có thể có nhiều match liên tiếp.
Giữ bảng `rounds` để không đảo cấu trúc gốc, nhưng mỗi match chỉ có một round (`round_number = 1`). Không dùng round số 2 để biểu diễn chơi lại: như vậy `matchId`, thắng/thua và lịch sử khớp model hiện tại.

Khi phát câu hỏi cho từng người, tạo slot trong `submissions`, lưu `question_sent_at` và `deadline_at` riêng cho người đó. Khi trả lời hoặc timeout, cập nhật slot. `submitted_at IS NULL` không tự phân biệt đang chờ và timeout; server sử dụng deadline cùng `is_correct` (NULL = chưa chấm, FALSE = sai/timeout). Với timeout, không có lựa chọn và điểm bằng 0.

Các cột `question_id`, `round_id`, `match_id` dư thừa có chủ đích để composite foreign key kiểm tra câu hỏi và người chơi thuộc đúng trận. Không đổi thành lấy ID bất kỳ do client gửi.

## Ràng buộc DB đã thêm

- Một user chỉ có một membership đang hoạt động (`left_at IS NULL`).
- Một phòng chỉ có một lời mời pending cho cùng người nhận; lịch sử rejected/expired không ngăn mời lại.
- Các phòng khác nhau vẫn được xếp hàng mời cùng người đang rảnh.
- Không tự mời chính mình; thời gian hết hạn phải sau lúc bắt đầu hiển thị.
- MULTI lưu nhiều option, không lặp option, không được chọn option của câu khác.
- Submission phải thuộc đúng câu, round, match và người tham gia match.
- Bộ câu hỏi có `deleted_at` để xóa mềm; chỉ công khai bộ đã lưu theo giao thức hiện tại.

## Trách nhiệm còn lại của backend

- Kiểm tra tối đa 5 thành viên, chủ phòng nằm trong membership, quyền chủ phòng và số câu không vượt số câu của bộ. Khóa phòng/transaction phải bao trùm thao tác kiểm tra và thêm người.
- Kiểm tra lại online/status của người gửi và người nhận ở cả lúc mời và lúc accept; unique index không thay thế việc kiểm tra trạng thái này.
- Ghi `delivered_at`, `expires_at = delivered_at + 5 giây` khi lời mời trở thành đầu hàng được gửi; queued invite có hai trường này NULL.
- Khi accept, cập nhật membership/status và kết thúc các lời mời còn lại nguyên tử; phát event sau khi state đã cập nhật.
- Khi trở lại cùng phòng sau khi rời, cập nhật membership cũ (`left_at = NULL`, `joined_at` mới, `ready = FALSE`), không INSERT lại khóa chính cũ.
- Sắp thứ tự vào phòng bằng `joined_at`, dùng thứ tự nội bộ server để phân xử nếu timestamp trùng.
- Khi server khởi động lại, dọn các phòng/membership/invite dang dở trước khi nhận client. Không coi hàng lưu trong DB là user online.
- Khi phòng trống, xóa khỏi danh sách phòng runtime; nếu đã có trận thì giữ hàng DB, đánh dấu đóng để bảo toàn FK/lịch sử. Mã phòng đang unique trên toàn bộ các hàng lưu.
- Không sửa tại chỗ câu hỏi/đáp án đã được dùng trong lịch sử: dùng phiên bản hoặc snapshot bất biến để lịch sử không đổi. Quy tắc này cần triển khai ở DAO/service.
- Đăng ký hiện chỉ nhận username/password: backend mặc định display_name bằng username; hash mật khẩu ở server, không đưa password_hash vào DTO mạng.
- Chỉ cập nhật total_wins sau khi hoàn tất trận và đúng một lần; hòa không cộng thắng.

## Kiểm thử độc lập trên Windows

Chạy trong PowerShell tại thư mục repo:

```powershell
python database/tests/test_schema.py --mysql-bin "C:/Program Files/MySQL/MySQL Server 8.0/bin"
```

Test runner tự tạo MySQL data directory dưới Hermes scratch, khởi động server tạm ở loopback với cổng trống, import schema và thử ràng buộc. Không dùng cấu hình/login của MySQL thật. Sau khi kết thúc, server tạm được dừng và thư mục tạm được dọn.
