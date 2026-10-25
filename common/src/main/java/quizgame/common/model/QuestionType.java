package quizgame.common.model;

/** Loại câu hỏi: chọn một, chọn nhiều, hoặc nhập đáp án. */
public enum QuestionType {
    SINGLE("Buttons"),
    MULTI("Checkboxes"),
    TYPE("Type answer");

    private final String label;

    QuestionType(String label) {
        this.label = label;
    }

    /** Nhãn hiển thị của loại câu hỏi. */
    public String label() {
        return label;
    }
}
