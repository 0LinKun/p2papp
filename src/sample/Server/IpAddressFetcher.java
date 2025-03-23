package sample.Server;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IPv4地址探测工具类（支持中英文系统环境）
 *
 * <p>本类通过解析操作系统网络配置信息，实现以下核心功能：
 * <ul>
 *   <li><b>跨语言适配</b>：自动识别中英文系统的ipconfig命令输出差异</li>
 *   <li><b>地址过滤机制</b>：排除本地回环地址(127.0.0.1)</li>
 *   <li><b>编码兼容处理</b>：采用GBK编码解析Windows中文系统输出</li>
 *   <li><b>GUI集成支持</b>：提供直接更新文本显示区域的方法</li>
 * </ul>
 *
 * @version 1.0
 * @since 2025.3.22
 */
public class IpAddressFetcher {

    /**
     * IPv4地址显示方法（线程安全版）
     *
     * @param displayArea 文本显示组件（需实现线程安全更新）
     * @implSpec 工作流程：
     * <ol>
     *   <li>调用{@link #getIPv4Addresses()}获取地址列表</li>
     *   <li>在显示区域追加格式化结果：
     *     <ul>
     *       <li>无地址时显示"No IPv4 address found"</li>
     *       <li>有地址时显示"Available IPv4 addresses:"及列表</li>
     *     </ul>
     *   </li>
     * </ol>
     * @apiNote 线程安全建议：
     * <p>建议通过{@code SwingUtilities.invokeLater} 调用本方法，
     * 确保在事件分发线程(EDT)更新UI组件
     */
    public static void IpAddress(JTextArea displayArea) {
        List<String> ipAddresses = getIPv4Addresses();
        if (ipAddresses.isEmpty()) {
            displayArea.append("No  IPv4 address found\n");
        } else {
            displayArea.append("Available  IPv4 addresses:\n");
            for (int i = 0; i < ipAddresses.size(); i++) {
                displayArea.append(ipAddresses.get(i) + "\n");
            }
        }
    }

    /**
     * IPv4地址获取方法（系统命令解析版）
     *
     * @return 有效IPv4地址列表（可能为空）
     * @implSpec 技术实现：
     * <ol>
     *   <li><b>命令执行</b>：通过{@code Runtime.exec("ipconfig")} 获取网络配置</li>
     *   <li><b>输出解析</b>：使用复合正则表达式匹配：
     *     <table>
     *       <tr><th>语言</th><th>模式</th></tr>
     *       <tr><td>英文</td><td>IPv4 Address[非数字]+(IPv4地址)</td></tr>
     *       <tr><td>中文</td><td>IPv4 地址[非数字]+(IPv4地址)</td></tr>
     *     </table>
     *   </li>
     *   <li><b>编码处理</b>：强制使用GBK解码Windows中文系统输出</li>
     * </ol>
     * @implNote 平台限制：
     * <ul>
     *   <li>仅支持Windows操作系统</li>
     *   <li>依赖ipconfig命令输出格式稳定性</li>
     *   <li>无法获取被防火墙屏蔽的虚拟适配器地址</li>
     * </ul>
     */
    public static List<String> getIPv4Addresses() {
        List<String> addresses = new ArrayList<>();

        try {
            // 执行ipconfig命令
            Process process = Runtime.getRuntime().exec("ipconfig");

            // 读取命令输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "GBK"))) { // Windows中文系统编码

                String line;
                Pattern pattern = Pattern.compile(
                        "(IPv4 Address[^\\d]+((?:\\d+\\.){3}\\d+))" // 英文系统
                                + "|(IPv4 地址[^\\d]+((?:\\d+\\.){3}\\d+))", // 中文系统
                        Pattern.CASE_INSENSITIVE
                );

                while ((line = reader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String ip = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
                        if (!ip.equals("127.0.0.1")) { // 过滤回环地址
                            addresses.add(ip);
                        }
                    }
                }
            }

            // 等待命令执行完成
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println("Error  executing command: " + e.getMessage());
        }
        return addresses;
    }

}
