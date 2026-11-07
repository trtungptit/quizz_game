package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Format;
import quizgame.common.model.QuestionSetSummary;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Dimension;
import java.util.Locale;

/** Thẻ một bộ câu hỏi ở trang chủ: SỬA/XÓA chỉ hiện với bộ của mình. */
public class QuestionSetCard extends CardPanel {
    private final java.util.List<WrappingText> texts = new java.util.ArrayList<>();
    public QuestionSetCard(QuestionSetSummary set, boolean mine, Runnable onPlay, Runnable onEdit, Runnable onDelete) {
        super(new BorderLayout(), AppTheme.STRIPES[(int) Math.floorMod(set.id(), (long) AppTheme.STRIPES.length)]);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        WrappingText name = text(set.name().toUpperCase(Locale.ROOT));
        name.setFont(AppTheme.FONT_TITLE.deriveFont(17f));
        name.setForeground(AppTheme.TITLE);
        WrappingText line = text(Format.setLine(set));
        line.setFont(AppTheme.FONT_BASE);
        WrappingText topic = text("Chủ đề: " + (set.topic() == null ? "" : set.topic()));
        topic.setFont(AppTheme.FONT_SMALL.deriveFont(13f));
        topic.setForeground(AppTheme.TEXT_MUTED);
        body.add(name);
        body.add(Box.createVerticalStrut(6));
        body.add(line);
        body.add(Box.createVerticalStrut(4));
        body.add(topic);
        body.add(Box.createVerticalStrut(10));
        add(body, BorderLayout.CENTER);

        JLabel share = new JLabel("Chia sẻ: Công khai");
        share.setFont(AppTheme.FONT_SMALL);
        share.setForeground(AppTheme.TEXT_MUTED);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        if (mine) {
            buttons.add(small(AppButton.soft("SỬA"), onEdit));
            buttons.add(small(AppButton.soft("XÓA"), onDelete));
        }
        buttons.add(small(AppButton.primary("CHƠI"), onPlay));
        JPanel footer = new JPanel(new java.awt.GridLayout(0, 1, 0, 6));
        footer.setOpaque(false);
        footer.add(share);
        footer.add(buttons);
        add(footer, BorderLayout.SOUTH);
    }

    private WrappingText text(String value) {
        WrappingText text = new WrappingText(value);
        texts.add(text);
        return text;
    }

    @Override
    public Dimension getPreferredSize() {
        int width = getWidth() > 0 ? getWidth() : 340;
        java.awt.Insets insets = getInsets();
        texts.forEach(text -> text.setWrapWidth(width - insets.left - insets.right));
        // BoxLayout lưu cache kích thước; cập nhật lại khi số dòng thay đổi sau resize.
        java.awt.Component body = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.CENTER);
        if (body instanceof JPanel panel && panel.getLayout() instanceof BoxLayout layout) {
            layout.invalidateLayout(panel);
        }
        Dimension preferred = super.getPreferredSize();
        return new Dimension(340, preferred.height);
    }

    @Override
    public void doLayout() {
        getPreferredSize();
        super.doLayout();
    }

    private static AppButton small(AppButton b, Runnable action) {
        b.setPreferredSize(new Dimension(56, 32));
        b.setFont(AppTheme.FONT_BOLD.deriveFont(12f));
        b.addActionListener(e -> action.run());
        return b;
    }
}
