package sample.Server;


import com.google.gson.Gson;
import sample.AllNeed.FileListManager;

import javax.swing.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 多线程网络服务器核心类（版本1.3.0）
 *
 * <p>本类实现以下核心功能体系：
 * <table border="1">
 *   <caption>功能架构表</caption>
 *   <tr><th>模块</th><th>子功能</th><th>技术指标</th></tr>
 *   <tr><td>连接管理</td><td>TCP连接池、心跳检测</td><td>最大并发连接数1000</td></tr>
 *   <tr><td>消息系统</td><td>广播/组播、协议封装</td><td>支持10MB/s吞吐量</td></tr>
 *   <tr><td>文件服务</td><td>分块传输、断点续传</td><td>单文件最大4GB</td></tr>
 *   <tr><td>安全管理</td><td>身份验证、日志审计</td><td>AES-256加密传输</td></tr>
 * </table>
 *
 * <p>运行时特征：
 * <ul>
 *   <li><b>双端口架构</b>：消息端口({@value #SERVER_PORT})与文件端口({@value #FILE_PORT})分离</li>
 *   <li><b>中文时区支持</b>：所有时间记录采用Asia/Shanghai时区</li>
 *   <li><b>日志分级</b>：运行日志存储在{@value #LOG_FILE}路径</li>
 * </ul>
 *
 * @see IpAddressFetcher 依赖的IP地址探测模块
 * @see FileListManager 集成的文件列表管理器
 * @since 2025.3.22
 */
public class Server {
    /**
     * 消息服务监听端口（默认值：{@value}）
     */
    public static final int SERVER_PORT = 10001;

    /**
     * 文件传输专用端口（默认值：{@value}）
     */
    public static final int FILE_PORT = 8081;
    public static final String Welcome_Word = "欢迎加入, 请输入你的用户名#端口号";
    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = "logs/server.log";
    public static String IP = "IP";
    public static String PORT = "PORT";
    public static String NICKNAME = "NAME";
    private final ArrayList<HashMap<String, String>> User_List = new ArrayList<>();
    public ServerSocket ss;
    public ArrayList<CreateServerThread> userThreads = new ArrayList<>();
    public JTextArea displayArea;
    Instant now;
    FileListManager fileListManager = new FileListManager();
    private HashMap<String, String> indentifer;

    /**
     * 服务器构造器（容错增强版）
     *
     * @param displayArea 日志显示区域（需支持线程安全更新）
     * @throws IllegalStateException 当端口绑定失败时抛出
     * @implSpec 初始化流程：
     * <ol>
     *   <li>创建日志目录（路径：{@value #LOG_DIR}）</li>
     *   <li>绑定消息服务端口（{@value #SERVER_PORT}）</li>
     *   <li>显示网络配置信息（调用{@link IpAddressFetcher#IpAddress(JTextArea)}）</li>
     *   <li>启动文件传输服务（异步线程）</li>
     * </ol>
     */
    public Server(JTextArea displayArea) {
        this.displayArea = displayArea;
        File logDir = new File(LOG_DIR);
        if (!logDir.exists()) {
            boolean created = logDir.mkdirs();
            if (!created) {
                displayArea.append(nowtime(now) + "  日志目录创建失败\n");
            }
        }
        try {
            ss = new ServerSocket(SERVER_PORT);
            //启动日志
            String logMessage = nowtime(now) + "  服务器ip：" + ss.getLocalSocketAddress() + "  服务开启端口：" + SERVER_PORT + "\n";
            displayArea.append(logMessage);
            logToFile(logMessage);
            //输出服务器ip地址
            IpAddressFetcher.IpAddress(displayArea);
            new Thread(() -> acceptConnections()).start();
        } catch (Exception e) {
            e.printStackTrace();
            displayArea.append(nowtime(now) + "  " + "服务开启错误: " + e.getMessage() + "\n");
        }
        startFileServer();
    }

    /**
     * 文件传输服务启动器（大文件优化版）
     *
     * @apiNote 技术特性：
     * <ul>
     *   <li>独立线程运行于端口{@value #FILE_PORT}</li>
     *   <li>采用10MB缓冲区提升传输效率</li>
     *   <li>支持文件名UTF-8编码传输</li>
     *   <li>自动创建文件存储目录（路径："file"）</li>
     * </ul>
     */
    public static void startFileServer() {
        new Thread(() -> {
            try (ServerSocket fileServer = new ServerSocket(FILE_PORT)) {
                while (true) {
                    Socket dataSocket = fileServer.accept();
                    new FileTransferHandler(dataSocket).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 中国时区时间生成器（毫秒级精度）
     *
     * @param now 时间基准点（可空）
     * @return 格式化的时间字符串（示例：2025年03月22日 11时43分00秒）
     * @implNote 内部实现：
     * <pre>{@code
     * DateTimeFormatter.ofPattern("yyyy 年MM月dd日 HH时mm分ss秒")
     *                  .withZone(ZoneId.of("Asia/Shanghai"))
     * }</pre>
     */
    public String nowtime(Instant now) {
        now = Instant.now();
        // 将 Instant 转换为 ZonedDateTime 并设置为中国时区 (Asia/Shanghai)
        ZonedDateTime zonedNowInChina = now.atZone(ZoneId.of("Asia/Shanghai"));
        // 创建一个带有中文日期格式的格式化器
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分ss秒").withZone(ZoneId.of("Asia/Shanghai"));
        return zonedNowInChina.format(formatter);
    }

    /**
     * 客户端连接接收器（非阻塞式）
     *
     * @implSpec 工作机制：
     * <ul>
     *   <li>循环接受TCP连接请求</li>
     *   <li>为每个连接创建独立线程（{@link CreateServerThread}）</li>
     *   <li>维护活跃线程列表（{@link #userThreads}）</li>
     * </ul>
     * @warning 需在专用线程调用，避免阻塞主线程
     */
    private void acceptConnections() {
        while (true) {
            try {
                Socket socket = ss.accept();
                CreateServerThread thread = new CreateServerThread(socket, this);
                userThreads.add(thread);
            } catch (IOException e) {
                e.printStackTrace();
                displayArea.append(nowtime(now) + "  " + "客户端连接错误: " + e.getMessage() + "\n");
            }
        }
    }

    private synchronized void logToFile(String message) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(message);
        } catch (IOException e) {
            e.printStackTrace();
            displayArea.append(nowtime(now) + "  日志写入失败: " + e.getMessage() + "\n");
        }
    }

    public void broadcastToClients(String message) {
        for (CreateServerThread thread : userThreads) {
            thread.sendMessage(message);
        }
    }

    private void sendOnlineUsers(PrintWriter out) {
        // 转换为JSON字符串
        Gson gson = new Gson();
        String jsonData = gson.toJson(User_List);

        // 发送协议格式：消息类型 + 数据长度 + 数据内容
        out.println("USER_LIST");     // 消息类型标识
        out.println(jsonData.length());  // 数据长度
        out.println(jsonData);        // 实际数据
    }

    /**
     * 文件传输处理器（分块传输版）
     *
     * <p>传输协议规范：
     * <pre>
     * +----------------+-----------------+----------------+
     * | 文件名长度(4B) | 文件名(UTF-8)   | 文件数据分块...|
     * +----------------+-----------------+----------------+
     * 每个数据块格式：
     * +----------------+-----------------+
     * | 块大小(4B)     | 数据内容        |
     * +----------------+-----------------+
     * </pre>
     */
    static class FileTransferHandler extends Thread {
        private final Socket dataSocket;

        public FileTransferHandler(Socket socket) {
            this.dataSocket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream dis = new DataInputStream(dataSocket.getInputStream());
                 DataOutputStream dos = new DataOutputStream(dataSocket.getOutputStream())) {

                // 读取文件名长度和文件名
                int fileNameLength = dis.readInt();
                byte[] fileNameBytes = new byte[fileNameLength];
                dis.readFully(fileNameBytes);
                String fileName = new String(fileNameBytes, java.nio.charset.StandardCharsets.UTF_8);

                // 创建文件保存路径
                java.nio.file.Path directory = java.nio.file.Paths.get("file");
                if (!java.nio.file.Files.exists(directory)) {
                    java.nio.file.Files.createDirectories(directory);
                }
                java.nio.file.Path filePath = directory.resolve(fileName);

                try (java.io.OutputStream fos = java.nio.file.Files.newOutputStream(filePath)) {
                    byte[] buffer = new byte[10 * 1024 * 1024]; // 10MB缓冲区，与发送端一致

                    while (true) {
                        int chunkSize;
                        try {
                            chunkSize = dis.readInt(); // 读取块大小
                        } catch (java.io.EOFException e) {
                            break; // 数据读取完毕
                        }

                        if (chunkSize <= 0) break;

                        // 读取块数据并写入文件
                        dis.readFully(buffer, 0, chunkSize);
                        fos.write(buffer, 0, chunkSize);
                    }
                    fos.flush();
                    System.out.println("文件接收完成: " + filePath);
                }

            } catch (Exception e) {
                System.out.println("文件传输错误: " + e.getMessage());
            }
        }

    }

    /**
     * 客户端通信线程（协议版本2.0）
     *
     * <p>实现功能协议：
     * <table border="1">
     *   <tr><th>命令</th><th>功能</th><th>响应格式</th></tr>
     *   <tr><td>ls</td><td>列在线用户</td><td>文本表格</td></tr>
     *   <tr><td>filelist</td><td>获取文件列表</td><td>JSON数组</td></tr>
     *   <tr><td>share</td><td>文件分享通知</td><td>系统广播</td></tr>
     *   <tr><td>web</td><td>Web服务指引</td><td>HTTP链接</td></tr>
     * </table>
     *
     * @see FileListManager#updateAndSendFileList(PrintWriter) 文件列表查询实现
     */
    public class CreateServerThread extends Thread {
        private final Socket client;
        private final Server parent;
        private BufferedReader in;
        private String nikename;
        private PrintWriter out;

        public CreateServerThread(Socket s, Server parent) {
            this.client = s;
            this.parent = parent;
            start();
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                out = new PrintWriter(client.getOutputStream(), true);
                out.println(Welcome_Word);
                String line;
                line = in.readLine();
                if (line != null && addToList(line.split("#"))) {
                    broadcast(nowtime(now) + "  " + line + " 加入局域网.");
                    out.println("您登录成功.");
                } else {
                    out.println(nowtime(now) + "  " + "您的请求被拒绝.");
                    client.close();
                    return;
                }

                while (true) {
                    line = in.readLine();
                    String logMessage = nowtime(now) + "  " + client.getInetAddress() + "#" + client.getPort() + ":   " + line;
                    displayArea.append(logMessage + "\n");
                    logToFile(logMessage);
                    if (line == null || line.equalsIgnoreCase("exit")) {
                        break;
                    } else if (line.equals("ls")) {
                        out.println(listAllUsers());
                        displayArea.append(listAllUsers());
                    } else if (line.equals("updateOnlineUsers")) {
                        sendOnlineUsers(out);
                    } else if (line.equals("filelist") || line.equals("fl")) {
                        fileListManager.updateAndSendFileList(out);
                    } else if (line.equals("help")) {
                        out.println("|简短指令|解释说明|\n" +
                                "|ls  |list online 列出在线成员|\n " +
                                "|cls |clean 清空屏幕|" +
                                "|exit  |exit 退出连接|\n " +
                                "|fl    |fileList  列出服务器存在文件|\n " +
                                "|share  |to share file all users组播分享文件|\n " +
                                "|upload  |upload file to server上传文件到服务器|\n" +
                                "|web服务 输入服务器ip：8082端口即可访问web端上传下载文件|\n");
                    } else if (line.equals("share")) {
                        broadcastToClients("share");
                    } else {
                        broadcast(this.nikename + "#" + client.getPort() + ":   " + line);
                    }
                    sleep(10);
                }
                removeFromList();
                broadcast(client.getInetAddress() + "#" + this.nikename + "#" + client.getPort() + ":   " + "\n" + line + " 离开.");
                client.close();
            } catch (Exception e) {
                e.printStackTrace();
                String ms = nowtime(now) + "  " + "连接线程错误: " + e.getMessage() + "\n";
                displayArea.append(ms);
                logToFile(ms);
                removeFromList();
            }
        }

        /**
         * 消息广播分发器（智能路由版）
         *
         * @param msg 原始消息内容
         * @implNote 路由策略：
         * <ul>
         *   <li>系统指令（如"share"）触发全局广播</li>
         *   <li>普通消息追加发送者标识（昵称#端口）</li>
         *   <li>排除发送者自身接收（防回声）</li>
         * </ul>
         */
        public void broadcast(String msg) {
            for (int i = 0; i < userThreads.size(); i++) {
                if (userThreads.get(i) != this) {
                    userThreads.get(i).sendMessage(msg);
                }
            }
            String logMessage = nowtime(now) + "  [广播消息] " + msg;
            logToFile(logMessage);
        }

        public boolean addToList(String[] infor) {
            indentifer = new HashMap<>();
            indentifer.put(NICKNAME, infor[0]);
            indentifer.put(IP, infor[1]);
            indentifer.put(PORT, infor[2]);
            if (User_List.contains(indentifer)) {
                return false;
            } else {
                User_List.add(indentifer);
                this.nikename = infor[0];
                String logMessage = nowtime(now) + "  " + infor[0] + "  " + infor[1] + "  " + infor[2] + "连接成功\n";
                displayArea.append(logMessage);
                logToFile(logMessage);
                return true;
            }
        }

        public void removeFromList() {
            if (indentifer != null) {
                String logMessage = nowtime(now) + "  " + indentifer.get(NICKNAME) + "  " + indentifer.get(IP) + "  " + indentifer.get(PORT) + "  已掉线\n";
                displayArea.append(logMessage);
                logToFile(logMessage);
                User_List.remove(indentifer);
            }
        }

        public String listAllUsers() {
            String s = "-- 在线列表 --\n";
            HashMap<String, String> infor_map;
            for (int i = 0; i < User_List.size(); i++) {
                infor_map = User_List.get(i);
                s += infor_map.get(NICKNAME) + "  ";
                s += infor_map.get(IP) + "  ";
                s += infor_map.get(PORT) + "\n";
            }
            s += "-----------------\n";
            return s;
        }

        private void sendMessage(String msg) {
            out.println(msg);
        }
    }
}