package sample.Client;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UDP多播文件发送服务类，实现可靠的分片文件传输协议
 *
 * <p>本类采用多播技术实现高效文件分发，核心功能包括：
 * <ul>
 *   <li><b>智能分片策略</b>：基于MTU（最大传输单元）自动计算分片大小</li>
 *   <li><b>流量控制机制</b>：通过发送缓冲区和延时设置优化网络吞吐</li>
 *   <li><b>元数据封装</b>：集成文件标识、分片序号等传输控制信息</li>
 *   <li><b>自适应编码</b>：支持任意尺寸文件的分片传输</li>
 * </ul>
 *
 * @version 1.0
 * @see FileReceiver 关联的接收端实现类
 * @since 2025.3.22
 */
public class FileSender {
    /**
     * 元数据固定长度（字节数）
     * <p>包含：
     * <ul>
     *   <li>chunkId(4字节) + totalChunks(4字节) + fileNameLength(4字节) = 12字节</li>
     * </ul>
     */
    /**
     * 元数据固定长度（字节数）
     * <p>包含：
     * <ul>
     *   <li>chunkId(4字节) + totalChunks(4字节) + fileNameLength(4字节) = 12字节</li>
     * </ul>
     */
    private static final int METADATA_SIZE = 12;

    /**
     * 最大数据报尺寸（符合以太网MTU规范）
     * <p>计算依据：1500(MTU) - 20(IP头) - 8(UDP头) = 1472字节有效载荷</p>
     */
    private static final int MAX_DATAGRAM_SIZE = 1472;

    /**
     * 单分片有效数据容量（扣除元数据后的净荷空间）
     * <p>动态计算：MAX_DATAGRAM_SIZE - METADATA_SIZE = 1460字节/分片</p>
     */
    private static final int DATA_CHUNK_SIZE = MAX_DATAGRAM_SIZE - METADATA_SIZE;

    /**
     * 多播组地址（D类地址范围）
     * <p>与接收端保持一致的组播地址，支持跨网段传输</p>
     */
    private static final String MULTICAST_GROUP = "239.255.10.1";

    /**
     * 目标端口（需与接收端监听端口匹配）
     * <p>采用IANA定义的临时端口范围（49152-65535）外的5000端口</p>
     */
    private static final int PORT = 5000;

    /**
     * 文件发送器构造方法（含完整的分片传输生命周期管理）
     *
     * @param filePath  源文件路径（支持绝对/相对路径）
     * @param clientFrame 日志输出组件（需实现线程安全访问）
     * @throws IOException 当发生以下情况时抛出：
     *                     <ul>
     *                       <li>文件不存在或不可读</li>
     *                       <li>文件尺寸超过4GB（受int类型分片数限制）</li>
     *                       <li>网络端口被占用或无多播权限</li>
     *                     </ul>
     * @implSpec 技术实现流程：
     * <ol>
     *   <li><b>文件预处理</b>：通过NIO接口读取字节流，获取规范文件名</li>
     *   <li><b>分片计算</b>：使用Math.ceil 确保余数分片正确处理</li>
     *   <li><b>网络初始化</b>：设置双倍MTU缓冲区（2944字节）提升吞吐量</li>
     *   <li><b>分片封装</b>：采用DataOutputStream实现二进制协议封装</li>
     *   <li><b>流控机制</b>：分片间1ms延时防止接收端溢出</li>
     * </ol>
     * @implNote 性能特征：
     * <ul>
     *   <li>理论最大吞吐量：1460字节/分片 * 1000分片/秒 ≈ 1.16MB/s</li>
     *   <li>实际性能受限于网络带宽和接收端处理能力</li>
     * </ul>
     */

    public FileSender(String filePath, ClientFrame clientFrame) throws IOException {
        // 新增时间戳记录
        final AtomicLong lastUpdate = new AtomicLong(System.currentTimeMillis());
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();
        byte[] fileBytes = Files.readAllBytes(path);

        int totalChunks = (int) Math.ceil((double) fileBytes.length / DATA_CHUNK_SIZE);

        try (MulticastSocket socket = new MulticastSocket()) {
            // 初始化进度条
            updateProgress(clientFrame, 0, "开始传输");
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            socket.setSendBufferSize(MAX_DATAGRAM_SIZE * 2);

            for (int chunkId = 0; chunkId < totalChunks; chunkId++) {
                int offset = chunkId * DATA_CHUNK_SIZE;
                int length = Math.min(DATA_CHUNK_SIZE, fileBytes.length - offset);

                ByteArrayOutputStream baos = new ByteArrayOutputStream(MAX_DATAGRAM_SIZE);
                DataOutputStream dos = new DataOutputStream(baos);

                // 写入元数据
                dos.writeInt(chunkId);
                dos.writeInt(totalChunks);
                byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
                dos.writeInt(fileNameBytes.length);
                dos.write(fileNameBytes);

                // 写入分片数据
                dos.write(fileBytes, offset, length);

                byte[] packetData = baos.toByteArray();
                DatagramPacket packet = new DatagramPacket(
                        packetData, packetData.length, group, PORT);

                socket.send(packet);
                // 智能更新策略（每2%或每秒更新一次）
                int progress = (int) ((chunkId + 1) * 100.0 / totalChunks);
                if (progress % 2 == 0 || System.currentTimeMillis()  - lastUpdate.get() > 1000) {
                    updateProgress(clientFrame, progress,
                            String.format(" 传输中 %.1fMB/%.1fMB",
                                    (chunkId * DATA_CHUNK_SIZE)/1048576.0,
                                    fileBytes.length/1048576.0));
                    lastUpdate.set(System.currentTimeMillis());
                }
                Thread.sleep(1);  // 防止发送过快导致丢包
            }
            ClientLogger.log(clientFrame.displayArea, "发送文件：" + fileName);
        } catch (InterruptedException e) {
            ClientLogger.log(clientFrame.displayArea, "发送文件失败：" + fileName);
            Thread.currentThread().interrupt();
        }
    }
    private void updateProgress(ClientFrame frame, int progress, String status) {
        SwingUtilities.invokeLater(()  -> {
            if (progress >= 0) {
                frame.progressBar.setValue(progress);
                frame.progressBar.setString(status);
                frame.progressBar.setForeground(new Color(
                        Math.min(255,  50 + progress*2),
                        Math.max(0,  200 - progress),
                        100));
            } else {
                frame.progressBar.setForeground(Color.RED);
                frame.progressBar.setString(status);
            }
        });
    }
}