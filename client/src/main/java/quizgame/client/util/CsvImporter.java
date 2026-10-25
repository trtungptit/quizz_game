package quizgame.client.util;

import quizgame.common.model.QuestionDraft;
import quizgame.common.model.QuestionType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Đọc bộ câu hỏi từ file CSV (chạy hoàn toàn ở Client). Dòng sai bị bỏ qua và ghi vào errors. */
public final class CsvImporter {
    private static final int DEFAULT_TIME = 20;

    private CsvImporter() {
    }

    /** Một bản ghi CSV và số dòng (trong file) nơi nó bắt đầu. */
    record Row(int line, List<String> cells) {
    }

    public static ImportResult read(Path csv) throws IOException {
        String text = new String(Files.readAllBytes(csv), StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        Path dir = csv.toAbsolutePath().getParent();
        List<QuestionDraft> questions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<Row> rows = parse(text, detectDelimiter(text));
        // Hàng đầu là tiêu đề
        for (int i = 1; i < rows.size(); i++) {
            Row row = rows.get(i);
            try {
                questions.add(toDraft(row.cells(), dir));
            } catch (IllegalArgumentException e) {
                errors.add("Dòng " + row.line() + ": " + e.getMessage());
            }
        }
        return new ImportResult(questions, errors);
    }

    /** Chọn ';' hay ',' theo số lần xuất hiện (ngoài dấu nháy) ở hàng tiêu đề. */
    static char detectDelimiter(String text) {
        int commas = 0;
        int semicolons = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted) {
                if (c == '\n') {
                    break;
                } else if (c == ',') {
                    commas++;
                } else if (c == ';') {
                    semicolons++;
                }
            }
        }
        return semicolons > commas ? ';' : ',';
    }

    /** Bộ đọc CSV: hỗ trợ ô trong dấu nháy kép, "" là một dấu nháy, xuống dòng trong ô. */
    static List<Row> parse(String text, char delimiter) {
        List<Row> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int startLine = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\r') {
                // bỏ qua, '\n' sẽ kết thúc bản ghi
            } else if (c == '\n') {
                cells.add(cell.toString());
                cell.setLength(0);
                addRow(rows, cells, startLine);
                cells = new ArrayList<>();
                line++;
                startLine = line;
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !cells.isEmpty()) {
            cells.add(cell.toString());
            addRow(rows, cells, startLine);
        }
        return rows;
    }

    private static void addRow(List<Row> rows, List<String> cells, int line) {
        // Bỏ qua dòng hoàn toàn trống (hàng tiêu đề vẫn là hàng đầu tiên có dữ liệu)
        if (cells.stream().allMatch(s -> s.isBlank())) {
            return;
        }
        rows.add(new Row(line, cells));
    }

    /** Đổi một hàng thành QuestionDraft; ném IllegalArgumentException (câu tiếng Việt) nếu hàng sai. */
    private static QuestionDraft toDraft(List<String> raw, Path dir) {
        List<String> c = new ArrayList<>(raw);
        while (c.size() < 9) {
            c.add("");
        }
        QuestionType type = parseType(c.get(0).trim());
        String content = c.get(1).trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("thiếu nội dung câu hỏi");
        }
        if (content.length() > DraftValidator.MAX_CONTENT) {
            throw new IllegalArgumentException("nội dung quá " + DraftValidator.MAX_CONTENT + " ký tự");
        }
        int time = parseTime(c.get(7).trim());
        String correct = c.get(6).trim();
        if (correct.isEmpty()) {
            throw new IllegalArgumentException("thiếu đáp án đúng");
        }
        String image = readImage(c.get(8).trim(), dir);

        if (type == QuestionType.TYPE) {
            List<String> accepted = new ArrayList<>();
            for (String part : correct.split("\\|")) {
                if (!part.isBlank()) {
                    accepted.add(part.trim());
                }
            }
            if (accepted.isEmpty()) {
                throw new IllegalArgumentException("thiếu đáp án đúng");
            }
            return new QuestionDraft(type, content, List.of(), List.of(), accepted, time, image);
        }

        List<String> options = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String o = c.get(2 + i).trim();
            if (o.isEmpty()) {
                throw new IllegalArgumentException("thiếu đáp án " + (char) ('A' + i));
            }
            if (o.length() > DraftValidator.MAX_ANSWER) {
                throw new IllegalArgumentException("đáp án " + (char) ('A' + i) + " quá " + DraftValidator.MAX_ANSWER
                        + " ký tự");
            }
            options.add(o);
        }
        TreeSet<Integer> indexes = new TreeSet<>();
        for (String letter : correct.split("[,;\\s]+")) {
            if (letter.isEmpty()) {
                continue;
            }
            if (letter.length() != 1 || "ABCD".indexOf(Character.toUpperCase(letter.charAt(0))) < 0) {
                throw new IllegalArgumentException("đáp án đúng '" + letter + "' ngoài A-D");
            }
            indexes.add("ABCD".indexOf(Character.toUpperCase(letter.charAt(0))));
        }
        if (indexes.isEmpty()) {
            throw new IllegalArgumentException("thiếu đáp án đúng");
        }
        if (type == QuestionType.SINGLE && indexes.size() != 1) {
            throw new IllegalArgumentException("câu Buttons chỉ có 1 đáp án đúng");
        }
        return new QuestionDraft(type, content, options, new ArrayList<>(indexes), List.of(), time, image);
    }

    private static QuestionType parseType(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "buttons" -> QuestionType.SINGLE;
            case "checkboxes" -> QuestionType.MULTI;
            case "type answer" -> QuestionType.TYPE;
            case "" -> throw new IllegalArgumentException("thiếu loại câu hỏi");
            default -> throw new IllegalArgumentException("sai loại câu hỏi '" + text + "'");
        };
    }

    private static int parseTime(String text) {
        if (text.isEmpty()) {
            return DEFAULT_TIME;
        }
        int time;
        try {
            time = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("thời gian '" + text + "' không phải số");
        }
        if (time < DraftValidator.MIN_TIME || time > DraftValidator.MAX_TIME) {
            throw new IllegalArgumentException("thời gian phải từ " + DraftValidator.MIN_TIME + " đến "
                    + DraftValidator.MAX_TIME + " giây");
        }
        return time;
    }

    /** Ảnh: đường dẫn tuyệt đối, hoặc tên file nằm cùng thư mục với file CSV. */
    private static String readImage(String text, Path dir) {
        if (text.isEmpty()) {
            return null;
        }
        Path path;
        try {
            path = Path.of(text);
            if (!path.isAbsolute()) {
                path = dir.resolve(path);
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("đường dẫn ảnh '" + text + "' không hợp lệ");
        }
        File file = path.toFile();
        if (!file.isFile()) {
            throw new IllegalArgumentException("không tìm thấy ảnh '" + text + "'");
        }
        try {
            return ImageUtil.fileToBase64(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("ảnh '" + text + "': " + e.getMessage());
        }
    }
}
