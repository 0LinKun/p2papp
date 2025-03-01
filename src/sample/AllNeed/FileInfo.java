package sample.AllNeed;

import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class FileInfo {
    public String filename;
    public int total_chunks;
    public static long chunk_size = 10 * 1024 * 1024; // 10M
    public List<ChunkInfo> chunks;

    public static class ChunkInfo {
        public int chunk_number;
        public String hash;

        public ChunkInfo(int chunk_number, String hash) {
            this.chunk_number = chunk_number;
            this.hash = hash;
        }
    }

    public FileInfo(String filename, int total_chunks, List<ChunkInfo> chunks) {
        this.filename = filename;
        this.total_chunks = total_chunks;
        this.chunks = chunks;
    }

    public static String calculateHash(ByteArrayInputStream file) throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = new FileInputStream(String.valueOf(file)); DigestInputStream dis = new DigestInputStream(is, md)) {
            /* Read decorated stream (dis) to EOF as normal... */
            while (dis.read() != -1);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
