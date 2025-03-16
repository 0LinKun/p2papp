package web;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MultipartParser {
    private static final String BOUNDARY_PREFIX = "--";
    private final InputStream inputStream;
    private final String boundary;

    public MultipartParser(InputStream is, String contentType) {
        this.inputStream  = is;
        this.boundary  = extractBoundary(contentType);
    }

    private String extractBoundary(String contentType) {
        String[] parts = contentType.split(";");
        for (String part : parts) {
            if (part.trim().startsWith("boundary="))  {
                return BOUNDARY_PREFIX + part.split("=")[1].trim();
            }
        }
        throw new IllegalArgumentException("Invalid multipart content type");
    }

    public Map<String, byte[]> parse() throws IOException {
        Map<String, byte[]> result = new HashMap<>();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        boolean inHeaders = true;
        boolean inFilePart = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine())  != null) {
                if (line.startsWith(boundary))  {
                    if (inFilePart) {
                        // 移除末尾的换行符
                        byte[] content = bos.toByteArray();
                        if (content.length  > 0 && content[content.length-1] == '\n') {
                            content = Arrays.copyOf(content,  content.length-1);
                        }
                        result.put("content",  content);
                        bos.reset();
                    }
                    inHeaders = true;
                    inFilePart = false;
                    continue;
                }

                if (inHeaders) {
                    if (line.isEmpty())  {
                        inHeaders = false;
                        inFilePart = true;
                        continue;
                    }

                    // 解析文件名（与原始代码逻辑保持一致）
                    if (line.startsWith("Content-Disposition:"))  {
                        String filename = extractFilename(line);
                        if (filename != null) {
                            result.put("filename",  filename.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                } else if (inFilePart) {
                    bos.write(line.getBytes(StandardCharsets.UTF_8));
                    bos.write('\n');
                }
            }
        }
        return result;
    }

    private String extractFilename(String dispositionLine) {
        Pattern pattern = Pattern.compile("filename\\*?=\"?(.*?)\"?$");
        Matcher matcher = pattern.matcher(dispositionLine);
        if (matcher.find())  {
            try {
                return URLDecoder.decode(matcher.group(1),  StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                return matcher.group(1);
            }
        }
        return null;
    }
}
