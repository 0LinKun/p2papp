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

class ClientLogger {
    private static final String LOG_DIR = "logs";
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    public static synchronized void log(JTextArea displayArea, String message) {
        try {
            // 创建日志目录
            Path logPath = Paths.get(LOG_DIR);
            if (!Files.exists(logPath))  {
                Files.createDirectories(logPath);
            }

            // 生成日志文件名
            String dateStr = DATE_FORMAT.format(Instant.now());
            String filename = "client_" + dateStr + ".log";

            // 生成带时间日志内容
            String logContent = String.format("[%s]  %s\n",
                    LogTimeFormatter.getFormattedTime(),
                    message);

            // 写入显示区域
            SwingUtilities.invokeLater(()  -> displayArea.append(logContent));

            // 写入文件（追加模式）
            Files.write(Paths.get(LOG_DIR,  filename), logContent.getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println(" 日志写入失败: " + e.getMessage());
        }
    }
}class LogTimeFormatter {
    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss")
                    .withZone(ZoneId.of("Asia/Shanghai"));

    public static String getFormattedTime() {
        return formatter.format(Instant.now());
    }
}
