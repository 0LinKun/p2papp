package sample.Client;

import com.google.gson.JsonParseException;
import sample.AllNeed.FileListManager;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientFileServer extends Thread {
    private static final int CONNECT_TIMEOUT = 3000; // 3秒连接超时
    private static FileListManager fileListManager;
    private final int port;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private volatile boolean isRunning = true;
    private ServerSocket serverSocket;
    private List<String> ClientList; // ["ip:port", ...]

    public ClientFileServer(int port) {
        this.port = port;
        this.fileListManager = new FileListManager();
    }

    // 核心连接验证逻辑
    private static boolean connectAndVerify(String ip, int port) {
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

    // 协议交互流程
    private static boolean performHandshake(PrintWriter out, BufferedReader in) throws IOException {
        out.println("LIST_REQUEST");  // 发送列表请求
        return in.readLine().startsWith("FILE_LIST");  // 验证响应头
    }

    // 哈希比对与文件请求决策
    private static boolean handleFileTransfer(BufferedReader in) throws NoSuchAlgorithmException {
        if (fileListManager.compareFileList(in)) {
            System.out.printf(" 【%tT】发现匹配服务器，触发传输%n", System.currentTimeMillis());
            return true;
        }
        System.out.printf(" 【%tT】文件列表不匹配，继续搜索%n", System.currentTimeMillis());
        return false;
    }

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

    // 智能连接策略实现
    public void startFileDiscovery(List<String> ClientList) {
        this.ClientList = ClientList;
        for (String address : new SmartServerIterator(ClientList)) {
            String[] parts = address.split(":");
            if (connectAndVerify(parts[0], Integer.parseInt(parts[1]))) {
                break; // 找到有效服务器后终止遍历
            }
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

    // 智能服务器迭代器（防止顺序依赖）
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

    private class ClientHandler implements Runnable {
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
            // 预留分块传输处理逻辑
            System.out.printf(" 【%tT】收到分块请求%n", System.currentTimeMillis());
            out.println("CHUNK_TRANSFER_READY");
        }
    }

}