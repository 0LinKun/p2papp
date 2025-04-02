package sample.Client;

import com.google.gson.JsonParseException;
import sample.AllNeed.FileInfo;
import sample.AllNeed.FileListManager;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
        String msg=in.readLine();
        return msg.equals("File_List");
        //return in.readLine().startsWith("File_List");  // 验证响应头
    }

    /**
     * 连接验证与文件传输处理
     *
     * @param ip   目标服务器IP地址
     * @param port 目标服务器端口
     * @return 验证通过返回true，否则false
     */
    private boolean connectAndVerify(String ip, int port) {
        try {
                Socket socket = new Socket(ip,port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);


            // 设置连接超时（JDK 8+特性）
            //socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT);

            // 增加流初始化验证
            if (in.ready()  && out.checkError())  {
                throw new IOException("Stream initialization failed");
            }
            // 协议握手流程
            if (performHandshake(out, in)) {
                boolean flag =handleFileTransfer(in);
                socket.close();
                return flag;
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
        if (!this.client.fileListManager.compareFileList(in)) {
            System.out.printf(" 【%tT】发现不匹配服务器，触发传输%n", System.currentTimeMillis());
            return true;
        }
        System.out.printf(" 【%tT】文件列表一致，继续搜索%n", System.currentTimeMillis());
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
                //break; // 找到有效服务器后终止遍历
            }
        }
    }

    private void getTheAnotherClientFiles(String ip, String port) {
        new Thread(() -> {
           try {

                // 请求文件列表
                Map<String, FileInfo> remoteFiles = this.client.fileListManager.remoteFileList;

                // 对比文件差异
                Map<String, FileInfo> localFiles = fileListManager.getFileList();
                List<String> filesToDownload = findMissingFiles(remoteFiles, localFiles);

                ClientLogger.log(this.client.displayArea,filesToDownload.toString());

                if(!filesToDownload.isEmpty()){
               ClientLogger.log(this.client.displayArea,"文件同步开始下载");

                // 下载缺失文件
                for (String filename : filesToDownload) {
                    FileInfo remoteFile = remoteFiles.get(filename);
                    downloadFile(ip, port, remoteFile);
                    ClientLogger.log(this.client.displayArea,"文件下载"+remoteFile);
                }

                    ClientLogger.log(this.client.displayArea,"文件同步结束");
                }else {ClientLogger.log(this.client.displayArea,"无文件需要同步");}
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
          int BUFFER_SIZE = 8192;
       String filename = fileInfo.filename;
        Path downloadPath = Paths.get(DOWNLOAD_DIR,  filename);
        try (Socket socket = new Socket(ip, Integer.parseInt(port));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             DataInputStream in = new DataInputStream(socket.getInputStream()))  {


            // 发送请求
            out.println("FILE_REQUEST");
            out.println(filename);
            out.flush();

            // 处理响应
            String header = in.readUTF();
            if (header.equals("FILE_RESPONSE"))  {
                long fileSize = in.readLong();
                Files.createDirectories(downloadPath.getParent());

                try (FileChannel fileChannel = FileChannel.open(
                        downloadPath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE)) {

                    long transferred = 0;
                    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

                    while (transferred < fileSize) {
                        int read = in.read(buffer.array(),  0,
                                (int) Math.min(BUFFER_SIZE,  fileSize - transferred));
                        if (read == -1) throw new EOFException("Unexpected end of stream");

                        buffer.limit(read);
                        fileChannel.write(buffer);
                        buffer.clear();
                        transferred += read;
                    }
                }
                System.out.println(" 下载完成: " + filename + " (" + fileSize + " bytes)");
            } else if (header.equals("ERROR"))  {
                System.err.println(" 服务端错误: " + in.readUTF());
            }
        } catch (IOException e) {
            System.err.println(" 传输失败: " + e.getMessage());
            try {
                Files.deleteIfExists(downloadPath);
            } catch (IOException ignored) {}
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
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                System.out.printf(" 【%tT】客户端连接：%s%n", System.currentTimeMillis(), clientSocket.getRemoteSocketAddress());

                    String command = in.readLine();
                    if ("LIST_REQUEST".equals(command)) {
                        System.out.printf(" 【%tT】收到文件列表请求%n", System.currentTimeMillis());
                        fileListManager.updateAndSendFileList(out);
                    } else if ("FILE_REQUEST".equals(command)) {
                        System.out.printf(" 【%tT】收到文件下载请求%n", System.currentTimeMillis());
                        handleFileRequest(clientSocket);
                    }

            } catch (IOException | NoSuchAlgorithmException e) {
                System.err.printf(" 【%tT】请求处理异常：%s%n", System.currentTimeMillis(), e.getMessage());
            }
        }

        private void handleFileRequest(Socket clientSocket) {
            final String FILE_STORAGE_DIR = "./file/";

            try (BufferedReader dataIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 DataOutputStream dataOut = new DataOutputStream(clientSocket.getOutputStream()))  {

                // 1. 读取文件名（使用UTF协议）
                String filename = dataIn.readLine();
                System.out.println("[client]  收到文件请求: " + filename);

                // 2. 构建文件路径
                Path filePath = Paths.get(FILE_STORAGE_DIR  + filename);

                // 3. 文件存在性检查
                if (!Files.exists(filePath))  {
                    dataOut.writeUTF("ERROR:File  not found");
                    dataOut.flush();
                    System.out.println("[client]  文件不存在: " + filename);
                    return;
                }

                // 4. 读取文件内容（流式方式避免内存溢出）
                byte[] fileData;
                try (InputStream fileIn = Files.newInputStream(filePath);
                     ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    byte[] temp = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fileIn.read(temp))  != -1) {
                        buffer.write(temp,  0, bytesRead);
                    }
                    fileData = buffer.toByteArray();
                }



                // 6. 发送文件响应
                dataOut.writeUTF("FILE_RESPONSE");
                dataOut.writeLong(fileData.length);
                dataOut.write(fileData);
                dataOut.flush();
                System.out.println("[client]  已发送文件: " + filename + " (" + fileData.length  + " bytes)");

            } catch (IOException e) {
                System.err.println("[client]  传输异常: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("[client]  关闭连接异常: " + e.getMessage());
                }
            }
        }

        private void sendError(Socket socket, String message) {
            try (DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream()))  {
                dataOut.writeUTF(message);
                dataOut.flush();
            } catch (IOException ex) {
                System.err.println("[Server]  发送错误信息失败: " + ex.getMessage());
            }
        }
    }

}