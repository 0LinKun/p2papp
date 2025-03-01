package sample.Server;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;

public class ServerFrame extends JPanel {
    private final JTextArea displayArea;
    private final JTextField inputField;
    private final JButton sendButton;
    private final Server server;

    public ServerFrame() {
        setLayout(new BorderLayout());

        displayArea = new JTextArea();
        displayArea.setEditable(false);
        displayArea.setLineWrap(true);
        displayArea.setWrapStyleWord(true);

        // 启用自动滚动
        DefaultCaret caret = (DefaultCaret) displayArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane scrollPane = new JScrollPane(displayArea);
        add(scrollPane, BorderLayout.CENTER);


        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        sendButton = new JButton("Broadcast");
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        server = new Server(displayArea);

        sendButton.addActionListener(e -> {
            String textToSend = inputField.getText();
            if (!textToSend.isEmpty()) {
                displayArea.append("Broadcasting: " + textToSend + "\n");
                inputField.setText("");
                server.broadcastToClients(textToSend);
            }
        });
    }
}