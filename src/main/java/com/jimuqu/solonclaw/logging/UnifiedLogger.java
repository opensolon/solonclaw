package com.jimuqu.solonclaw.logging;

import org.noear.solon.annotation.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一日志记录器
 */
@Component
public class UnifiedLogger {
    private final LogStore logStore;

    public UnifiedLogger(LogStore logStore) {
        this.logStore = logStore;
    }

    /**
     * 记录日志
     */
    public void log(LogLevel level, String source, String sessionId, String message) {
        log(level, source, sessionId, message, null);
    }

    /**
     * 记录日志（带元数据）
     */
    public void log(LogLevel level, String source, String sessionId, String message, Map<String, Object> metadata) {
        LogEntry entry = new LogEntry(level, source, sessionId, message);
        if (metadata != null && !metadata.isEmpty()) {
            entry.setMetadata(metadata);
        }
        logStore.writeLog(entry);
    }

    /**
     * INFO 级别日志
     */
    public void info(String source, String sessionId, String message) {
        log(LogLevel.INFO, source, sessionId, message);
    }

    /**
     * 用户对话日志
     */
    public void userChat(String sessionId, String message) {
        log(LogLevel.USER_CHAT, "Gateway", sessionId, message);
    }

    /**
     * Agent 思考日志
     */
    public void agentThink(String sessionId, String thought) {
        log(LogLevel.AGENT_THINK, "Agent", sessionId, thought);
    }

    /**
     * 决策日志
     */
    public void decision(String sessionId, String decision, Map<String, Object> metadata) {
        log(LogLevel.DECISION, "DecisionEngine", sessionId, decision, metadata);
    }

    /**
     * 行动日志
     */
    public void action(String sessionId, String action, Map<String, Object> metadata) {
        log(LogLevel.ACTION, "Action", sessionId, action, metadata);
    }

    /**
     * 反省日志
     */
    public void reflection(String sessionId, String reflection) {
        log(LogLevel.REFLECTION, "Reflection", sessionId, reflection);
    }

    /**
     * 错误日志
     */
    public void error(String source, String sessionId, String error, Throwable throwable) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        if (throwable != null) {
            metadata.put("exception", throwable.getClass().getName());
            metadata.put("message", throwable.getMessage());
        }
        log(LogLevel.ERROR, source, sessionId, error, metadata);
    }

    /**
     * 获取日志存储
     */
    public LogStore getLogStore() {
        return logStore;
    }
}