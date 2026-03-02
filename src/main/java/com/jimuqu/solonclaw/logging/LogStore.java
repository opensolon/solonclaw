package com.jimuqu.solonclaw.logging;

import org.noear.solon.annotation.Component;
import org.noear.solon.Solon;
import org.noear.snack4.ONode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 日志存储类 - 使用文件系统存储日志
 */
@Component
public class LogStore {
    private static final String LOG_DIR = "workspace/logs";
    private static final String LOG_FILE_PREFIX = "solonclaw-";
    private static final String LOG_FILE_SUFFIX = ".log";
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Path logDir;

    public LogStore() {
        this.logDir = Paths.get("workspace/logs");
        ensureLogDirExists();
    }

    private void ensureLogDirExists() {
        try {
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create log directory: " + logDir, e);
        }
    }

    /**
     * 写入日志
     */
    public void writeLog(LogEntry entry) {
        String dateStr = LocalDateTime.now().format(FILE_DATE_FORMAT);
        Path logFile = logDir.resolve(LOG_FILE_PREFIX + dateStr + LOG_FILE_SUFFIX);

        try (FileWriter writer = new FileWriter(logFile.toFile(), true)) {
            writer.write(formatLogEntry(entry));
            writer.write("\n");
        } catch (IOException e) {
            throw new RuntimeException("Failed to write log entry", e);
        }
    }

    /**
     * 批量写入日志
     */
    public void writeLogs(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        String dateStr = LocalDateTime.now().format(FILE_DATE_FORMAT);
        Path logFile = logDir.resolve(LOG_FILE_PREFIX + dateStr + LOG_FILE_SUFFIX);

        try (FileWriter writer = new FileWriter(logFile.toFile(), true)) {
            for (LogEntry entry : entries) {
                writer.write(formatLogEntry(entry));
                writer.write("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write log entries", e);
        }
    }

    /**
     * 格式化日志条目为 JSON
     */
    private String formatLogEntry(LogEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", entry.getTimestamp().format(LOG_TIMESTAMP_FORMAT));
        map.put("level", entry.getLevel().getCode());
        map.put("levelPriority", entry.getLevel().getPriority());
        map.put("source", entry.getSource());
        map.put("sessionId", entry.getSessionId());
        map.put("message", entry.getMessage());
        if (entry.getMetadata() != null && !entry.getMetadata().isEmpty()) {
            map.put("metadata", entry.getMetadata());
        }

        return ONode.serialize(map);
    }

    /**
     * 查询日志
     */
    public List<LogEntry> queryLogs(LogQuery query) {
        List<LogEntry> results = new ArrayList<>();

        try {
            // 获取所有日志文件
            List<Path> logFiles = getLogFiles(query);

            for (Path logFile : logFiles) {
                List<LogEntry> entries = parseLogFile(logFile, query);
                results.addAll(entries);
            }

            // 排序
            results.sort(Comparator.comparing(LogEntry::getTimestamp).reversed());

            // 分页
            if (query.getPage() != null && query.getPageSize() != null) {
                int start = (query.getPage() - 1) * query.getPageSize();
                int end = Math.min(start + query.getPageSize(), results.size());
                if (start < results.size()) {
                    results = results.subList(start, end);
                } else {
                    results = new ArrayList<>();
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to query logs", e);
        }

        return results;
    }

    /**
     * 获取日志文件列表
     */
    private List<Path> getLogFiles(LogQuery query) throws IOException {
        try (Stream<Path> stream = Files.list(logDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith(LOG_FILE_PREFIX))
                    .filter(p -> p.getFileName().toString().endsWith(LOG_FILE_SUFFIX))
                    .sorted(Comparator.reverseOrder())
                    .limit(query.getMaxFiles() != null ? query.getMaxFiles() : 30)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 解析日志文件
     */
    private List<LogEntry> parseLogFile(Path logFile, LogQuery query) throws IOException {
        List<LogEntry> entries = new ArrayList<>();

        try (Stream<String> lines = Files.lines(logFile)) {
            lines.forEach(line -> {
                try {
                    LogEntry entry = parseLogEntry(line);
                    if (matchesQuery(entry, query)) {
                        entries.add(entry);
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }
            });
        }

        return entries;
    }

    /**
     * 解析单条日志
     */
    private LogEntry parseLogEntry(String line) {
        ONode node = ONode.ofJson(line);

        LogEntry entry = new LogEntry();
        entry.setTimestamp(LocalDateTime.parse(node.get("timestamp").getString(), LOG_TIMESTAMP_FORMAT));
        entry.setLevel(LogLevel.valueOf(node.get("level").getString()));
        entry.setSource(node.get("source").getString());
        entry.setSessionId(node.get("sessionId").getString());
        entry.setMessage(node.get("message").getString());

        // 简化：暂时不解析 metadata
        entry.setMetadata(new HashMap<>());

        return entry;
    }

    /**
     * 判断日志是否匹配查询条件
     */
    private boolean matchesQuery(LogEntry entry, LogQuery query) {
        // 级别过滤
        if (query.getLevels() != null && !query.getLevels().isEmpty()) {
            if (!query.getLevels().contains(entry.getLevel())) {
                return false;
            }
        }

        // 来源过滤
        if (query.getSources() != null && !query.getSources().isEmpty()) {
            if (!query.getSources().contains(entry.getSource())) {
                return false;
            }
        }

        // 会话过滤
        if (query.getSessionId() != null && !query.getSessionId().isEmpty()) {
            if (!query.getSessionId().equals(entry.getSessionId())) {
                return false;
            }
        }

        // 关键词过滤
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            if (!entry.getMessage().toLowerCase().contains(query.getKeyword().toLowerCase())) {
                return false;
            }
        }

        // 时间范围过滤
        if (query.getStartTime() != null && entry.getTimestamp().isBefore(query.getStartTime())) {
            return false;
        }
        if (query.getEndTime() != null && entry.getTimestamp().isAfter(query.getEndTime())) {
            return false;
        }

        return true;
    }

    /**
     * 获取日志统计
     */
    public LogStats getStats() {
        LogStats stats = new LogStats();

        try (Stream<Path> stream = Files.list(logDir)) {
            stats.setTotalFiles((int) stream
                    .filter(p -> p.getFileName().toString().startsWith(LOG_FILE_PREFIX))
                    .filter(p -> p.getFileName().toString().endsWith(LOG_FILE_SUFFIX))
                    .count());
        } catch (IOException e) {
            // ignore
        }

        return stats;
    }

    /**
     * 执行日志维护
     * 清理超过 30 天的日志文件
     */
    public void performMaintenance() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        clearLogs(cutoffDate);
    }

    /**
     * 清空日志
     */
    public void clearLogs(LocalDateTime before) {
        try (Stream<Path> stream = Files.list(logDir)) {
            stream.filter(p -> p.getFileName().toString().startsWith(LOG_FILE_PREFIX))
                    .filter(p -> p.getFileName().toString().endsWith(LOG_FILE_SUFFIX))
                    .forEach(p -> {
                        try {
                            String dateStr = p.getFileName().toString()
                                    .substring(LOG_FILE_PREFIX.length(),
                                            p.getFileName().toString().length() - LOG_FILE_SUFFIX.length());
                            LocalDateTime fileDate = LocalDateTime.parse(dateStr, FILE_DATE_FORMAT);

                            if (before == null || fileDate.isBefore(before)) {
                                Files.delete(p);
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear logs", e);
        }
    }
}