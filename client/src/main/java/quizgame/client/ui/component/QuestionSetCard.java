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
    public QuestionSetCard(QuestionSetSummary set, boolean mine, Runnable onPlay, Runnable onEdit, Runnable onDelete) {
        super(new BorderLayout(), AppTheme.STRIPES[(int) Math.floorMod(set.id(), (long) AppTheme.STRIPES.length)]);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(set.name().toUpperCase(Locale.ROOT));
        name.setFont(AppTheme.FONT_TITLE.deriveFont(17f));
        name.setForeground(AppTheme.TITLE);
        JLabel line = new JLabel(Format.setLine(set));
        line.setFont(AppTheme.FONT_BASE);
        JLabel topic = new JLabel("Chủ đề: " + (set.topic() == null ? "" : set.topic()));
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
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(share, BorderLayout.WEST);
        footer.add(buttons, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    private static AppButton small(AppButton b, Runnable action) {
        b.setPreferredSize(new Dimension(56, 32));
        b.setFont(AppTheme.FONT_BOLD.deriveFont(12f));
        b.addActionListener(e -> action.run());
        return b;
    }
}
