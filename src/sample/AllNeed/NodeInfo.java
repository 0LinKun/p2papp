package sample.AllNeed;

import java.util.Map;

// NodeInfo类，用于封装节点信息
public class NodeInfo {
    private String nodeId;
    private String ipAddress;
    private int port;
    private Map<String, String> metadata;
    private long lastHeartbeat;

    public NodeInfo(String nodeId, String ipAddress, int port, Map<String, String> metadata) {
        this.nodeId = nodeId;
        this.ipAddress = ipAddress;
        this.port = port;
        this.metadata = metadata;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
