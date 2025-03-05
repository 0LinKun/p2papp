package sample.Client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileSender {
    private static final int METADATA_SIZE = 12;
    private static final int MAX_DATAGRAM_SIZE = 1472;
    private static final int DATA_CHUNK_SIZE = MAX_DATAGRAM_SIZE - METADATA_SIZE;
    private static final String MULTICAST_GROUP = "239.255.10.1";
    private static final int PORT = 5000;

    public FileSender(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();
        byte[] fileBytes = Files.readAllBytes(path);

        int totalChunks = (int) Math.ceil((double)  fileBytes.length  / DATA_CHUNK_SIZE);

        try (MulticastSocket socket = new MulticastSocket()) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            socket.setSendBufferSize(MAX_DATAGRAM_SIZE  * 2);

            for (int chunkId = 0; chunkId < totalChunks; chunkId++) {
                int offset = chunkId * DATA_CHUNK_SIZE;
                int length = Math.min(DATA_CHUNK_SIZE,  fileBytes.length  - offset);

                ByteArrayOutputStream baos = new ByteArrayOutputStream(MAX_DATAGRAM_SIZE);
                DataOutputStream dos = new DataOutputStream(baos);

                // 写入元数据
                dos.writeInt(chunkId);
                dos.writeInt(totalChunks);
                byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
                dos.writeInt(fileNameBytes.length);
                dos.write(fileNameBytes);

                // 写入分片数据
                dos.write(fileBytes,  offset, length);

                byte[] packetData = baos.toByteArray();
                DatagramPacket packet = new DatagramPacket(
                        packetData, packetData.length,  group, PORT);

                socket.send(packet);
                Thread.sleep(1);  // 防止发送过快导致丢包
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}