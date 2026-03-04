package com.jimuqu.solonclaw.logging;

import org.noear.solon.annotation.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 统一日志记录器
 */
@Component
public class UnifiedLogger {
    private final LogStore logStore;

    // 错误分类统计：按异常类名分类
    private final Map<String, AtomicLong> errorTypeCounts = new ConcurrentHashMap<>();

    // 错误分类统计：按来源分类
    private final Map<String, AtomicLong> errorSourceCounts = new ConcurrentHashMap<>();

    // 总错误计数
    private final AtomicLong totalErrorCount = new AtomicLong(0);

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

            // 按异常类型统计
            String exceptionType = throwable.getClass().getName();
            errorTypeCounts.computeIfAbsent(exceptionType, k -> new AtomicLong(0)).incrementAndGet();
        }

        // 按来源统计
        errorSourceCounts.computeIfAbsent(source, k -> new AtomicLong(0)).incrementAndGet();

        // 总错误计数
        totalErrorCount.incrementAndGet();

        log(LogLevel.ERROR, source, sessionId, error, metadata);
    }

    /**
     * 获取错误分类统计（按异常类型）
     */
    public Map<String, Long> getErrorTypeStats() {
        Map<String, Long> result = new HashMap<>();
        errorTypeCounts.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    /**
     * 获取错误分类统计（按来源）
     */
    public Map<String, Long> getErrorSourceStats() {
        Map<String, Long> result = new HashMap<>();
        errorSourceCounts.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    /**
     * 获取总错误数
     */
    public long getTotalErrorCount() {
        return totalErrorCount.get();
    }

    /**
     * 获取完整错误统计
     */
    public ErrorStatistics getErrorStatistics() {
        return new ErrorStatistics(
            totalErrorCount.get(),
            getErrorTypeStats(),
            getErrorSourceStats()
        );
    }

    /**
     * 重置错误统计
     */
    public void resetErrorStats() {
        errorTypeCounts.clear();
        errorSourceCounts.clear();
        totalErrorCount.set(0);
    }

    /**
     * 获取日志存储
     */
    public LogStore getLogStore() {
        return logStore;
    }

    /**
     * 错误统计数据
     */
    public record ErrorStatistics(
        long totalErrors,
        Map<String, Long> byExceptionType,
        Map<String, Long> bySource
    ) {}
}