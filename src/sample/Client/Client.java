package sample.Client;

import sample.Server.Server;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import javax.swing.*;

public class Client implements Runnable {

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

    public Client(String host, JTextArea displayArea) {
        this.host = host;
        this.displayArea = displayArea;
        new Thread(this).start();
    }

    public void checkmessage(String message) {

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
                displayArea.append(infor[0] + "#" + ip + "#" + infor[1] + "\n");
                nickName = infor[0];
                sendMessage(infor[0] + "#" + ip + "#" + infor[1]);
                this.connect_success = true;
                break;
            } else {
                displayArea.append("语法错误, 请输入格式为你的用户名#端口号");
            }
        }
    }
    // 在Client类中添加：
    public synchronized void sendBinaryData(byte[] buffer, int bytesRead,Socket filesock)
            throws IOException {

        OutputStream os = filesock.getOutputStream();

        // 先发送数据长度（4字节头）
        ByteBuffer header = ByteBuffer.allocate(4);
        header.putInt(bytesRead);
        os.write(header.array());

        // 发送实际数据
        os.write(buffer,  0, bytesRead);
        os.flush();
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
                displayArea.append(msg + "\n");
                while (true) {
                    if (this.connect_success == true) break;
                }
            }
            // 启动消息接收线程
            new Thread(() -> {
                while (true) {
                    try {
                        String response = in.readLine();
                        if (response == null || response.equalsIgnoreCase("exit")) {
                            break;
                        }
                        SwingUtilities.invokeLater(() -> {
                            //displayArea.append(socket.getInetAddress().toString()+":"+socket.getPort()+"\n");
                            displayArea.append(response + "\n");
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                        SwingUtilities.invokeLater(() -> {
                            displayArea.append("接受到错误信息: " + e.getMessage() + "\n");
                        });
                        break;
                    }
                }
            }).start();


        } catch (Exception e) {
            e.printStackTrace();
            displayArea.append("链接服务错误: " + e.getMessage() + "\n");
        }
    }

    public synchronized void sendMessage(String message) {
        if (message.equals("cls")) {
            displayArea.setText("");
            return;
        }
        out.println(message);
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