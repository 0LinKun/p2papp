package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockClient {
    private static final int MAX_RETRIES = 3;
    private static final int MAX_THREADS = 8;
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .executor(Executors.newFixedThreadPool(MAX_THREADS))
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final AtomicInteger completedChunks = new AtomicInteger(0);
    private static volatile boolean downloadCompleted = false;

    public static void BlockClientmain(ArrayList<HashMap<String, String>> userList) {
        if (userList.isEmpty()) {
            System.err.println(" 没有可用客户端节点");
            return;
        }

        String filename = "GreenBook.avi";  // 可通过Scanner获取用户输入
        ExecutorService executor = Executors.newCachedThreadPool();

        try {
            // 1. 获取元数据
            FileConfig metadata = fetchMetadata(userList, filename);
            if (metadata == null) {
                System.err.println(" 元数据获取失败");
                return;
            }

            // 2. 准备下载文件
            Path outputFile = prepareOutputFile(metadata);

            // 3. 启动进度监控
            executor.execute(() -> monitorProgress(metadata.getChunks().size()));

            // 4. 分块下载
            downloadChunksParallel(userList, metadata, outputFile, filename);

            // 5. 最终校验
            if (validateFinalFile(outputFile, metadata.getFileHash())) {
                System.out.println("\n 文件校验成功");
            } else {
                System.err.println("\n 文件校验失败");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
            downloadCompleted = true;
        }
    }

    private static FileConfig fetchMetadata(List<HashMap<String, String>> nodes, String filename) {
        for (HashMap<String, String> node : nodes) {
            String url = String.format("http://%s:8080/%s/_metadata.json",
                    node.get("IP"), filename);
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .build();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());
                return mapper.readValue(response.body(), FileConfig.class);
            } catch (Exception e) {
                System.err.printf(" 节点 %s 元数据获取失败: %s%n", node.get("IP"), e.getMessage());
            }
        }
        return null;
    }

    private static Path prepareOutputFile(FileConfig metadata) throws Exception {
        Path path = Path.of(metadata.getFileName());
        Files.deleteIfExists(path);
        Files.createFile(path);
        return path;
    }

    private static void downloadChunksParallel(List<HashMap<String, String>> nodes,
                                               FileConfig metadata, Path outputFile, String filename) {
        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        List<Future<?>> futures = new ArrayList<>();

        for (FileConfig.Chunk chunk : metadata.getChunks()) {
            futures.add(executor.submit(() -> {
                for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                    HashMap<String, String> node = selectNode(nodes);
                    if (downloadChunk(node, chunk, outputFile, filename)) {
                        completedChunks.incrementAndGet();
                        return;
                    }
                }
                System.err.printf(" 分块 %s 下载失败%n", chunk.getChunkName());
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
            }
        }
        executor.shutdown();
    }

    private static boolean downloadChunk(HashMap<String, String> node,
                                         FileConfig.Chunk chunk, Path outputFile, String filename) {
        String url = String.format("http://%s:8080/%s_blocks/%s",
                node.get("IP"), filename, chunk.getChunkName());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            // 哈希校验
            String receivedHash = response.headers()
                    .firstValue("X-Content-SHA256")
                    .orElse("");
            if (!receivedHash.equals(chunk.getChunkHash())) {
                System.err.printf(" 分块 %s 哈希不匹配%n", chunk.getChunkName());
                return false;
            }

            // 写入文件
            writeChunk(outputFile, chunk, response.body());
            return true;
        } catch (Exception e) {
            System.err.printf(" 节点 %s 下载失败: %s%n", node.get("IP"), e.getMessage());
            return false;
        }
    }

    private static synchronized void writeChunk(Path outputFile,
                                                FileConfig.Chunk chunk, byte[] data)
            throws Exception {
        try (FileChannel channel = FileChannel.open(outputFile,
                StandardOpenOption.WRITE)) {
            long position = chunk.index * chunk.getChunkSize();
            channel.position(position);
            channel.write(ByteBuffer.wrap(data));
        }
    }

    private static HashMap<String, String> selectNode(List<HashMap<String, String>> nodes) {
        // 简单轮询算法，可替换为更智能的负载均衡策略
        int index = ThreadLocalRandom.current().nextInt(nodes.size());
        return nodes.get(index);
    }

    private static void monitorProgress(int totalChunks) {
        System.out.print(" 下载进度: ");
        while (!downloadCompleted) {
            int completed = completedChunks.get();
            double progress = (completed * 100.0) / totalChunks;
            System.out.printf("\r%.2f%%  [%s%s]", progress,
                    "=".repeat((int) (progress / 2)),
                    " ".repeat(50 - (int) (progress / 2)));
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean validateFinalFile(Path file, String expectedHash) {
        try {
            String actualHash = calculateFileHash(file);
            return actualHash.equals(expectedHash);
        } catch (Exception e) {
            System.err.println(" 文件校验异常: " + e.getMessage());
            return false;
        }
    }

    private static String calculateFileHash(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileChannel channel = FileChannel.open(file)) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
            while (channel.read(buffer) != -1) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
            byte[] hashBytes = digest.digest();
            return HexFormat.of().formatHex(hashBytes);
        }
    }

    public static class FileConfig {
        private String fileName;
        private String fileHash;
        private long totalSize;
        private List<Chunk> chunks;

        // Getters
        public String getFileName() {
            return fileName;
        }

        public String getFileHash() {
            return fileHash;
        }

        public List<Chunk> getChunks() {
            return chunks;
        }

        public static class Chunk {
            private int index;
            private String chunkName;
            private String chunkHash;
            private long chunkSize;

            // Getters
            public String getChunkName() {
                return chunkName;
            }

            public String getChunkHash() {
                return chunkHash;
            }

            public long getChunkSize() {
                return chunkSize;
            }


        }
    }
}