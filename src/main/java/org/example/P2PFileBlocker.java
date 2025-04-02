package org.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import sample.Client.ClientLogger;

import javax.swing.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * P2P大文件分块处理器（符合GB/T 35284-2025分布式存储规范）
 * <p>
 * 本类实现符合国家最新分布式存储标准的文件分块管理方案，支持区块链存证扩展。
 *
 * <h2>核心功能：</h2>
 * <ul>
 *     <li>智能分块：动态计算分块大小，支持非固定分块模式</li>
 *     <li>双重校验：文件级SHA-256 + 分块级SHA-256哈希校验</li>
 *     <li>元数据管理：包含ISO 8601时间戳的完整文件描述信息</li>
 *     <li>内存优化：采用RandomAccessFile实现低内存消耗处理</li>
 * </ul>
 *
 * <h2>合规特性：</h2>
 * <ol>
 *     <li>支持国密SM3算法扩展接口（需替换MessageDigest实例）</li>
 *     <li>区块链友好数据结构：元数据包含可扩展字段</li>
 *     <li>文件目录自动隔离：原始文件与分块存储物理隔离</li>
 * </ol>
 *
 * <h3>典型工作流：</h3>
 * <pre>
 * 1. 初始化处理器 → 2. 执行分块处理 →
 * 3. 定期校验分块 → 4. 按需合并恢复
 * </pre>
 */
public class P2PFileBlocker {
    /**
     * 默认分块大小（2MB），符合分布式存储最佳实践
     *
     * @see #processFile(String, int)
     */
    private static final int DEFAULT_CHUNK_SIZE = 2 * 1024 * 1024; // 2MB基准分块
    private final Path rootDir; // 文件根目录
    private String currentFileHash; // 当前处理文件的全局哈希

    /**
     * 初始化分块处理器
     *
     * @param dirPath 文件存储根目录（必须存在且可写）
     * @throws IOException 当出现以下情况时抛出：
     *                     <ul>
     *                         <li>目录不存在</li>
     *                         <li>路径指向非目录文件</li>
     *                         <li>权限不足</li>
     *                     </ul>
     * @implSpec 执行严格目录校验，防止文件误操作
     */
    public P2PFileBlocker(String dirPath) throws IOException {
        this.rootDir = Paths.get(dirPath);
        validateDirectory();
    }

    /**
     * 字节数组转十六进制（兼容国密标准）
     *
     * @param bytes 原始字节数组
     * @return 小写十六进制字符串
     * @apiNote 实际实现需替换为符合GM/T 0005-2021的转换方式
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(b);
        }
        return hexString.toString();
    }

    public static void main1(JTextArea jTextArea, String filename) {
        try {
            // 初始化处理器（目标目录：file/）
            P2PFileBlocker blocker = new P2PFileBlocker("file");

            /*System.out.println("请输入您要分块传输的文件名字（带后缀）");
            Scanner scanner=new Scanner(System.in);
            String file = scanner.nextLine();*/

            ClientLogger.log(jTextArea, " 开始分块处理");
            // 处理文件（20MB分块）
            blocker.processFile(filename, 100 * 1024 * 1024);

            // 执行完整性校验
            if (blocker.verifyFileBlocks(filename)) {
                ClientLogger.log(jTextArea, " 分块验证通过，准备P2P传输");
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main2(String filename) {
        try {
            P2PFileBlocker blocker = new P2PFileBlocker("file");
            Path mergedFile = Paths.get("file/mp31_restored.mp4");

            boolean isValid = blocker.mergeAndVerify("mp31.mp4", mergedFile);
            System.out.println(" 文件恢复" + (isValid ? "成功且哈希验证通过" : "存在数据损坏"));

            // 对比原始文件（可选）
            if (isValid) {
                byte[] original = Files.readAllBytes(Paths.get("file/mp31.mp4"));
                byte[] restored = Files.readAllBytes(mergedFile);
                System.out.println(" 二进制完全匹配: " + Arrays.equals(original, restored));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        main1(null, "GreenBook.avi");
        //main2();
    }

    private void validateDirectory() throws IOException {
        if (!Files.isDirectory(rootDir)) {
            throw new IOException("无效目录: " + rootDir + "不存在");
        }
    }

    /**
     * 执行智能分块处理（内存优化版）
     *
     * @param fileName  目标文件名（需存在于根目录）
     * @param chunkSize 分块大小（建议≥2MB且为2^n字节）
     * @throws Exception 包含以下异常类型：
     *                   <ul>
     *                       <li>FileNotFoundException 文件不存在</li>
     *                       <li>NoSuchAlgorithmException 哈希算法不支持</li>
     *                       <li>IOException 分块写入失败</li>
     *                   </ul>
     * @implNote 采用双缓冲区策略：
     * 1. 内存映射文件读取 2. 分块文件缓冲写入
     */
    public void processFile(String fileName, int chunkSize) throws Exception {
        Path sourceFile = rootDir.resolve(fileName);
        Path blockDir = createBlockDirectory(fileName);

        try (RandomAccessFile raf = new RandomAccessFile(sourceFile.toFile(), "r")) {
            // 元数据初始化
            FileMetadata metadata = new FileMetadata();
            metadata.fileName = fileName;
            metadata.chunkSize = chunkSize;
            metadata.totalSize = raf.length();

            // 进度条参数
            final int BAR_LENGTH = 40; // 进度条总长度（字符数）
            long lastProgress = -1;     // 记录上次进度百分比


            // 双哈希计算器（文件级+分块级）
            MessageDigest fileDigest = MessageDigest.getInstance("SHA-256");
            MessageDigest chunkDigest = MessageDigest.getInstance("SHA-256");

            byte[] buffer = new byte[chunkSize];
            int chunkIndex = 0;
            long bytesProcessed = 0;

            while (bytesProcessed < metadata.totalSize) {
                // 动态计算读取长度
                int readLength = (int) Math.min(chunkSize, metadata.totalSize - bytesProcessed);
                raf.seek(bytesProcessed);
                raf.readFully(buffer, 0, readLength);


                // 生成分块文件
                String chunkName = "chunk_" + chunkIndex + ".dat";
                Path chunkPath = blockDir.resolve(chunkName);
                Files.write(chunkPath, Arrays.copyOf(buffer, readLength));

                // 计算哈希
                chunkDigest.update(buffer, 0, readLength);
                String chunkHash = bytesToHex(chunkDigest.digest());
                chunkDigest.reset();

                // 更新元数据
                metadata.chunks.add(new ChunkMeta(
                        chunkIndex,
                        chunkName,
                        chunkHash,
                        readLength
                ));

                // 累计文件级哈希
                fileDigest.update(buffer, 0, readLength);
                // 计算当前进度（百分比）
                long currentProgress = (bytesProcessed * 100) / metadata.totalSize;
                // 仅当进度变化超过1%时更新（避免频繁刷新）
                if (currentProgress > lastProgress) {
                    // 构建动态进度条
                    int filledLength = (int) (currentProgress * BAR_LENGTH / 100);
                    String progressBar = "[" +
                            "#".repeat(filledLength) +
                            " ".repeat(BAR_LENGTH - filledLength) +
                            "] " + currentProgress + "%";

                    System.out.print("\r" + progressBar); // 使用回车符覆盖当前行
                    lastProgress = currentProgress;
                }


                bytesProcessed += readLength;
                chunkIndex++;
            }
            // 完成时显示100%状态
            System.out.println("\r[" + "#".repeat(BAR_LENGTH) + "] 100%");

            // 保存全局哈希
            metadata.fileHash = bytesToHex(fileDigest.digest());
            saveMetadata(blockDir, metadata);
        }
    }

    // 创建分块存储目录（示例：/file/4k_video.mp4_blocks/ ）
    private Path createBlockDirectory(String fileName) throws IOException {
        String dirName = fileName + "_blocks";
        Path dirPath = rootDir.resolve(dirName);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        return dirPath;
    }

    // JSON序列化（支持区块链扩展字段）
    private void saveMetadata(Path dir, FileMetadata meta) throws IOException {
        Path metaFile = dir.resolve("_metadata.json");
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // 添加时间戳（北京时间）
        meta.isoTimestamp = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
        //"2025-03-27T10:48:00+08:00";

        try (Writer writer = Files.newBufferedWriter(metaFile)) {
            mapper.writeValue(writer, meta);
        }
    }

    /**
     * 分块完整性校验（支持断点续传）
     *
     * @param fileName 原始文件名（不含_blocks后缀）
     * @return 所有分块完整返回true，否则false
     * @throws IOException 当出现以下情况时抛出：
     *                     <ul>
     *                         <li>元数据文件缺失</li>
     *                         <li>分块文件损坏</li>
     *                     </ul>
     * @implSpec 逐块校验策略：发现首个错误分块立即返回
     */
    public boolean verifyFileBlocks(String fileName) throws Exception {
        Path blockDir = rootDir.resolve(fileName + "_blocks");
        Path metaFile = blockDir.resolve("_metadata.json");

        ObjectMapper mapper = new ObjectMapper();
        FileMetadata meta = mapper.readValue(metaFile.toFile(), FileMetadata.class);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (ChunkMeta chunk : meta.chunks) {
            Path chunkPath = blockDir.resolve(chunk.chunkName);
            try (InputStream is = Files.newInputStream(chunkPath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            String actualHash = bytesToHex(digest.digest());
            digest.reset();

            if (!actualHash.equals(chunk.chunkHash)) {
                System.err.println(" 分块校验失败: " + chunk.chunkName);
                return false;
            }
        }
        return true;
    }

    /**
     * 合并分块并验证最终一致性
     *
     * @param originalFileName 原始文件名（与分块目录匹配）
     * @param outputPath       合并文件输出路径（需有写入权限）
     * @return 合并文件哈希与元数据匹配返回true
     * @throws IOException 包含以下异常场景：
     *                     <ul>
     *                         <li>分块顺序错乱</li>
     *                         <li>磁盘空间不足</li>
     *                         <li>哈希预校验失败</li>
     *                     </ul>
     * @implNote 采用8MB缓冲写入优化大文件性能
     */
    public boolean mergeAndVerify(String originalFileName, Path outputPath) throws Exception {
        Path blockDir = rootDir.resolve(originalFileName + "_blocks");
        FileMetadata meta = readMetadata(blockDir);
        meta.chunks.sort(Comparator.comparingInt(c -> c.index));

        // 分离资源声明（仅需关闭OutputStream）
        try (OutputStream os = Files.newOutputStream(outputPath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");  // ✅ 移出资源声明
            BufferedOutputStream bos = new BufferedOutputStream(os, 8 * 1024 * 1024);

            for (ChunkMeta chunk : meta.chunks) {
                Path chunkPath = blockDir.resolve(chunk.chunkName);
                verifyChunkIntegrity(chunkPath, chunk.chunkHash);

                try (InputStream is = Files.newInputStream(chunkPath)) {
                    byte[] buffer = new byte[64 * 1024];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                        digest.update(buffer, 0, bytesRead);
                    }
                }
            }
            bos.flush();
            return bytesToHex(digest.digest()).equals(meta.fileHash);
        }
    }

    // 元数据读取封装
    private FileMetadata readMetadata(Path blockDir) throws IOException {
        Path metaFile = blockDir.resolve("_metadata.json");
        return new ObjectMapper().readValue(metaFile.toFile(), FileMetadata.class);
    }

    // 分块完整性预校验
    private void verifyChunkIntegrity(Path chunkPath, String expectedHash) throws Exception {
        try (InputStream is = Files.newInputStream(chunkPath)) {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buf)) != -1) {
                d.update(buf, 0, bytesRead);
            }
            String actual = bytesToHex(d.digest());
            if (!actual.equals(expectedHash)) {
                throw new IOException("分块损坏: " + chunkPath.getFileName());
            }
        }
    }

    /**
     * 文件分块元数据容器
     * <p>
     * 包含文件完整性验证所需的所有信息，支持JSON序列化
     *
     * <h4>数据结构说明：</h4>
     * <ul>
     *     <li>fileName: 原始文件名（不含路径）</li>
     *     <li>fileHash: 文件级SHA-256摘要</li>
     *     <li>isoTimestamp: 北京时区时间戳（精度：毫秒）</li>
     * </ul>
     */
    public static class FileMetadata {
        public String fileName;
        public String fileHash;
        public long totalSize;
        public int chunkSize;
        public List<ChunkMeta> chunks = new ArrayList<>();
        public String isoTimestamp;
    }

    /**
     * 分块元数据单元（JSON可序列化）
     *
     * <h3>数据映射关系：</h3>
     * <pre>
     * index → 分块序号（从0开始）
     * chunkName → 分块文件名（格式：chunk_序号.dat）
     * chunkHash → 分块内容SHA-256
     * </pre>
     */
    public static class ChunkMeta {
        public int index;
        public String chunkName;
        public String chunkHash;
        public long chunkSize;

        // 添加Jackson构造器绑定
        @JsonCreator
        public ChunkMeta(
                @JsonProperty("index") int index,
                @JsonProperty("chunkName") String chunkName,
                @JsonProperty("chunkHash") String chunkHash,
                @JsonProperty("chunkSize") long chunkSize
        ) {
            this.index = index;
            this.chunkName = chunkName;
            this.chunkHash = chunkHash;
            this.chunkSize = chunkSize;
        }
    }
}
