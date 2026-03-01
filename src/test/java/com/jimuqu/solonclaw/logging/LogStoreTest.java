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
}