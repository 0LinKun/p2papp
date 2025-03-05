package sample.Server;

import sample.AllNeed.FileInfo;
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
import java.util.Arrays;
import java.util.HashMap;

public class Server {
    public static final int SERVER_PORT = 10001;
    // 文件传输专用端口
    public static final int FILE_PORT = 8081;
    public static String IP = "IP";
    public static String PORT = "PORT";
    public static String NICKNAME = "NAME";
    public static final String Welcome_Word = "欢迎加入, 请输入你的用户名#端口号";
    public ServerSocket ss;
    public ArrayList<CreateServerThread> userThreads = new ArrayList<>();
    private final ArrayList<HashMap<String, String>> User_List = new ArrayList<>();
    private HashMap<String, String> indentifer;
    public JTextArea displayArea;
    Instant now;
    FileListManager fileListManager = new FileListManager();

    public String nowtime(Instant now){
        now = Instant.now();
        // 将 Instant 转换为 ZonedDateTime 并设置为中国时区 (Asia/Shanghai)
        ZonedDateTime zonedNowInChina = now.atZone(ZoneId.of("Asia/Shanghai"));
        // 创建一个带有中文日期格式的格式化器
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分ss秒").withZone(ZoneId.of("Asia/Shanghai"));
        return  zonedNowInChina.format(formatter);
    }

    public Server(JTextArea displayArea) {
        this.displayArea = displayArea;
        try {
            ss = new ServerSocket(SERVER_PORT);
            displayArea.append(nowtime(now) + "  " + "服务器ip：" + ss.getLocalSocketAddress().toString() + "   服务开启端口： " + SERVER_PORT + "\n");
            new Thread(() -> acceptConnections()).start();
        } catch (Exception e) {
            e.printStackTrace();
            displayArea.append(nowtime(now) + "  " + "服务开启错误: " + e.getMessage() + "\n");
        }
    }




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

    public void broadcastToClients(String message) {
        for (CreateServerThread thread : userThreads) {
            thread.sendMessage(message);
        }
    }

    // Inner class CreateServerThread
    public class CreateServerThread extends Thread {
        private final Socket client;
        private BufferedReader in;
        private PrintWriter out;
        private final Server parent;

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
                }
                else {
                    out.println(nowtime(now) + "  " + "您的请求被拒绝.");
                    client.close();
                    return;
                }

                while (true) {
                    line = in.readLine();
                    displayArea.append(nowtime(now) + "  " + client.getInetAddress() + "#" + client.getPort() + ":   " + line + "\n");
                    if (line == null || line.equalsIgnoreCase("exit")) {
                        break;
                    } else if (line.equals("ls")) {
                        out.println(listAllUsers());
                        displayArea.append(listAllUsers());
                    } else if (line.equals("filelist") || line.equals("fl")) {
                        fileListManager.updateAndSendFileList(out);

                    } else if (line.equals("help")) {
                        out.println("ls \t list online \n exit  exit \n send file  send file\n");
                    }else if (line.equals("upload")){
                        startFileServer();
                    }
                    else {
                        broadcast(nowtime(now) + "  " + client.getInetAddress() + "#" + client.getPort() + ":   " + line + "\n");
                    }
                }
                removeFromList();
                broadcast(nowtime(now) + "  " + client.getInetAddress() + "#" + client.getPort() + ":   " + line + " 离开.");
                client.close();
            } catch (Exception e) {
                e.printStackTrace();
                displayArea.append(nowtime(now) + "  " + "连接线程错误: " + e.getMessage() + "\n");
                removeFromList();
            }
        }

        public void broadcast(String msg) {
            for (int i = 0; i < userThreads.size(); i++) {
                if (userThreads.get(i) != this) {
                    userThreads.get(i).sendMessage(msg);
                }
            }
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
                displayArea.append(nowtime(now) + "  " + infor[0] + "  " + infor[1] + "  " + infor[2] + "连接成功\n");
                return true;
            }
        }

        public void removeFromList() {
            if (indentifer != null) {
                displayArea.append(nowtime(now) + "  " + indentifer.get(NICKNAME) + "  " + indentifer.get(IP) + "  " + indentifer.get(PORT) + "  已掉线\n");
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

    static class FileTransferHandler extends Thread {
        private final Socket dataSocket;

        public FileTransferHandler(Socket socket) {
            this.dataSocket  = socket;
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

        private byte[] getFileChunk(File file, int chunkNumber) throws IOException {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                long start = (chunkNumber - 1) * FileInfo.chunk_size;
                raf.seek(start);
                byte[] buffer = new byte[(int) FileInfo.chunk_size];
                int read = raf.read(buffer);
                return read == -1 ? new byte[0] : Arrays.copyOf(buffer,  read);
            }
        }
    }
}