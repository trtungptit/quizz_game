package quizgame.client.ui.component;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

/** Chia văn bản thuần thành các dòng vừa chiều rộng, kể cả từ dài không có dấu cách. */
final class TextLines {
    private TextLines() { }

    static List<String> wrap(String text, FontMetrics metrics, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            int start = 0;
            while (start < paragraph.length()) {
                int end = start;
                int wordEnd = start;
                while (end < paragraph.length()) {
                    int next = end + Character.charCount(paragraph.codePointAt(end));
                    if (metrics.stringWidth(paragraph.substring(start, next)) > Math.max(1, width)) break;
                    end = next;
                    if (Character.isWhitespace(paragraph.codePointBefore(end))) wordEnd = end;
                }
                if (end == start) end += Character.charCount(paragraph.codePointAt(start));
                else if (end < paragraph.length() && wordEnd > start) end = wordEnd;
                lines.add(paragraph.substring(start, end).stripTrailing());
                start = end;
                while (start < paragraph.length() && Character.isWhitespace(paragraph.charAt(start))) start++;
            }
        }
        return lines;
    }
}
