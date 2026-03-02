package com.jimuqu.solonclaw.logging;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 日志条目
 */
public class LogEntry {
    private static String HOSTNAME = "unknown";
    private static String IP_ADDRESS = "unknown";

    static {
        try {
            java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
            HOSTNAME = localHost.getHostName();
            IP_ADDRESS = localHost.getHostAddress();
        } catch (java.net.UnknownHostException e) {
            // Keep default values
        }
    }

    private String id;
    private LocalDateTime timestamp;
    private LogLevel level;
    private String source;
    private String sessionId;
    private String category;
    private String traceId;
    private String hostname;
    private String ip;
    private String message;
    private long duration;
    private Map<String, Object> metadata;

    public LogEntry() {
        this.timestamp = LocalDateTime.now();
        this.metadata = new HashMap<>();
        this.id = java.util.UUID.randomUUID().toString();
        this.hostname = HOSTNAME;
        this.ip = IP_ADDRESS;
    }

    public static String getHOSTNAME() {
        return HOSTNAME;
    }

    public static String getIpAddress() {
        return IP_ADDRESS;
    }

    public LogEntry(LogLevel level, String source, String sessionId, String message) {
        this();
        this.level = level;
        this.source = source;
        this.sessionId = sessionId;
        this.message = message;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public LogLevel getLevel() {
        return level;
    }

    public void setLevel(LogLevel level) {
        this.level = level;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * 添加元数据
     */
    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * 获取元数据
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
}