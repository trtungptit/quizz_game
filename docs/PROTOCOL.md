# Giao thức Client – Server (hợp đồng giữa frontend và backend)

Đây là tài liệu DUY NHẤT hai bên phải tuân theo. Frontend (module `client`) viết `SocketGameClient` đúng tài liệu này;
backend (module `server`) cài đúng tài liệu này. Sửa giao thức = sửa file này trước, rồi mới sửa code hai bên.

## 1. Đường truyền
- TCP, cổng mặc định 9090. Client mở 1 kết nối và giữ suốt phiên.
- Mỗi thông điệp = 4 byte số nguyên không dấu big-endian (độ dài N) + N byte JSON UTF-8. Tối đa N = 2 MB (2 097 152).
  Vượt quá thì bên nhận đóng kết nối. Java: `DataOutputStream.writeInt(n); write(bytes)` và `DataInputStream.readInt(); readFully(buf)`.
- Mã hóa JSON: Gson. Enum viết đúng tên hằng. Thời gian là chuỗi ISO-8601 (ví dụ `2026-10-25T09:41:00`). Mọi khoảng thời gian tính bằng mili giây
  (trường tên `...Ms`) hoặc giây (trường tên `...Seconds`). Ảnh là chuỗi Base64 (PNG hoặc JPG).

## 2. Khung thông điệp (envelope)
Yêu cầu (Client gửi):
```json
{ "type": "LOGIN", "reqId": 1, "data": { "username": "an", "password": "123456" } }
```
Phản hồi (Server gửi, cùng `type`, cùng `reqId`):
```json
{ "type": "LOGIN", "reqId": 1, "ok": true,  "data": { "username": "an", "totalWins": 12 } }
{ "type": "LOGIN", "reqId": 1, "ok": false, "error": { "code": "WRONG_CREDENTIALS", "message": "Sai tên đăng nhập hoặc mật khẩu" } }
```
Sự kiện đẩy (Server tự gửi, KHÔNG có `reqId` và `ok`):
```json
{ "type": "ONLINE_LIST", "data": [ { "username": "binh", "totalWins": 9, "status": "IDLE" } ] }
```
- `reqId` do Client đánh số tăng dần từ 1. Server trả nguyên giá trị đó.
- Phản hồi thành công mà không có dữ liệu thì `"data": null`.
- `error.message` là câu tiếng Việt để hiện thẳng cho người dùng. `error.code` để code xử lý.
- Server nhận `type` lạ hoặc `data` sai cấu trúc: trả `ok:false`, `code = BAD_REQUEST`, không đóng kết nối.
- Yêu cầu nào ngoài `REGISTER`, `LOGIN`, `PING` mà chưa đăng nhập: `ok:false`, `code = NOT_LOGGED_IN`.

## 3. Kiểu dữ liệu dùng chung (JSON đúng tên trường như record Java trong quizgame.common.model)
```text
UserStatus     : IDLE | IN_ROOM | PLAYING
UserInfo       : { username, totalWins, status }
ChangeType     : ADD | UPDATE | REMOVE
RoomMember     : { username, ready }
RoomPhase      : WAITING | PLAYING
QuestionType   : SINGLE | MULTI | TYPE
QuestionDraft  : { type, content, options[], correctIndexes[], acceptedAnswers[], timeSeconds, imageBase64|null }
                 SINGLE: options đúng 4, correctIndexes đúng 1 phần tử (0-3)
                 MULTI : options đúng 4, correctIndexes >= 1 phần tử, không trùng
                 TYPE  : options rỗng, acceptedAnswers >= 1 phần tử không rỗng
                 timeSeconds 5-120. content không rỗng, tối đa 500 ký tự. Mỗi option/đáp án tối đa 200 ký tự.
QuestionSetSummary : { id, name, topic, owner, questionCount, types[], hasImages }
QuestionSet    : { id, name, topic, description, owner, questions[QuestionDraft] }   (id = 0 khi tạo mới)
RoomState      : { code, name, host, members[RoomMember], pendingInvitees[], set: QuestionSetSummary|null,
                   numQuestions, timeOverrideSeconds|null, phase }
Invite         : { id, fromUser, roomCode, roomName, expiresInMs }
InviteResult   : ACCEPTED | REJECTED | EXPIRED | JOINED_OTHER | ROOM_FULL
GameStart      : { numQuestions, imagesBase64: { "<imageId>": "<base64>" } }
Question       : { qIndex, total, type, content, options[], imageId|null, timeLimitMs }   (KHÔNG có đáp án đúng)
Answer         : { choices[], text }       SINGLE/MULTI dùng choices (chỉ số 0-3), TYPE dùng text
AnswerResult   : { qIndex, correct, correctIndexes[], acceptedAnswers[], points, totalScore }
ScoreRow       : { rank, username, score, currentQuestion, finished }
PlayerStatus   : FINISHED | QUIT | DISCONNECTED
MatchRow       : { rank, username, score, correctCount, finishTimeMs, status }
MatchResult    : { matchId, winner|null, draw, rows[MatchRow] }
LeaderboardRow : { rank, username, totalWins, totalScore }
MatchSummary   : { matchId, startedAt, roomName, setName, myRank, myScore }
```

## 4. Yêu cầu của Client và phản hồi
Cột "Lỗi" liệt kê các `error.code` có thể gặp. Mọi yêu cầu đều có thể trả `BAD_REQUEST`, `NOT_LOGGED_IN`, `INTERNAL`.

### 4.1 Tài khoản
| type | data gửi | data phản hồi | Lỗi |
| --- | --- | --- | --- |
| REGISTER | { username, password } | null | USERNAME_TAKEN, INVALID_INPUT |
| LOGIN | { username, password } | { username, totalWins } | WRONG_CREDENTIALS, ALREADY_ONLINE |
| LOGOUT | null | null | |
| PING | null | null (type phản hồi là PONG) | |
- username: 3-20 ký tự chữ, số hoặc `_`, không phân biệt hoa thường khi kiểm tra trùng. password: 6-64 ký tự.
- Sau `LOGIN` thành công Server đẩy ngay `ONLINE_LIST`, và đẩy `USER_UPDATE {ADD}` cho những người khác.

### 4.2 Phòng
| type | data gửi | data phản hồi | Lỗi |
| --- | --- | --- | --- |
| CREATE_ROOM | { name } | RoomState | ALREADY_IN_ROOM, INVALID_INPUT |
| JOIN_ROOM | { code } | RoomState | ROOM_NOT_FOUND, ROOM_FULL, ROOM_PLAYING, ALREADY_IN_ROOM |
| LEAVE_ROOM | null | null | NOT_IN_ROOM |
| SET_READY | { ready } | null | NOT_IN_ROOM, ROOM_PLAYING |
| REMIND_READY | null | null | NOT_IN_ROOM, TOO_FAST (quá 1 lần / 5 giây), NOT_READY_YOURSELF |
| KICK | { username } | null | NOT_HOST, ROOM_PLAYING, USER_NOT_IN_ROOM |
| ROOM_CONFIG | { setId\|null, numQuestions, timeOverrideSeconds\|null } | null | NOT_HOST, SET_NOT_FOUND, INVALID_INPUT |
| START_GAME | null | null | NOT_HOST, NO_SET, NOT_ENOUGH_PLAYERS (< 2), NOT_ALL_READY |
- Mã phòng: 6 ký tự chữ HOA và số, bỏ O, 0, I, 1. Tối đa 5 thành viên. Tên phòng 1-30 ký tự.
- `numQuestions` từ 1 đến số câu của bộ; `timeOverrideSeconds` null hoặc 5-60.
- Sau mỗi thay đổi của phòng (vào, ra, sẵn sàng, cấu hình, lời mời đang chờ, kết thúc trận) Server đẩy `ROOM_STATE` cho MỌI thành viên.
- Chủ phòng rời: quyền chủ chuyển cho người vào sớm nhất còn lại; phòng trống thì xóa.

### 4.3 Lời mời
| type | data gửi | data phản hồi | Lỗi |
| --- | --- | --- | --- |
| INVITE | { username } | null | NOT_IN_ROOM, ROOM_FULL, USER_NOT_FOUND, USER_BUSY, ALREADY_INVITED |
| INVITE_REPLY | { inviteId, accept } | RoomState nếu accept = true, ngược lại null | INVITE_EXPIRED, ROOM_FULL, ROOM_PLAYING |
- Chỉ mời được người `IDLE`. Server giữ hàng đợi lời mời cho mỗi người: lời mời đầu hàng được đẩy `INVITE_RECEIVED` và bắt đầu đếm 5000 ms
  từ lúc đẩy; hết hạn hoặc được trả lời thì đẩy lời mời kế tiếp. Chấp nhận 1 lời thì các lời còn lại bị hủy.
- Người mời nhận `INVITE_RESULT` cho mỗi kết cục. Tên người mời đang chờ nằm trong `RoomState.pendingInvitees` đến khi có kết cục.

### 4.4 Chơi
| type | data gửi | data phản hồi | Lỗi |
| --- | --- | --- | --- |
| ANSWER | { qIndex, answer: Answer } | null | WRONG_QUESTION, ALREADY_ANSWERED, TIME_UP, NOT_PLAYING |
| QUIT_GAME | null | null | NOT_PLAYING |
- Kết quả chấm không nằm trong phản hồi mà được đẩy bằng `ANSWER_RESULT` ngay sau đó.
- Server chỉ nhận đáp án ĐẦU TIÊN cho mỗi câu. Đáp án đến muộn quá `timeLimitMs` + 500 ms thì `TIME_UP`.

### 4.5 Bộ câu hỏi
| type | data gửi | data phản hồi | Lỗi |
| --- | --- | --- | --- |
| SET_LIST | null | QuestionSetSummary[] | |
| SET_GET | { id } | QuestionSet | SET_NOT_FOUND |
| SET_CREATE | { set: QuestionSet } | { id } | INVALID_SET (message nêu rõ câu số mấy sai gì) |
| SET_UPDATE | { set: QuestionSet } | null | SET_NOT_FOUND, NOT_OWNER, SET_IN_USE, INVALID_SET |
| SET_DELETE | { id } | null | SET_NOT_FOUND, NOT_OWNER, SET_IN_USE |
- Bộ câu hỏi luôn công khai. `SET_IN_USE` = đang dùng trong một trận chưa kết thúc. Xóa là xóa mềm (lịch sử vẫn hiện đúng tên bộ).
- Server kiểm tra lại toàn bộ quy tắc của `QuestionDraft`, ảnh sau giải mã tối đa 1 MB.

### 4.6 Xem
| type | data gửi | data phản hồi |
| --- | --- | --- |
| LEADERBOARD | null | LeaderboardRow[] : tối đa 50 dòng đầu; nếu người hỏi ngoài top 50 thì thêm dòng của họ ở cuối (rank thật) |
| HISTORY | null | MatchSummary[] của chính người hỏi, mới nhất trước, tối đa 100 |
| HISTORY_DETAIL | { matchId } | MatchResult (lỗi MATCH_NOT_FOUND) |
- Xếp hạng: tổng số trận thắng giảm dần, bằng nhau thì tổng điểm giảm dần, rồi tên tăng dần.

## 5. Sự kiện Server đẩy xuống
| type | data | Khi nào |
| --- | --- | --- |
| ONLINE_LIST | UserInfo[] (không gồm chính người nhận) | Ngay sau LOGIN |
| USER_UPDATE | { user: UserInfo, change: ChangeType } | Có người vào, ra, đổi trạng thái hoặc đổi số trận thắng (kể cả chính người nhận: change = UPDATE) |
| INVITE_RECEIVED | Invite | Lời mời đến lượt hiển thị |
| INVITE_RESULT | { toUser, result: InviteResult } | Cho người mời |
| ROOM_STATE | RoomState | Mọi thay đổi của phòng, cho mọi thành viên |
| READY_REMINDER | { fromUser } | Cho những người chưa sẵn sàng khi có người nhắc |
| KICKED | null | Cho người bị kick (Server đã xóa họ khỏi phòng) |
| GAME_START | GameStart | Trận bắt đầu, mọi thành viên |
| QUESTION | Question | Câu tiếp theo của TỪNG người (mỗi người một tốc độ) |
| ANSWER_RESULT | AnswerResult | Sau khi trả lời hoặc khi hết giờ (points 0, correct false) |
| SCOREBOARD | ScoreRow[] (đủ mọi người trong trận, đã xếp hạng) | Mỗi khi có người xong 1 câu |
| PLAYER_LEFT | { username } | Có người thoát hoặc rớt mạng giữa trận |
| GAME_END | MatchResult | Mọi người còn lại đã xong; Server đưa phòng về WAITING, tắt sẵn sàng của mọi người |
- Client tự lấy cửa sổ 5 dòng quanh mình từ `SCOREBOARD`.

## 6. Luồng một trận
1. Chủ phòng `START_GAME` -> Server đẩy `GAME_START` rồi ngay sau đó `QUESTION` (qIndex 0) cho mỗi người. Server ghi mốc giờ gửi cho từng người.
2. Người chơi `ANSWER` -> Server chấm, đẩy `ANSWER_RESULT` cho người đó, đẩy `SCOREBOARD` cho cả phòng.
3. Khoảng 1500 ms sau `ANSWER_RESULT`, Server đẩy `QUESTION` kế tiếp cho người đó. Hết giờ mà chưa trả lời: Server tự đẩy `ANSWER_RESULT` (points 0) rồi tiếp tục như trên.
4. Sau `ANSWER_RESULT` của câu cuối, người đó không nhận thêm `QUESTION` (Client hiện 'Đã xong, đang chờ người khác').
5. Mọi người xong hoặc rời: Server ghi CSDL, đẩy `GAME_END` rồi `ROOM_STATE` (phase WAITING) và `USER_UPDATE` về số trận thắng.
- Điểm một câu đúng: `round(500 + 500 * (1 - t / T))`, t = thời gian Server đo từ lúc gửi QUESTION đến lúc nhận ANSWER (tối đa T), T = timeLimitMs / 1000.
  Sai hoặc không trả lời: 0.
- SINGLE đúng khi `choices` đúng 1 phần tử bằng đáp án. MULTI đúng khi tập `choices` trùng khít `correctIndexes`. TYPE đúng khi `text` sau khi
  cắt khoảng trắng đầu cuối, gộp nhiều khoảng trắng thành một và bỏ phân biệt hoa thường bằng một trong `acceptedAnswers` (làm cùng cách với đáp án lưu).
- Thắng: điểm cao nhất; nhiều người cùng điểm cao nhất = hòa, `winner = null`, không ai được cộng trận thắng.
  Người thoát hoặc rớt mạng giữa trận bị xếp cuối, thua; nếu chỉ còn 1 người ở lại thì người đó thắng.
- `timeLimitMs` của câu = `timeOverrideSeconds * 1000` nếu phòng có ghi đè, ngược lại `timeSeconds * 1000` của câu.

## 7. Giữ kết nối
- Client gửi `PING` mỗi 5 giây. Server đặt `setSoTimeout(15000)`; 15 giây không nhận gì thì đóng kết nối và dọn dẹp như người này thoát.
- Khi kết nối đóng, Server: bỏ khỏi danh sách online, rời phòng, nếu đang chơi thì coi như `DISCONNECTED`, đẩy `USER_UPDATE {REMOVE}`.

## 8. Việc backend cần làm (tóm tắt)
Cổng TCP, lớp đóng khung, `ClientHandler` mỗi kết nối, `OnlineRegistry`, `RoomManager` (mọi thay đổi trong `synchronized(room)`),
`InviteManager`, `GameSession` với bộ hẹn giờ riêng từng người, `Scoring`, tầng truy cập MySQL, lưu ảnh ra thư mục `images/`.
Chi tiết kiến trúc xem tài liệu Đặc tả Game giải đố online.
