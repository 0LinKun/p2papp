package web;

import java.util.List;

public class Gson {
    // 手动实现JSON序列化（仅应急使用）
    public String toJson(List<String> list) {
        StringBuilder sb = new StringBuilder("{\"files\":[");
        for (int i=0; i<list.size();  i++) {
            sb.append("\"").append(list.get(i)).append("\"");
            if (i != list.size()-1)  sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

}
