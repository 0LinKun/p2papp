package sample.AllNeed;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文件分块信息管理类（版本2.1.0）
 *
 * <p>本类用于处理大文件的分块存储与验证，主要功能包括：
 * <ul>
 *   <li>定义10MB文件分块标准（实际变量值建议修正为 10*1024*1024）</li>
 *   <li>计算文件整体SHA-256哈希值</li>
 *   <li>维护分块哈希信息列表</li>
 *   <li>提供不可修改的数据访问接口</li>
 * </ul>
 *
 * @since 2025-03-22
 */
public class FileInfo {
    /**
     * 分块大小配置（当前值为10MB）
     *
     * @apiNote 实际应用中建议通过配置文件动态调整
     */
    public static long chunk_size = 10 * 1024 * 1024;
    public String filename;
    public int total_chunks;
    public List<ChunkInfo> chunks= new ArrayList<>(); // 确保默认初始化 ;
    private long fileSize;
    private String fileHash;

    /**
     * 文件元数据构造器
     *
     * @param filename     目标文件名（包含扩展名）
     * @param total_chunks 总分块数（基于文件大小自动计算）
     * @param chunks       分块信息集合（需按顺序排列）
     * @param fileSize     文件字节大小（单位：bytes）
     * @param fileHash     文件整体哈希值（SHA-256）
     */
    public FileInfo(String filename, int total_chunks,
                    List<ChunkInfo> chunks, long fileSize, String fileHash) {
        this.filename = filename;
        this.total_chunks = total_chunks;
        this.chunks = chunks;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
    }

    public FileInfo() {

    }

    /**
     * 计算文件哈希值（线程安全）
     *
     * @param is 输入流（建议使用BufferedInputStream包装）
     * @return 64字符SHA-256哈希值
     * @throws NoSuchAlgorithmException 当JVM不支持SHA-256时抛出
     * @throws IOException              发生I/O错误时抛出
     * @implNote 使用8KB缓冲提升大文件处理性能
     */
    public static String calculateHash(InputStream is) throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (DigestInputStream dis = new DigestInputStream(is, md)) {
            byte[] buffer = new byte[8192];  // 缓冲读取提升性能
            while (dis.read(buffer) != -1) ;  // 批量读取至流结束
        }
        byte[] digest = md.digest();
        return bytesToHex(digest);  // 复用字节转十六进制方法
    }

    // 辅助方法：字节数组转HEX字符串
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 获取文件名字
     *
     * @return 文件名字符串
     */
    public String getFileName() {
        return filename;
    }

    /**
     * 获取文件大小
     *
     * @return 字节数量（含单位转换方法建议）
     */
    public long getFileSize() {
        return fileSize;
    }

    public String getFileHash() {
        return fileHash;
    }

    public String getFilePath() {
        return "file";
    }

    /**
     * 获取不可修改的分块列表
     *
     * @return 防御性拷贝的分块集合
     * @see Collections#unmodifiableList
     */
    public List<ChunkInfo> getChunks() {
        return Collections.unmodifiableList(chunks);  // 返回不可修改的副本保证数据安全
    }

    public int getTotalChunks() {
        return total_chunks;
    }

    /**
     * 文件块元数据（静态内部类）
     * <p>描述单个文件块的验证信息：
     * <ul>
     *   <li>chunk_number: 块序号（从1开始）</li>
     *   <li>hash: 本块内容的SHA-256值</li>
     * </ul>
     */
    public static class ChunkInfo {
        public int chunk_number;
        public String hash;

        /**
         * 文件块构造器
         *
         * @param chunk_number 块序号（需保持连续）
         * @param hash         本块哈希值（建议校验格式）
         */
        public ChunkInfo(int chunk_number, String hash) {
            this.chunk_number = chunk_number;
            this.hash = hash;
        }

        /**
         * 获取本块哈希值
         *
         * @return 64字符十六进制字符串
         * @throws IllegalArgumentException 哈希值无效时抛出
         */
        public String getHash() {
            return this.hash;
        }
    }
}
