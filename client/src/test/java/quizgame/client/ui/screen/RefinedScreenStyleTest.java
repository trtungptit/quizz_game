package quizgame.client.ui.screen;

import org.junit.jupiter.api.Test;
import quizgame.client.net.GameClient;
import quizgame.client.ui.*;
import quizgame.client.ui.component.AnswerButton;
import quizgame.common.model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

class RefinedScreenStyleTest {
    @Test
    void homeAndNavigationRestoreGoldAccentAndKeepActions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            MainFrame frame = frame();
            try {
                HomePanel home = new HomePanel(frame);
                AbstractButton create = findButton(home, "TẠO PHÒNG THI ĐẤU");
                assertNotNull(create);
                create.setSize(300, 56);
                assertEquals(AppTheme.ACCENT.getRGB(), paint(create).getRGB(100, 10));
                assertEquals(Color.WHITE, ((JLabel) get(home, "selfName")).getForeground());
                NavBar nav = new NavBar(key -> {}, () -> {});
                JLabel logo = findLabel(nav, AppTheme.APP_NAME);
                assertEquals(AppTheme.ACCENT, logo.getForeground());
            } finally { frame.dispose(); }
        });
    }

    @Test
    void selectedSingleAnswerKeepsSelectionUntilResultThenResetsOnNextQuestion() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            MainFrame frame = frame();
            GamePanel game = new GamePanel(frame);
            try {
                game.begin(new GameStart(2, Map.of()));
                game.onQuestion(question(QuestionType.SINGLE, 0));
                AnswerButton[] buttons = (AnswerButton[]) get(game, "singleButtons");
                buttons[1].doClick(0);
                assertTrue(buttons[1].isSelected(), "Đáp án đã gửi phải còn viền chọn");
                for (int i = 0; i < 4; i++) {
                    assertFalse(buttons[i].isEnabled());
                    assertEquals(i == 1, buttons[i].isSelected());
                }
                game.onAnswerResult(new AnswerResult(0, false, List.of(2), List.of(), 0, 0));
                buttons[1].setSize(360, 84);
                buttons[2].setSize(360, 84);
                assertEquals(AppTheme.WRONG.getRGB(), paint(buttons[1]).getRGB(160, 15));
                assertEquals(AppTheme.ANSWER_C.getRGB(), paint(buttons[2]).getRGB(160, 15));
                game.onQuestion(question(QuestionType.SINGLE, 1));
                for (AnswerButton b : (AnswerButton[]) get(game, "singleButtons")) {
                    assertFalse(b.isSelected());
                    assertTrue(b.isEnabled());
                }
            } finally { game.end(); frame.dispose(); }
        });
    }

    @Test
    void multiResultUsesSharedSoftStatusTokensAndResets() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            MainFrame frame = frame();
            GamePanel game = new GamePanel(frame);
            try {
                game.begin(new GameStart(2, Map.of()));
                game.onQuestion(question(QuestionType.MULTI, 0));
                JCheckBox[] checks = (JCheckBox[]) get(game, "multiChecks");
                checks[0].doClick(0);
                ((JButton) get(game, "multiConfirm")).doClick(0);
                game.onAnswerResult(new AnswerResult(0, false, List.of(1, 2), List.of(), 0, 0));
                JPanel[] rows = (JPanel[]) get(game, "multiRows");
                assertEquals(AppTheme.WRONG_SOFT, rows[0].getBackground());
                assertEquals(AppTheme.CORRECT_SOFT, rows[1].getBackground());
                game.onQuestion(question(QuestionType.MULTI, 1));
                for (JPanel row : rows) assertEquals(AppTheme.CARD, row.getBackground());
            } finally { game.end(); frame.dispose(); }
        });
    }

    private static Question question(QuestionType type, int index) {
        return new Question(index, 2, type, "Câu hỏi thử nghiệm", List.of("A", "B", "C", "D"), null, 120000);
    }

    private static MainFrame frame() {
        GameClient client = (GameClient) Proxy.newProxyInstance(GameClient.class.getClassLoader(),
                new Class<?>[]{GameClient.class}, (p, method, args) -> {
                    if (method.getReturnType() == void.class) return null;
                    return new CompletableFuture<>();
                });
        return new MainFrame(client, Preferences.userRoot().node("quiz-theme-tests"));
    }

    private static Object get(Object object, String name) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static BufferedImage paint(JComponent component) {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); component.paint(g); g.dispose(); return image;
    }

    private static AbstractButton findButton(Component component, String text) {
        if (component instanceof AbstractButton b && text.equals(b.getText())) return b;
        if (component instanceof Container c) for (Component child : c.getComponents()) {
            AbstractButton found = findButton(child, text); if (found != null) return found;
        }
        return null;
    }

    private static JLabel findLabel(Component component, String text) {
        if (component instanceof JLabel l && text.equals(l.getText())) return l;
        if (component instanceof Container c) for (Component child : c.getComponents()) {
            JLabel found = findLabel(child, text); if (found != null) return found;
        }
        return null;
    }
}
