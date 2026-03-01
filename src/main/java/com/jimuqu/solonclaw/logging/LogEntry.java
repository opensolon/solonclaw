package com.jimuqu.solonclaw.logging;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 日志条目
 */
public class LogEntry {
    private LocalDateTime timestamp;
    private LogLevel level;
    private String source;
    private String sessionId;
    private String message;
    private Map<String, Object> metadata;

    public LogEntry() {
        this.timestamp = LocalDateTime.now();
        this.metadata = new HashMap<>();
    }

    public LogEntry(LogLevel level, String source, String sessionId, String message) {
        this();
        this.level = level;
        this.source = source;
        this.sessionId = sessionId;
        this.message = message;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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