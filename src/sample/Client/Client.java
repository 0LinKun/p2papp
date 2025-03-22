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


public class Client implements Runnable {
    private final JTextArea onlineArea;
    final JTextArea displayArea;
    public final String host;
    public ClientFileServer clientFileServer;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean connect_success = false;
    private volatile boolean connected = false;
    public Thread runningThread;
    public ArrayList<HashMap<String, String>> userList;
    public Map<String, FileInfo> currentFileList;
    FileListManager fileListManager;
    public Client(String host, JTextArea displayArea, JTextArea onlineArea) {
        this.host = host;
        this.displayArea = displayArea;
        this.onlineArea = onlineArea;
        IpAddressFetcher.IpAddress(displayArea);
        new Thread(this).start();
        // 在类中定义锁对象
        fileListManager=new FileListManager();
    }

        public ClientFileServer getClientFileServer(){return this.clientFileServer;}

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
                        }else if(ListenServerFileList(response)){//接受到files启动刷新服务器文件列表格式
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

    public boolean ListenServerFileList(String response) {
        Gson gson = new Gson();
        try {
            // 解析顶层数据结构
            Map<String, Object> responseMap = gson.fromJson(response, new TypeToken<Map<String, Object>>(){}.getType());
            if (!responseMap.containsKey("files"))  {
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
                fileInfo.filename  = (String) fileEntry.get("filename");
                fileInfo.total_chunks  = ((Double) fileEntry.get("total_chunks")).intValue();

                // 分块数据映射
                List<Map<String, Object>> chunks = (List<Map<String, Object>>) fileEntry.get("chunks");
                fileInfo.chunks  = new ArrayList<>();

                for (Map<String, Object> chunk : chunks) {
                    FileInfo.ChunkInfo chunkInfo = new FileInfo.ChunkInfo(
                            ((Double) chunk.get("number")).intValue(),
                            (String) chunk.get("hash")
                    );
                    fileInfo.chunks.add(chunkInfo);
                }

                serverFileList.put(fileInfo.filename,  fileInfo);
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

    // 2. 修改解析代码
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

    public synchronized void sendMessage(String message) {
        out.println(message);
    }

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
                this.clientFileServer = new ClientFileServer(Integer.parseInt(port),this);
                clientFileServer.start();
                break;
            } else {
                ClientLogger.log(displayArea, "语法错误, 请输入格式为你的用户名#端口号");
            }
        }
    }

    // 在Client类中添加：
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

    public boolean isConnected() {
        return connected;
    }


}