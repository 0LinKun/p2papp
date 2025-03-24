package sample.AllNeed;

import com.google.gson.*;

import java.io.*;
import java.net.ProtocolException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class FileListManager {

    public  Map<String, FileInfo> remoteFileList;
    private Map<String, FileInfo> currentFileList = new HashMap<>();

    // 在FileListManager类中添加：
    public static FileInfo generateFileInfo(Path path) throws IOException, NoSuchAlgorithmException {

        File file = path.toFile();
        long fileSize = Files.size(path);

        // 计算整体文件哈希
        String fileHash = calculateFullFileHash(path);

        // 分块处理
        int totalChunks = (int) Math.ceil((double) fileSize / FileInfo.chunk_size);
        List<FileInfo.ChunkInfo> chunks = new ArrayList<>();

        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[(int) FileInfo.chunk_size];
            int chunkIndex = 0;

            while (is.available() > 0) {
                int read = is.read(buffer);
                byte[] actualData = Arrays.copyOf(buffer, read);

                String chunkHash = calculateHash(actualData, read);
                chunks.add(new FileInfo.ChunkInfo(++chunkIndex, chunkHash));
            }
        }

        return new FileInfo(file.getName(), totalChunks, chunks, fileSize, fileHash);
    }

    private static String calculateFullFileHash(Path path)
            throws NoSuchAlgorithmException, IOException {

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(path);
             DigestInputStream dis = new DigestInputStream(is, md)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) ;
        }
        return bytesToHex(md.digest());
    }

    public static String calculateHash(byte[] data, int length)
            throws NoSuchAlgorithmException {

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(data, 0, length);
        byte[] digest = md.digest();
        return bytesToHex(digest);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public Map<String, FileInfo> getCurrentFileList() {
        return currentFileList;
    }

    public FileInfo getFileInfo(String filename) {
        return currentFileList.get(filename);
    }

    public void updateAndSendFileList(PrintWriter out) throws IOException, NoSuchAlgorithmException {
        updateFileList();
        sendFileList(out);
    }

    public void updateFileList() throws IOException, NoSuchAlgorithmException {
        // 文件夹路径
        String folderPath = "file";
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new FileNotFoundException(System.getProperty("user.dir") + "Folder not found or not a directory: " + folderPath);
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
    }

    private void sendFileList(PrintWriter out) {
        // 构建可扩展的JSON结构
        List<Map<String, Object>> fileList = new ArrayList<>();

        for (FileInfo fileInfo : currentFileList.values()) {
            Map<String, Object> fileData = new LinkedHashMap<>();
            fileData.put("filename", fileInfo.filename);
            fileData.put("total_chunks", fileInfo.chunks.isEmpty() ? 0 : fileInfo.total_chunks);
            fileData.put("chunk_size", FileInfo.chunk_size);  // 保持全局块大小

            //分块数据智能封装
            List<Map<String, Object>> chunks = new ArrayList<>();
            if (!fileInfo.chunks.isEmpty()) {
                for (FileInfo.ChunkInfo chunk : fileInfo.chunks) {
                    Map<String, Object> chunkData = new HashMap<>();
                    chunkData.put("number", chunk.chunk_number);
                    chunkData.put("hash", chunk.hash);
                    chunkData.put("offset", chunk.chunk_number * FileInfo.chunk_size);  // 增加计算字段
                    chunks.add(chunkData);
                }
            }
            fileData.put("chunks", chunks);

            // 增加校验元数据
            fileData.put("protocol_version", "1.0");
            fileData.put("timestamp", System.currentTimeMillis());
            fileList.add(fileData);
        }

        // 构建完整报文
        Map<String, Object> payload = new HashMap<>();
        payload.put("files", fileList);


        // 序列化并发送（使用GSON库）
        out.println("File_List");
        out.println(new Gson().toJson(payload));
        out.flush();  // 保持原有刷新机制
    }

    public Map<String, FileInfo> receiveFileList(BufferedReader in) throws IOException {
        StringBuilder json = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) { // 兼容多行JSON传输
            json.append(line);
        }

        JsonObject root = JsonParser.parseString(json.toString()).getAsJsonObject();
        // 验证协议版本
        JsonArray filesArray = root.getAsJsonArray("files");
        for (JsonElement fileElement : filesArray) {
            JsonObject fileObj = fileElement.getAsJsonObject();

            // 增强字段存在性校验（防御性编程）
            if (!fileObj.has("protocol_version"))  {
                throw new ProtocolException("协议版本字段缺失");
            }

            String version = fileObj.get("protocol_version").getAsString();
            if (!"1.0".equals(version)) {
                throw new ProtocolException("文件" + fileObj.get("filename")  + "版本不兼容，当前支持1.0");
            }

        }

        // 反序列化核心数据
        Map<String, FileInfo> result = new HashMap<>();
        for (JsonElement elem : root.getAsJsonArray("files")) {
            JsonObject fileObj = elem.getAsJsonObject();
            FileInfo info = new FileInfo();
            info.filename = fileObj.get("filename").getAsString();
            info.total_chunks = fileObj.get("total_chunks").getAsInt();

            // 分块数据重建
            for (JsonElement chunkElem : fileObj.getAsJsonArray("chunks")) {
                JsonObject chunk = chunkElem.getAsJsonObject();
                info.chunks.add(new FileInfo.ChunkInfo(
                        chunk.get("number").getAsInt(),
                        chunk.get("hash").getAsString()
                ));
            }
            result.put(info.filename, info);
        }
        return result;
    }

    public boolean compareFileList(BufferedReader in) {
        try {
            //比较本地文件列表变量和远程客户端发送的列表
            remoteFileList = receiveFileList(in);
            if (isLocalConsistent(currentFileList, remoteFileList)) {
                return false;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    public boolean isLocalConsistent(Map<String, FileInfo> local, Map<String, FileInfo> remote) {
        // 关键文件全量覆盖检查
        return remote.keySet().stream().allMatch(local::containsKey);
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
                long remaining = Math.min(FileInfo.chunk_size, fileSize - i * FileInfo.chunk_size);
                byte[] chunkBuffer = new byte[(int) remaining];
                readFully(fileHashStream, chunkBuffer);  // 确保完整读取

                // 优化3: 复用缓冲数据计算分块哈希
                try (ByteArrayInputStream chunkStream = new ByteArrayInputStream(chunkBuffer)) {
                    String chunkHash = FileInfo.calculateHash(chunkStream);
                    chunks.add(new FileInfo.ChunkInfo(i + 1, chunkHash));
                }
            }

            // 获取文件级哈希
            String fileHash = bytesToHex(fileDigest.digest());
            return new FileInfo(file.getName(), totalChunks, chunks, fileSize, fileHash);
        }
    }

    // 辅助方法：精确读取字节流
    private void readFully(InputStream is, byte[] buffer) throws IOException {
        int bytesRead = 0;
        while (bytesRead < buffer.length) {
            int res = is.read(buffer, bytesRead, buffer.length - bytesRead);
            if (res == -1) throw new EOFException("Unexpected end of stream");
            bytesRead += res;
        }
    }

    // 辅助方法：分块数计算（防御整数溢出）
    private int calculateTotalChunks(long fileSize) {
        if (FileInfo.chunk_size <= 0) throw new IllegalArgumentException("Invalid chunk size");
        long total = (fileSize + FileInfo.chunk_size - 1) / FileInfo.chunk_size;
        if (total > Integer.MAX_VALUE) throw new IllegalStateException("File too large for chunking");
        return (int) total;
    }


    public Map<String, FileInfo> getFileList() {
        return this.currentFileList;
    }
}
