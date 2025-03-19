package sample.Client;

import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import sample.Server.IpAddressFetcher;
import sample.Server.Server;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.swing.*;
import com.google.gson.Gson;

public class Client implements Runnable {

    private final JTextArea onlineArea;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private final JTextArea displayArea;
    private String nickName;
    private BufferedReader line;
    private final boolean open_server = true;
    private volatile boolean connect_success = false;
    private final Object lock = new Object(); // 用于同步sendMessage
    private final String host;
    private volatile boolean connected = false;

    public Client(String host, JTextArea displayArea,JTextArea onlineArea) {
        this.host = host;
        this.displayArea = displayArea;
        this.onlineArea=onlineArea;
        IpAddressFetcher.IpAddress(displayArea);
        new Thread(this).start();
    }



    @Override
    public void run() {
        try {
            socket = new Socket(this.host, Server.SERVER_PORT); // Use actual host address as needed
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            connected = true;
            //System.out.println("run"+Thread.currentThread());
            String msg = in.readLine(); // First word received must be welcome word
            Thread.sleep(100);//等待输出连接成功字样

            if (msg != null && msg.equals(Server.Welcome_Word)) {
                ClientLogger.log(displayArea,  msg + "\n");
                while (true) {
                    if (this.connect_success == true) break;
                }
            }

            // 启动消息接收线程
            new Thread(() -> {
                Boolean FileState = false;
                while (true) {
                    try {
                        String response = in.readLine();
                        if (response == null || response.equalsIgnoreCase("exit")) {
                            break;
                        } else if (response.equals("share")) {
                            if (FileState == false) {
                                FileState = true;
                                new FileReceiver().start();
                            }
                        }else if(ListenUserlist(response)){
                            continue;
                        }

                        SwingUtilities.invokeLater(() -> {
                            ClientLogger.log(displayArea,  response + "\n");
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                        SwingUtilities.invokeLater(() -> {
                            ClientLogger.log(displayArea,  "接受到错误信息: " + e.getMessage() + "\n");
                        });
                        break;
                    }
                }
            }).start();


        } catch (Exception e) {
            e.printStackTrace();
            ClientLogger.log(displayArea,  "链接服务错误: " + e.getMessage() + "\n");
        }
    }

    // 1. 创建对应的用户模型类
    class User {
        private String username;
        private int id;

        // 必须包含getter方法
        public String getUsername() { return username; }
    }

    // 2. 修改解析代码
    private boolean ListenUserlist(String response) {
        if (response.equals("USER_LIST"))  {
            try {
                // 读取数据长度
                int length = Integer.parseInt(in.readLine());

                // 读取JSON数据
                char[] buffer = new char[length];
                in.read(buffer,  0, length);
                String jsonData = new String(buffer);

                // 解析JSON数据
                Gson gson = new Gson();
                Type type = new TypeToken<ArrayList<HashMap<String, String>>>(){}.getType();
                ArrayList<HashMap<String, String>> userList = gson.fromJson(jsonData,  type);

                // 更新在线用户显示
                // 更新在线用户显示
                SwingUtilities.invokeLater(()  -> {
                    onlineArea.setText("");
                    for (HashMap<String, String> user : userList) {
                        String line = String.format("%s:%s:%s\n",user.get("NAME"),user.get("IP"), user.get("PORT"));
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

        InetAddress addr = null;
        try {
            addr = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        String ip = addr.getHostAddress();
        while (true) {
            String[] infor = message.split("#");
            if (infor.length == 2 && Integer.parseInt(infor[1]) > 0 && Integer.parseInt(infor[1]) < 65535) {
                ClientLogger.log(displayArea,  infor[0] + "#" + ip + "#" + infor[1] + "\n");
                nickName = infor[0];
                sendMessage(infor[0] + "#" + ip + "#" + infor[1]);
                this.connect_success = true;
                break;
            } else {
                ClientLogger.log(displayArea,  "语法错误, 请输入格式为你的用户名#端口号");
            }
        }
    }

    // 在Client类中添加：
    public synchronized void sendBinaryData(byte[] buffer, int bytesRead, Socket filesock)
            throws IOException {

        OutputStream os = filesock.getOutputStream();

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