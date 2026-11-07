package quizgame.client.ui.screen;

import org.junit.jupiter.api.Test;
import quizgame.client.ui.AppTheme;
import quizgame.client.ui.NavBar;
import quizgame.common.model.QuestionDraft;
import quizgame.common.model.QuestionType;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Dùng màn soạn thật, chỉ thao tác bản nháp trong bộ nhớ, không TCP/DB. */
class SetEditorChoicesTest {
    @Test
    void allFiveActionsHaveDistinctIllustrationsAndAccessibleKeyboardButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            SetEditorPanel panel = new SetEditorPanel(null);
            panel.startNew();
            List<JButton> cards = cards(panel);
            assertEquals(5, cards.size(), "Ba loại câu + hai thao tác CSV đều phải là nút minh họa");
            Set<Integer> illustrations = new HashSet<>();
            for (JButton card : cards) {
                assertNotNull(card.getIcon());
                assertTrue(card.getIcon().getIconWidth() >= 100);
                assertTrue(card.getIcon().getIconHeight() >= 60);
                assertTrue(card.isFocusable());
                assertNotNull(card.getAccessibleContext().getAccessibleName());
                assertFalse(card.getAccessibleContext().getAccessibleName().isBlank());
                assertNotNull(card.getToolTipText());
                Object enter = card.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER"));
                assertNotNull(enter, "Enter phải kích hoạt được thẻ");
                assertNotNull(card.getActionMap().get(enter));
                Icon icon = card.getIcon();
                BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                g.setColor(Color.WHITE); g.fillRect(0, 0, image.getWidth(), image.getHeight());
                icon.paintIcon(card, g, 0, 0); g.dispose();
                int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
                assertTrue(Arrays.stream(pixels).filter(p -> p != Color.WHITE.getRGB()).count() > 200);
                illustrations.add(Arrays.hashCode(pixels));
            }
            assertEquals(5, illustrations.size(), "Không dùng cùng một hình cho mọi loại");
        });
    }

    @Test
    void choosingEachIllustrationCreatesTheSameDraftTypeAndOptionsAsBefore() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            for (QuestionType type : QuestionType.values()) {
                SetEditorPanel panel = new SetEditorPanel(null);
                panel.startNew();
                JButton card = cards(panel).stream().filter(b -> type.name().equals(b.getActionCommand()))
                        .findFirst().orElseThrow(() -> new AssertionError("Thiếu thẻ " + type));
                // Thử cả đường bàn phím: không có listener chuột gọi trực tiếp action.
                if (type == QuestionType.MULTI) {
                    Object key = card.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER"));
                    card.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(card, 0, "ENTER"));
                } else card.doClick(0);
                List<?> drafts = (List<?>) get(panel, "drafts");
                assertEquals(1, drafts.size());
                QuestionDraft draft = (QuestionDraft) drafts.get(0);
                assertEquals(type, draft.type());
                assertEquals(type, get(get(panel, "editor"), "type"));
                assertEquals(type == QuestionType.TYPE ? 0 : 4, draft.options().size());
                assertEquals(20, draft.timeSeconds());
                panel.startNew();
                assertEquals(5, cards(panel).size());
                assertTrue(((List<?>) get(panel, "drafts")).isEmpty());
            }
        });
    }

    @Test
    void choiceCardsStayInsideOriginalFiveColumnLayoutAtSupportedSizes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            SetEditorPanel panel = new SetEditorPanel(null);
            panel.startNew();
            JPanel root = new JPanel(new BorderLayout());
            root.add(new NavBar(key -> {}, () -> {}), BorderLayout.NORTH);
            root.add(panel, BorderLayout.CENTER);
            JFrame host = new JFrame(); host.setUndecorated(true); host.setContentPane(root); host.pack();
            try {
                for (int[] size : new int[][]{{900, 550}, {1100, 650}, {1366, 700}}) {
                    host.setSize(size[0], size[1]); host.validate();
                    for (int i = 0; i < 5; i++) layout(root);
                    List<JButton> cards = cards(panel);
                    assertEquals(5, cards.size());
                    Rectangle previous = null;
                    for (JButton card : cards) {
                        Rectangle bounds = SwingUtilities.convertRectangle(card.getParent(), card.getBounds(), panel);
                        assertTrue(new Rectangle(panel.getSize()).contains(bounds));
                        assertTrue(card.getWidth() >= 140 && card.getHeight() >= 140);
                        for (Container ancestor = card.getParent(); ancestor != null && ancestor != root;
                             ancestor = ancestor.getParent()) {
                            Rectangle inParent = SwingUtilities.convertRectangle(card.getParent(), card.getBounds(), ancestor);
                            assertTrue(new Rectangle(ancestor.getSize()).contains(inParent),
                                    "Thẻ bị cắt khi có NavBar tại " + size[0] + "x" + size[1]);
                        }
                        if (previous != null) {
                            assertEquals(previous.y, bounds.y, "Giữ hàng 5 thẻ như bố cục gốc");
                            assertTrue(previous.getMaxX() <= bounds.x);
                        }
                        previous = bounds;
                    }
                }
            } finally { host.dispose(); }
        });
    }

    private static List<JButton> cards(Component c) {
        List<JButton> result = new ArrayList<>();
        if (!c.isVisible()) return result;
        if (c instanceof JButton b && Set.of("SINGLE", "MULTI", "TYPE", "TEMPLATE", "IMPORT")
                .contains(b.getActionCommand())) result.add(b);
        if (c instanceof Container parent) for (Component child : parent.getComponents()) result.addAll(cards(child));
        return result;
    }

    private static Object get(Object object, String name) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void layout(Component c) {
        if (c instanceof Container parent) { parent.doLayout(); for (Component child : parent.getComponents()) {
            if (child.isVisible()) layout(child);
        } }
    }
}
