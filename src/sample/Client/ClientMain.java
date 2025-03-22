package sample.Client;

import javax.swing.*;
import java.awt.*;

/**
 * 客户端主窗口类，作为客户端应用程序的GUI入口和框架容器
 *
 * <p>本类继承自Swing的JFrame，负责构建客户端主界面框架，主要功能包括：
 * <ol>
 *   <li><b>窗口初始化</b>：设定窗口标题、尺寸（700x700像素）、居中显示和关闭行为</li>
 *   <li><b>界面整合</b>：通过BorderLayout布局管理器集成核心功能面板（ClientFrame）</li>
 *   <li><b>启动入口</b>：提供符合Swing线程规范的应用程序启动方法</li>
 * </ol>
 *
 * @version 1.0
 * @see ClientFrame 核心功能面板实现类
 * @since 2025.3.22
 */
public class ClientMain extends JFrame {
    /**
     * 核心功能面板实例，集成消息通信、文件传输等主要功能组件
     * <p>通过BorderLayout.CENTER定位占据主窗口中央区域</p>
     */
    private final ClientFrame clientFrame;
    /**
     * 主容器面板，采用Border布局管理器实现界面元素组织
     * <p>作为JFrame的顶级容器，承载所有子组件的布局和渲染</p>
     */
    private final JPanel mainPanel;

    /**
     * 主窗口构造器，执行界面初始化三部曲：
     * <ol>
     *   <li><b>窗口配置</b>：设置标题为"Client Main Frame"，初始尺寸700x700，关闭时退出进程</li>
     *   <li><b>布局构建</b>：创建BorderLayout主面板，嵌入ClientFrame功能组件</li>
     *   <li><b>可视化设置</b>：窗口居中显示，组件可见性初始化</li>
     * </ol>
     *
     * @implNote 继承自JFrame的EXIT_ON_CLOSE设置确保窗口关闭时释放所有资源
     */
    public ClientMain() {
        setTitle("Client Main Frame");
        setSize(700, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        clientFrame = new ClientFrame();
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(clientFrame, BorderLayout.CENTER);
        add(mainPanel);
    }

    /**
     * 客户端应用程序主入口，遵循Swing线程安全规范
     * <p>执行流程：
     * <ul>
     *   <li>通过SwingUtilities.invokeLater 确保GUI创建在事件调度线程(EDT)</li>
     *   <li>实例化主窗口对象并设置可见性</li>
     *   <li>隐式初始化Swing组件渲染管线</li>
     * </ul>
     *
     * @param args 命令行参数（本实现未使用）
     * @see SwingUtilities#invokeLater(Runnable) Swing线程安全机制
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ClientMain().setVisible(true);
        });
    }
}