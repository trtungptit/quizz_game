package quizgame.client.ui.component;

import javax.swing.JTextArea;
import java.awt.Dimension;
import java.awt.Insets;

/** Văn bản chỉ đọc xuống dòng theo chiều rộng thật và yêu cầu đủ chiều cao. */
public class WrappingText extends JTextArea {
    private int wrapWidth = 240;

    public WrappingText(String text) {
        super(text);
        setEditable(false);
        setFocusable(false);
        if (getCaret() instanceof javax.swing.text.DefaultCaret caret) {
            caret.setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        }
        setLineWrap(true);
        setWrapStyleWord(true);
        setOpaque(false);
        setMargin(new Insets(0, 0, 0, 0));
        setAlignmentX(LEFT_ALIGNMENT);
    }

    public void setWrapWidth(int width) {
        wrapWidth = Math.max(1, width);
    }

    @Override
    public Dimension getPreferredSize() {
        if (getUI() == null) return super.getPreferredSize();
        Insets insets = getInsets();
        int width = Math.max(1, wrapWidth - insets.left - insets.right);
        int lineHeight = getFontMetrics(getFont()).getHeight();
        int height = TextLines.wrap(getText(), getFontMetrics(getFont()), Math.max(1, width - 1)).size() * lineHeight
                + insets.top + insets.bottom + 2;
        return new Dimension(wrapWidth, height);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(1, getPreferredSize().height);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
