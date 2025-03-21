package sample.AllNeed;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class FileInfo {
    public static long chunk_size = 10 * 1024 * 1024; // 10kb
    public String filename;
    public int total_chunks;
    public List<ChunkInfo> chunks;
    private  long fileSize;
    private  String fileHash;


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

    // 新增getter方法
    public String getFileName() {
        return filename;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getFileHash() {
        return fileHash;
    }

    public static class ChunkInfo {
        public int chunk_number;
        public String hash;

        public ChunkInfo(int chunk_number, String hash) {
            this.chunk_number = chunk_number;
            this.hash = hash;
        }
    }
}
