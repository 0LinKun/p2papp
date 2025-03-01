package sample.Client;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ClientFrame extends JPanel {
    private final JTextArea displayArea;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JButton connectButton;
    private final JTextField ipField;
    private Client client;
    private boolean isConnected = false;
    private static final int SERVER_PORT = 10001; // 假设服务器端口是固定的

    public ClientFrame() {
        setLayout(new BorderLayout());

        // 创建显示区域
        displayArea = new JTextArea();
        displayArea.setEditable(false);
        // 创建文本区域并启用行包装
        displayArea.setLineWrap(true);
        displayArea.setWrapStyleWord(true);

        // 启用自动滚动
        DefaultCaret caret = (DefaultCaret) displayArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        // 将文本区域放入滚动面板中
        JScrollPane scrollPane = new JScrollPane(displayArea);
        // 添加滚动面板到框架
        add(scrollPane, BorderLayout.CENTER);

        // 创建输入面板
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        sendButton = new JButton("Send");
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        // 创建连接面板
        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT)); // 使用FlowLayout保持组件左对齐
        JLabel ipLabel = new JLabel("Server IP:");
        ipField = new JTextField("localhost", 15); // 默认值为"localhost"
        connectButton = new JButton("Connect");

        connectionPanel.add(ipLabel);
        connectionPanel.add(ipField);
        connectionPanel.add(connectButton);

        add(connectionPanel, BorderLayout.NORTH);

        // 设置按钮监听器
        connectButton.addActionListener(e -> connectToServer());
        sendButton.addActionListener(e -> sendMessage());

        // 初始化时禁用发送按钮，直到成功连接
        sendButton.setEnabled(false);
    }

    private void connectToServer() {
        if (!isConnected) {
            String ip = ipField.getText().trim();

            if (ip.isEmpty()) {
                appendToDisplayArea("Server IP cannot be empty.\n");
                return;
            }

            // 禁用连接按钮以防止重复点击
            connectButton.setEnabled(false);

            // 在新的线程中尝试连接
            new Thread(() -> {
                try {
                    client = new Client(ip, displayArea);
                    //System.out.println("clientframe"+Thread.currentThread());
                    Thread.sleep(100);
                    // 检查是否成功连接,等待connected变true
                    SwingUtilities.invokeLater(() -> {
                        if (client.isConnected()) {
                            isConnected = true;
                            sendButton.setEnabled(true);
                            connectButton.setText("Disconnect");
                            appendToDisplayArea("连接到服务器ip为 " + ip + "\n");
                        } else {
                            appendToDisplayArea(ip + " 服务器不存在或连接失败\n");
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        appendToDisplayArea("连接过程中出现错误: " + e.getMessage() + "\n");
                    });
                } finally {
                    // 重新启用连接按钮
                    SwingUtilities.invokeLater(() -> connectButton.setEnabled(true));
                }
            }).start();
        } else {
            disconnectFromServer();
        }
    }

    private void disconnectFromServer() {
        if (client != null && isConnected) {
            client.exit();
            isConnected = false;
            sendButton.setEnabled(false);
            connectButton.setText("Connect");
            appendToDisplayArea("Disconnected from server.\n");
        }
    }

    private void sendMessage() {
        String textToSend = inputField.getText().trim();
        if (!textToSend.isEmpty()) {
            appendToDisplayArea("Sending: " + textToSend + "\n");
            inputField.setText("");
            if (textToSend.contains("#")) {
                client.checkmessage(textToSend);
            } else {
                client.sendMessage(textToSend);
            }
        }
    }

    private void appendToDisplayArea(final String message) {
        SwingUtilities.invokeLater(() -> displayArea.append(message));
    }
}