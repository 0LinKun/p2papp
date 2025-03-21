package sample.Client;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class FileReceiver extends Thread {
    private static final int MAX_DATAGRAM_SIZE = 1472;
    private static final String MULTICAST_GROUP = "239.255.10.1";
    private static final int PORT = 5000;
    JTextArea jTextArea;
    FileReceiver(JTextArea jTextArea){
        this.jTextArea=jTextArea;
    }
    public void run() {
        try {
            Files.createDirectories(Paths.get("file"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (MulticastSocket socket = new MulticastSocket(PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            socket.joinGroup(group);
            socket.setReceiveBufferSize(MAX_DATAGRAM_SIZE * 100);

            Map<Integer, byte[]> receivedChunks = new ConcurrentHashMap<>();
            AtomicInteger totalChunks = new AtomicInteger(-1);
            AtomicReference<String> fileName = new AtomicReference<>();
            AtomicInteger receivedCount = new AtomicInteger(0);

            while (true) {
                byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                new Thread(() -> processPacket(packet, receivedChunks, totalChunks, fileName, receivedCount)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processPacket(DatagramPacket packet, Map<Integer, byte[]> receivedChunks,
                               AtomicInteger totalChunks, AtomicReference<String> fileName,
                               AtomicInteger receivedCount) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(
                    packet.getData(), packet.getOffset(), packet.getLength());
            DataInputStream dis = new DataInputStream(bais);

            int chunkId = dis.readInt();
            int total = dis.readInt();
            int fileNameLength = dis.readInt();
            byte[] fileNameBytes = new byte[fileNameLength];
            dis.readFully(fileNameBytes);
            String currentFileName = new String(fileNameBytes, StandardCharsets.UTF_8);

            // 初始化元数据
            if (totalChunks.get() == -1) {
                totalChunks.set(total);
                fileName.set(currentFileName);
            }

            // 读取分片数据
            int dataLength = packet.getLength() - 12 - fileNameLength;
            byte[] chunkData = new byte[dataLength];
            dis.readFully(chunkData);

            // 存储分片
            if (!receivedChunks.containsKey(chunkId)) {
                receivedChunks.put(chunkId, chunkData);
                int count = receivedCount.incrementAndGet();

                if (count == totalChunks.get()) {
                    assembleFile(receivedChunks, fileName.get());
                    receivedChunks.clear();
                    receivedCount.set(0);
                    totalChunks.set(-1);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void assembleFile(Map<Integer, byte[]> chunks, String fileName) {
        try (FileOutputStream fos = new FileOutputStream(Paths.get("file", fileName).toFile())) {
            for (int i = 0; i < chunks.size(); i++) {
                byte[] chunk = chunks.get(i);
                if (chunk != null) {
                    fos.write(chunk);
                }
            }
            ClientLogger.log(this.jTextArea," 文件接收完成：" + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}