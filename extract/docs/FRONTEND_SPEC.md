# Game giải đố online – Đặc tả frontend (module client)

## Bối cảnh
Bài tập lớn Lập trình mạng: 1 Server, nhiều Client, TCP socket. Tài liệu này mô tả CLIENT (giao diện + lớp mạng phía client).
Client nối mạng thật tới Server theo `docs/PROTOCOL.md`; KHÔNG có dữ liệu giả. Bố cục từng màn: `docs/UI_WIREFRAMES.md` và ảnh trong `docs/ui/`.

## Công nghệ (bắt buộc)
- Java 17, Maven multi-module: `common`, `server`, `client`. Package gốc: `quizgame`
- Java Swing + FlatLaf. Không dùng JavaFX, không dùng GUI builder (.form)
- Thư viện cho phép: FlatLaf, Gson (>= 2.10.1, hỗ trợ record), JUnit 5. Không thêm thư viện khác (đọc/ghi CSV tự viết bằng java.io)
- Toàn bộ chữ trên giao diện bằng tiếng Việt. Tên ứng dụng: hằng số `AppTheme.APP_NAME = "QUIZ"`

## Quy tắc kiến trúc
1. Giao diện chỉ nói chuyện với interface `GameClient`, không biết socket hay JSON. Lớp thật duy nhất là `SocketGameClient`.
2. Mọi lớp dữ liệu nằm ở module `common`, package `quizgame.common.model`, dùng Java record. Tên trường khớp từng chữ với `PROTOCOL.md` mục 3
   (Gson chuyển trực tiếp record <-> JSON).
3. Phương thức yêu cầu trả về `CompletableFuture`; sự kiện Server đẩy xuống đi qua `GameEventListener`.
   Lỗi Server trả (`ok:false`) làm future hoàn thành ngoại lệ `GameException(code, message)`.
4. Callback có thể chạy trên luồng đọc socket. Mọi cập nhật giao diện PHẢI bọc trong `SwingUtilities.invokeLater`.
5. 1 JFrame duy nhất (`MainFrame`): `NavBar` ở trên, `CardLayout` ở dưới. Mỗi màn là 1 JPanel trong `quizgame.client.ui.screen`, hộp thoại trong `ui.dialog`, thành phần dùng lại trong `ui.component` (xem cây thư mục trong CLAUDE.md).
6. Lỗi mạng hiện bằng `JOptionPane`, không được nuốt lỗi. Mất kết nối (`onDisconnected`): báo 'Mất kết nối tới Server' rồi về màn LOGIN.
7. Màu, font, khoảng cách nằm trong 1 lớp `AppTheme`. Phong cách (xem ảnh trong docs/ui): nền #F4F1FF, thanh điều hướng gradient
   #3B1FD1 -> #7C3AED -> #C026D3, màu chính #5B3DF5, nhấn vàng #FFC83D, đúng #22C55E, sai #EF4444, bo góc 12-18 px,
   thẻ trắng có viền trên màu, nút có viền dưới dày 3 px cho cảm giác nổi, tiêu đề IN HOA có gạch vàng cam bên dưới.
   Bốn nút đáp án Buttons có 4 màu cố định: A đỏ #EF4444 (▲), B xanh dương #3B82F6 (◆), C cam #F59E0B (●), D xanh lá #22C55E (■).
   Sau khi trả lời: đáp án đúng sáng rõ có viền xanh và dấu ✓, các nút còn lại mờ đi. Đổ bóng thẻ và nền gradient là tùy chọn.
8. Thành phần dùng lại phải tách riêng: `NavBar`, `OnlinePlayersPanel` (dùng ở HOME và ROOM), `QuestionSetCard`, `MatchResultTable`, `ImagePicker`.
9. Mọi danh sách phải có 3 trạng thái: đang tải ('Đang tải...'), rỗng (chữ hướng dẫn, ví dụ 'Chưa có bộ câu hỏi nào, bấm TẠO BỘ CÂU HỎI'),
   lỗi (thông báo + nút THỬ LẠI). Không có dữ liệu giả để lấp chỗ trống.

## Lớp dữ liệu
Định nghĩa đầy đủ (kiểu, ràng buộc) nằm ở `PROTOCOL.md` mục 3. Dưới đây là chữ ký Java cần tạo trong `quizgame.common.model`:
- enum UserStatus { IDLE, IN_ROOM, PLAYING }  // nhãn: 'Đang rảnh', 'Trong phòng', 'Đang chơi' (hàm label())
- record UserInfo(String username, int totalWins, UserStatus status)
- enum ChangeType { ADD, UPDATE, REMOVE }
- record RoomMember(String username, boolean ready)
- enum RoomPhase { WAITING, PLAYING }
- enum QuestionType { SINGLE, MULTI, TYPE }  // label(): 'Buttons', 'Checkboxes', 'Type answer'
- record QuestionDraft(QuestionType type, String content, List<String> options, List<Integer> correctIndexes,
  List<String> acceptedAnswers, int timeSeconds, String imageBase64)   // imageBase64 có thể null ở MỌI loại câu
- record QuestionSetSummary(long id, String name, String topic, String owner, int questionCount, List<QuestionType> types, boolean hasImages)
- record QuestionSet(long id, String name, String topic, String description, String owner, List<QuestionDraft> questions)
- record RoomState(String code, String name, String host, List<RoomMember> members, List<String> pendingInvitees,
  QuestionSetSummary set, int numQuestions, Integer timeOverrideSeconds, RoomPhase phase)
- record Invite(long id, String fromUser, String roomCode, String roomName, long expiresInMs)
- enum InviteResult { ACCEPTED, REJECTED, EXPIRED, JOINED_OTHER, ROOM_FULL }  // label() tiếng Việt cho người mời
- record GameStart(int numQuestions, Map<String, String> imagesBase64)
- record Question(int qIndex, int total, QuestionType type, String content, List<String> options, String imageId, long timeLimitMs)
- record Answer(List<Integer> choices, String text)
- record AnswerResult(int qIndex, boolean correct, List<Integer> correctIndexes, List<String> acceptedAnswers, int points, int totalScore)
- record ScoreRow(int rank, String username, int score, int currentQuestion, boolean finished)
- enum PlayerStatus { FINISHED, QUIT, DISCONNECTED }
- record MatchRow(int rank, String username, int score, int correctCount, long finishTimeMs, PlayerStatus status)
- record MatchResult(long matchId, String winner, boolean draw, List<MatchRow> rows)
- record LeaderboardRow(int rank, String username, int totalWins, int totalScore)
- record MatchSummary(long matchId, String startedAt, String roomName, String setName, int myRank, int myScore)  // startedAt: ISO-8601
- Chỉ client (quizgame.client.util): record ImportResult(List<QuestionDraft> questions, List<String> errors)
- Chỉ client: class GameException extends Exception { String code(); }

## Interface GameClient (quizgame.client.net)
Kết nối: connect(String host, int port) -> CompletableFuture<Void>; close()
Tài khoản: login(user, pass), register(user, pass), logout()  -> CompletableFuture<Void>
Phòng: createRoom(name) -> CompletableFuture<RoomState>; joinRoom(code) -> CompletableFuture<RoomState>; leaveRoom(); setReady(boolean);
  remindReady(); kick(username); configRoom(Long setId, int numQuestions, Integer timeOverrideSeconds); startGame()
Lời mời: invite(username); replyInvite(long inviteId, boolean accept) -> CompletableFuture<RoomState> (null nếu từ chối)
Chơi: answer(int qIndex, Answer answer); quitGame()
Bộ câu hỏi: listSets() -> List<QuestionSetSummary>; getSet(id) -> QuestionSet; createSet(QuestionSet) -> Long; updateSet(QuestionSet); deleteSet(long id)
Xem: getLeaderboard() -> List<LeaderboardRow>; getHistory() -> List<MatchSummary>; getHistoryDetail(long matchId) -> MatchResult
addListener(GameEventListener) / removeListener(GameEventListener)
Mỗi phương thức ứng với đúng 1 `type` trong PROTOCOL.md mục 4.

## Interface GameEventListener (mọi phương thức là default rỗng)
onOnlineList(List<UserInfo>), onUserUpdate(UserInfo, ChangeType), onInviteReceived(Invite), onInviteResult(String toUser, InviteResult),
onRoomState(RoomState), onReadyReminder(String fromUser), onKicked(), onGameStart(GameStart), onQuestion(Question),
onAnswerResult(AnswerResult), onScoreboard(List<ScoreRow>), onPlayerLeft(String username), onGameEnd(MatchResult), onDisconnected()
Ánh xạ từng sự kiện đẩy xuống (PROTOCOL.md mục 5) vào đúng phương thức ở trên.

## Luật nghiệp vụ giao diện cần thể hiện
- Phòng tối đa 5 người, mã phòng 6 ký tự do Server sinh. Chỉ mời được người 'Đang rảnh'; người khác hiện nút 'BẬN' (vô hiệu).
- Nút BẮT ĐẦU chỉ bật cho chủ phòng khi mọi người sẵn sàng, có >= 2 người và đã chọn bộ câu hỏi.
- Người đã sẵn sàng bấm nút lần nữa = nhắc người chưa sẵn sàng (remindReady), tối đa 1 lần/5 giây. Người bị nhắc thấy
  'Đồng đội của bạn nhắc bạn sẵn sàng'. Chủ phòng bấm vào 1 người để Kick (chỉ khi phase = WAITING).
- Lời mời hiện lần lượt từng cái, đếm ngược theo `expiresInMs`, hết giờ tự đóng. Người mời thấy kết quả.
- Mỗi câu có thời gian riêng. Chủ phòng có thể ghi đè 1 giá trị chung 5-60 giây; để trống = theo từng câu.
- Mỗi người chơi theo tốc độ riêng. SINGLE: bấm đáp án là gửi ngay, không đổi được. MULTI: tick nhiều ô rồi bấm XÁC NHẬN.
  TYPE: ô nhập + XÁC NHẬN (Enter cũng được). Đã xác nhận thì khóa. Đồng hồ đếm ngược chỉ để hiển thị; hết giờ thì chờ Server gửi câu mới.
- Bảng xếp hạng mini trên màn chơi: lấy từ SCOREBOARD, hiện mình và tối đa 2 người trên, 2 người dưới (chạm đầu/cuối thì dịch cửa sổ để đủ 5 dòng),
  tô đậm dòng của mình. Viết thành hàm tĩnh riêng, có unit test.
- Sau trận: màn KẾT QUẢ, rồi cả phòng về trạng thái chờ, mọi người bị tắt Sẵn sàng; ai muốn nghỉ thì RỜI PHÒNG.
- Hòa (nhiều người cùng điểm cao nhất): không ai thắng. Bộ câu hỏi luôn công khai; chỉ chủ bộ thấy nút SỬA/XÓA.
- Nút CHƠI trên thẻ bộ câu hỏi: hỏi tên phòng (mặc định 'Phòng của <tên>'), createRoom rồi configRoom với bộ đó, vào ROOM.
  Nút TẠO PHÒNG THI ĐẤU: createRoom, chưa chọn bộ, vào ROOM. Nút MỜI khi chưa ở trong phòng: tự createRoom (tên mặc định) rồi mới invite và vào ROOM.
- Trạng thái của chính mình ở HOME lấy từ USER_UPDATE về chính mình.

## Import bộ câu hỏi từ file (chạy hoàn toàn ở Client)
- 'Download Template' ghi file CSV UTF-8 có BOM (để Excel hiển thị đúng tiếng Việt), gồm hàng tiêu đề + 3 dòng ví dụ (mỗi loại 1 dòng).
- Cột theo thứ tự: Loại câu hỏi | Nội dung | Đáp án A | Đáp án B | Đáp án C | Đáp án D | Đáp án đúng | Thời gian (giây) | Đường dẫn/Tên file ảnh (nếu có)
- Loại câu hỏi: Buttons | Checkboxes | Type answer (không phân biệt hoa thường).
- Đáp án đúng: Buttons = 1 chữ (A-D); Checkboxes = nhiều chữ ngăn bằng dấu phẩy (A,C); Type answer = các đáp án chấp nhận ngăn bằng dấu gạch đứng (|),
  khi đó Đáp án A-D để trống.
- Thời gian trống = 20. Ảnh: tên file hoặc đường dẫn; tên file tìm trong cùng thư mục với file CSV; thu nhỏ rộng tối đa 800 px.
- Tự nhận dấu phân cách ',' hoặc ';' (Excel tiếng Việt thường lưu bằng ';'). Hỗ trợ ô trong dấu nháy kép và xuống dòng trong ô.
- Dòng sai (thiếu đáp án, sai loại, đáp án đúng ngoài A-D, ảnh không tìm thấy hoặc quá 1 MB sau khi thu nhỏ) bị bỏ qua và ghi vào
  ImportResult.errors dạng 'Dòng 7: thiếu đáp án đúng'. Các dòng đúng vẫn được thêm vào bộ đang soạn.

## Danh sách màn (CardLayout key)
LOGIN, HOME, ROOM, GAME, RESULT, SET_EDITOR, LEADERBOARD, HISTORY.
Hộp thoại: InviteDialog, SetPickerDialog, MatchDetailDialog.
