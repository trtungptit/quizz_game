package quizgame.client.ui.component;

import quizgame.common.model.ScoreRow;

import java.util.List;

/** Chọn cửa sổ 5 dòng của bảng xếp hạng mini quanh người chơi. */
public final class ScoreWindow {
    public static final int SIZE = 5;

    private ScoreWindow() {
    }

    /**
     * Mình và tối đa 2 người trên, 2 người dưới; chạm đầu/cuối thì dịch cửa sổ để đủ 5 dòng.
     * Ít hơn 5 người thì trả hết. Không thấy mình trong danh sách thì lấy 5 dòng đầu.
     */
    public static List<ScoreRow> around(List<ScoreRow> rows, String me) {
        int n = rows.size();
        if (n <= SIZE) {
            return List.copyOf(rows);
        }
        int index = 0;
        for (int i = 0; i < n; i++) {
            if (rows.get(i).username().equals(me)) {
                index = i;
                break;
            }
        }
        int start = Math.max(0, Math.min(index - 2, n - SIZE));
        return List.copyOf(rows.subList(start, start + SIZE));
    }
}
