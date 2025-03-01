package sample.Server;

import javax.swing.*;
import java.awt.*;

public class ServerMain extends JFrame {
    private final ServerFrame serverFrame;
    private final JPanel mainPanel;

    public ServerMain() {
        setTitle("Server Main Frame");
        setSize(700, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        serverFrame = new ServerFrame();
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(serverFrame, BorderLayout.CENTER);
        add(mainPanel);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ServerMain().setVisible(true);
        });
    }
}
