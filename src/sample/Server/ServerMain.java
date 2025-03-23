package sample.Server;

import web.FileServer;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * 服务器主程序入口及主界面框架
 *
 * <p>主要功能：
 * <ul>
 *   <li>创建700x700像素的主窗口</li>
 *   <li>集成服务器功能面板(ServerFrame)</li>
 *   <li>同步启动Web文件服务(FileServer)</li>
 * </ul>
 *
 * @see ServerFrame 集成的服务器功能面板
 * @see FileServer 文件服务模块
 * @since 2025.3.22
 */
public class ServerMain extends JFrame {
    /**
     * 主界面核心面板
     */
    private final ServerFrame serverFrame;

    /**
     * 主窗口容器
     */
    private final JPanel mainPanel;

    /**
     * 构造主窗口界面
     * <p>初始化步骤：
     * 1. 设置窗口标题和关闭操作
     * 2. 创建服务器功能面板
     * 3. 配置边界布局管理器
     */
    public ServerMain() {
        setTitle("Server Main Frame");
        setSize(700, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        serverFrame = new ServerFrame();
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(serverFrame, BorderLayout.CENTER);
        add(mainPanel);
    }


    public static void main(String[] args) {
        //启动web服务
        FileServer fileserver = new FileServer();
        try {
            FileServer.Filemain();
        } catch (IOException e) {
            e.printStackTrace();
        }


        SwingUtilities.invokeLater(() -> {
            new ServerMain().setVisible(true);
        });

    }
}
