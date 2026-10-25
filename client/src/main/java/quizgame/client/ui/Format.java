package quizgame.client.ui;

import quizgame.common.model.QuestionSetSummary;
import quizgame.common.model.QuestionType;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Các hàm định dạng chữ hiển thị. */
public final class Format {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Format() {
    }

    /** Thời lượng mm:ss từ mili giây (làm tròn xuống giây). */
    public static String duration(long ms) {
        long totalSeconds = Math.max(0, ms) / 1000;
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    /** ISO-8601 sang dd/MM/yyyy HH:mm; không đọc được thì trả nguyên chuỗi. */
    public static String dateTime(String iso) {
        if (iso == null) {
            return "";
        }
        try {
            return LocalDateTime.parse(iso).format(DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(iso).toLocalDateTime().format(DATE_TIME);
            } catch (DateTimeParseException e2) {
                return iso;
            }
        }
    }

    /** Các loại câu hỏi, cách nhau ' / ', thêm 'Hình ảnh' nếu bộ có ảnh. */
    public static String types(List<QuestionType> types, boolean hasImages) {
        List<String> labels = new ArrayList<>();
        types.stream().distinct().sorted(Comparator.comparingInt(QuestionType::ordinal))
                .forEach(t -> labels.add(t.label()));
        if (hasImages) {
            labels.add("Hình ảnh");
        }
        return String.join(" / ", labels);
    }

    /** 'N câu • các loại' của một bộ câu hỏi. */
    public static String setLine(QuestionSetSummary set) {
        String types = types(set.types(), set.hasImages());
        return set.questionCount() + " câu" + (types.isEmpty() ? "" : " • " + types);
    }
}
