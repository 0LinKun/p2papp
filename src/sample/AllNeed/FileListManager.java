package sample.AllNeed;

import com.sun.istack.internal.localization.NullLocalizable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class FileListManager {

    private final String folderPath = "file"; // 文件夹路径
    private Map<String, FileInfo> currentFileList = new HashMap<>();

    public void updateAndSendFileList(PrintWriter out) throws IOException, NoSuchAlgorithmException {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new FileNotFoundException(System.getProperty("user.dir")+"Folder not found or not a directory: " + folderPath);
        }

        boolean updated = false;
        Map<String, FileInfo> newFileList = new HashMap<>();

        for (File file : Objects.requireNonNull(folder.listFiles())) {
            if (file.isFile()) {
                FileInfo fileInfo = generateFile(file);
                newFileList.put(file.getName(), fileInfo);

                if (!currentFileList.containsKey(file.getName()) ||
                        !currentFileList.get(file.getName()).equals(fileInfo)) {
                    updated = true;
                }
            }
        }

        // Remove files that no longer exist in the folder from the current list
        List<String> toRemove = new ArrayList<>();
        for (String fileName : currentFileList.keySet()) {
            if (!newFileList.containsKey(fileName)) {
                toRemove.add(fileName);
                updated = true;
            }
        }
        for (String fileName : toRemove) {
            currentFileList.remove(fileName);
        }

        if (updated) {
            currentFileList = newFileList;
        }

        sendFileList(out);
    }

    private FileInfo generateFile(File file) throws IOException, NoSuchAlgorithmException {
        final long fileSize = file.length();
        final int totalChunks = calculateTotalChunks(fileSize);
        final List<FileInfo.ChunkInfo> chunks = new ArrayList<>();
        final Path path = file.toPath();

        // 优化1: 文件级哈希与分块哈希并行计算
        try (InputStream is = Files.newInputStream(path);
             BufferedInputStream bis = new BufferedInputStream(is)) {

            MessageDigest fileDigest = MessageDigest.getInstance("SHA-256");
            DigestInputStream fileHashStream = new DigestInputStream(bis, fileDigest);

            for (int i = 0; i < totalChunks; i++) {
                // 优化2: 分块读取与哈希计算
                long remaining = Math.min(FileInfo.chunk_size,  fileSize - i * FileInfo.chunk_size);
                byte[] chunkBuffer = new byte[(int) remaining];
                readFully(fileHashStream, chunkBuffer);  // 确保完整读取

                // 优化3: 复用缓冲数据计算分块哈希
                try (ByteArrayInputStream chunkStream = new ByteArrayInputStream(chunkBuffer)) {
                    String chunkHash = FileInfo.calculateHash(chunkStream);
                    chunks.add(new  FileInfo.ChunkInfo(i + 1, chunkHash));
                }
            }

            // 获取文件级哈希
            String fileHash = bytesToHex(fileDigest.digest());
            return new FileInfo(file.getName(),  totalChunks, chunks, fileSize, fileHash);
        }
    }

    // 辅助方法：精确读取字节流
    private void readFully(InputStream is, byte[] buffer) throws IOException {
        int bytesRead = 0;
        while (bytesRead < buffer.length)  {
            int res = is.read(buffer,  bytesRead, buffer.length  - bytesRead);
            if (res == -1) throw new EOFException("Unexpected end of stream");
            bytesRead += res;
        }
    }

    // 辅助方法：分块数计算（防御整数溢出）
    private int calculateTotalChunks(long fileSize) {
        if (FileInfo.chunk_size  <= 0) throw new IllegalArgumentException("Invalid chunk size");
        long total = (fileSize + FileInfo.chunk_size  - 1) / FileInfo.chunk_size;
        if (total > Integer.MAX_VALUE) throw new IllegalStateException("File too large for chunking");
        return (int) total;
    }
    // 在FileListManager类中添加：
    public static FileInfo generateFileInfo(Path path)
            throws IOException, NoSuchAlgorithmException {

        File file = path.toFile();
        long fileSize = Files.size(path);

        // 计算整体文件哈希
        String fileHash = calculateFullFileHash(path);

        // 分块处理
        int totalChunks = (int) Math.ceil((double)  fileSize / FileInfo.chunk_size);
        List<FileInfo.ChunkInfo> chunks = new ArrayList<>();

        try (InputStream is = Files.newInputStream(path))  {
            byte[] buffer = new byte[(int) FileInfo.chunk_size];
            int chunkIndex = 0;

            while (is.available()  > 0) {
                int read = is.read(buffer);
                byte[] actualData = Arrays.copyOf(buffer,  read);

                String chunkHash = calculateHash(actualData, read);
                chunks.add(new  FileInfo.ChunkInfo(++chunkIndex, chunkHash));
            }
        }

        return new FileInfo(file.getName(),  totalChunks, chunks, fileSize, fileHash);
    }

    private static String calculateFullFileHash(Path path)
            throws NoSuchAlgorithmException, IOException {

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(path);
             DigestInputStream dis = new DigestInputStream(is, md)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer)  != -1);
        }
        return bytesToHex(md.digest());
    }
    public static String calculateHash(byte[] data, int length)
            throws NoSuchAlgorithmException {

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(data,  0, length);
        byte[] digest = md.digest();
        return bytesToHex(digest);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x",  b));
        }
        return sb.toString();
    }

    private void sendFileList(PrintWriter out) {
        // 表头
        out.printf("%-60s %-15s %-15s %-20s %-45s%n", "Filename", "Total Chunks", "Chunk Size", "Chunk Number", "Hash");
        out.println(String.format("%63s", "").replace(' ', '-')); // 分割线

        // 数据
        for (FileInfo fileInfo : currentFileList.values()) {
            boolean firstLine = true;
            if (!fileInfo.chunks.isEmpty()) {
                for (FileInfo.ChunkInfo chunkInfo : fileInfo.chunks) {
                    String filename = firstLine ? fileInfo.filename : "";
                    out.printf("%-60s %-15d %-15d %-20d %-45s%n",
                            filename,
                            fileInfo.total_chunks,
                            fileInfo.chunk_size,
                            chunkInfo.chunk_number,
                            chunkInfo.hash);
                    firstLine = false;
                }
            } else {
                // 文件没有块时，只打印文件名和总块数、块大小（假设为0或其它默认值）
                out.printf("%-60s %-15d %-15d %-20s %-45s%n",
                        fileInfo.filename,
                        0, // 总块数
                        0, // 块大小
                        "未分块", // 块编号
                        ""); // 哈希值
            }
            out.println(String.format("%63s", "").replace(' ', '-')); // 分割线
        }
        out.flush();
    }
}
