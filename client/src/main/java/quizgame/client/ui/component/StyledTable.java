package quizgame.client.ui.component;

import quizgame.client.ui.AppTheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.geom.Point2D;
import java.util.List;

/** Bảng chỉ đọc: tiêu đề gradient, dòng cao, không lưới; màu nền và chữ đậm theo từng ô. */
public class StyledTable extends JTable {
    /** Quy định kiểu của từng ô. */
    public interface CellStyle {
        /** Màu nền ô; null = nền mặc định. */
        Color background(int row, int col);

        boolean bold(int row, int col);
    }

    private final DefaultTableModel model;
    private CellStyle style = new CellStyle() {
        @Override
        public Color background(int row, int col) {
            return null;
        }

        @Override
        public boolean bold(int row, int col) {
            return false;
        }
    };

    public StyledTable(String... columns) {
        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        setModel(model);
        setRowHeight(36);
        setShowGrid(false);
        setIntercellSpacing(new java.awt.Dimension(0, 0));
        setRowSelectionAllowed(false);
        setFocusable(false);
        setFillsViewportHeight(true);
        setBackground(Color.WHITE);
        setFont(AppTheme.FONT_BASE);
        getTableHeader().setReorderingAllowed(false);
        getTableHeader().setResizingAllowed(false);
        getTableHeader().setDefaultRenderer(new HeaderRenderer());
        getTableHeader().setPreferredSize(new java.awt.Dimension(0, 34));
        setDefaultRenderer(Object.class, new BodyRenderer());
    }

    /** Đặt độ rộng ưu tiên của từng cột (cột còn lại chia phần dư theo tỉ lệ). */
    public void setColumnWidths(int... widths) {
        for (int i = 0; i < widths.length && i < getColumnCount(); i++) {
            getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    /** Bọc bảng trong JScrollPane không viền (cần để hiện tiêu đề cột). */
    public JScrollPane inScrollPane() {
        JScrollPane pane = new JScrollPane(this);
        pane.setBorder(null);
        pane.getViewport().setBackground(Color.WHITE);
        return pane;
    }

    public void setCellStyle(CellStyle style) {
        this.style = style;
    }

    /** Thay toàn bộ dòng của bảng. */
    public void setRows(List<Object[]> rows) {
        model.setRowCount(0);
        rows.forEach(model::addRow);
    }

    /** Tiêu đề: gradient chạy suốt chiều rộng bảng, mỗi ô lấy đúng đoạn của mình. */
    private final class HeaderRenderer extends JLabel implements TableCellRenderer {
        private int offsetX;
        private int totalWidth = 1;

        HeaderRenderer() {
            setOpaque(false);
            setFont(AppTheme.FONT_BOLD.deriveFont(Font.BOLD, 12f));
            setForeground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            setText(value == null ? "" : value.toString().toUpperCase());
            offsetX = 0;
            for (int i = 0; i < column; i++) {
                offsetX += table.getColumnModel().getColumn(i).getWidth();
            }
            totalWidth = Math.max(1, table.getColumnModel().getTotalColumnWidth());
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setPaint(new LinearGradientPaint(new Point2D.Float(-offsetX, 0),
                    new Point2D.Float(totalWidth - offsetX, 0), new float[]{0f, 0.5f, 1f},
                    new Color[]{AppTheme.GRADIENT_START, AppTheme.GRADIENT_MID, AppTheme.PRIMARY}));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private final class BodyRenderer extends DefaultTableCellRenderer {
        BodyRenderer() {
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            setHorizontalAlignment(SwingConstants.LEFT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            Color bg = style.background(row, column);
            setBackground(bg != null ? bg : Color.WHITE);
            setForeground(AppTheme.TEXT);
            setFont(style.bold(row, column) ? AppTheme.FONT_BASE.deriveFont(Font.BOLD) : AppTheme.FONT_BASE);
            return this;
        }
    }
}
