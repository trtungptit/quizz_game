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
└── server/                          # BACKEND (người dùng làm sau, chưa đụng tới)
    └── src/main/java/quizgame/server/
        ├── ServerApp.java
        ├── net/                     # ServerSocket, ClientHandler
        ├── service/                 # Auth, Presence, Invite, Room, Game, QuestionSet, Stats
        └── dao/                     # JDBC tới cơ sở dữ liệu
```
Lớp mới phải đặt đúng thư mục trên. Thư mục `server` chỉ tạo khung rỗng ở P1.

## Quy tắc làm việc
1. Đang làm FRONTEND (module `client`). Backend (module `server`) do người dùng làm sau, không tự viết trừ khi được yêu cầu.
2. KHÔNG tạo dữ liệu giả, lớp Fake, hay mock trong code chạy thật. Mọi dữ liệu đến từ Server qua `SocketGameClient`. Danh sách rỗng thì hiện
   trạng thái rỗng (chữ hướng dẫn), chưa tải xong thì hiện 'Đang tải...', lỗi thì hiện lỗi và nút Thử lại.
   Chỉ được dùng server giả bên trong thư mục test (`src/test`) để kiểm thử.
3. Không đổi tên lớp, tên trường, tên `type` đã ghi trong tài liệu. Muốn đổi thì dừng lại và hỏi người dùng.
4. Mọi cập nhật giao diện từ callback mạng phải qua `SwingUtilities.invokeLater`. Không bao giờ đọc/ghi socket trên luồng giao diện.
5. Chỉ dùng thư viện: FlatLaf, Gson (>= 2.10.1), JUnit 5. Không thêm thư viện khác khi chưa được cho phép.
6. Chữ trên giao diện bằng tiếng Việt, có dấu. Comment trong code bằng tiếng Việt, ngắn gọn.
7. Sau mỗi bước: build được (`mvn -q -DskipTests package`), chạy được, nói rõ cách kiểm tra bằng tay, rồi gợi ý lệnh `git commit`.
8. Khi ảnh mockup và chữ trong tài liệu khác nhau: ảnh quyết định bố cục, chữ quyết định hành vi.

## Lệnh thường dùng
- Build: `mvn -q -DskipTests package`
- Chạy Client: `mvn -q -pl client -am exec:java`
- Test: `mvn -q test`
