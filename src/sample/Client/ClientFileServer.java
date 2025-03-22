package sample.Client;

import com.google.gson.JsonParseException;
import sample.AllNeed.FileInfo;
import sample.AllNeed.FileListManager;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 客户端文件服务器线程类，负责处理P2P文件共享网络中的服务器端逻辑。
 * 本类实现以下核心功能：
 * 1. 作为独立线程运行的文件服务器，监听指定端口
 * 2. 智能节点发现与文件同步机制
 * 3. 多线程处理客户端连接请求
 * 4. 文件分块传输与哈希校验
 * 5. 动态更新维护文件列表
 *
 * @author OlinKun
 * @version 1.0
 * @since 2025-03-22
 */
public class ClientFileServer extends Thread {
    /**
     * 连接超时时间（毫秒）
     */
    private static final int CONNECT_TIMEOUT = 3000;
    /**
     * 默认文件下载存储目录
     */
    private static final String DOWNLOAD_DIR = "file/";
    /**
     * 文件列表管理器实例
     */
    private static FileListManager fileListManager;

    /**
     * 服务端监听端口
     */
    private final int port;
    /**
     * 关联的客户端主对象
     */
    private final Client client;
    /**
     * 线程池用于处理并发连接
     */
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    /**
     * 服务器运行状态标志
     */
    private volatile boolean isRunning = true;
    /**
     * 服务器Socket对象
     */
    private ServerSocket serverSocket;
    /**
     * 客户端列表（格式："IP:Port"）
     */
    private List<String> ClientList;

    /**
     * 构造方法初始化文件服务器
     *
     * @param port   监听端口号
     * @param client 关联的客户端主对象
     */
    public ClientFileServer(int port, Client client) {
        this.port = port;
        this.client = client;
        fileListManager = client.fileListManager;

    }

    /**
     * 执行协议握手流程
     *
     * @param out 输出流
     * @param in  输入流
     * @return 握手成功返回true，否则false
     * @throws IOException 当I/O异常发生时抛出
     */
    private static boolean performHandshake(PrintWriter out, BufferedReader in) throws IOException {
        out.println("LIST_REQUEST");  // 发送列表请求
        return in.readLine().startsWith("files");  // 验证响应头
    }

    /**
     * 连接验证与文件传输处理
     *
     * @param ip   目标服务器IP地址
     * @param port 目标服务器端口
     * @return 验证通过返回true，否则false
     */
    private boolean connectAndVerify(String ip, int port) {
        try (
                Socket socket = new Socket(ip, port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {

            // 设置连接超时（JDK 8+特性）
            socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT);

            // 协议握手流程
            if (performHandshake(out, in)) {
                return handleFileTransfer(in);
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            System.err.printf(" 【%tT】连接 %s:%d 失败：%s%n",
                    System.currentTimeMillis(), ip, port, e.getMessage());
        }
        return false;
    }

    /**
     * 哈希比对与文件传输决策
     *
     * @param in 输入流
     * @return 需要传输文件返回true，否则false
     * @throws NoSuchAlgorithmException 当哈希算法不可用时抛出
     */
    private boolean handleFileTransfer(BufferedReader in) throws NoSuchAlgorithmException {
        if (this.client.fileListManager.compareFileList(in)) {
            System.out.printf(" 【%tT】发现匹配服务器，触发传输%n", System.currentTimeMillis());
            return true;
        }
        System.out.printf(" 【%tT】文件列表不匹配，继续搜索%n", System.currentTimeMillis());
        return false;
    }

    /**
     * 服务器主运行方法（线程入口）
     */
    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            this.serverSocket = serverSocket;
            serverSocket.setReuseAddress(true);

            System.out.printf(" 【%tT】文件服务器已启动，监听端口：%d%n", System.currentTimeMillis(), port);

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            if (isRunning) {
                System.err.printf(" 【%tT】服务器异常：%s%n", System.currentTimeMillis(), e.getMessage());
            }
        } finally {
            shutdown();
        }
    }

    /**
     * 启动智能文件发现流程
     *
     * @param ClientList 可用客户端列表（格式："IP:Port"）
     */
    public void startFileDiscovery(List<String> ClientList) {
        this.ClientList = ClientList;
        try {
            this.client.fileListManager.updateFileList();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        String ip, port;
        for (String address : new SmartServerIterator(ClientList)) {
            String[] parts = address.split(":");
            if (connectAndVerify(parts[0], Integer.parseInt(parts[1]))) {//发现匹配触发传输，下面就是传输开始
                ip = parts[0];
                port = parts[1];
                getTheAnotherClientFiles(ip, port);
                break; // 找到有效服务器后终止遍历
            }
        }
    }

    private void getTheAnotherClientFiles(String ip, String port) {
        new Thread(() -> {
            try {

                // 请求文件列表
                Map<String, FileInfo> remoteFiles = this.client.fileListManager.getCurrentFileList();

                // 对比文件差异
                Map<String, FileInfo> localFiles = fileListManager.getFileList();
                List<String> filesToDownload = findMissingFiles(remoteFiles, localFiles);

                // 下载缺失文件
                for (String filename : filesToDownload) {
                    FileInfo remoteFile = remoteFiles.get(filename);
                    downloadFile(ip, port, remoteFile);
                }

            } catch (IOException e) {
                ClientLogger.log(this.client.displayArea, "文件同步错误: " + e.getMessage());
            }
        }).start();
    }

    private List<String> findMissingFiles(Map<String, FileInfo> remote, Map<String, FileInfo> local) {
        List<String> missing = new ArrayList<>();
        for (String filename : remote.keySet()) {
            if (!local.containsKey(filename) ||
                    !local.get(filename).getFileHash().equals(remote.get(filename).getFileHash())) {
                missing.add(filename);
            }
        }
        return missing;
    }

    private void downloadFile(String ip, String port, FileInfo fileInfo) throws IOException {
        try (Socket socket = new Socket(ip, Integer.parseInt(port));
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            File file = new File(DOWNLOAD_DIR + fileInfo.getFileName());
            Files.createDirectories(file.getParentFile().toPath());

            // 下载所有文件块
            try (FileOutputStream fos = new FileOutputStream(file)) {
                for (int i = 0; i < fileInfo.getTotalChunks(); i++) {
                    out.writeUTF("CHUNK_REQUEST");
                    out.writeUTF(fileInfo.getFileName());
                    out.writeInt(i);

                    // 接收块数据
                    int chunkSize = in.readInt();
                    byte[] chunkData = new byte[chunkSize];
                    in.readFully(chunkData);
                    fos.write(chunkData);
                }
            }
            ClientLogger.log(this.client.displayArea, "下载完成: " + fileInfo.getFileName());
        }
    }

    //接受在线用户列表
    public List<String> receiveClientList(ArrayList<HashMap<String, String>> userList) throws IOException, JsonParseException {
        String IP_KEY = "IP";
        String PORT_KEY = "PORT";
        this.ClientList = new ArrayList<>();
        // 转换为目标格式
        for (Map<String, String> user : userList) {
            String ip = user.getOrDefault(IP_KEY, "");
            String port = user.getOrDefault(PORT_KEY, "");
            if (!ip.isEmpty() && !port.isEmpty()) {
                this.ClientList.add(ip + ":" + port);
            }
        }
        return this.ClientList;
    }

    public synchronized void shutdown() {
        if (!isRunning) return;
        isRunning = false;
        threadPool.shutdown();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.printf(" 【%tT】关闭服务器异常：%s%n", System.currentTimeMillis(), e.getMessage());
        }
        System.out.printf(" 【%tT】文件服务器已关闭%n", System.currentTimeMillis());
    }

    /**
     * 智能服务器迭代器（内部类）
     * 实现特点：
     * - 随机打乱服务器连接顺序避免单点依赖
     * - 支持迭代器遍历
     */
    private static class SmartServerIterator implements Iterable<String> {
        private final List<String> shuffledList;

        SmartServerIterator(List<String> servers) {
            this.shuffledList = new ArrayList<>(servers);
            Collections.shuffle(shuffledList);  // 随机打乱顺序
        }

        @Override
        public Iterator<String> iterator() {
            return shuffledList.iterator();
        }
    }

    /**
     * 客户端请求处理程序（内部类）
     * 处理以下请求类型：
     * 1. 文件列表请求（LIST_REQUEST）
     * 2. 文件块请求（CHUNK_REQUEST）
     */
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                System.out.printf(" 【%tT】客户端连接：%s%n",
                        System.currentTimeMillis(), clientSocket.getRemoteSocketAddress());

                String command = in.readUTF();
                if ("LIST_REQUEST".equals(command)) {
                    System.out.printf(" 【%tT】收到文件列表请求%n", System.currentTimeMillis());
                    fileListManager.updateAndSendFileList(out);
                } else if ("CHUNK_REQUEST".equals(command)) {
                    handleChunkRequest(in, out);
                }

            } catch (IOException | NoSuchAlgorithmException e) {
                System.err.printf(" 【%tT】请求处理异常：%s%n", System.currentTimeMillis(), e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.printf(" 【%tT】关闭连接异常：%s%n", System.currentTimeMillis(), e.getMessage());
                }
            }
        }

        private void handleChunkRequest(DataInputStream in, PrintWriter out) throws IOException {
            String filename = in.readUTF();
            int chunkNumber = in.readInt();

            try {
                FileInfo fileInfo = fileListManager.getFileInfo(filename);
                if (fileInfo == null) {
                    out.println("ERROR:  File not found");
                    return;
                }

                // 读取文件块
                byte[] chunkData = Files.readAllBytes(Paths.get(fileInfo.getFilePath()));
                String chunkHash = FileListManager.calculateHash(chunkData, chunkData.length);

                // 验证块哈希
                if (!chunkHash.equals(fileInfo.getChunks().get(chunkNumber).getHash())) {
                    out.println("ERROR:  Chunk verification failed");
                    return;
                }

                // 发送块数据
                out.println("CHUNK_RESPONSE");
                ((DataOutputStream) clientSocket.getOutputStream()).writeInt(chunkData.length);
                clientSocket.getOutputStream().write(chunkData);

            } catch (NoSuchAlgorithmException | IOException e) {
                out.println("ERROR:  " + e.getMessage());
            }
        }
    }

}