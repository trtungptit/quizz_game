package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;
import quizgame.client.ui.Dialogs;
import quizgame.client.util.ImageUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;

/** Ô chọn ảnh: bấm để chọn PNG/JPG, tự thu nhỏ về rộng tối đa 800 px, xem trước, có nút gỡ. Lưu Base64. */
public class ImagePicker extends JPanel {
    private static final int PREVIEW_W = 340;
    private static final int PREVIEW_H = 200;

    private final JPanel empty = new JPanel();
    private final JPanel filled = new JPanel(new BorderLayout(0, 8));
    private final JLabel preview = new JLabel("", SwingConstants.CENTER);
    private String imageBase64;
    private Runnable onChange = () -> { };

    public ImagePicker() {
        super(new BorderLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(380, 260));
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        empty.setOpaque(false);
        empty.setLayout(new BoxLayout(empty, BoxLayout.Y_AXIS));
        JLabel link = new JLabel("<html><u>TẢI HÌNH ẢNH</u></html>");
        link.setFont(AppTheme.FONT_BOLD);
        link.setForeground(AppTheme.SOFT_TEXT);
        JLabel hint = new JLabel("<html><u>(PNG/JPG, tối đa 800 px)</u></html>");
        hint.setFont(AppTheme.FONT_SMALL);
        hint.setForeground(AppTheme.TEXT_MUTED);
        link.setAlignmentX(Component.CENTER_ALIGNMENT);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        empty.add(Box.createVerticalGlue());
        empty.add(link);
        empty.add(Box.createVerticalStrut(4));
        empty.add(hint);
        empty.add(Box.createVerticalGlue());
        empty.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        empty.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                choose();
            }
        });

        filled.setOpaque(false);
        filled.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        AppButton remove = AppButton.soft("GỠ ẢNH");
        remove.setPreferredSize(new Dimension(110, 34));
        remove.addActionListener(e -> setImageBase64(null, true));
        AppButton change = AppButton.soft("ĐỔI ẢNH");
        change.setPreferredSize(new Dimension(110, 34));
        change.addActionListener(e -> choose());
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(change);
        buttons.add(remove);
        filled.add(preview, BorderLayout.CENTER);
        filled.add(buttons, BorderLayout.SOUTH);
        setImageBase64(null, false);
    }

    /** Hàm được gọi mỗi khi người dùng đổi hoặc gỡ ảnh. */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** Ảnh hiện tại ở dạng Base64, null nếu không có. */
    public String getImageBase64() {
        return imageBase64;
    }

    /** Nạp ảnh từ dữ liệu có sẵn (không gọi onChange). */
    public void setImageBase64(String base64) {
        setImageBase64(base64, false);
    }

    private void setImageBase64(String base64, boolean notify) {
        this.imageBase64 = base64;
        removeAll();
        if (base64 == null) {
            add(empty, BorderLayout.CENTER);
        } else {
            try {
                preview.setIcon(new ImageIcon(ImageUtil.fit(ImageUtil.decode(base64), PREVIEW_W, PREVIEW_H)));
                preview.setText("");
            } catch (IOException | IllegalArgumentException e) {
                preview.setIcon(null);
                preview.setText("Không xem trước được ảnh");
            }
            add(filled, BorderLayout.CENTER);
        }
        revalidate();
        repaint();
        if (notify) {
            onChange.run();
        }
    }

    private void choose() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Ảnh PNG/JPG", "png", "jpg", "jpeg"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            setImageBase64(ImageUtil.fileToBase64(file), true);
        } catch (IOException e) {
            Dialogs.error(this, e.getMessage());
        }
    }

    /** Viền nét đứt tím quanh ô. */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(AppTheme.SOFT);
        g2.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 24, 24);
        g2.setColor(new java.awt.Color(0x8B5CF6));
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{7f, 5f}, 0f));
        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 24, 24);
        g2.dispose();
    }
}
