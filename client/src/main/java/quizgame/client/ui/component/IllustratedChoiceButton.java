package quizgame.client.ui.component;

import quizgame.client.util.SoundPlayer;

import quizgame.client.ui.AppTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

/** Thẻ lựa chọn có minh họa, hoạt động như nút thật với chuột, Tab, Space và Enter. */
public final class IllustratedChoiceButton extends JButton {
    private final String description;

    public IllustratedChoiceButton(ChoiceIllustration.Kind kind, String title, String description, Runnable action) {
        super(title);
        this.description = description;
        setIcon(new ChoiceIllustration(kind));
        setActionCommand(kind.name());
        setFont(AppTheme.FONT_BOLD.deriveFont(15f));
        setForeground(AppTheme.TEXT);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(title + " — " + description);
        getAccessibleContext().setAccessibleName(title);
        getAccessibleContext().setAccessibleDescription(description);
        addActionListener(event -> {
            SoundPlayer.click();
            action.run();
        });
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "activateChoice");
        getActionMap().put("activateChoice", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { doClick(0); }
        });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        boolean active = isEnabled() && (hasFocus() || getModel().isRollover() || getModel().isPressed());
        g.setColor(active ? AppTheme.SOFT : AppTheme.CARD);
        int arc = AppTheme.ARC_CARD * 2;
        g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        g.setColor(active ? AppTheme.PRIMARY : AppTheme.BORDER);
        g.setStroke(new BasicStroke(active ? 2f : 1f));
        g.drawRoundRect(1, 1, Math.max(0, getWidth() - 3), Math.max(0, getHeight() - 3), arc, arc);

        int width = Math.max(1, getWidth() - 24);
        Font secondary = AppTheme.FONT_BASE.deriveFont(13f);
        List<String> titles = TextLines.wrap(getText(), g.getFontMetrics(getFont()), width);
        List<String> subtitles = TextLines.wrap(description, g.getFontMetrics(secondary), width);
        int titleHeight = g.getFontMetrics(getFont()).getHeight();
        int subtitleHeight = g.getFontMetrics(secondary).getHeight();
        // Chừa hai dòng mô tả để các hình và tiêu đề thẳng hàng, kể cả ở cửa sổ thấp.
        int descriptionLines = Math.max(2, subtitles.size());
        int textHeight = 14 + titleHeight * titles.size() + 6 + subtitleHeight * descriptionLines;
        double scale = Math.min(1.0, Math.min(width / (double) getIcon().getIconWidth(),
                Math.max(1, getHeight() - 24 - textHeight) / (double) getIcon().getIconHeight()));
        int imageHeight = (int) Math.ceil(getIcon().getIconHeight() * scale);
        int totalHeight = imageHeight + textHeight;
        int y = Math.max(12, (getHeight() - totalHeight) / 2);
        Graphics2D image = (Graphics2D) g.create();
        image.translate((getWidth() - getIcon().getIconWidth() * scale) / 2, y);
        image.scale(scale, scale);
        if (!isEnabled()) image.setComposite(AlphaComposite.SrcOver.derive(.45f));
        getIcon().paintIcon(this, image, 0, 0);
        image.dispose();
        y += imageHeight + 14;
        y = drawLines(g, titles, getFont(), isEnabled() ? AppTheme.TEXT : AppTheme.TEXT_MUTED, y);
        drawLines(g, subtitles, secondary, AppTheme.TEXT_MUTED, y + 6);
        g.dispose();
    }

    private int drawLines(Graphics2D g, List<String> lines, Font font, Color color, int y) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics metrics = g.getFontMetrics();
        for (String line : lines) {
            g.drawString(line, (getWidth() - metrics.stringWidth(line)) / 2, y + metrics.getAscent());
            y += metrics.getHeight();
        }
        return y;
    }
}
