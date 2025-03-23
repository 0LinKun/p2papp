package web;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.fileupload.MultipartStream;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.*;
import java.util.logging.Formatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FileServer {

    private static final int PORT = 8082;
    private static final String UPLOAD_DIR = "file";
    private static final Set<String> ALLOWED_ORIGINS = new HashSet<>(Arrays.asList(
            "http://localhost", "https://your-domain.com", "http://127.0.0.1"
    ));
    // 新增静态日志对象
    private static final Logger logger = Logger.getLogger(FileServer.class.getName());

    public static void Filemain() throws IOException {
        // 初始化上传目录
        Files.createDirectories(Paths.get(UPLOAD_DIR));

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        // 新增日志目录创建
        Files.createDirectories(Paths.get("logs"));

        // 配置日志处理器（自动滚动）
        FileHandler fileHandler = new FileHandler(
                "logs/FileServer_%g.log",   // 文件名模式
                1024 * 1024,           // 单文件最大1MB
                5,                     // 保留5个历史文件
                true                   // 追加模式
        );

        // 自定义日志格式（时间+IP+文件名）
        fileHandler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                String action = record.getMessage().startsWith("[ 下载]") ? "DOWNLOAD" : "UPLOAD";
                String filename = record.getMessage().replace("[ 下载] ", "");
                return String.format("[%1$tF  %1$tT] %2$s | %3$-7s | %4$s%n",
                        new Date(record.getMillis()),
                        record.getParameters()[0],  // IP地址
                        action,                    // 操作类型
                        filename                   // 文件名
                );
            }
        });

        logger.addHandler(fileHandler);
        logger.setUseParentHandlers(false);  // 禁用控制台输出

        // 全局请求处理器
        server.createContext("/", exchange -> {
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
        if (ALLOWED_ORIGINS.contains(origin)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        }
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().add("Access-Control-Max-Age", "86400");

        // 预检请求快速响应
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        // 路由分发
        switch (exchange.getRequestURI().getPath()) {
            case "/list":
                handleList(exchange);
                break;
            case "/upload":
                handleUpload(exchange);
                break;
            case "/download":
                handleDownload(exchange);
                break;
            case "/":
                handleIndex(exchange);
            default:
                sendError(exchange, 404, "接口不存在");
        }
    }

    private static void handleIndex(HttpExchange exchange) {
        try {
            // 1. 构建文件路径（兼容Windows/Linux）
            Path indexPath = Paths.get("src/web", "index.html");

            // 2. 验证文件存在性
            if (!Files.exists(indexPath)) {
                sendErrorResponse(exchange, 404, "<h1>404 - File Not Found</h1>");
                return;
            }

            // 3. 设置响应头（包含MIME类型识别）
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", detectMimeType(indexPath.toString()));

            // 4. 分块读取文件内容（避免大文件内存溢出）
            try (InputStream fis = Files.newInputStream(indexPath);
                 OutputStream os = exchange.getResponseBody()) {

                // 5. 发送HTTP 200头（延迟响应体传输）
                exchange.sendResponseHeaders(200, Files.size(indexPath));

                // 6. 缓冲区传输（8KB最佳实践）
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            // 7. 异常处理（带时间戳日志）
            System.err.println("[ERROR][" + LocalDateTime.now() + "] Index处理失败: " + e.getMessage());
            sendErrorResponse(exchange, 500, "<h1>500 - Internal Server Error</h1>");
        }
    }

    // 辅助方法：MIME类型检测（扩展识别）
    private static String detectMimeType(String filename) {
        if (filename.endsWith(".html")) return "text/html; charset=utf-8";
        if (filename.endsWith(".css")) return "text/css";
        if (filename.endsWith(".js")) return "application/javascript";
        return "application/octet-stream";
    }

    // 辅助方法：错误响应封装
    private static void sendErrorResponse(HttpExchange exchange, int code, String message) {
        try {
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(code, message.length());
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
                if (file.isFile()) fileList.add(file.getName());
            }
        }
        String response = new Gson().toJson(fileList);
        sendResponse(exchange, 200, response);
    }

    // 文件上传接口
    // 文件上传处理逻辑
    private static void handleUpload(HttpExchange exchange) throws IOException {
        // 仅处理POST请求
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        // 解析Content-Type获取boundary
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("multipart/form-data")) {
            sendResponse(exchange, 400, "Invalid Content-Type");
            return;
        }

        // 提取boundary参数
        String boundary = contentType.split("boundary=")[1];
        Path uploadDir = Paths.get(UPLOAD_DIR);
        Files.createDirectories(uploadDir);

        try (InputStream is = exchange.getRequestBody()) {
            // 使用Apache Commons FileUpload的MultipartStream（需添加依赖）
            MultipartStream multipartStream = new MultipartStream(is,
                    boundary.getBytes(StandardCharsets.UTF_8),
                    4096, // 缓冲区大小
                    null   // 进度监听器

            );

            boolean nextPart = multipartStream.skipPreamble();
            while (nextPart) {
                // 解析part头部
                String headers = multipartStream.readHeaders();

                // 匹配文件名
                Pattern fileNamePattern = Pattern.compile(
                        "filename=\"(.*?)\"",
                        Pattern.CASE_INSENSITIVE
                );
                Matcher matcher = fileNamePattern.matcher(headers);

                if (matcher.find()) {
                    String fileName = URLDecoder.decode(
                            matcher.group(1),
                            StandardCharsets.UTF_8.name()
                    );
                    // 记录日志（核心新增点）
                    String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
                    logger.log(Level.INFO, fileName, new Object[]{clientIP});
                    // 直接保存原始文件名
                    Path filePath = uploadDir.resolve(fileName).normalize();

                    // 确保保存到指定目录
                    if (!filePath.startsWith(uploadDir)) {
                        sendResponse(exchange, 403, "Invalid file path");
                        return;
                    }

                    // 写入文件
                    try (OutputStream os = Files.newOutputStream(filePath)) {
                        multipartStream.readBodyData(os);
                    }

                    sendResponse(exchange, 200, "Upload success: " + fileName);
                    return;
                }
                nextPart = multipartStream.readBoundary();
            }
            sendResponse(exchange, 400, "No file found");
        } catch (Exception e) {
            sendResponse(exchange, 500, "Upload failed: " + e.getMessage());
        }
    }


    // 文件下载接口
    private static void handleDownload(HttpExchange exchange) throws IOException {
        try {
            // 参数解析
            String query = exchange.getRequestURI().getQuery();
            if (query == null) {
                sendResponse(exchange, 400, "Missing query parameters");
                return;
            }

            Map<String, String> params = parseQuery(query);
            String filename = params.get("file");
            if (filename == null || filename.isEmpty()) {
                sendResponse(exchange, 400, "Invalid file parameter");
                return;
            }

            // 文件名安全处理
            filename = sanitizeFilename(filename);
            if (filename == null) {
                sendResponse(exchange, 400, "Illegal filename");
                return;
            }

            // 路径安全验证
            Path uploadDir = Paths.get(UPLOAD_DIR).toRealPath();
            Path filePath = uploadDir.resolve(filename).normalize();
            if (!filePath.startsWith(uploadDir)) {
                sendResponse(exchange, 403, "Access denied");
                return;
            }

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendResponse(exchange, 404, "File not found");
                return;
            }

            //  新增日志记录
            String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
            logger.log(Level.INFO, "[下载] " + filename, new Object[]{clientIP});

            // 设置响应头
            exchange.getResponseHeaders().add("Content-Type",
                    Files.probeContentType(filePath) + "; charset=UTF-8");
            exchange.getResponseHeaders().add("Content-Disposition",
                    "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encodeRFC5987(filename));
            exchange.getResponseHeaders().add("Content-Length", String.valueOf(Files.size(filePath)));

            // 分块传输（支持大文件）
            exchange.sendResponseHeaders(200, Files.size(filePath));
            try (OutputStream os = exchange.getResponseBody();
                 InputStream is = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[8192]; // 增大缓冲区提升性能
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        } catch (InvalidPathException e) {
            sendResponse(exchange, 400, "Invalid path characters");
        } catch (Exception e) {
            sendResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    private static String encodeRFC5987(String filename) throws UnsupportedEncodingException {
        return URLEncoder.encode(filename, String.valueOf(StandardCharsets.UTF_8))
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~"); // RFC 3986保留字符特殊处理
    }

    private static String sanitizeFilename(String rawName) {
        // 步骤1：路径标准化与截断
        String name = Paths.get(rawName).getFileName().toString();
        // 步骤2：替换非法字符
        name = name.replaceAll("[\\\\/:*?\"<> |]", "_");
        // 步骤3：过滤保留名称（Windows）
        if (name.matches("(?i)^(CON |PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?$")) {
            return null;
        }
        return name.isEmpty() ? null : name;
    }

    // 辅助方法：解析URL查询参数
    private static Map<String, String> parseQuery(String query) {
        return Arrays.stream(query.split("&"))
                .map(p -> p.split("="))
                .collect(Collectors.toMap(
                        arr -> arr[0],
                        arr -> arr.length > 1 ? arr[1] : ""
                ));
    }

    // 统一响应工具
    private static void sendResponse(HttpExchange exchange, int code, String message) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        byte[] response = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static void sendError(HttpExchange exchange, int code, String error) throws IOException {
        String response = "{\"error\":\"" + error + "\"}";
        sendResponse(exchange, code, response);
    }
}