package org.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * 大文件分块存储HTTP服务端（支持元数据查询和流量控制）
 *
 * <h2>核心功能：</h2>
 * <ul>
 *     <li>提供分块文件下载服务（路径：/mp31.mp4_blocks/{chunkName} ）</li>
 *     <li>元数据JSON查询接口（路径：/_metadata.json ）</li>
 *     <li>基于Guava的QPS限流（默认：1000请求/秒）</li>
 *     <li>SHA-256哈希校验头（X-Content-SHA256）</li>
 * </ul>
 *
 * <h2>安全机制：</h2>
 * <ol>
 *     <li>路径规范化验证：防止目录遍历攻击</li>
 *     <li>响应头校验：确保数据完整性</li>
 *     <li>连接池隔离：固定线程池（CPU核心数×2）</li>
 * </ol>
 */
public class BlockServer {
    /**
     * 默认服务端口
     */
    private static final int DEFAULT_PORT = 8080;
    private final Path baseDir;
    private final String filename;
    /**
     * 速率限制器（令牌桶算法）
     */
    private final RateLimiter rateLimiter = RateLimiter.create(1000);
    private FileConfig metadata;

    /**
     * 初始化文件存储服务
     *
     * @param storagePath 存储根目录路径（需包含_metadata.json ）
     * @throws IOException 当出现以下情况时抛出：
     *                     <ul>
     *                         <li>路径解析失败</li>
     *                         <li>元数据文件缺失</li>
     *                         <li>文件权限不足</li>
     *                     </ul>
     */
    public BlockServer(String storagePath, String filename) throws IOException {
        this.filename = filename;
        this.baseDir = Paths.get(storagePath).toRealPath();
        loadMetadata();
    }


    public static void BlockServermain(String file) throws IOException {
        new BlockServer("file/", file).start();
    }

    private void loadMetadata() throws IOException {
        Path blockDir = baseDir.resolve(filename + "_blocks");
        Path metaFile = blockDir.resolve("_metadata.json");

        // 增加目录存在性校验
        if (!Files.isDirectory(blockDir)) {
            throw new FileNotFoundException("分块目录缺失: " + blockDir);
        }

        // 增加元数据文件校验
        if (!Files.isRegularFile(metaFile)) {
            throw new JsonParseException("元数据文件未找到");
        }

        this.metadata = new ObjectMapper().readValue(metaFile.toFile(), FileConfig.class);
    }

    /**
     * 启动HTTP服务（阻塞式）
     *
     * @throws IOException 当出现以下情况时抛出：
     *                     <ul>
     *                         <li>端口占用</li>
     *                         <li>Socket配置异常</li>
     *                     </ul>
     * @implNote 线程池策略：
     * 固定大小（CPU核心数×2），防止资源耗尽
     */
    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(DEFAULT_PORT), 0);

        // 动态生成访问路径：/${filename}_blocks/
        String blockPath = "/" + filename + "_blocks/";
        server.createContext(blockPath, this::handleBlock);
        server.createContext("/_metadata.json", this::handleMetadata);

        server.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2));
        server.start();
        System.out.println("Server  started on port " + DEFAULT_PORT);
    }

    /**
     * 处理分块下载请求（核心逻辑）
     *
     * <h3>处理流程：</h3>
     * <pre>
     * 1. 速率限制检查 → 2. 路径合法性验证 →
     * 3. 哈希值计算 → 4. 零拷贝传输
     * </pre>
     *
     * @param exchange HTTP请求上下文
     */
    private void handleBlock(HttpExchange exchange) throws IOException {
        if (!rateLimiter.tryAcquire()) {
            sendResponse(exchange, 429, "Too many requests");
            return;
        }
        try {
            // 动态获取块路径（原硬编码替换为filename）
            String requestPath = exchange.getRequestURI().getPath();
            String chunkName = requestPath.substring(
                    ("/" + filename + "_blocks/").length()
            );
            // 动态构建块文件路径
            Path chunkFile = baseDir.resolve(filename + "_blocks").resolve(chunkName).normalize();
            System.out.println("Resolved  path: " + chunkFile.toAbsolutePath());
            if (!chunkFile.startsWith(baseDir) || Files.notExists(chunkFile)) {
                sendResponse(exchange, 404, "Chunk not found");
                return;
            }
            exchange.getResponseHeaders().add("X-Content-SHA256", computeHash(chunkFile));
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");

            sendFile(exchange, chunkFile);
        } catch (Exception e) {
            sendResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    /**
     * 元数据响应处理器（线程安全）
     *
     * <h3>数据序列化：</h3>
     * <ul>
     *     <li>使用Jackson进行JSON序列化</li>
     *     <li>Content-Type: application/json</li>
     *     <li>严格长度声明（data.length ）</li>
     * </ul>
     */
    private void handleMetadata(HttpExchange exchange) throws IOException {
        try {
            byte[] data = new ObjectMapper().writeValueAsBytes(metadata);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
        } catch (Exception e) {
            sendResponse(exchange, 503, "Metadata unavailable");
        }
    }

    /**
     * 计算文件SHA-256哈希值（内存优化版）
     *
     * @param file 目标文件路径
     * @return 64位十六进制哈希字符串
     * @throws IOException 当出现以下情况时抛出：
     *                     <ul>
     *                         <li>文件读取失败</li>
     *                         <li>哈希算法不支持</li>
     *                     </ul>
     * @implSpec 使用8KB缓冲区降低内存压力
     */
    private String computeHash(Path file) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192]; // 8KB缓冲区

            // 分块读取计算哈希
            while (true) {
                int readCount = inputStream.read(buffer);
                if (readCount == -1) break;
                digest.update(buffer, 0, readCount);
            }

            // 转换为十六进制字符串
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256算法不可用", e);
        }
    }

    /**
     * 高效文件传输方法（NIO优化）
     *
     * @param exchange HTTP请求上下文
     * @param file     待传输文件
     * @throws IOException 当发生以下情况时抛出：
     *                     <ul>
     *                         <li>文件大小变更</li>
     *                         <li>网络连接中断</li>
     *                     </ul>
     * @implNote 使用1MB直接缓冲区减少JVM堆内存占用
     */
    private void sendFile(HttpExchange exchange, Path file) throws IOException {
        long size = Files.size(file);
        exchange.sendResponseHeaders(200, size);

        try (OutputStream os = exchange.getResponseBody();
             FileInputStream fis = new FileInputStream(file.toFile());
             FileChannel channel = fis.getChannel()) {

            // 使用transferTo实现零拷贝
            WritableByteChannel outChannel = Channels.newChannel(os);
            long transferred = 0;
            while (transferred < size) {
                transferred += channel.transferTo(transferred, size - transferred, outChannel);
            }
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String message)
            throws IOException {
        exchange.sendResponseHeaders(code, message.length());
        exchange.getResponseBody().write(message.getBytes());
    }

    public static class FileConfig {
        public String fileName;
        public String fileHash;
        public long totalSize;
        public int chunkSize;
        public List<ChunkConfig> chunks = new ArrayList<>();
        public String isoTimestamp;

        @JsonCreator
        public FileConfig(
                @JsonProperty("fileName") String fileName,
                @JsonProperty("fileHash") String fileHash,
                @JsonProperty("totalSize") long totalSize,
                @JsonProperty("chunkSize") int chunkSize,
                @JsonProperty("chunks") List<ChunkConfig> chunks,
                @JsonProperty("isoTimestamp") String isoTimestamp
        ) {
            this.fileName  = fileName;
            this.fileHash  = fileHash;
            this.totalSize  = totalSize;
            this.chunkSize  = chunkSize;
            this.chunks  = chunks != null ? chunks : new ArrayList<>();
            this.isoTimestamp  = isoTimestamp;
        }
    }

    public static class ChunkConfig {
        public int index;
        public String chunkName;
        public String chunkHash;
        public long chunkSize;

        @JsonCreator
        public ChunkConfig(
                @JsonProperty("index") int index,
                @JsonProperty("chunkName") String chunkName,
                @JsonProperty("chunkHash") String chunkHash,
                @JsonProperty("chunkSize") long chunkSize
        ) {
            this.index  = index;
            this.chunkName  = chunkName;
            this.chunkHash  = chunkHash;
            this.chunkSize  = chunkSize;
        }
    }

}
