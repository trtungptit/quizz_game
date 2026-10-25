package quizgame.client;

import quizgame.client.net.GameClient;
import quizgame.client.net.SocketGameClient;
import quizgame.client.ui.AppTheme;
import quizgame.client.ui.MainFrame;

import javax.swing.SwingUtilities;

/** Điểm vào của Client: cài giao diện, tạo GameClient một lần rồi mở MainFrame trên luồng giao diện. */
public class ClientApp {
    public static void main(String[] args) {
        AppTheme.install();
        GameClient client = new SocketGameClient();
        SwingUtilities.invokeLater(() -> new MainFrame(client).setVisible(true));
    }
}
