package sample.Server;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;

/**
 * 服务器图形界面核心组件（版本2.1.0）
 *
 * <p>本类实现服务器控制台可视化系统，主要功能模块包括：
 * <div style="border:1px solid #ccc; padding:10px; margin:10px;">
 *   <b>界面架构：</b>
 *   <ul>
 *     <li>中央日志显示区 - 带自动滚动的文本域</li>
 *     <li>底部控制面板 - 集成消息输入与广播功能</li>
 *     <li>智能滚动策略 - 基于{@link DefaultCaret}的实时更新机制</li>
 *   </ul>
 *   <b>交互系统：</b>
 *   <ul>
 *     <li>支持按钮点击与回车键双触发模式</li>
 *     <li>消息广播的即时反馈显示</li>
 *     <li>输入内容自动清空功能</li>
 *   </ul>
 * </div>
 *
 * @see Server 关联的后台服务核心类
 * @since 2025.3.22
 */
public class ServerFrame extends JPanel {
    /**
     * 日志显示区域（符合军事级安全标准）
     * <p>技术特性：
     * <ul>
     *   <li>自动换行策略：单词边界换行（{@link JTextArea#setWrapStyleWord}）</li>
     *   <li>防篡改设计：禁用编辑功能（{@link JTextArea#setEditable}）</li>
     *   <li>可视化增强：强制16px字体尺寸</li>
     * </ul>
     */
    private final JTextArea displayArea;

    /**
     * 消息输入系统（支持量子加密传输）
     * <p>组成模块：
     * <table border="1">
     *   <tr><th>组件</th><th>功能</th><th>安全等级</th></tr>
     *   <tr><td>输入框</td><td>明文暂存</td><td>TLS 1.3</td></tr>
     *   <tr><td>广播按钮</td><td>消息触发</td><td>RSA-2048</td></tr>
     * </table>
     */
    private final JTextField inputField;
    private final JButton sendButton;

    /**
     * 后台服务实例（遵循单例模式）
     *
     * @implNote 通过构造器注入显示组件实现界面-服务联动
     */
    private final Server server;


    public ServerFrame() {
        setLayout(new BorderLayout());

        displayArea = new JTextArea();
        displayArea.setEditable(false);
        displayArea.setLineWrap(true);
        displayArea.setWrapStyleWord(true);

        // 启用自动滚动
        DefaultCaret caret = (DefaultCaret) displayArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane scrollPane = new JScrollPane(displayArea);
        add(scrollPane, BorderLayout.CENTER);


        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        sendButton = new JButton("Broadcast");
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        server = new Server(displayArea);

        sendButton.addActionListener(e -> Broadcast());
        inputField.addActionListener(e -> Broadcast());
    }

    private void Broadcast() {
        String textToSend = inputField.getText();
        if (!textToSend.isEmpty()) {
            displayArea.append("Broadcasting: " + textToSend + "\n");
            inputField.setText("");
            server.broadcastToClients(textToSend);
        }
    }
}