package quizgame.client.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Ghi file CSV mẫu (UTF-8 có BOM để Excel hiển thị đúng tiếng Việt). */
public final class CsvTemplate {
    public static final List<String> HEADER = List.of("Loại câu hỏi", "Nội dung", "Đáp án A", "Đáp án B", "Đáp án C",
            "Đáp án D", "Đáp án đúng", "Thời gian (giây)", "Đường dẫn/Tên file ảnh (nếu có)");

    private static final List<List<String>> EXAMPLES = List.of(
            List.of("Buttons", "Giao thức nào đảm bảo dữ liệu đến đúng thứ tự?", "UDP", "TCP", "ICMP", "ARP", "B", "20", ""),
            List.of("Checkboxes", "Chọn các số chẵn", "1", "2", "3", "4", "B,D", "30", ""),
            List.of("Type answer", "Thủ đô của Việt Nam là gì?", "", "", "", "", "Hà Nội|Ha Noi", "15", ""));

    private CsvTemplate() {
    }

    /** Ghi hàng tiêu đề và 3 dòng ví dụ (mỗi loại một dòng). */
    public static void write(Path file) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            out.write(toCsv().getBytes(StandardCharsets.UTF_8));
        }
    }

    static String toCsv() {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, HEADER);
        EXAMPLES.forEach(row -> appendRow(sb, row));
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, List<String> row) {
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quote(row.get(i)));
        }
        sb.append("\r\n");
    }

    private static String quote(String field) {
        if (field.contains(",") || field.contains(";") || field.contains("\"") || field.contains("\n")) {
            return '"' + field.replace("\"", "\"\"") + '"';
        }
        return field;
    }
}
