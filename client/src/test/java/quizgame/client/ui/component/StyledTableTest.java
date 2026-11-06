package quizgame.client.ui.component;

import org.junit.jupiter.api.Test;
import quizgame.client.ui.AppTheme;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyledTableTest {
    @Test
    void selectedRowUsesSelectionColorsInEveryColumn() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StyledTable table = table();
            table.setRowSelectionInterval(0, 0);
            for (int col = 0; col < table.getColumnCount(); col++) {
                Component cell = render(table, 0, col);
                assertEquals(table.getSelectionBackground(), cell.getBackground());
                assertEquals(table.getSelectionForeground(), cell.getForeground());
            }
        });
    }

    @Test
    void movingSelectionRestoresDefaultColorsOnPreviousRow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StyledTable table = table();
            table.setRowSelectionInterval(0, 0);
            assertEquals(table.getSelectionBackground(), render(table, 0, 0).getBackground());
            table.setRowSelectionInterval(1, 1);
            assertEquals(table.getSelectionBackground(), render(table, 1, 0).getBackground());
            Component previous = render(table, 0, 0);
            assertEquals(Color.WHITE, previous.getBackground());
            assertEquals(AppTheme.TEXT, previous.getForeground());
            table.clearSelection();
            assertEquals(Color.WHITE, render(table, 1, 0).getBackground());
        });
    }

    @Test
    void selectionPreservesBoldAndRestoresCustomCellStyle() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StyledTable table = table();
            table.setCellStyle(new StyledTable.CellStyle() {
                @Override
                public Color background(int row, int col) {
                    return row == 0 ? Color.PINK : null;
                }

                @Override
                public boolean bold(int row, int col) {
                    return row == 0;
                }
            });
            Component styled = render(table, 0, 0);
            assertEquals(Color.PINK, styled.getBackground());
            assertTrue(styled.getFont().isBold());
            table.setRowSelectionInterval(0, 0);
            Component selected = render(table, 0, 0);
            assertEquals(table.getSelectionBackground(), selected.getBackground());
            assertEquals(table.getSelectionForeground(), selected.getForeground());
            assertTrue(selected.getFont().isBold());
            table.setRowSelectionInterval(1, 1);
            Component restored = render(table, 0, 0);
            assertEquals(Color.PINK, restored.getBackground());
            assertEquals(AppTheme.TEXT, restored.getForeground());
            assertTrue(restored.getFont().isBold());
            assertFalse(render(table, 1, 0).getFont().isBold());
        });
    }

    @Test
    void tableWithRowSelectionDisabledKeepsDefaultAppearance() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StyledTable table = table();
            table.setRowSelectionAllowed(false);
            table.setRowSelectionInterval(0, 0);
            Component cell = render(table, 0, 0);
            assertEquals(Color.WHITE, cell.getBackground());
            assertEquals(AppTheme.TEXT, cell.getForeground());
        });
    }

    private static StyledTable table() {
        StyledTable table = new StyledTable("Tên", "Chủ đề");
        table.setRows(List.of(new Object[]{"Bộ A", "A"}, new Object[]{"Bộ B", "B"}));
        table.setRowSelectionAllowed(true);
        table.setSelectionBackground(new Color(32, 80, 160));
        table.setSelectionForeground(Color.YELLOW);
        return table;
    }

    private static Component render(StyledTable table, int row, int col) {
        return table.prepareRenderer(table.getCellRenderer(row, col), row, col);
    }
}
