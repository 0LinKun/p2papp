package sample.Client;

import javax.swing.*;
import java.awt.*;

public class ClientMain extends JFrame {
    private final ClientFrame clientFrame;
    private final JPanel mainPanel;

    public ClientMain() {
        setTitle("Client Main Frame");
        setSize(700, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        clientFrame = new ClientFrame();
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(clientFrame, BorderLayout.CENTER);
        add(mainPanel);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ClientMain().setVisible(true);
        });
    }
}