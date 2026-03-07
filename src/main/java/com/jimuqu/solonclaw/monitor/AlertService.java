package com.jimuqu.solonclaw.monitor;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * 告警服务
 * 处理系统告警和通知
 */
@Component
public class AlertService {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private static final int MAX_ALERT_HISTORY = 100;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 告警历史
    private final List<AlertRecord> alertHistory = new CopyOnWriteArrayList<>();

    // 告警计数（按级别）
    private final Map<AlertLevel, Long> alertCounts = new ConcurrentHashMap<>();

    // 告警冷却时间（毫秒）- 防止相同告警频繁触发
    private final Map<String, Long> lastAlertTime = new ConcurrentHashMap<>();
    private static final long ALERT_COOLDOWN = 60000; // 1分钟

    /**
     * 发送告警
     */
    public void sendAlert(AlertLevel level, String title, String message) {
        String alertKey = level + ":" + title;

        // 检查冷却时间
        Long lastTime = lastAlertTime.get(alertKey);
        if (lastTime != null && System.currentTimeMillis() - lastTime < ALERT_COOLDOWN) {
            log.debug("Alert {} is in cooldown, skipping", alertKey);
            return;
        }

        // 记录告警
        AlertRecord record = new AlertRecord(level, title, message, LocalDateTime.now());
        alertHistory.add(record);

        // 保持历史记录数量限制
        if (alertHistory.size() > MAX_ALERT_HISTORY) {
            alertHistory.remove(0);
        }

        // 更新计数
        alertCounts.merge(level, 1L, Long::sum);

        // 更新最后告警时间
        lastAlertTime.put(alertKey, System.currentTimeMillis());

        // 记录日志
        logAlert(record);
    }

    /**
     * 发送信息级别告警
     */
    public void info(String title, String message) {
        sendAlert(AlertLevel.INFO, title, message);
    }

    /**
     * 发送警告级别告警
     */
    public void warning(String title, String message) {
        sendAlert(AlertLevel.WARNING, title, message);
    }

    /**
     * 发送严重级别告警
     */
    public void critical(String title, String message) {
        sendAlert(AlertLevel.CRITICAL, title, message);
    }

    /**
     * 发送紧急级别告警
     */
    public void emergency(String title, String message) {
        sendAlert(AlertLevel.EMERGENCY, title, message);
    }

    /**
     * 记录告警日志
     */
    private void logAlert(AlertRecord record) {
        String logMessage = String.format("[%s] %s - %s",
                record.getLevel().getDescription(),
                record.getTitle(),
                record.getMessage());

        switch (record.getLevel()) {
            case INFO -> log.info(logMessage);
            case WARNING -> log.warn(logMessage);
            case CRITICAL, EMERGENCY -> log.error(logMessage);
        }
    }

    /**
     * 获取告警历史
     */
    public List<AlertRecord> getAlertHistory() {
        return new CopyOnWriteArrayList<>(alertHistory);
    }

    /**
     * 获取告警历史（分页）
     */
    public List<AlertRecord> getAlertHistory(int page, int pageSize) {
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, alertHistory.size());

        if (start >= alertHistory.size()) {
            return List.of();
        }

        return alertHistory.subList(alertHistory.size() - end, alertHistory.size() - start);
    }

    /**
     * 获取告警统计
     */
    public Map<AlertLevel, Long> getAlertCounts() {
        return new ConcurrentHashMap<>(alertCounts);
    }

    /**
     * 清除告警历史
     */
    public void clearHistory() {
        alertHistory.clear();
        alertCounts.clear();
        lastAlertTime.clear();
        log.info("Alert history cleared");
    }

    /**
     * 告警记录
     */
    public static class AlertRecord {
        private final AlertLevel level;
        private final String title;
        private final String message;
        private final LocalDateTime timestamp;

        public AlertRecord(AlertLevel level, String title, String message, LocalDateTime timestamp) {
            this.level = level;
            this.title = title;
            this.message = message;
            this.timestamp = timestamp;
        }

        public AlertLevel getLevel() {
            return level;
        }

        public String getTitle() {
            return title;
        }

        public String getMessage() {
            return message;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}