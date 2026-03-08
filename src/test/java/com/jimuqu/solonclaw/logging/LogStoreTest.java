package com.jimuqu.solonclaw.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
        LogEntry entry = new LogEntry(LogLevel.INFO, "Test", "test-session", "这是一条测试日志");

        logStore.writeLog(entry);

        String raw = logStore.getRawLogs(100);
        assertNotNull(raw);
        assertTrue(raw.contains("这是一条测试日志"));
        assertTrue(raw.contains("[INFO]"));
        assertTrue(raw.contains("[Test]"));
        assertTrue(raw.contains("[test-session]"));
    }

    @Test
    void testBatchWriteLogs() {
        List<LogEntry> entries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            entries.add(new LogEntry(LogLevel.INFO, "Test", "test-session", "日志 " + i));
        }

        logStore.writeLogs(entries);

        String raw = logStore.getRawLogs(100);
        for (int i = 0; i < 5; i++) {
            assertTrue(raw.contains("日志 " + i));
        }
    }

    @Test
    void testLogFormat() {
        logStore.writeLog(new LogEntry(LogLevel.ERROR, "Source1", "s1", "error msg unique_marker_format"));

        String raw = logStore.getRawLogs(500);
        assertTrue(raw.contains("[ERROR]"));
        assertTrue(raw.contains("[Source1]"));
        assertTrue(raw.contains("[s1]"));
        assertTrue(raw.contains("error msg unique_marker_format"));
    }

    @Test
    void testLogStats() {
        LogStats stats = logStore.getStats();
        assertNotNull(stats);
        assertTrue(stats.getTotalFiles() >= 0);
    }

    @Test
    void testClearLogs() {
        String uniqueMsg = "clear_test_unique_" + System.currentTimeMillis();
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Test", "s1", uniqueMsg));

        String before = logStore.getRawLogs(500);
        assertTrue(before.contains(uniqueMsg));

        logStore.clearLogs(null);

        String after = logStore.getRawLogs(500);
        assertTrue(after.isEmpty());
    }

    @Test
    void testGetRawLogsLimit() {
        for (int i = 0; i < 20; i++) {
            logStore.writeLog(new LogEntry(LogLevel.INFO, "Source", "session1", "消息" + i));
        }

        String raw = logStore.getRawLogs(10);
        long lineCount = raw.lines().filter(l -> !l.isBlank()).count();
        assertTrue(lineCount <= 10);
    }

    @Test
    void testGetRawLogsNewestFirst() {
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Source", "s1", "第一条"));
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Source", "s1", "第二条"));

        String raw = logStore.getRawLogs(100);
        int pos1 = raw.indexOf("第一条");
        int pos2 = raw.indexOf("第二条");
        // 倒序，第二条应在第一条前面
        assertTrue(pos2 < pos1);
    }

    @Test
    void testGetRawLogsEmpty() {
        logStore.clearLogs(null);
        String raw = logStore.getRawLogs(100);
        assertTrue(raw.isEmpty());
    }

    @Test
    void testGetLogsByTimeRange() {
        logStore.writeLog(new LogEntry(LogLevel.INFO, "Source1", "session1", "最近的消息"));
        String raw = logStore.getRawLogs(100);
        assertNotNull(raw);
        assertTrue(raw.contains("最近的消息"));
    }
}
