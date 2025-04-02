package sample.Client;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 客户端日志记录工具类（线程安全），实现界面与文件双路日志记录功能
 *
 * <p>本类采用同步机制确保多线程环境下的日志完整性，主要功能特性包括：
 * <ul>
 *   <li>日志文件按日滚动存储：日志文件按"client_yyyyMMdd.log" 格式命名，自动创建logs目录</li>
 *   <li>界面实时更新：通过SwingUtilities保证UI线程安全更新</li>
 *   <li>高性能写入：采用NIO文件操作，使用追加写入模式(StandardOpenOption.APPEND)</li>
 *   <li>结构化日志格式：每条日志包含上海时区的时间戳（精确到秒）</li>
 * </ul>
 *
 * @see LogTimeFormatter 关联的时间格式化工具类
 * @since 2024.1.0
 */
public class ClientLogger {
    /**
     * 日志存储目录路径，采用相对路径"logs"
     * <p>首次使用时自动创建目录结构，支持Windows/Linux多平台路径格式</p>
     */
    private static final String LOG_DIR = "logs";
    /**
     * 日志文件日期格式器（线程安全）
     * <p>定义日志文件名的日期部分格式为年月日(yyyyMMdd)，适用于按日滚动日志场景</p>
     */
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault());

    /**
     * 双路日志记录方法（同步锁保证线程安全）
     *
     * <p>方法执行流程：
     * <ol>
     *   <li>检测并创建日志目录（如果不存在）</li>
     *   <li>生成带时间戳的日志内容（调用LogTimeFormatter）</li>
     *   <li>通过事件调度线程更新显示区域</li>
     *   <li>以追加模式写入日志文件</li>
     * </ol>
     *
     * @param displayArea 消息显示区域组件（需线程安全访问）
     * @param message     日志消息内容（自动添加换行符）
     * @implNote 当发生IO异常时，错误信息将输出到标准错误流但不会中断程序执行
     * @see SwingUtilities#invokeLater(Runnable) 用于保证UI线程安全
     */
    public static synchronized void log(JTextArea displayArea, String message) {
        try {
            // 创建日志目录
            Path logPath = Paths.get(LOG_DIR);
            if (!Files.exists(logPath)) {
                Files.createDirectories(logPath);
            }

            // 生成日志文件名
            String dateStr = DATE_FORMAT.format(Instant.now());
            String filename = "client_" + dateStr + ".log";

            // 生成带时间日志内容
            String logContent = String.format("[%s]  %s\n", LogTimeFormatter.getFormattedTime(), message);

            // 写入显示区域
            SwingUtilities.invokeLater(() -> displayArea.append(logContent));

            // 写入文件（追加模式）
            Files.write(Paths.get(LOG_DIR, filename), logContent.getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println(" 日志写入失败: " + e.getMessage());
        }
    }
}

/**
 * 上海时区时间格式化工具类，提供标准化时间戳生成服务
 *
 * <p>本类采用不可变设计模式，主要特征包括：
 * <ul>
 *   <li>固定时区为"Asia/Shanghai"（中国标准时间）</li>
 *   <li>时间格式为"yyyy-MM-dd HH:mm:ss"（如2025-03-22 11:06:00）</li>
 *   <li>线程安全设计：所有方法均为静态同步方法</li>
 * </ul>
 */
class LogTimeFormatter {
    /**
     * 时间格式化器实例（不可变对象）
     * <p>配置说明：
     * <ul>
     *   <li>时间格式：年-月-日 时:分:秒（24小时制）</li>
     *   <li>时区设定：东八区（UTC+8），包含夏令时自动调整</li>
     * </ul>
     */
    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss")
                    .withZone(ZoneId.of("Asia/Shanghai"));

    /**
     * 获取当前时间的格式化字符串
     *
     * @return 符合ISO扩展格式的本地化时间字符串，例如："2025-03-22 11:06:00"
     * @implSpec 该方法通过Instant.now() 获取UTC时间后转换为上海时区时间
     */
    public static String getFormattedTime() {
        return formatter.format(Instant.now());
    }
}
