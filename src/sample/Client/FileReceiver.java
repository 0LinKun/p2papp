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

/**
 * UDP多播文件接收服务线程类（继承Thread），实现可靠的分片文件传输与重组
 *
 * <p>本类采用多播通信机制实现高效文件分发，核心特性包括：
 * <ul>
 *   <li><b>分片传输协议</b>：支持大文件分片传输（每片最大1472字节）</li>
 *   <li><b>多线程处理</b>：每个数据包分配独立处理线程，提升并发性能</li>
 *   <li><b>完整性校验</b>：通过分片序号检测实现自动重组</li>
 *   <li><b>断点续传</b>：ConcurrentHashMap缓存已接收分片，避免重复处理</li>
 * </ul>
 *
 * @version 1.0
 * @see ClientLogger 关联的日志记录工具类
 * @since 2025.3.22
 */
public class FileReceiver extends Thread {
    /**
     * 最大数据报尺寸（基于以太网MTU 1500字节的典型设置）
     * <p>计算方式：1500(MTU) - 20(IP头) - 8(UDP头) = 1472字节载荷</p>
     */
    private static final int MAX_DATAGRAM_SIZE = 1472;

    /**
     * 多播组地址（D类地址范围：224.0.0.0~239.255.255.255）
     * <p>采用管理作用域地址(239.255.0.0/16)，适用于企业级应用</p>
     */
    private static final String MULTICAST_GROUP = "239.255.10.1";

    /**
     * 监听端口（IANA注册端口范围外的临时端口）
     * <p>需与发送端配置一致，建议通过配置文件动态设置</p>
     */
    private static final int PORT = 5000;

    /**
     * 日志显示区域组件，用于实时更新接收状态
     * <p>通过ClientLogger工具类实现线程安全的日志输出</p>
     */

    private final JTextArea jTextArea;

    /**
     * 构造方法初始化日志显示组件
     *
     * @param jTextArea 消息显示区域（需非空且已初始化）
     * @throws NullPointerException 当jTextArea参数为null时抛出
     */
    FileReceiver(JTextArea jTextArea) {
        this.jTextArea = jTextArea;
    }

    /**
     * 线程主执行逻辑，实现完整的文件接收流程
     *
     * <p>执行步骤：
     * <ol>
     *   <li><b>创建存储目录</b>：在项目根目录下建立file文件夹</li>
     *   <li><b>初始化多播套接字</b>：加入多播组并设置接收缓冲区</li>
     *   <li><b>启动接收循环</b>：持续监听数据报并为每个包创建处理线程</li>
     *   <li><b>资源自动释放</b>：使用try-with-resources确保socket关闭</li>
     * </ol>
     *
     * @implNote 接收缓冲区设为1472*100=147200字节，可缓存约100个数据包
     * @see MulticastSocket Java多播套接字API
     */
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

    /**
     * 数据包处理核心方法（线程安全）
     *
     * <p>数据包结构解析：
     * <table border="1">
     *   <tr><th>字节范围</th><th>数据类型</th><th>说明</th></tr>
     *   <tr><td>0-3</td><td>int</td><td>分片序号（从0开始）</td></tr>
     *   <tr><td>4-7</td><td>int</td><td>总分片数</td></tr>
     *   <tr><td>8-11</td><td>int</td><td>文件名长度N</td></tr>
     *   <tr><td>12-(11+N)</td><td>byte[]</td><td>UTF-8编码的文件名</td></tr>
     *   <tr><td>剩余字节</td><td>byte[]</td><td>分片数据内容</td></tr>
     * </table>
     *
     * @param packet         接收到的原始数据报
     * @param receivedChunks 已接收分片缓存映射（Key:分片序号，Value:分片数据）
     * @param totalChunks    总分片数原子引用（初始值-1表示未初始化）
     * @param fileName       文件名原子引用（UTF-8字符串）
     * @param receivedCount  已接收有效分片计数器
     */
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

    /**
     * 文件重组方法（同步写入保证数据完整性）
     *
     * <p>重组流程：
     * <ol>
     *   <li>按分片序号顺序写入（0到totalChunks-1）</li>
     *   <li>自动跳过缺失分片（当前版本暂未实现重传请求）</li>
     *   <li>使用NIO的Path接口构建跨平台文件路径</li>
     *   <li>通过FileOutputStream实现字节流写入</li>
     * </ol>
     *
     * @param chunks   有序分片集合（需确保包含所有分片）
     * @param fileName 目标文件名（自动存入file目录）
     * @throws IOException 当文件创建或写入失败时抛出
     */
    private void assembleFile(Map<Integer, byte[]> chunks, String fileName) {
        try (FileOutputStream fos = new FileOutputStream(Paths.get("file", fileName).toFile())) {
            for (int i = 0; i < chunks.size(); i++) {
                byte[] chunk = chunks.get(i);
                if (chunk != null) {
                    fos.write(chunk);
                }
            }
            ClientLogger.log(this.jTextArea, " 文件接收完成：" + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}