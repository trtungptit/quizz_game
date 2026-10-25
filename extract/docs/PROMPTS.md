# Trình tự làm frontend: các prompt cho Claude Code

Cách dùng: dán MỖI LẦN MỘT prompt vào Claude Code (nên bật chế độ lập kế hoạch: đọc kế hoạch, sửa nếu sai rồi mới cho viết code). Xong mỗi bước thì build,
chạy, kiểm tra theo cột 'Đạt khi' ở cuối file, `git commit`, rồi mới sang bước sau. Ở các bước màn hình (P4 đến P10), đính kèm ảnh của màn đó
(gõ đường dẫn `docs/ui/<tên file>` vào prompt hoặc kéo thả ảnh vào khung chat); chỉ đính kèm ảnh của màn đang làm.

Không có dữ liệu giả. Đến khi backend chạy được, các màn chỉ kiểm tra được: build xanh, mở được màn, hiện đúng trạng thái rỗng và thông báo lỗi.
Khi backend chạy, quay lại bấm thử theo bảng kiểm tra.

---

## P1. Tạo project
```text
Đọc CLAUDE.md và docs/FRONTEND_SPEC.md trước khi làm.
Tạo project Maven multi-module theo spec: pom cha + 3 module common, server, client, Java 17, package gốc quizgame.
Module client phụ thuộc common, có FlatLaf, Gson (2.10.1 trở lên), JUnit 5 và exec-maven-plugin để chạy bằng `mvn -q -pl client -am exec:java`.
Module server tạm thời chỉ có lớp ServerMain in ra 'Server chưa làm'. Module common phụ thuộc Gson.
Trong client tạo:
- ClientMain: bật FlatLightLaf, mở MainFrame trên EDT bằng SwingUtilities.invokeLater.
- MainFrame (quizgame.client.ui): JFrame 1100x720, CardLayout, phương thức showScreen(String key) với các key trong spec;
  mỗi key tạm thời là 1 JPanel chỉ có JLabel ghi tên màn.
Thêm .gitignore cho Maven và IDE. Lập kế hoạch trước, chờ tôi duyệt rồi mới viết code. Xong cho tôi lệnh build và lệnh chạy.
```

## P2. Lớp dữ liệu
```text
Đọc CLAUDE.md, docs/FRONTEND_SPEC.md (mục 'Lớp dữ liệu') và docs/PROTOCOL.md (mục 3).
Tạo đúng các enum và record đó trong module common, package quizgame.common.model, đúng tên và thứ tự trường. Không thêm, bớt hay đổi tên trường.
Mỗi lớp một file, có Javadoc 1 dòng tiếng Việt.
Thêm label() cho UserStatus ('Đang rảnh', 'Trong phòng', 'Đang chơi'), InviteResult (câu thông báo tiếng Việt cho người mời)
và QuestionType ('Buttons', 'Checkboxes', 'Type answer').
Viết unit test: dùng Gson chuyển từng record sang JSON rồi ngược lại cho kết quả bằng nhau, và đọc được các ví dụ JSON trong PROTOCOL.md.
Chạy `mvn -q test`.
```

## P3. Lớp mạng thật (SocketGameClient)
```text
Đọc CLAUDE.md, docs/FRONTEND_SPEC.md và docs/PROTOCOL.md. KHÔNG tạo dữ liệu giả hay lớp Fake.
1. Trong common: lớp FramedIO (ghi/đọc thông điệp có 4 byte độ dài big-endian + JSON UTF-8, tối đa 2 MB, vượt thì ném ProtocolException)
   và record Envelope(String type, Long reqId, Boolean ok, JsonElement data, ErrorInfo error) cùng ErrorInfo(String code, String message).
2. Trong client.net: interface GameClient, GameEventListener, class GameException(code, message), đúng spec.
3. SocketGameClient implements GameClient:
   - connect(host, port): mở Socket với thời gian chờ kết nối 5 giây, chạy 1 luồng đọc daemon, 1 luồng PING mỗi 5 giây.
   - Mỗi yêu cầu: tăng AtomicLong reqId, lưu CompletableFuture trong ConcurrentHashMap, gửi (phương thức gửi có synchronized).
     Phản hồi ok:true hoàn thành future (đổi data sang kiểu đích bằng Gson); ok:false hoàn thành bằng GameException(code, message).
   - Thông điệp không có reqId là sự kiện đẩy: đổi theo bảng PROTOCOL.md mục 5 rồi gọi đúng phương thức của mọi listener (CopyOnWriteArrayList).
   - Mất kết nối (EOF hoặc ngoại lệ): hoàn thành mọi future đang chờ bằng lỗi 'Mất kết nối tới Server', đóng socket, gọi onDisconnected đúng 1 lần.
   - Mỗi phương thức của GameClient ứng với đúng 1 type trong PROTOCOL.md mục 4, dùng đúng tên trường.
4. Test JUnit (được dùng ServerSocket cục bộ NGAY TRONG src/test làm server thử): FramedIO đi-về, thông điệp dài 1 MB, vượt 2 MB, khung bị cắt giữa chừng;
   SocketGameClient: ghép đúng phản hồi theo reqId khi 2 yêu cầu chạy chồng nhau, ok:false thành GameException, sự kiện đẩy tới đúng listener,
   đóng kết nối thì onDisconnected gọi đúng 1 lần.
5. GameClient tạo 1 lần trong ClientMain và truyền vào MainFrame (không dùng biến static toàn cục).
Giải thích ngắn cho tôi: vì sao TCP cần đóng khung thông điệp, và vì sao phải ghép phản hồi theo reqId.
```

## P4. Khung ứng dụng và đăng nhập  (ảnh: 01-dang-nhap.png)
```text
Đọc CLAUDE.md, docs/FRONTEND_SPEC.md và docs/UI_WIREFRAMES.md. Làm đúng bố cục mục LOGIN và NAVBAR theo ảnh docs/ui/01-dang-nhap.png.
1. AppTheme (quizgame.client.ui): bảng màu theo Quy tắc kiến trúc số 7, font, khoảng cách, hằng số APP_NAME = 'QUIZ';
   hàm cài FlatLaf + UIManager cho phong cách trong spec (bo góc FlatLaf: Component.arc = 16, Button.arc = 12).
   Gradient của thanh điều hướng vẽ bằng paintComponent + GradientPaint.
2. NavBar theo mục NAVBAR. MainFrame = NavBar ở NORTH + CardLayout ở CENTER; showScreen(key) ẩn NavBar ở LOGIN và GAME.
3. LoginPanel theo mục LOGIN: 2 tab Đăng nhập / Đăng ký, ô Máy chủ (host + cổng, mặc định localhost và 9090, nhớ lần trước bằng java.util.prefs).
   - Bấm nút: connect(host, port) rồi login hoặc register. Không kết nối được: 'Không kết nối được tới Server <host>:<cổng>'.
   - Đăng ký: nhập lại mật khẩu; tên 3-20 ký tự chữ/số/_, mật khẩu 6-64 ký tự, 2 mật khẩu khớp (kiểm tra ở Client trước khi gửi). Đăng ký xong tự đăng nhập.
   - Lúc chờ phản hồi: khóa nút, hiện 'Đang kết nối...'. Lỗi Server hiện dòng chữ đỏ dưới form (dùng error.message).
   - Đăng nhập thành công: MainFrame lưu tên người dùng và totalWins hiện tại, chuyển HOME. Đăng xuất: logout() rồi về LOGIN.
   - onDisconnected ở bất kỳ màn nào: báo 'Mất kết nối tới Server' rồi về LOGIN.
4. Các màn còn lại tạm là JPanel có JLabel ghi tên màn (để các bước sau thay).
```

## P5. Trang chủ và danh sách online  (ảnh: 02-trang-chu.png)
```text
Đọc CLAUDE.md, docs/FRONTEND_SPEC.md và docs/UI_WIREFRAMES.md. Làm đúng bố cục mục HOME theo ảnh docs/ui/02-trang-chu.png. Dữ liệu thật qua GameClient.
HomePanel:
- Trái: tiêu đề KHÁM PHÁ BỘ CÂU HỎI, ô tìm (lọc ngay khi gõ theo tên và chủ đề, không phân biệt hoa thường), nút TẠO BỘ CÂU HỎI (sang SET_EDITOR),
  checkbox 'Chỉ bộ của tôi', lưới QuestionSetCard 2 cột trong JScrollPane, nút lớn TẠO PHÒNG THI ĐẤU ở cuối. Dữ liệu từ listSets() mỗi lần vào màn.
  3 trạng thái: Đang tải / rỗng ('Chưa có bộ câu hỏi nào, bấm TẠO BỘ CÂU HỎI') / lỗi + THỬ LẠI.
- QuestionSetCard (lớp riêng): tên IN HOA, 'N câu • <các loại>' (Buttons/Checkboxes/Type answer, thêm 'Hình ảnh' nếu hasImages), 'Chủ đề: ...',
  'Chia sẻ: Công khai', nút CHƠI; SỬA và XÓA chỉ hiện khi owner là mình (XÓA hỏi xác nhận rồi deleteSet; lỗi SET_IN_USE hiện message).
- CHƠI: hỏi tên phòng (mặc định 'Phòng của <tên>'), createRoom rồi configRoom với bộ đó, chuyển ROOM.
  TẠO PHÒNG THI ĐẤU: hỏi tên phòng, createRoom (chưa chọn bộ), chuyển ROOM.
- Phải: khung gradient '<tên> • <TRẠNG THÁI>' + 'Tổng số trận thắng: n' (cập nhật khi onUserUpdate về chính mình), bên dưới là OnlinePlayersPanel.
OnlinePlayersPanel (component riêng, dùng lại ở ROOM, tiêu đề truyền vào):
- Ô tìm người chơi; mỗi dòng: avatar tròn, tên đậm, 'n trận thắng • <trạng thái>', nút MỜI nếu 'Đang rảnh', ngược lại nút BẬN vô hiệu. Không hiện chính mình.
  Chân: 'Online: x • Rảnh: y'. Danh sách rỗng: 'Chưa có ai khác online'.
- Thời gian thực: onOnlineList thay cả danh sách; onUserUpdate chỉ thêm/sửa/xóa đúng 1 dòng, giữ vị trí cuộn và nội dung ô tìm.
- Bấm MỜI: invite(username), nút đổi thành 'ĐÃ MỜI' (vô hiệu) tới khi có onInviteResult. Nếu mình chưa ở trong phòng: createRoom (tên mặc định) rồi invite và chuyển ROOM.
Panel đăng ký listener khi hiện và hủy đăng ký khi ẩn hoặc đăng xuất.
```

## P6. Phòng và lời mời  (ảnh: 03-phong.png, 04-hop-thoai-loi-moi.png, 05-hop-thoai-chon-bo-cau-hoi.png)
```text
Đọc CLAUDE.md, docs/FRONTEND_SPEC.md và docs/UI_WIREFRAMES.md. Theo ảnh docs/ui/03-phong.png, 04-hop-thoai-loi-moi.png, 05-hop-thoai-chon-bo-cau-hoi.png.
1. RoomPanel theo mục ROOM, vẽ lại toàn bộ từ RoomState mỗi khi có onRoomState:
   - Trái: 'PHÒNG: <tên>'; ô Mã phòng (nút Sao chép) và ô Chủ phòng.
   - 'NGƯỜI CHƠI (x/5)': 5 dòng, dòng trống ghi '(Trống)'; mỗi dòng có tên, huy hiệu CHỦ PHÒNG, Sẵn sàng/Chưa. Chủ phòng thấy nút KICK trên dòng người khác
     (hỏi xác nhận), chỉ khi phase = WAITING.
   - 'BỘ CÂU HỎI ĐÃ CHỌN': khung 'tên • N câu • các loại' + nút CHỌN LẠI BỘ CÂU HỎI (chỉ chủ phòng) mở SetPickerDialog (bảng + ô tìm, nút CHỌN/HỦY, dùng listSets).
     Chưa chọn thì ghi 'Chưa chọn bộ câu hỏi'.
   - Dòng cấu hình (chỉ chủ phòng sửa, người khác chỉ xem): Số câu (JSpinner, tối đa bằng số câu của bộ); Thời gian: radio 'Theo từng câu' hoặc
     'Mỗi câu [5-60] giây'. Mọi thay đổi gọi configRoom.
   - 'TRẠNG THÁI': nhật ký cuộn, dòng mới ở trên, tự dựng từ onRoomState (ai vào/rời/đổi sẵn sàng; pendingInvitees -> 'Đã mời: X • Đang chờ phản hồi...'),
     onInviteResult (InviteResult.label()), onReadyReminder.
   - Hàng nút: RỜI PHÒNG | SẴN SÀNG | BẮT ĐẦU. Lần đầu bấm = setReady(true); đã sẵn sàng mà còn người chưa sẵn sàng thì nút đổi thành 'NHẮC MỌI NGƯỜI'
     gọi remindReady() (khóa 5 giây mỗi lần); có nút nhỏ Hủy sẵn sàng. BẮT ĐẦU chỉ hiện với chủ phòng, chỉ bật khi đủ điều kiện trong spec.
   - Phải: OnlinePlayersPanel (dùng lại từ HOME) tiêu đề 'MỜI ĐỐI THỦ ONLINE'.
   - onReadyReminder: hiện 'Đồng đội của bạn nhắc bạn sẵn sàng' và nhấp nháy nút SẴN SÀNG. onKicked: thông báo rồi về HOME. RỜI PHÒNG: leaveRoom() rồi về HOME.
     onGameStart: sang GAME.
2. InviteDialog (JDialog không modal, do MainFrame quản lý, hoạt động ở mọi màn trừ GAME) theo mục INVITE DIALOG:
   - Nhiều lời mời thì xếp hàng, hiện từng cái, tiêu đề '(1/3)'. JProgressBar đếm ngược theo expiresInMs (javax.swing.Timer).
   - Hết giờ tự đóng và hiện lời mời tiếp. ĐỒNG Ý: replyInvite(id, true), xóa hàng đợi, sang ROOM. TỪ CHỐI: replyInvite(id, false).
```

## P7. Màn chơi  (ảnh: 06-man-choi-buttons.png, 07-man-choi-checkboxes.png, 08-man-choi-type-answer.png)
```text
Đọc CLAUDE.md, docs/FRONTEND_SPEC.md, docs/UI_WIREFRAMES.md và docs/PROTOCOL.md (mục 5 và 6). Theo ảnh docs/ui/06, 07, 08.
GamePanel theo mục GAME (không có NavBar). Vào màn khi onGameStart: giải mã imagesBase64 thành Map<String, ImageIcon> để dùng lại.
- Thanh trên: 'CÂU x/N', thanh đồng hồ đếm ngược + số giây theo timeLimitMs của từng câu (đỏ khi còn dưới 3 giây), 'ĐIỂM: n',
  nút THOÁT (hỏi xác nhận 'Thoát sẽ bị xử thua') gọi quitGame() rồi về HOME.
- Giữa: nội dung câu hỏi (JTextArea tự xuống dòng, không sửa), ảnh nếu có imageId (co vừa khung, giữ tỉ lệ), rồi vùng trả lời theo type:
  * SINGLE: 4 nút lớn A/B/C/D đúng 4 màu cố định trong spec. Bấm là gọi answer() và khóa cả 4 nút ngay.
  * MULTI: 4 dòng có ô tick + nút XÁC NHẬN (không tick gì thì không gửi được). Gửi xong khóa.
  * TYPE: ô nhập + nút XÁC NHẬN (Enter cũng được), ô trống không gửi. Gửi xong khóa.
- Khi có onAnswerResult: SINGLE sáng đáp án đúng (viền xanh, ✓), làm mờ các nút còn lại, nút mình chọn sai tô đỏ; MULTI tô xanh các ô đúng và đỏ ô chọn sai;
  TYPE hiện ĐÚNG/SAI và các đáp án chấp nhận. Hiện '+<points>' hoặc 'SAI'. Cập nhật 'ĐIỂM' bằng totalScore.
- onQuestion mới: thay câu ngay, mở khóa, đặt lại đồng hồ. Đồng hồ chỉ để hiển thị; hết giờ thì chờ Server gửi câu mới, KHÔNG tự gửi gì.
  Sau câu cuối: hiện 'Đã xong, đang chờ người khác'.
- Phải: bảng XẾP HẠNG từ onScoreboard, cột Hạng | Tên | Điểm | Câu. Lấy cửa sổ quanh mình (2 trên, 2 dưới, chạm đầu/cuối thì dịch để đủ 5 dòng, ít hơn 5 người thì hiện hết),
  viết thành hàm tĩnh riêng có unit test. Tô đậm dòng của mình.
- onPlayerLeft: dòng thông báo nhỏ '<tên> đã rời trận'. onGameEnd: sang RESULT.
Dừng mọi javax.swing.Timer khi rời màn.
```

## P8. Kết quả trận  (ảnh: 09-ket-qua.png)
```text
Đọc CLAUDE.md, docs/FRONTEND_SPEC.md và docs/UI_WIREFRAMES.md. Theo ảnh docs/ui/09-ket-qua.png.
ResultPanel theo mục RESULT, dựng từ MatchResult (NavBar hiện lại):
- Biểu ngữ vàng: '<tên> THẮNG!', 'BẠN THẮNG!' nếu là mình, hoặc 'HÒA, KHÔNG AI ĐƯỢC TÍNH THẮNG'.
- MatchResultTable (component riêng, dùng lại ở MatchDetailDialog của P10): Hạng | Người chơi | Điểm | Số câu đúng | Thời gian giải xong (mm:ss) | Ghi chú
  (Bỏ cuộc / Mất kết nối). Tô vàng dòng của mình. Hàm định dạng thời gian ở quizgame.client.ui.Format (có unit test).
- Nút VỀ PHÒNG CHỜ (sang ROOM; phòng đã về WAITING, mọi người chưa sẵn sàng) và RỜI PHÒNG (leaveRoom() rồi về HOME).
```

## P9. Soạn bộ câu hỏi  (ảnh: 10-soan-bo-buoc-1.png, 11-soan-buttons.png, 12-soan-checkboxes.png, 13-soan-type-answer.png)
```text
Đọc CLAUDE.md, docs/FRONTEND_SPEC.md, docs/UI_WIREFRAMES.md và docs/PROTOCOL.md (mục 3 và 4.5). Theo ảnh docs/ui/10, 11, 12, 13.
SetEditorPanel theo mục SET_EDITOR (TẠO BỘ CÂU HỎI từ HOME vào đây; SỬA thì getSet(id) rồi nạp vào). CHƯA làm Download Template và Import (để P9b),
nhưng hai nút đó vẫn phải có trên giao diện, bấm thì báo 'Sắp có'.
- Giữ bộ đang soạn trong List<QuestionDraft> (bản nháp, chưa lưu tới khi bấm HOÀN THÀNH).
- Bước 1 (chưa có câu): Tên, Chủ đề, Mô tả + 5 ô lớn: Buttons ('Câu hỏi 1 đáp án'), Checkboxes ('Câu hỏi nhiều đáp án'), Type answer ('Thuật ngữ, từ khóa'),
  Download Template, Import file. Bấm 1 trong 3 loại đầu thì thêm câu đầu tiên và sang Bước 2.
- Bước 2: Tên/Chủ đề/Mô tả thu gọn trên 1 dòng; QuestionEditor đổi theo type của câu đang sửa:
  * Chung: 'Câu hỏi' (JTextArea), 'Thời gian' (JSpinner 5-120, mặc định 20), ImagePicker bên trái (component riêng: bấm 'TẢI HÌNH ẢNH' mở JFileChooser PNG/JPG,
    thu nhỏ về rộng tối đa 800 px, xem trước, có nút gỡ ảnh, lưu Base64, báo lỗi nếu sau khi thu nhỏ vẫn > 1 MB). Ảnh dùng được cho CẢ 3 loại.
  * Buttons: 4 ô A-D, radio chọn đáp án đúng, ô đúng tô nền xanh.
  * Checkboxes: 4 ô A-D, mỗi ô có nút Đ/S (Đ = đúng), tối thiểu 1 ô đúng.
  * Type answer: danh sách 'Đáp án' (ít nhất 1 dòng) + nút 'Thêm các câu trả lời được chấp nhận', mỗi dòng có nút xóa.
- Thanh dưới: HOÀN THÀNH | các số 1,2,3... (bấm để chuyển câu; câu đang sửa nền tím, thiếu dữ liệu gạch đỏ dưới số, đủ dữ liệu gạch xanh; cuộn ngang khi nhiều câu)
  | nút '+' mở menu 3 loại để thêm câu. Nút 'Xóa câu này' ở góc trên.
- HOÀN THÀNH: kiểm tra tên bộ không trống, có ít nhất 1 câu, mọi câu đủ dữ liệu theo quy tắc của từng loại (PROTOCOL.md mục 3); lỗi thì nhảy tới câu lỗi và nói rõ thiếu gì;
  đúng thì createSet hoặc updateSet, lỗi Server hiện error.message, thành công thì về HOME. Rời màn khi có thay đổi chưa lưu thì hỏi xác nhận.
```

## P9b. Download Template và Import file
```text
Đọc CLAUDE.md và docs/FRONTEND_SPEC.md, mục 'Import bộ câu hỏi từ file'. Không thêm thư viện. Làm ở quizgame.client.util và nối vào SetEditorPanel:
1. TemplateWriter: ghi file CSV UTF-8 có BOM, hàng tiêu đề đúng thứ tự cột + 3 dòng ví dụ (Buttons, Checkboxes, Type answer).
   Nút Download Template mở JFileChooser (lưu) với tên gợi ý mau_bo_cau_hoi.csv.
2. QuestionImporter.read(Path csv) -> ImportResult: tự nhận dấu phân cách ',' hay ';', tự viết bộ đọc CSV hỗ trợ ô trong dấu nháy kép và xuống dòng trong ô, bỏ BOM.
   Kiểm tra từng dòng theo spec; dòng sai bị bỏ qua và ghi 'Dòng <số>: <lý do>'. Ảnh: tên file tìm trong cùng thư mục với file CSV, thu nhỏ 800 px, Base64, > 1 MB thì lỗi dòng.
3. Nút Import file: JFileChooser CSV -> QuestionImporter chạy trên SwingWorker (không chặn giao diện) -> thêm câu hợp lệ vào bộ đang soạn rồi chuyển sang Bước 2 ở câu vừa nhập đầu tiên;
   nếu có lỗi hiện hộp thoại liệt kê toàn bộ dòng lỗi.
4. Unit test QuestionImporter: 3 loại câu, dấu ';', ô có dấu phẩy, dòng thiếu đáp án, đáp án đúng ngoài A-D.
```

## P10. Bảng xếp hạng và lịch sử  (ảnh: 14-xep-hang.png, 15-lich-su.png)
```text
Đọc CLAUDE.md, docs/FRONTEND_SPEC.md và docs/UI_WIREFRAMES.md. Theo ảnh docs/ui/14-xep-hang.png và 15-lich-su.png.
1. LeaderboardPanel (mục LEADERBOARD): bảng Hạng | Tên | Số trận thắng | Tổng điểm từ getLeaderboard(); 3 hạng đầu tô vàng, bạc, đồng; nếu dòng cuối có rank
   không liền kề với dòng trước thì chèn một dòng '...' trước nó; tô đậm dòng của mình. Nút LÀM MỚI. Tải mỗi lần mở màn, có 3 trạng thái (tải/rỗng/lỗi).
2. HistoryPanel (mục HISTORY): bảng Thời gian (định dạng dd/MM/yyyy HH:mm từ startedAt) | Phòng | Bộ câu hỏi | Hạng của tôi | Điểm, mới nhất trên cùng.
   Nháy đúp 1 dòng mở MatchDetailDialog: tiêu đề + MatchResultTable dùng lại từ P8 (gọi getHistoryDetail), không copy code.
```

## P11. Rà soát toàn bộ frontend
```text
Đọc CLAUDE.md và docs/FRONTEND_SPEC.md rồi rà soát toàn bộ module client, CHƯA sửa gì, chỉ liệt kê:
1. Chỗ nào cập nhật giao diện từ luồng khác mà không qua invokeLater.
2. Chỗ nào có dữ liệu giả, lớp Fake hoặc hằng số mẫu lọt vào code chạy thật.
3. Listener nào đăng ký mà không hủy, Timer nào không được dừng.
4. CompletableFuture nào không xử lý lỗi (exceptionally / whenComplete).
5. Chỗ nào lệch spec (tên lớp, luật nghiệp vụ, chữ tiếng Anh trên giao diện) hoặc lệch PROTOCOL.md (tên type, tên trường).
6. Chỗ nào giao diện khác docs/UI_WIREFRAMES.md và ảnh trong docs/ui (thiếu nút, sai thứ tự, sai nhãn).
7. Màn nào thiếu trạng thái rỗng, đang tải hoặc lỗi.
8. Code trùng lặp nên tách hàm.
Trình bày thành bảng: file:dòng | vấn đề | cách sửa. Tôi duyệt rồi mới sửa.
```

---

## Kiểm tra sau mỗi bước
| Bước | Kiểm tra | Đạt khi |
| --- | --- | --- |
| P1 | Chạy app | Mở được cửa sổ, đủ 8 màn |
| P2 | `mvn -q test` | Test xanh, đủ lớp như spec |
| P3 | `mvn -q test` | Test FramedIO và SocketGameClient xanh; không có lớp Fake nào |
| P4 | Chạy app khi Server chưa mở; sau đó khi Server đã chạy | Chưa mở Server: báo 'Không kết nối được'. Có Server: đăng ký, đăng nhập, sai mật khẩu hiện lỗi đỏ, đúng thì vào HOME |
| P5 | Mở 2 Client | Hai bên thấy nhau trong danh sách online, đổi trạng thái cập nhật ngay, không nháy cả bảng; danh sách bộ câu hỏi rỗng hiện chữ hướng dẫn |
| P6 | Tạo phòng, mời, sẵn sàng, nhắc, kick, để lời mời hết giờ | Nút BẮT ĐẦU chỉ bật đúng lúc; lời mời tự đóng sau 5 giây; nhật ký trạng thái ghi đủ sự kiện |
| P7 | Chơi 1 trận đủ 3 loại câu, thử hết giờ, thử THOÁT | Tô đúng/sai ngay, bảng xếp hạng mini đổi thứ hạng, không bấm được 2 lần |
| P8 | Chơi xong 1 trận | Bảng kết quả đúng; về phòng thì mọi người chưa sẵn sàng |
| P9 | Tạo bộ đủ 3 loại câu có ảnh và thời gian riêng, sửa bộ của mình | Lưu được, xuất hiện ở HOME; bộ người khác không có nút SỬA/XÓA |
| P9b | Download Template rồi Import CSV có 1 dòng lỗi | Câu đúng được thêm, hộp thoại liệt kê dòng lỗi, mở bằng Excel đúng tiếng Việt |
| P10 | Mở Xếp hạng và Lịch sử, nháy đúp 1 trận | Dòng của mình tô đậm; bảng chi tiết giống màn Kết quả |
| P11 | Đọc bảng rà soát | Các mục lệch được sửa hết |

Khi frontend xong, backend chỉ còn việc cài đúng `docs/PROTOCOL.md`. Mọi thứ nối vào nhau qua giao thức đó.
