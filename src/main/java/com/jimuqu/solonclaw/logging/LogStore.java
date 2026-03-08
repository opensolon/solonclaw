package com.jimuqu.solonclaw.logging;

import org.noear.solon.annotation.Component;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
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

        try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(logFile, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND), StandardCharsets.UTF_8)) {
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

        try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(logFile, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND), StandardCharsets.UTF_8)) {
            for (LogEntry entry : entries) {
                writer.write(formatLogEntry(entry));
                writer.write("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write log entries", e);
        }
    }

    /**
     * 格式化日志条目为纯文本
     */
    private String formatLogEntry(LogEntry entry) {
        return entry.getTimestamp().format(LOG_TIMESTAMP_FORMAT)
                + " [" + entry.getLevel().getCode() + "]"
                + " [" + entry.getSource() + "]"
                + " [" + (entry.getSessionId() != null ? entry.getSessionId() : "") + "]"
                + " " + entry.getMessage();
    }

    /**
     * 获取原始日志文本（最新的日志文件内容，倒序返回）
     */
    public String getRawLogs(int maxLines) {
        try (Stream<Path> stream = Files.list(logDir)) {
            List<Path> logFiles = stream
                    .filter(p -> p.getFileName().toString().startsWith(LOG_FILE_PREFIX))
                    .filter(p -> p.getFileName().toString().endsWith(LOG_FILE_SUFFIX))
                    .sorted(Comparator.reverseOrder())
                    .limit(3)
                    .collect(Collectors.toList());

            List<String> lines = new ArrayList<>();
            for (Path logFile : logFiles) {
                List<String> fileLines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
                lines.addAll(fileLines);
                if (lines.size() >= maxLines) break;
            }

            // 倒序，最新的在上面
            Collections.reverse(lines);
            if (lines.size() > maxLines) {
                lines = lines.subList(0, maxLines);
            }
            return String.join("\n", lines);
        } catch (IOException e) {
            return "";
        }
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
                            LocalDateTime fileDate = LocalDate.parse(dateStr, FILE_DATE_FORMAT).atStartOfDay();

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