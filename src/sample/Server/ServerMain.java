package sample.Server;

import web.FileServer;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

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
        //启动web服务
        FileServer fileserver = new FileServer();
        try {
            FileServer.Filemain();
        } catch (IOException e) {
            e.printStackTrace();
        }


        SwingUtilities.invokeLater(() -> {
            new ServerMain().setVisible(true);
        });

    }
}
