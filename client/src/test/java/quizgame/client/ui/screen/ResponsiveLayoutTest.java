package quizgame.client.ui.screen;

import org.junit.jupiter.api.Test;
import quizgame.client.net.GameClient;
import quizgame.client.ui.AppTheme;
import quizgame.client.ui.MainFrame;
import quizgame.common.model.*;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra bố cục thật trên EDT, không kết nối server hoặc DB. */
class ResponsiveLayoutTest {
    @Test
    void homeGridFitsViewportWithLongNamesAtDifferentWidths() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            GameClient client = (GameClient) Proxy.newProxyInstance(GameClient.class.getClassLoader(),
                    new Class<?>[]{GameClient.class}, (proxy, method, args) -> {
                        if (method.getReturnType() == void.class) return null;
                        return CompletableFuture.failedFuture(new UnsupportedOperationException("Layout fixture"));
                    });
            MainFrame frame = new MainFrame(client, Preferences.userRoot().node("quiz-layout-tests"));
            try {
                frame.onlineUsers().setSelf(new UserInfo("tester", 0, UserStatus.IDLE));
                HomePanel home = new HomePanel(frame);
                set(home, "sets", List.of(
                        new QuestionSetSummary(1, "Tên bộ câu hỏi dài để kiểm tra bố cục màn hình chính ".repeat(3),
                                "Chủ đề dài", "tester", 10, List.of(QuestionType.SINGLE), false),
                        new QuestionSetSummary(2, "Bộ câu hỏi khác", "Mạng", "other", 10,
                                List.of(QuestionType.MULTI), true),
                        new QuestionSetSummary(3, "Bộ cuối", "Mạng", "tester", 10,
                                List.of(QuestionType.TYPE), false)));
                Method render = HomePanel.class.getDeclaredMethod("renderSets");
                render.setAccessible(true);
                render.invoke(home);
                JFrame host = host(home, 1100, 650);
                try {
                    for (int width : new int[]{1100, 900, 1366, 1100}) {
                        host.setSize(width, 650);
                        host.validate();
                        settle(home);
                        JPanel grid = (JPanel) get(home, "grid");
                        JViewport viewport = (JViewport) grid.getParent().getParent();
                        assertTrue(grid.getWidth() <= viewport.getWidth(),
                                "Lưới tràn viewport tại chiều rộng " + width);
                        for (Component child : grid.getComponents()) {
                            if (child instanceof quizgame.client.ui.component.QuestionSetCard card) {
                                assertCardFits(card);
                            }
                        }
                    }
                } finally {
                    host.dispose();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void questionImageIsCenteredAfterResize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            GamePanel game = new GamePanel(null);
            JFrame host = null;
            try {
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(400, 800,
                        java.awt.image.BufferedImage.TYPE_INT_RGB);
                game.begin(new GameStart(1, java.util.Map.of("image",
                        quizgame.client.util.ImageUtil.toBase64(image))));
                game.onQuestion(new Question(0, 1, QuestionType.SINGLE, "Câu hỏi ảnh",
                        List.of("A", "B", "C", "D"), "image", 120000));
                host = host(game, 1100, 650);
                for (int width : new int[]{1100, 900, 1366, 1100}) {
                    host.setSize(width, 650);
                    host.validate();
                    settle(game);
                    JLabel label = (JLabel) get(game, "imageLabel");
                    double center = label.getX() + label.getWidth() / 2.0;
                    assertEquals(label.getParent().getWidth() / 2.0, center, 1.0,
                            "Ảnh phải căn giữa vùng câu hỏi sau resize");
                    assertTrue(label.getIcon().getIconWidth() <= label.getWidth());
                    assertTrue(label.getIcon().getIconHeight() <= label.getHeight());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                game.end();
                if (host != null) host.dispose();
            }
        });
    }

    @Test
    void longQuestionAndMultiConfirmationRemainReachable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            GamePanel game = new GamePanel(null);
            JFrame host = null;
            try {
                game.begin(new GameStart(1, java.util.Map.of()));
                game.onQuestion(new Question(0, 1, QuestionType.MULTI,
                        "Nội dung câu hỏi dài để kiểm tra khả năng đọc đầy đủ. ".repeat(9),
                        List.of("A", "B", "C", "D"), null, 120000));
                host = host(game, 1100, 650);
                for (int[] size : new int[][]{{1100, 650}, {900, 550}, {1366, 700}}) {
                    host.setSize(size[0], size[1]);
                    host.validate();
                    settle(game);
                    JTextArea question = (JTextArea) get(game, "questionText");
                    assertTrue(question.getHeight() >= question.getPreferredSize().height,
                            "Câu hỏi dài bị cắt chiều cao");
                    assertTrue(question.modelToView2D(question.getDocument().getLength()).getMaxY()
                            <= question.getHeight(), "Dòng cuối câu hỏi không hiển thị đủ");
                    JPanel answers = (JPanel) get(game, "answerArea");
                    JButton confirm = (JButton) get(game, "multiConfirm");
                    Rectangle bounds = SwingUtilities.convertRectangle(confirm.getParent(), confirm.getBounds(), answers);
                    assertTrue(bounds.y >= 0 && bounds.y + bounds.height <= answers.getHeight(),
                            "Nút MULTI nằm ngoài vùng trả lời");
                    assertTrue(confirm.getHeight() >= confirm.getPreferredSize().height,
                            "Nút MULTI bị co mất nội dung");
                    confirm.scrollRectToVisible(new Rectangle(0, 0, confirm.getWidth(), confirm.getHeight()));
                    JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, answers);
                    assertNotNull(viewport, "Nội dung dài cần cuộn dọc");
                    Rectangle inView = SwingUtilities.convertRectangle(confirm.getParent(), confirm.getBounds(),
                            viewport.getView());
                    assertTrue(viewport.getViewRect().contains(inView), "Cuộn không tới được nút MULTI");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                game.end();
                if (host != null) host.dispose();
            }
        });
    }

    @Test
    void switchingQuestionTypesResetsScrollAndKeepsEveryAnswerReadable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            GamePanel game = new GamePanel(null);
            JFrame host = null;
            try {
                game.begin(new GameStart(3, java.util.Map.of()));
                String answer = "Đáp án dài có nhiều nội dung để kiểm tra khả năng xuống dòng ".repeat(3);
                game.onQuestion(new Question(0, 3, QuestionType.SINGLE, "Nội dung dài. ".repeat(30),
                        List.of(answer, answer, answer, answer), null, 120000));
                host = host(game, 900, 550);
                quizgame.client.ui.component.AnswerButton[] buttons =
                        (quizgame.client.ui.component.AnswerButton[]) get(game, "singleButtons");
                for (var button : buttons) {
                    assertTrue(button.getHeight() >= button.preferredHeightForWidth(button.getWidth()),
                            "Đáp án dài không đủ chiều cao");
                }
                JPanel answers = (JPanel) get(game, "answerArea");
                JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, answers);
                buttons[3].scrollRectToVisible(new Rectangle(0, 0, buttons[3].getWidth(), buttons[3].getHeight()));
                assertTrue(viewport.getViewPosition().y > 0);
                game.onQuestion(new Question(1, 3, QuestionType.TYPE, "Câu nhập chữ",
                        List.of(), null, 120000));
                settle(game);
                assertEquals(0, viewport.getViewPosition().y, "Câu mới phải bắt đầu từ trên cùng");
                assertFalse(((JLabel) get(game, "imageLabel")).isVisible());
                JButton confirm = (JButton) get(game, "typeConfirm");
                Rectangle bounds = SwingUtilities.convertRectangle(confirm.getParent(), confirm.getBounds(), answers);
                assertTrue(bounds.x >= 0 && bounds.y >= 0 && bounds.getMaxX() <= answers.getWidth()
                        && bounds.getMaxY() <= answers.getHeight());
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                game.end();
                if (host != null) host.dispose();
            }
        });
    }

    @Test
    void longMultiOptionsWrapWithoutShrinkingTheConfirmationButton() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            GamePanel game = new GamePanel(null);
            JFrame host = null;
            try {
                game.begin(new GameStart(1, java.util.Map.of()));
                String option = "Đáp án nhiều lựa chọn có nội dung dài cần hiển thị đầy đủ ".repeat(3);
                game.onQuestion(new Question(0, 1, QuestionType.MULTI, "Chọn các đáp án đúng",
                        List.of(option, option, option, option), null, 120000));
                host = host(game, 900, 550);
                JCheckBox[] checks = (JCheckBox[]) get(game, "multiChecks");
                for (JCheckBox check : checks) {
                    assertNotNull(check.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey),
                            "Đáp án MULTI dài cần xuống dòng");
                    assertTrue(check.getHeight() >= check.getPreferredSize().height);
                    assertTrue(check.getWidth() >= check.getPreferredSize().width);
                }
                JButton confirm = (JButton) get(game, "multiConfirm");
                assertTrue(confirm.getHeight() >= confirm.getPreferredSize().height);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                game.end();
                if (host != null) host.dispose();
            }
        });
    }

    private static void assertCardFits(Container parent) {
        for (Component child : parent.getComponents()) {
            if (!child.isVisible()) continue;
            assertTrue(child.getX() >= 0 && child.getX() + child.getWidth() <= parent.getWidth(),
                    "Thành phần thẻ tràn ngang: " + child.getClass().getSimpleName());
            if (child instanceof JLabel label && label.getText() != null) {
                assertTrue(label.getPreferredSize().width <= label.getWidth(),
                        "Nhãn thẻ bị cắt: " + label.getText());
            }
            if (child instanceof JTextArea text) {
                try {
                    java.awt.geom.Rectangle2D last = text.modelToView2D(text.getDocument().getLength());
                    assertNotNull(last);
                    assertTrue(last.getMaxY() <= text.getHeight(), "Dòng cuối của thẻ bị cắt: "
                            + text.getSize() + " preferred=" + text.getPreferredSize() + " last=" + last
                            + " text=" + text.getText());
                } catch (javax.swing.text.BadLocationException e) {
                    throw new AssertionError(e);
                }
            }
            if (child instanceof Container container) assertCardFits(container);
        }
        if (parent.getLayout() instanceof BorderLayout border) {
            Component west = border.getLayoutComponent(BorderLayout.WEST);
            Component east = border.getLayoutComponent(BorderLayout.EAST);
            if (west != null && east != null) {
                assertFalse(west.getBounds().intersects(east.getBounds()), "Nút và nhãn chồng nhau");
            }
        }
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static JFrame host(JPanel panel, int width, int height) {
        JFrame host = new JFrame();
        host.setUndecorated(true);
        host.setContentPane(panel);
        host.pack();
        host.setSize(width, height);
        host.validate();
        settle(panel);
        return host;
    }

    private static void settle(Component component) {
        for (int i = 0; i < 6; i++) layout(component);
    }

    private static void layout(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                if (child.isVisible()) layout(child);
            }
        }
    }
}
