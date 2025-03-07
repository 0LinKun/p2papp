package web;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileServer {
    private static final int PORT = 8082;
    private static final String UPLOAD_DIR = "file";
    private static final Set<String> ALLOWED_ORIGINS = new HashSet<>(Arrays.asList(
            "http://localhost", "https://your-domain.com","http://127.0.0.1"
    ));

    public static void main(String[] args) throws IOException {
        // 初始化上传目录
        Files.createDirectories(Paths.get(UPLOAD_DIR));

        HttpServer server = HttpServer.create(new  InetSocketAddress(PORT), 0);

        // 全局请求处理器
        server.createContext("/",  exchange -> {
            try {
                handleRequest(exchange);
            } catch (Exception e) {
                sendError(exchange, 500, "服务器内部错误");
            }
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println(" 文件服务器启动，端口：" + PORT);
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        // 动态CORS配置
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (ALLOWED_ORIGINS.contains(origin))  {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin",  origin);
        }
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods",  "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers",  "Content-Type, Authorization");
        exchange.getResponseHeaders().add("Access-Control-Max-Age",  "86400");

        // 预检请求快速响应
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod()))  {
            exchange.sendResponseHeaders(204,  -1);
            return;
        }

        // 路由分发
        switch (exchange.getRequestURI().getPath())  {
            case "/list":
                handleList(exchange);
                break;
            case "/upload":
                handleUpload(exchange);
                break;
            case "/download":
                handleDownload(exchange);
                break;
            case"/index":
                handleIndex(exchange);
            default:
                sendError(exchange, 404, "接口不存在");
        }
    }

    private static void handleIndex(HttpExchange exchange) {
        try {
            // 1. 构建文件路径（兼容Windows/Linux）
            Path indexPath = Paths.get("src/web",  "index.html");

            // 2. 验证文件存在性
            if (!Files.exists(indexPath))  {
                sendErrorResponse(exchange, 404, "<h1>404 - File Not Found</h1>");
                return;
            }

            // 3. 设置响应头（包含MIME类型识别）
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type",  detectMimeType(indexPath.toString()));

            // 4. 分块读取文件内容（避免大文件内存溢出）
            try (InputStream fis = Files.newInputStream(indexPath);
                 OutputStream os = exchange.getResponseBody())  {

                // 5. 发送HTTP 200头（延迟响应体传输）
                exchange.sendResponseHeaders(200,  Files.size(indexPath));

                // 6. 缓冲区传输（8KB最佳实践）
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer))  != -1) {
                    os.write(buffer,  0, bytesRead);
                }
            }
        } catch (IOException e) {
            // 7. 异常处理（带时间戳日志）
            System.err.println("[ERROR]["  + LocalDateTime.now()  + "] Index处理失败: " + e.getMessage());
            sendErrorResponse(exchange, 500, "<h1>500 - Internal Server Error</h1>");
        }
    }

    // 辅助方法：MIME类型检测（扩展识别）
    private static String detectMimeType(String filename) {
        if (filename.endsWith(".html"))  return "text/html; charset=utf-8";
        if (filename.endsWith(".css"))  return "text/css";
        if (filename.endsWith(".js"))  return "application/javascript";
        return "application/octet-stream";
    }

    // 辅助方法：错误响应封装
    private static void sendErrorResponse(HttpExchange exchange, int code, String message) {
        try {
            exchange.getResponseHeaders().set("Content-Type",  "text/html");
            exchange.sendResponseHeaders(code,  message.length());
            exchange.getResponseBody().write(message.getBytes());
        } catch (IOException ex) {
            System.err.println(" 发送错误响应失败: " + ex.getMessage());
        } finally {
            exchange.close();
        }
    }

    // 文件列表接口
    private static void handleList(HttpExchange exchange) throws IOException {
        File[] files = new File(UPLOAD_DIR).listFiles();
        List<String> fileList = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (file.isFile())  fileList.add(file.getName());
            }
        }
        String response = new Gson().toJson(fileList);
        sendResponse(exchange, 200, response);
    }

    // 文件上传接口
    private static void handleUpload(HttpExchange exchange) throws IOException {
        // 验证请求方法
        if (!"POST".equals(exchange.getRequestMethod()))  {
            sendError(exchange, 405, "方法不允许");
            return;
        }

        // 获取原始文件名（双重保障方案）
        String fileName = null;
        try {
            // 方案1：从请求头获取
            String disposition = exchange.getRequestHeaders().getFirst("Content-Disposition");
            if (disposition != null) {
                Pattern pattern = Pattern.compile("filename\\*?=\"?(.*?)\"?$");
                Matcher matcher = pattern.matcher(disposition);
                if (matcher.find())  {
                    fileName = URLDecoder.decode(matcher.group(1),  StandardCharsets.UTF_8.name());
                }
            }

            // 方案2：从自定义请求头获取（前端新增的X-File-Name）
            if (fileName == null) {
                fileName = exchange.getRequestHeaders().getFirst("X-File-Name");
                if (fileName != null) {
                    fileName = URLDecoder.decode(fileName,  StandardCharsets.UTF_8.name());
                }
            }

//            // 安全过滤
//            fileName = fileName
//                    .replaceAll("[\\\\/]", "_")  // 匹配 \ 或 /
//                    .replaceAll("^\\.+", "")     // 过滤开头点号
//                    .replace("\0", "")           // 替换空字符
//                    .replaceAll("(?i)[^\\w\\-_. ]", ""); // 新增特殊字符过滤

            // 最终文件名校验
            if (fileName == null || fileName.isEmpty())  {
                throw new IllegalArgumentException("无效的文件名");
            }
        } catch (Exception e) {
            sendError(exchange, 400, "文件名解析失败");
            return;
        }

        // 文件保存逻辑
        try (InputStream is = exchange.getRequestBody();
             OutputStream os = new FileOutputStream(UPLOAD_DIR + "/" + fileName)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer))  != -1) {
                os.write(buffer,  0, bytesRead);
            }
            sendResponse(exchange, 200, "上传成功");
        } catch (Exception e) {
            sendError(exchange, 500, "上传失败: " + e.getMessage());
        }
    }

    // 文件下载接口
    private static void handleDownload(HttpExchange exchange) throws IOException {
        String fileName = exchange.getRequestURI().getQuery().split("=")[1];
        Path filePath = Paths.get(UPLOAD_DIR,  fileName);

        if (!Files.exists(filePath))  {
            sendError(exchange, 404, "文件不存在");
            return;
        }

        exchange.getResponseHeaders().add("Content-Type",  "application/octet-stream");
        exchange.getResponseHeaders().add("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(fileName,  "UTF-8") + "\"");

        exchange.sendResponseHeaders(200,  Files.size(filePath));
        try (OutputStream os = exchange.getResponseBody();
             InputStream is = Files.newInputStream(filePath))  {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer))  != -1) {
                os.write(buffer,  0, bytesRead);
            }
        }
    }

    // 统一响应工具
    private static void sendResponse(HttpExchange exchange, int code, String message) throws IOException {
        exchange.getResponseHeaders().add("Content-Type",  "application/json; charset=utf-8");
        byte[] response = message.getBytes("UTF-8");
        exchange.sendResponseHeaders(code,  response.length);
        try (OutputStream os = exchange.getResponseBody())  {
            os.write(response);
        }
    }

    private static void sendError(HttpExchange exchange, int code, String error) throws IOException {
        String response = "{\"error\":\"" + error + "\"}";
        sendResponse(exchange, code, response);
    }
}