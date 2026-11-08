# Game giải đố online (QUIZ) – bài tập lớn Lập trình mạng

Hệ thống gồm 1 Server và nhiều Client nói chuyện bằng TCP socket. Người chơi đăng ký/đăng nhập, xem danh sách online, tạo phòng tối đa 5 người,
mời nhau, chơi bộ câu hỏi (Buttons, Checkboxes, Type answer), xem xếp hạng và lịch sử.

## Tài liệu (đọc trước khi làm bất cứ việc gì)
- `docs/FRONTEND_SPEC.md`: công nghệ, quy tắc kiến trúc, lớp dữ liệu, luật nghiệp vụ của giao diện.
- `docs/UI_WIREFRAMES.md` và ảnh trong `docs/ui/`: bố cục từng màn.
- `docs/PROTOCOL.md`: hợp đồng giao thức với Server. Client PHẢI tuân theo từng chữ.
- `docs/PROMPTS.md`: trình tự các bước làm (P1 đến P11). Người dùng sẽ dán từng bước; mỗi lần chỉ làm đúng bước được giao.

## Cấu trúc thư mục (bắt buộc, không tự đổi)
```
quiz-game/
├── pom.xml                          # pom cha, khai báo 3 module
├── CLAUDE.md
├── docs/                            # tài liệu + docs/ui/*.png
├── common/                          # dùng chung Client và Server
│   └── src/main/java/quizgame/common/
│       ├── model/                   # record + enum dữ liệu (User, RoomState, Question...)
│       └── protocol/                # FramedIO, Envelope, ErrorInfo, JsonUtil (Gson)
├── client/                          # FRONTEND (đang làm)
│   ├── src/main/java/quizgame/client/
│   │   ├── ClientApp.java           # main: bật FlatLaf, mở MainFrame
│   │   ├── net/                     # GameClient, GameEventListener, SocketGameClient
│   │   ├── ui/                      # MainFrame, NavBar, AppTheme, Format
│   │   │   ├── screen/              # LoginPanel, HomePanel, RoomPanel, GamePanel, ResultPanel,
│   │   │   │                        #   SetEditorPanel, LeaderboardPanel, HistoryPanel
│   │   │   ├── dialog/              # InviteDialog, SetPickerDialog, MatchDetailDialog
│   │   │   └── component/           # OnlinePlayersPanel, SetCard, MatchResultTable, nút 4 màu...
│   │   └── util/                    # CsvTemplate, CsvImporter, ImageUtil, ImportResult
│   ├── src/main/resources/          # icon, font (nếu có)
│   └── src/test/java/quizgame/client/   # test; server giả CHỈ được đặt ở đây
└── server/                          # BACKEND; chỉ triển khai theo phạm vi được giao
    └── src/main/java/quizgame/server/
        ├── ServerApp.java
        ├── net/                     # ServerSocket, ClientHandler
        ├── service/                 # Auth, Presence, Invite, Room, Game, QuestionSet, Stats
        └── dao/                     # JDBC tới cơ sở dữ liệu
```
Lớp mới phải đặt đúng thư mục trên.

## Quy tắc làm việc
1. Không tự ý sửa `client`, `common`, protocol hoặc schema; backend `server` theo kế hoạch đã được duyệt. Giai đoạn gameplay mới chưa được phép tự commit/push.
   Ngoại lệ đã được duyệt: sửa `client/.../ui/MainFrame.java` và test luồng Swing tương ứng để đồng bộ rời phòng/điều hướng sau trận; không đổi bố cục hoặc protocol.
   Ngoại lệ đã được duyệt: âm thanh tự tổng hợp (`client/.../util/SoundPlayer.java`, `javax.sound`, không file/thư viện ngoài) gắn vào
   `AppButton`, `AnswerButton`, `NavBar` (tiếng click) và `GamePanel` (nhạc nền trong trận, tiếng đúng/sai); không nút tắt, không đổi bố cục hoặc protocol.
   Mở rộng đã duyệt: tiếng click cho mọi thứ bấm được còn lại (checkbox, radio, `IllustratedChoiceButton`, nút số câu và menu ở
   `SetEditorPanel`, logo NavBar, `ImagePicker`, nhấp đúp mở dòng ở `SetPickerDialog`/`HistoryPanel`).
   Đã duyệt: màn GAME đổi sang nền tối theo ảnh mẫu mới của người dùng (thay bố cục ảnh 06–08): đồng hồ tròn, 4 ô đáp án lớn,
   khung kết quả "ĐÚNG!/SAI! … +điểm", bảng xếp hạng nền tối (`TimerRing`, `GameScoreList`). Chỉ đổi Client, không đổi protocol.
2. KHÔNG tạo dữ liệu giả, lớp Fake, hay mock trong code chạy thật. Mọi dữ liệu đến từ Server qua `SocketGameClient`. Danh sách rỗng thì hiện
   trạng thái rỗng (chữ hướng dẫn), chưa tải xong thì hiện 'Đang tải...', lỗi thì hiện lỗi và nút Thử lại.
   Chỉ được dùng server giả bên trong thư mục test (`src/test`) để kiểm thử.
3. Không đổi tên lớp, tên trường, tên `type` đã ghi trong tài liệu. Muốn đổi thì dừng lại và hỏi người dùng.
4. Mọi cập nhật giao diện từ callback mạng phải qua `SwingUtilities.invokeLater`. Không bao giờ đọc/ghi socket trên luồng giao diện.
5. Client chỉ dùng FlatLaf, Gson (>= 2.10.1), JUnit 5. Server được dùng JDBC và MySQL Connector/J đã được duyệt; không thêm thư viện khác nếu chưa được phép.
6. Chữ trên giao diện bằng tiếng Việt, có dấu. Comment trong code bằng tiếng Việt, ngắn gọn.
7. Sau mỗi bước: build được (`mvn -q -DskipTests package`), chạy được, nói rõ cách kiểm tra bằng tay, rồi gợi ý lệnh `git commit`.
8. Khi ảnh mockup và chữ trong tài liệu khác nhau: ảnh quyết định bố cục, chữ quyết định hành vi.

## Lệnh thường dùng
- Build: `mvn -q -DskipTests package`
- Chạy Client: `mvn -q -pl client -am exec:java`
- Test: `mvn -q test`
