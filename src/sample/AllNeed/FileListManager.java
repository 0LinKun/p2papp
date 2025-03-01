package sample.AllNeed;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
                FileInfo fileInfo = generateFileInfo(file);
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

    private FileInfo generateFileInfo(File file) throws IOException, NoSuchAlgorithmException {
        long fileSize = file.length();
        int totalChunks = (int) Math.ceil((double) fileSize / FileInfo.chunk_size);
        List<FileInfo.ChunkInfo> chunks = new ArrayList<>();

        for (int i = 0; i < totalChunks; i++) {
            long start = i * FileInfo.chunk_size;
            long length = Math.min(FileInfo.chunk_size, fileSize - start);
            Path path = Paths.get(file.getAbsolutePath());
            InputStream is = Files.newInputStream(path);
            byte[] buffer = new byte[(int) length];
            is.skip(start);
            is.read(buffer);
            is.close();

            String hash = FileInfo.calculateHash(new ByteArrayInputStream(buffer));
            chunks.add(new FileInfo.ChunkInfo(i + 1, hash));
        }

        return new FileInfo(file.getName(), totalChunks, chunks);
    }

    private void sendFileList(PrintWriter out) {
        // 表头
        out.printf("%-20s %-15s %-15s %-20s %-45s%n", "Filename", "Total Chunks", "Chunk Size", "Chunk Number", "Hash");
        out.println(String.format("%63s", "").replace(' ', '-')); // 分割线

        // 数据
        for (FileInfo fileInfo : currentFileList.values()) {
            boolean firstLine = true;
            if (!fileInfo.chunks.isEmpty()) {
                for (FileInfo.ChunkInfo chunkInfo : fileInfo.chunks) {
                    String filename = firstLine ? fileInfo.filename : "";
                    out.printf("%-20s %-15d %-15d %-20d %-45s%n",
                            filename,
                            fileInfo.total_chunks,
                            fileInfo.chunk_size,
                            chunkInfo.chunk_number,
                            chunkInfo.hash);
                    firstLine = false;
                }
            } else {
                // 文件没有块时，只打印文件名和总块数、块大小（假设为0或其它默认值）
                out.printf("%-20s %-15d %-15d %-20s %-45s%n",
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
