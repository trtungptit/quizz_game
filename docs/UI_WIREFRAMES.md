# QUIZ – Wireframe giao diện

Ảnh mockup có màu nằm trong `docs/ui/`; file này mô tả bố cục bằng chữ. Phần [THÊM] = thiết kế bổ sung vì wireframe gốc của người dùng chưa có.
Dấu * = chỉ hiện khi đủ điều kiện. Dữ liệu trong ảnh chỉ là ví dụ minh họa, KHÔNG đưa vào code. Nguồn dữ liệu xem FRONTEND_SPEC.md.

## NAVBAR (mọi màn trừ LOGIN và GAME)  — ảnh: mọi màn
    | QUIZ    TRANG CHỦ    XẾP HẠNG [THÊM]    LỊCH SỬ                         ĐĂNG XUẤT |
Nền gradient tím, chữ QUIZ màu vàng, mục đang mở nằm trong viên thuốc trắng mờ. Bấm QUIZ = về HOME.

## LOGIN [THÊM]  — ảnh 01-dang-nhap.png
    +----------------------------------------------+
    |                    QUIZ                      |
    |        [ ĐĂNG NHẬP ]   [ ĐĂNG KÝ ]   (2 tab)   |
    |  Máy chủ        [localhost] : [9090]          |   <- nhớ lần dùng trước (java.util.prefs)
    |  Tên đăng nhập  [__________________________]   |
    |  Mật khẩu       [__________________________]   |
    |  Nhập lại MK    [__________________________]   |  <- chỉ tab Đăng ký
    |  (dòng lỗi màu đỏ)                            |
    |  [          ĐĂNG NHẬP / ĐĂNG KÝ           ]   |
    +----------------------------------------------+
Bấm nút: connect(host, port) rồi login/register. Không kết nối được: hiện 'Không kết nối được tới Server <host>:<port>'.

## HOME (trang chủ)  — ảnh 02-trang-chu.png
    +---------------------------------------------+------------------------+
    | KHÁM PHÁ BỘ CÂU HỎI                          | Người chơi 1 • ĐANG RẢNH | (nền gradient)
    | [Tìm bộ câu hỏi...]      [TẠO BỘ CÂU HỎI]    | Tổng số trận thắng: 12     |
    | [ ] Chỉ bộ của tôi [THÊM]                   +------------------------+
    | +------------------+ +------------------+ | NGƯỜI CHƠI ONLINE        |
    | | KIẾN THỨC TỔNG HỢP | | NHÌN HÌNH ĐOÁN TÊN | | [Tìm người chơi...]     |
    | | 18 câu • Buttons/  | | 12 câu • Hình ảnh | | (avatar) Người chơi 2 [MỜI] |
    | |   Type answer    | |   / Type answer  | | 9 trận thắng • Đang rảnh   |
    | | Chủ đề: ...      | | Chủ đề: ...      | | (avatar) Người chơi 4 [BẬN] |
    | | Chia sẻ: Công khai| | Chia sẻ: Công khai| | 17 trận thắng • Đang chơi   |
    | | [SỬA][XÓA]* [CHƠI]| |        [CHƠI]    | |                        |
    | +------------------+ +------------------+ | Online: 3 • Rảnh: 2     |
    | [           TẠO PHÒNG THI ĐẤU           ] +------------------------+
    Lưới thẻ 2 cột, cuộn dọc, mỗi thẻ có dải màu riêng ở trên. [SỬA][XÓA] chỉ hiện ở bộ do mình tạo. Tìm kiếm lọc theo tên và chủ đề.
    Trạng thái người chơi hiện chữ 'Đang rảnh' / 'Trong phòng' / 'Đang chơi'; chỉ 'Đang rảnh' có nút MỜI, còn lại nút BẬN (vô hiệu).
    Cột bên phải ghi 'NGƯỜI CHƠI ONLINE' (wireframe gốc ghi 'BẠN BÈ' nhưng hệ thống không có kết bạn).

## ROOM (phòng thi đấu)  — ảnh 03-phong.png
    +--------------------------------------------------+-----------------------+
    | PHÒNG: ABC                                       | MỜI ĐỐI THỦ ONLINE       |
    | [Mã phòng: QD-4821 (Sao chép)] [Chủ phòng: NC 1] | [Tìm người chơi...]     |
    | NGƯỜI CHƠI (3/5) [THÊM]                          | Người chơi 2   [MỜI]  |
    |  1. Người chơi 1 (chủ phòng) • Sẵn sàng          | 9 trận thắng • Rảnh   |
    |  2. Người chơi 3          • Chưa      [KICK]*     | Người chơi 4   [BẬN]  |
    |  3. (Trống)  4. (Trống)  5. (Trống)               | 17 trận thắng • Bận   |
    | BỘ CÂU HỎI ĐÃ CHỌN                                +-----------------------+
    |  [Kiến thức tổng hợp • 18 câu • Buttons/Type...] |
    |  [Chọn lại bộ câu hỏi]*                           |
    |  Số câu [ 10 ]  Thời gian: (•) Theo từng câu  ( ) Mỗi câu [20] giây   [THÊM] (chỉ chủ phòng sửa)
    | TRẠNG THÁI (nhật ký, dòng mới ở trên, cuộn)        |
    |  Người chơi 3 • Sẵn sàng                           |
    |  Đã mời: Người chơi 2 • Đang chờ phản hồi...        |
    | [  RỜI PHÒNG  ] [ SẴN SÀNG / NHẮC MỌI NGƯỜI ] [ BẮT ĐẦU* ] |
    +--------------------------------------------------+
    Nhật ký TRẠNG THÁI tự dựng từ onRoomState (ai vào/rời/đổi sẵn sàng, pendingInvitees = 'Đang chờ phản hồi'), onInviteResult, onReadyReminder.
    Chưa chọn bộ: khung ghi 'Chưa chọn bộ câu hỏi' + nút CHỌN BỘ CÂU HỎI.

## INVITE DIALOG (hộp thoại lời mời, không modal) [THÊM]  — ảnh 04-hop-thoai-loi-moi.png
    +----------------------------------------+
    | LỜI MỜI THI ĐẤU                (1/3)    |
    | Người chơi 2 mời bạn vào phòng ABC      |
    | [██████░░░░] 3s                         |
    | [  TỪ CHỐI  ]            [  ĐỒNG Ý  ]   |
    +----------------------------------------+

## SET PICKER DIALOG (Chọn lại bộ câu hỏi) [THÊM]  — ảnh 05-hop-thoai-chon-bo-cau-hoi.png
    | CHỌN BỘ CÂU HỎI                                  |
    | [Tìm...]                                         |
    | bảng: Tên | Chủ đề | Số câu | Loại | Người tạo   |
    | [HỦY]                               [CHỌN]       |

## GAME (màn chơi) [THÊM], không có NAVBAR  — ảnh 06, 07, 08
    +-----------------------------------------------------------------+
    | CÂU 3/10        [████████░░] 07s        ĐIỂM: 1450      [THOÁT]   |  (thanh gradient)
    +--------------------------------------------+--------------------+
    |  Nội dung câu hỏi (nhiều dòng, không sửa)   | XẾP HẠNG            |
    |  [        ảnh nếu có (co vừa khung)      ]   | 1 An        1450  4|
    |                                            | 2 Bạn *     1200  3|
    |  SINGLE : 4 nút lớn 4 màu A. B. C. D.        | 3 Chi        900  3|
    |  MULTI  : 4 dòng có ô tick + [XÁC NHẬN]       | 4 Dũng      600  2|
    |  TYPE   : [ô nhập________] [XÁC NHẬN]        | 5 Em        300  1|
    |  Kết quả: ĐÚNG +950 / SAI (+ đáp án đúng)    | (hạng, tên, điểm,   |
    |  Xong hết: 'Đã xong, đang chờ người khác'     |  câu đang làm)      |
    +--------------------------------------------+--------------------+
    Sau khi trả lời: SINGLE sáng đáp án đúng (viền xanh, ✓) và làm mờ các nút còn lại, nút mình chọn sai tô đỏ; MULTI tô xanh các ô đúng,
    đỏ ô chọn sai; TYPE hiện các đáp án chấp nhận. Đồng hồ đỏ khi còn dưới 3 giây.

## RESULT (kết quả trận) [THÊM]  — ảnh 09-ket-qua.png
    |  <Tên> THẮNG!  /  BẠN THẮNG!  /  HÒA, KHÔNG AI ĐƯỢC TÍNH THẮNG   (biểu ngữ vàng) |
    |  Hạng | Người chơi | Điểm | Số câu đúng | Thời gian xong | Ghi chú |
    |  (tô vàng dòng của mình; Ghi chú: Bỏ cuộc / Mất kết nối)          |
    |  [   RỜI PHÒNG   ]                  [  VỀ PHÒNG CHỜ  ]            |

## SET_EDITOR (soạn bộ câu hỏi, theo wireframe 'Quizzz')  — ảnh 10, 11, 12, 13
Bước 1 (chưa có câu nào):
    | QUIZZZ                                                     |
    | Tên     [______________________________]                   |
    | Chủ đề   [______________________________]                   |
    | Mô tả    [______________________________]                   |
    | +----------+----------+----------+----------+----------+  |
    | | Buttons  |Checkboxes|Type answer| Download |  Import  |  |
    | |Câu hỏi 1 |Câu hỏi   |Thuật ngữ, | Template |   file   |  |
    | | đáp án  |nhiều đáp |từ khóa   |          |          |  |
    | +----------+----------+----------+----------+----------+  |
    | [HOÀN THÀNH] | (chưa có câu)                    [ + ]   |
Bước 2 (sau khi chọn 1 loại hoặc bấm +): Tên/Chủ đề/Mô tả thu gọn thành 1 dòng phía trên.
    | BUTTONS  (hoặc CHECKBOXES / TYPE ANSWER)          [Xóa câu này] [THÊM] |
    | Câu hỏi   [______________________________________]      |
    | Thời gian [ 20 ] giây (5-120)                           |
    | +--------------------+---------------------------------+ |
    | |                    | BUTTONS:   (o) A. [____]  (xanh = đúng) |
    | |   TẢI HÌNH ẢNH     |            ( ) B. [____]  C. D.   | |
    | |  (bấm để chọn,     | CHECKBOXES:[Đ/S] A. [____]  (Đ = đúng)|
    | |  xem trước, nút    |            [Đ/S] B. C. D.         | |
    | |  gỡ ảnh)           | TYPE:      Đáp án [____]            | |
    | |                    |            Đáp án [____]            | |
    | |                    |   [+ Thêm các câu trả lời được chấp nhận]| |
    | +--------------------+---------------------------------+ |
    | [HOÀN THÀNH] | 1 2 3 4 5 6 7 8 9 10 ...             [ + ]   |
    Thanh dưới: số = chuyển câu (câu đang sửa nền tím, thiếu dữ liệu gạch đỏ dưới số, đủ dữ liệu gạch xanh), cuộn ngang khi nhiều câu.
    [ + ] mở menu 3 loại (Buttons / Checkboxes / Type answer) để thêm câu mới. [HOÀN THÀNH] kiểm tra đủ dữ liệu tất cả câu,
    lỗi thì nhảy tới câu lỗi và nói rõ thiếu gì; đúng thì lưu (createSet hoặc updateSet) rồi về HOME. Rời màn khi chưa lưu thì hỏi xác nhận.
    Download Template, Import file: xem mục Import trong FRONTEND_SPEC.md; hai nút này có ở cả Bước 1 và Bước 2 (menu '...').

## LEADERBOARD [THÊM]  — ảnh 14-xep-hang.png
    | XẾP HẠNG TOÀN HỆ THỐNG                          [Làm mới] |
    | Hạng | Tên | Số trận thắng | Tổng điểm   (top 50; 3 hạng đầu tô vàng/bạc/đồng) |
    | ... (nếu mình ngoài top 50: dòng '...' rồi dòng của mình)  |

## HISTORY [THÊM]  — ảnh 15-lich-su.png
    | LỊCH SỬ TRẬN ĐẤU                                       |
    | Thời gian | Phòng | Bộ câu hỏi | Hạng của tôi | Điểm (mới nhất trên) |
    | nháy đúp 1 dòng -> MATCH DETAIL DIALOG: tiêu đề + cùng bảng MatchResultTable như màn RESULT |
