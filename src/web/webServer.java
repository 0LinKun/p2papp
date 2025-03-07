package web;

import java.io.*;
import java.net.*;
import java.nio.file.*;


public class webServer {
    private static final int PORT = 80; // 监听端口
    private static final String WEB_ROOT = "src/web"; // 静态文件根目录

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println(" 服务器已启动，监听端口：" + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start(); // 多线程处理
            }
        } catch (IOException e) {
            System.err.println(" 服务器启动失败：" + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             BufferedOutputStream out = new BufferedOutputStream(clientSocket.getOutputStream()))  {

            // 读取客户端请求
            String requestLine = in.readLine();
            if (requestLine == null) {
                sendDefaultPage(out); // 默认返回 index.html
                return;
            }

            // 解析请求路径
            String requestPath;
            try {
                String[] requestParts = requestLine.split(" ");
                if (requestParts.length  < 2) {
                    System.err.println(" 请求格式错误，返回默认页面");
                    sendDefaultPage(out); // 默认返回 index.html
                    return;
                }
                requestPath = requestParts[1];
            } catch (Exception e) {
                System.err.println(" 请求解析异常：" + e.getMessage());
                sendDefaultPage(out); // 默认返回 index.html
                return;
            }

            // 获取文件路径
            String filePath = WEB_ROOT + (requestPath.equals("/")  ? "/index.html"  : requestPath);

            // 检查文件是否存在
            if (!Files.exists(Paths.get(filePath)))  {
                System.err.println(" 文件未找到：" + filePath);
                sendDefaultPage(out); // 默认返回 index.html
                return;
            }

            // 获取文件MIME类型
            String contentType = getContentType(filePath);

            // 发送HTTP响应头
            out.write("HTTP/1.1  200 OK\r\n".getBytes());
            out.write(("Content-Type:  " + contentType + "\r\n").getBytes());
            out.write("Connection:  close\r\n\r\n".getBytes());

            // 发送文件内容
            Files.copy(Paths.get(filePath),  out);
            System.out.println(" 文件已发送：" + filePath + "，MIME类型：" + contentType);
        } catch (IOException e) {
            System.err.println(" 客户端处理异常：" + e.getMessage());
        } finally {
            try {
                clientSocket.close();  // 确保关闭连接
            } catch (IOException e) {
                System.err.println(" 关闭连接异常：" + e.getMessage());
            }
        }
    }

    private static void sendDefaultPage(BufferedOutputStream out) throws IOException {
        String filePath = WEB_ROOT + "/index.html";
        if (!Files.exists(Paths.get(filePath)))  {
            sendErrorResponse(out, 404, "文件未找到");
            return;
        }

        // 发送HTTP响应头
        out.write("HTTP/1.1  200 OK\r\n".getBytes());
        out.write("Content-Type:  text/html\r\n".getBytes());
        out.write("Connection:  close\r\n\r\n".getBytes());

        // 发送文件内容
        Files.copy(Paths.get(filePath),  out);
        System.out.println(" 默认页面已发送：" + filePath);
    }

    private static String getContentType(String filePath) {
        if (filePath.endsWith(".html"))  return "text/html";
        if (filePath.endsWith(".css"))  return "text/css";
        if (filePath.endsWith(".js"))  return "application/javascript";
        return "application/octet-stream";
    }

    private static void sendErrorResponse(BufferedOutputStream out, int statusCode, String message) throws IOException {
        out.write(("HTTP/1.1  " + statusCode + " " + message + "\r\n").getBytes());
        out.write("Content-Type:  text/plain\r\n".getBytes());
        out.write("Connection:  close\r\n\r\n".getBytes());
        out.write((message  + "\r\n").getBytes());
        System.err.println(" 错误响应：" + statusCode + " - " + message);
    }
}