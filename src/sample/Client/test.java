package sample.Client;

import java.io.IOException;
import java.net.*;

public class test {
    public static void main(String[] args) {
        // 组播配置参数
        final String MULTICAST_GROUP = "239.255.10.1";
        final int PORT = 5000;
        final String MESSAGE = "helloworld";

        try (DatagramSocket socket = new DatagramSocket()) {
            // 设置组播目标地址
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);

            // 构建数据包
            byte[] buffer = MESSAGE.getBytes();
            DatagramPacket packet = new DatagramPacket(
                    buffer,
                    buffer.length,
                    group,
                    PORT
            );

            // 发送数据包
            socket.send(packet);
            System.out.println(" 成功发送组播消息: " + MESSAGE);

        } catch (UnknownHostException e) {
            System.err.println(" 无效的组播地址: " + e.getMessage());
        } catch (IOException e) {
            System.err.println(" 网络错误: " + e.getMessage());
        }
    }

}
