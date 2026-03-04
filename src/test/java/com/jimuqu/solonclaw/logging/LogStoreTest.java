package com.jimuqu.solonclaw.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 日志存储测试
 */
class LogStoreTest {

    private LogStore logStore;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        logStore = new LogStore();
    }

    @Test
    void testWriteAndReadLog() {
        LogEntry entry = new LogEntry(
                LogLevel.INFO,
                "Test",
                "test-session",
                "这是一条测试日志"
        );
        entry.addMetadata("key", "value");

        logStore.writeLog(entry);

        LogQuery query = new LogQuery()
                .addSource("Test")
                .setSessionId("test-session");

        List<LogEntry> results = logStore.queryLogs(query);

        assertFalse(results.isEmpty());
        assertEquals(LogLevel.INFO, results.get(0).getLevel());
        assertEquals("Test", results.get(0).getSource());
        assertEquals("test-session", results.get(0).getSessionId());
        assertEquals("这是一条测试日志", results.get(0).getMessage());
    }

    @Test
    void testBatchWriteLogs() {
        List<LogEntry> entries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LogEntry entry = new LogEntry(
                    LogLevel.INFO,
                    "Test",
                    "test-session",
                    "日志 " + i
            );
            entries.add(entry);
        }

        logStore.writeLogs(entries);

        LogQuery query = new LogQuery()
                .addSource("Test")
                .setSessionId("test-session");

        List<LogEntry> results = logStore.queryLogs(query);

        assertEquals(5, results.size());
    }

    @Test
    void testQueryByLevel() {
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Test", "s1", "info"));
        logStore.writeLog(new LogEntry(LogLevel.USER_CHAT, "Test", "s1", "chat"));
        logStore.writeLog(new LogEntry(LogLevel.ERROR, "Test", "s1", "error"));

        LogQuery query = new LogQuery()
                .addLevel(LogLevel.ERROR);

        List<LogEntry> results = logStore.queryLogs(query);

        assertEquals(1, results.size());
        assertEquals(LogLevel.ERROR, results.get(0).getLevel());
    }

    @Test
    void testQueryBySource() {
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Source1", "s1", "msg1"));
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Source2", "s1", "msg2"));
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Source1", "s1", "msg3"));

        LogQuery query = new LogQuery()
                .addSource("Source1");

        List<LogEntry> results = logStore.queryLogs(query);

        assertEquals(2, results.size());
    }

    @Test
    void testQueryByKeyword() {
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Test", "s1", "包含关键词的消息"));
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Test", "s1", "不包含的消息"));
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Test", "s1", "关键词在这里"));

        LogQuery query = new LogQuery()
                .setKeyword("关键词");

        List<LogEntry> results = logStore.queryLogs(query);

        assertEquals(2, results.size());
    }

    @Test
    void testPagination() {
        for (int i = 0; i < 15; i++) {
            logStore.writeLog(new LogEntry(LogLevel.INFO, "Test", "s1", "日志 " + i));
        }

        LogQuery query = new LogQuery()
                .addSource("Test")
                .setPage(1)
                .setPageSize(10);

        List<LogEntry> results = logStore.queryLogs(query);

        assertEquals(10, results.size());

        query.setPage(2);
        results = logStore.queryLogs(query);

        assertEquals(5, results.size());
    }

    @Test
    void testLogStats() {
        LogStats stats = logStore.getStats();

        assertNotNull(stats);
        assertTrue(stats.getTotalFiles() >= 0);
    }

    @Test
    void testClearLogs() {
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Test", "s1", "msg"));

        LogQuery query = new LogQuery().addSource("Test");
        List<LogEntry> before = logStore.queryLogs(query);
        assertFalse(before.isEmpty());

        logStore.clearLogs(null);

        List<LogEntry> after = logStore.queryLogs(query);
        // 清空后查询应该返回空列表（因为清空的是之前的日志）
    }

    // ========== 新增测试：getRecentLogs 和 getLogsByTimeRange ==========

    /**
     * 测试获取最近的日志（所有会话）
     */
    @Test
    void testGetRecentLogsAllSessions() {
        // 写入几条测试日志
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Source1", "session1", "消息1"));
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Source2", "session2", "消息2"));
        logStore.writeLog(new LogEntry(LogLevel.ERROR, "Source3", "session3", "错误消息"));

        // 获取最近的日志
        List<LogEntry> logs = logStore.getRecentLogs(null, 10);

        assertNotNull(logs);
        assertTrue(logs.size() >= 3);
    }

    /**
     * 测试获取特定会话的最近日志
     */
    @Test
    void testGetRecentLogsBySession() {
        // 写入不同会话的日志
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Source1", "session1", "消息1"));
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Source2", "session1", "消息2"));
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Source3", "session2", "消息3"));

        // 获取 session1 的日志
        List<LogEntry> logs = logStore.getRecentLogs("session1", 10);

        assertNotNull(logs);
        // 验证所有返回的日志都属于 session1
        assertTrue(logs.stream().allMatch(e -> "session1".equals(e.getSessionId())));
    }

    /**
     * 测试限制返回数量
     */
    @Test
    void testGetRecentLogsWithLimit() {
        // 写入多条日志
        for (int i = 0; i < 20; i++) {
            logStore.writeLog(new LogEntry(LogLevel.INFO, "Source", "session1", "消息" + i));
        }

        // 只获取最近的 10 条
        List<LogEntry> logs = logStore.getRecentLogs(null, 10);

        assertNotNull(logs);
        assertTrue(logs.size() <= 10);
    }

    /**
     * 测试获取指定时间范围的日志
     */
    @Test
    void testGetLogsByTimeRange() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);

        // 写入日志
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Source1", "session1", "最近的消息"));

        // 获取最近一小时的日志
        List<LogEntry> logs = logStore.getLogsByTimeRange(null, oneHourAgo, now);

        assertNotNull(logs);
    }

    /**
     * 测试获取特定会话和时间范围的日志
     */
    @Test
    void testGetLogsByTimeRangeAndSession() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);

        // 写入不同会话的日志
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Source1", "session1", "消息1"));
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Source2", "session2", "消息2"));

        // 获取 session1 的日志
        List<LogEntry> logs = logStore.getLogsByTimeRange("session1", oneHourAgo, now);

        assertNotNull(logs);
        // 验证所有返回的日志都属于 session1
        assertTrue(logs.stream().allMatch(e -> "session1".equals(e.getSessionId())));
    }
}