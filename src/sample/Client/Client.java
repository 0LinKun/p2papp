package sample.Client;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import sample.AllNeed.FileInfo;
import sample.AllNeed.FileListManager;
import sample.Server.IpAddressFetcher;
import sample.Server.Server;

import javax.swing.*;
import java.io.*;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P2P网络客户端核心类，实现服务器连接、消息通信、文件传输及用户列表管理功能。
 * <p>
 * 本类负责与中央服务器建立长连接，处理用户身份验证、在线状态同步、文件共享元数据解析，
 * 并协调客户端文件服务器（ClientFileServer）的启停。通过多线程机制实现异步消息处理。
 * </p>
 *
 * @author 0linkun
 * @version 1.0
 * @see ClientFileServer
 * @see FileListManager
 * @see IpAddressFetcher
 */
public class Client implements Runnable {
    /**
     * 服务器主机地址（IP）
     */
    public final String host;
    /**
     * 主信息显示区域的Swing组件引用
     */
    final JTextArea displayArea;
    /**
     * 在线用户列表显示区域的Swing组件引用
     */
    private final JTextArea onlineArea;
    public ClientFileServer clientFileServer;
    public Thread runningThread;
    public ArrayList<HashMap<String, String>> userList;
    public Map<String, FileInfo> currentFileList;
    FileListManager fileListManager;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean connect_success = false;
    private volatile boolean connected = false;

    /**
     * 客户端构造函数，初始化网络连接和UI组件。
     *
     * @param host        服务器主机地址（格式：192.168.1.100 或 example.com ）
     * @param displayArea 用于显示日志的JTextArea组件（非空）
     * @param onlineArea  用于展示在线用户的JTextArea组件（非空）
     * @throws IllegalStateException 若IP地址获取失败时通过日志提示
     */
    public Client(String host, JTextArea displayArea, JTextArea onlineArea) {
        this.host = host;
        this.displayArea = displayArea;
        this.onlineArea = onlineArea;
        IpAddressFetcher.IpAddress(displayArea);
        new Thread(this).start();
        // 在类中定义锁对象
        fileListManager = new FileListManager();
    }

    public ClientFileServer getClientFileServer() {
        return this.clientFileServer;
    }

    /**
     * 主线程执行体，建立服务器连接并启动消息监听循环。
     * <p>
     * 执行流程：
     * 1. 与服务器建立Socket连接（端口：Server.SERVER_PORT）
     * 2. 验证欢迎消息（Server.Welcome_Word）
     * 3. 启动独立线程处理持续消息接收
     * </p>
     *
     * @implNote 通过connect_success标志位控制连接状态同步
     */
    @Override
    public void run() {
        try {
            socket = new Socket(this.host, Server.SERVER_PORT); // Use actual host address as needed
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            connected = true;
            String msg = in.readLine(); // First word received must be welcome word
            Thread.sleep(100);//等待输出连接成功字样

            if (msg != null && msg.equals(Server.Welcome_Word)) {
                ClientLogger.log(displayArea, msg + "\n");
                while (true) {
                    if (this.connect_success) break;
                }
            }

            // 启动消息接收线程
            runningThread = new Thread(() -> {
                Boolean FileState = false;
                while (true) {
                    try {
                        String response = in.readLine();
                        if (response == null || response.equalsIgnoreCase("exit")) {
                            break;
                        } else if (response.equals("share")) {
                            if (!FileState) {
                                FileState = true;
                                new FileReceiver(this.displayArea).start();
                            }
                        } else if (ListenUserList(response)) {//接受到USER_LIST启动刷新用户列表函数
                            continue;
                        } else if (ListenServerFileList(response)) {//接受到files启动刷新服务器文件列表格式
                            continue;
                        }
                        ClientLogger.log(displayArea, response);

                    } catch (IOException e) {
                        e.printStackTrace();
                        SwingUtilities.invokeLater(() -> {
                            ClientLogger.log(displayArea, "接受到错误信息: " + e.getMessage() + "\n");
                        });
                        break;
                    }
                }
            });
            runningThread.start();

        } catch (Exception e) {
            e.printStackTrace();
            ClientLogger.log(displayArea, "链接服务错误: " + e.getMessage() + "\n");
        }
    }

    /**
     * 解析服务器下发的文件列表元数据（JSON格式）。
     *
     * @param response 服务器原始响应字符串，预期包含files键的文件列表
     * @return true表示成功解析并更新文件列表，false表示非文件列表消息
     * @throws JsonSyntaxException 当JSON格式不合法时抛出
     * @throws ClassCastException  当数据类型转换异常时抛出
     * @示例响应格式： {
     * "files": [
     * {
     * "filename": "example.txt",
     * "total_chunks": 5,
     * "chunks": [
     * {"number": 1, "hash": "a1b2c3..."},
     * ...
     * ]
     * }
     * ]
     * }
     */
    public boolean ListenServerFileList(String response) {
        Gson gson = new Gson();
        try {
            // 解析顶层数据结构
            Map<String, Object> responseMap = gson.fromJson(response, new TypeToken<Map<String, Object>>() {
            }.getType());
            if (!responseMap.containsKey("files")) {
                return false;
            }
            // 获取文件列表数组
            List<Map<String, Object>> serverFiles = (List<Map<String, Object>>) responseMap.get("files");
            // 创建临时文件列表
            Map<String, FileInfo> serverFileList = new HashMap<>();
            // 遍历每个文件项
            for (Map<String, Object> fileEntry : serverFiles) {
                FileInfo fileInfo = new FileInfo();

                // 基础字段映射
                fileInfo.filename = (String) fileEntry.get("filename");
                fileInfo.total_chunks = ((Double) fileEntry.get("total_chunks")).intValue();

                // 分块数据映射
                List<Map<String, Object>> chunks = (List<Map<String, Object>>) fileEntry.get("chunks");
                fileInfo.chunks = new ArrayList<>();

                for (Map<String, Object> chunk : chunks) {
                    FileInfo.ChunkInfo chunkInfo = new FileInfo.ChunkInfo(
                            ((Double) chunk.get("number")).intValue(),
                            (String) chunk.get("hash")
                    );
                    fileInfo.chunks.add(chunkInfo);
                }

                serverFileList.put(fileInfo.filename, fileInfo);
            }

            // 更新当前文件列表
            currentFileList = serverFileList;
            displayArea.append("同步服务器文件列表完成\n");
        } catch (JsonSyntaxException e) {
            System.err.println("JSON 解析错误: " + e.getMessage());
        } catch (ClassCastException e) {
            System.err.println(" 类型转换错误: " + e.getMessage());
        }
        return true;
    }

    /**
     * 处理在线用户列表更新消息（协议格式：USER_LIST开头）。
     *
     * @param response 服务器消息首行，若为"USER_LIST"则触发后续处理
     * @return true表示成功更新用户列表，false表示非用户列表消息
     * @implSpec 数据格式要求：
     * 1. 首行消息为"USER_LIST"
     * 2. 第二行为JSON数据长度（字节数）
     * 3. 后续内容为UTF-8编码的JSON数组
     */
    private boolean ListenUserList(String response) {
        if (response.equals("USER_LIST")) {
            try {
                // 读取数据长度
                int length = Integer.parseInt(in.readLine());

                // 读取JSON数据
                char[] buffer = new char[length];
                in.read(buffer, 0, length);
                String jsonData = new String(buffer);

                // 解析JSON数据
                Gson gson = new Gson();
                Type type = new TypeToken<ArrayList<HashMap<String, String>>>() {
                }.getType();
                this.userList = gson.fromJson(jsonData, type);
                displayArea.append("同步用户列表完成\n");
                // 更新在线用户显示
                // 更新在线用户显示
                SwingUtilities.invokeLater(() -> {
                    onlineArea.setText("");
                    onlineArea.append("当前在线人数：" + this.userList.size() + "\n");
                    for (HashMap<String, String> user : this.userList) {
                        String line = String.format("%s:%s:%s\n", user.get("NAME"), user.get("IP"), user.get("PORT"));
                        onlineArea.append(line);
                    }
                });
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    /**
     * 发送文本消息至服务器（线程安全方法）。
     *
     * @param message 待发送的原始消息字符串（自动追加换行符）
     * @throws IllegalStateException 若连接未建立时调用
     */
    public synchronized void sendMessage(String message) {
        out.println(message);
    }

    /**
     * 验证并处理用户输入的身份信息（格式：用户名#端口号）。
     *
     * @param message 用户输入的原始字符串
     * @throws NumberFormatException 当端口号非整数时通过日志提示
     * @循环逻辑 持续验证直至格式正确，成功后启动客户端文件服务器
     * @协议格式示例： "JohnDoe#8080"
     */
    public void checkMessage(String message) {

        InetAddress a = null;
        try {
            a = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        String ip = null;
        if (a != null) {
            ip = a.getHostAddress();
        }
        while (true) {
            String[] info = message.split("#");
            if (info.length == 2 && Integer.parseInt(info[1]) > 0 && Integer.parseInt(info[1]) < 65535) {
                ClientLogger.log(displayArea, info[0] + "#" + ip + "#" + info[1] + "\n");
                String nickName = info[0];
                String port = info[1];
                sendMessage(nickName + "#" + ip + "#" + info[1]);
                this.connect_success = true;
                this.clientFileServer = new ClientFileServer(Integer.parseInt(port), this);
                clientFileServer.start();
                break;
            } else {
                ClientLogger.log(displayArea, "语法错误, 请输入格式为你的用户名#端口号");
            }
        }
    }

    /**
     * 发送二进制数据块至指定Socket（带4字节长度头协议）。
     *
     * @param buffer    字节数据缓冲区
     * @param bytesRead 有效数据长度（单位：字节）
     * @param FileSock  目标Socket连接
     * @throws IOException 当网络写入失败时抛出
     * @协议说明： 1. 前4字节（大端序）表示数据长度N
     * 2. 后续N字节为实际数据内容
     */
    public synchronized void sendBinaryData(byte[] buffer, int bytesRead, Socket FileSock)
            throws IOException {

        OutputStream os = FileSock.getOutputStream();

        // 先发送数据长度（4字节头）
        ByteBuffer header = ByteBuffer.allocate(4);
        header.putInt(bytesRead);
        os.write(header.array());

        // 发送实际数据
        os.write(buffer, 0, bytesRead);
        os.flush();
    }

    /**
     * 优雅关闭客户端连接，释放资源。
     *
     * @执行步骤： 1. 发送"exit"指令至服务器
     * 2. 依次关闭输入流、输出流、Socket连接
     */
    public void exit() {
        out.println("exit");
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取当前连接状态。
     *
     * @return true表示已成功建立服务器连接，false表示未连接
     */
    public boolean isConnected() {
        return connected;
    }


}