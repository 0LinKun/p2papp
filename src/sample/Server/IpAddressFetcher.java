package sample.Server;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IpAddressFetcher {


    public static void IpAddress(JTextArea displayArea) {
        List<String> ipAddresses = getIPv4Addresses();
        if (ipAddresses.isEmpty())  {
            displayArea.append("No  IPv4 address found\n");
        } else {
            displayArea.append("Available  IPv4 addresses:\n");
            for (int i = 0; i < ipAddresses.size(); i++) {
                displayArea.append(ipAddresses.get(i)+"\n");
            }
        }
    }

    public static List<String> getIPv4Addresses() {
        List<String> addresses = new ArrayList<>();

        try {
            // 执行ipconfig命令
            Process process = Runtime.getRuntime().exec("ipconfig");

            // 读取命令输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(),  "GBK"))) { // Windows中文系统编码

                String line;
                Pattern pattern = Pattern.compile(
                        "(IPv4 Address[^\\d]+((?:\\d+\\.){3}\\d+))" // 英文系统
                                + "|(IPv4 地址[^\\d]+((?:\\d+\\.){3}\\d+))", // 中文系统
                        Pattern.CASE_INSENSITIVE
                );

                while ((line = reader.readLine())  != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find())  {
                        String ip = matcher.group(2)  != null ? matcher.group(2)  : matcher.group(4);
                        if (!ip.equals("127.0.0.1"))  { // 过滤回环地址
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
