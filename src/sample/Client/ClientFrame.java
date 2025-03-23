package sample.Client;

import sample.AllNeed.FileInfo;
import sample.AllNeed.FileListManager;
import sample.Server.Server;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;

/**
 * 客户端主界面类，继承自JPanel，负责构建用户交互界面并处理客户端核心功能逻辑。
 * <p>
 * 该类整合了网络连接管理、文件传输、消息通信、用户列表同步等功能，通过Swing组件实现可视化操作。
 * 主要功能包括：连接/断开服务器、消息发送、文件上传、群发文件、在线用户列表刷新、文件同步等。
 * </p>
 *
 * @see Client 关联的客户端核心逻辑类
 * @see Server 服务器端实现类
 */
public class ClientFrame extends JPanel {
    // 界面组件定义
    private final JTextArea displayArea;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JButton connectButton;
    private final JTextField ipField;
    private final JProgressBar progressBar;
    private final JTextArea onlineArea;    // 在线人数显示框
    private final JButton syncButton;
    private final JButton shareButton;
    private final JButton uploadButton;
    private final JButton refreshButton;
    String ip;
    /**
     * 客户端网络连接核心实例，负责维护Socket连接及协议通信
     */
    private Client client;
    /**
     * 连接状态标志位，true表示已建立服务器连接
     */
    private boolean isConnected = false;

    /**
     * 构造客户端主界面，初始化所有GUI组件并配置事件监听。
     * <p>
     * 界面布局采用BorderLayout，包含以下主要区域：
     * 1. 北区：服务器连接面板（IP输入、连接按钮）
     * 2. 中区：消息显示区域（带滚动条）
     * 3. 南区：消息输入面板（输入框+发送按钮）
     * 4. 东区：在线用户列表面板（带刷新按钮）
     * </p>
     * <p>
     * 初始化时禁用非连接状态下的功能按钮，防止误操作
     * </p>
     */
    public ClientFrame() {
        setLayout(new BorderLayout());

        // 创建右侧在线用户面板
        // 右侧在线用户面板
        JPanel onlinePanel = new JPanel(new BorderLayout(5, 5));
        onlinePanel.setPreferredSize(new Dimension(240, 0)); // 设置固定宽度
        onlinePanel.setBorder(BorderFactory.createTitledBorder(" 在线用户"));

        // 刷新按钮
        refreshButton = new JButton("刷新");
        refreshButton.addActionListener(e -> updateOnlineUsers()); // 绑定刷新事件

        // 在线用户显示区域
        onlineArea = new JTextArea();
        onlineArea.setEditable(false);
        onlineArea.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JScrollPane onlineScroll = new JScrollPane(onlineArea);

        // 组装右侧面板
        onlinePanel.add(refreshButton, BorderLayout.NORTH);
        onlinePanel.add(onlineScroll, BorderLayout.CENTER);

        // ▶▶ 将右侧面板添加到主界面 ▶▶
        add(onlinePanel, BorderLayout.EAST);

        // 创建显示区域
        displayArea = new JTextArea();
        displayArea.setEditable(false);
        // 创建文本区域并启用行包装
        displayArea.setLineWrap(true);
        displayArea.setWrapStyleWord(true);

        // 创建输入面板
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        sendButton = new JButton("Send");
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        // 创建连接面板
        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT)); // 使用FlowLayout保持组件左对齐
        JLabel ipLabel = new JLabel("服务器 IP:");
        ipField = new JTextField("localhost", 10); // 默认值为"localhost"
        connectButton = new JButton("连接");

        connectionPanel.add(ipLabel);
        connectionPanel.add(ipField);
        connectionPanel.add(connectButton);

        add(connectionPanel, BorderLayout.NORTH);
        // 在connectionPanel增加上传按钮
        uploadButton = new JButton("上传");
        connectionPanel.add(uploadButton);  // 添加到现有的连接面板


        // 在connectionPanel增加分享按钮
        shareButton = new JButton("群发");
        connectionPanel.add(shareButton);  // 添加到现有的连接面板

        syncButton = new JButton("同步");
        connectionPanel.add(syncButton);  // 添加到现有的连接面板

        // 在inputPanel增加进度显示
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(300, 25));
        inputPanel.add(progressBar, BorderLayout.NORTH);

        // 启用自动滚动
        DefaultCaret caret = (DefaultCaret) displayArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        // 将文本区域放入滚动面板中
        JScrollPane scrollPane = new JScrollPane(displayArea);
        // 添加滚动面板到框架
        add(scrollPane, BorderLayout.CENTER);

        // 设置按钮监听器
        inputField.addActionListener(e -> sendMessage());//监听输入框
        uploadButton.addActionListener(e -> upload());//监听upload
        shareButton.addActionListener(e -> share());//监听share
        connectButton.addActionListener(e -> connectToServer());
        sendButton.addActionListener(e -> sendMessage());
        syncButton.addActionListener(e -> sync());

        // 初始化时禁用发送按钮，直到成功连接
        sendButton.setEnabled(false);
        syncButton.setEnabled(false);
        shareButton.setEnabled(false);
        uploadButton.setEnabled(false);
        refreshButton.setEnabled(false);
    }

    /**
     * 执行文件同步操作，包含三阶段流程：
     * 1. 同步服务器文件列表
     * 2. 同步在线用户列表
     * 3. 启动客户端本地的文件发现服务
     *
     * @throws RuntimeException 当线程中断或IO异常时抛出
     * @see FileListManager 文件列表管理工具类
     * @see ClientFileServer 客户端文件服务模块
     */
    private void sync() {
        client.sendMessage("filelist");
        try {
            displayArea.append("同步服务器文件列表\n");
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        updateOnlineUsers();
        try {
            displayArea.append("同步在线用户列表\n");
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            ClientFileServer clientFileServer = this.client.getClientFileServer();
            clientFileServer.startFileDiscovery(clientFileServer.receiveClientList(client.userList));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 更新在线用户列表，向服务器请求最新用户数据
     *
     * @see Client#sendMessage(String) 消息发送机制
     */
    private void updateOnlineUsers() {
        client.sendMessage("updateOnlineUsers");
    }

    /**
     * 执行群发文件操作，流程包含：
     * 1. 弹出文件选择对话框
     * 2. 通过组播方式广播文件
     * 3. 使用独立线程处理文件传输
     *
     * @throws IOException 文件选择或传输异常时抛出
     * @see FileSender 文件发送工具类
     */
    private void share() {
        client.sendMessage("share");
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            new Thread(() -> {
                try {
                    //通过组播广播发送文件
                    new FileSender(selectedFile.toString(), this.displayArea);
                    System.out.println("File  share successfully.");
                } catch (IOException ex) {
                    appendToDisplayArea("share失败: " + ex.getMessage() + "\n");
                }
            }).start();

        }
    }

    /**
     * 执行文件上传操作，包含分块传输机制：
     * 1. 选择本地文件并生成元数据（文件名、大小、哈希）
     * 2. 建立专用文件传输Socket连接
     * 3. 分块传输文件（10MB/块）并实时更新进度条
     * 4. 每块数据附加哈希校验
     *
     * @throws NoSuchAlgorithmException 哈希算法不可用时抛出
     * @see FileListManager#generateFileInfo(java.nio.file.Path)  文件元数据生成方法
     */
    private void upload() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            new Thread(() -> {
                try {
                    Socket fileSock = new Socket(ip, Server.FILE_PORT);
                    // 生成文件元数据
                    FileInfo fileInfo = FileListManager.generateFileInfo(selectedFile.toPath());

                    //发送文件名
                    client.sendBinaryData(fileInfo.getFileName().getBytes(), fileInfo.getFileName().getBytes().length, fileSock);
                    // 发送元数据
                    client.sendMessage("UPLOAD_META#" + fileInfo.getFileName()
                            + "#" + fileInfo.getFileSize()
                            + "#" + fileInfo.getFileHash());

                    // 分块发送
                    try (InputStream is = Files.newInputStream(selectedFile.toPath())) {
                        byte[] buffer = new byte[10 * 1024 * 1024]; // 10MB分块
                        int bytesRead;
                        int chunkIndex = 0;

                        while ((bytesRead = is.read(buffer)) > 0) {
                            String chunkHash = FileListManager.calculateHash(buffer, bytesRead);
                            client.sendMessage("UPLOAD_CHUNK#" + chunkIndex
                                    + "#" + bytesRead
                                    + "#" + chunkHash);
                            client.sendBinaryData(buffer, bytesRead, fileSock); // 发送二进制数据
                            chunkIndex++;

                            // 更新进度
                            int progress = (int) ((chunkIndex * 10 * 1024 * 1024 * 100)
                                    / selectedFile.length());
                            SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                        }
                    }
                    appendToDisplayArea("上传文件成功：" + fileInfo.getFileName());
                } catch (IOException | NoSuchAlgorithmException ex) {
                    appendToDisplayArea("上传失败: " + ex.getMessage() + "\n");
                }
            }).start();
        }
    }

    /**
     * 管理服务器连接状态，实现连接/断开的双态切换：
     * <p>
     * 连接流程：
     * 1. 验证IP有效性
     * 2. 创建Client实例建立连接
     * 3. 启用功能按钮并更新界面状态
     * <p>
     * 断开流程：
     * 1. 调用Client.exit() 关闭连接
     * 2. 重置界面状态
     * </p>
     *
     * @see Client#isConnected() 连接状态检测方法
     */
    private void connectToServer() {
        if (!isConnected) {
            ip = ipField.getText().trim();

            if (ip.isEmpty()) {
                appendToDisplayArea("Server IP cannot be empty.\n");
                return;
            }

            // 禁用连接按钮以防止重复点击
            connectButton.setEnabled(false);

            // 在新的线程中尝试连接
            new Thread(() -> {
                try {
                    client = new Client(ip, displayArea, onlineArea);
                    Thread.sleep(100);
                    // 检查是否成功连接,等待connected变true
                    SwingUtilities.invokeLater(() -> {
                        if (client.isConnected()) {
                            isConnected = true;
                            sendButton.setEnabled(true);
                            appendToDisplayArea("连接到服务器ip为 " + ip + "\n");
                        } else {
                            appendToDisplayArea(ip + " 服务器不存在或连接失败\n");
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> appendToDisplayArea("连接过程中出现错误: " + e.getMessage() + "\n"));
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
            connectButton.setText("连接");
            appendToDisplayArea("Disconnected from server.\n");
        }
    }

    /**
     * 处理消息发送逻辑，支持特殊命令：
     * 1. 包含"#"的消息作为协议指令处理
     * 2. "cls"命令清空消息显示区
     * 3. 普通文本消息直接发送
     *
     * @see Client#checkMessage(String) 协议消息解析方法
     */
    private void sendMessage() {
        String textToSend = inputField.getText().trim();
        if (!textToSend.isEmpty()) {
            appendToDisplayArea("Sending: " + textToSend + "\n");
            inputField.setText("");
            if (textToSend.contains("#")) {
                syncButton.setEnabled(true);
                refreshButton.setEnabled(true);
                shareButton.setEnabled(true);
                uploadButton.setEnabled(true);
                connectButton.setText("断开");
                client.checkMessage(textToSend);
            } else if (textToSend.equals("cls")) {
                displayArea.setText("");
            } else {
                client.sendMessage(textToSend);
            }
        }
    }

    /**
     * 线程安全的显示区域更新方法，使用日志记录组件
     *
     * @param message 需要显示的消息内容
     * @see ClientLogger 客户端日志工具类
     */
    private void appendToDisplayArea(final String message) {
        //SwingUtilities.invokeLater(() -> displayArea.append(message));
        ClientLogger.log(displayArea, message);
    }
}
