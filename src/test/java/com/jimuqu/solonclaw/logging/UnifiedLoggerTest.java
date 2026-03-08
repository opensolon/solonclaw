package com.jimuqu.solonclaw.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一日志记录器测试
 */
class UnifiedLoggerTest {

    private UnifiedLogger logger;
    private LogStore logStore;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        logStore = new LogStore();
        logger = new UnifiedLogger(logStore);
    }

    @Test
    void testLog() {
        logger.log(LogLevel.INFO, "Test", "session1", "测试消息");
        String raw = logStore.getRawLogs(100);
        assertTrue(raw.contains("[INFO]"));
        assertTrue(raw.contains("测试消息"));
    }

    @Test
    void testInfo() {
        logger.info("Test", "session1", "INFO 消息");
        String raw = logStore.getRawLogs(100);
        assertTrue(raw.contains("[INFO]"));
        assertTrue(raw.contains("INFO 消息"));
    }

    @Test
    void testUserChat() {
        logger.userChat("session1", "用户输入的消息");
        String raw = logStore.getRawLogs(100);
        assertTrue(raw.contains("[USER_CHAT]"));
        assertTrue(raw.contains("用户输入的消息"));
    }

    @Test
    void testAgentThink() {
        logger.agentThink("session1", "Agent 正在思考...");
        String raw = logStore.getRawLogs(100);
        assertTrue(raw.contains("[AGENT_THINK]"));
        assertTrue(raw.contains("Agent 正在思考..."));
    }

    @Test
    void testDecision() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("action", "call_tool");
        logger.decision("session1", "决定调用 shell 工具", metadata);
        String raw = logStore.getRawLogs(100);
        assertTrue(raw.contains("[DECISION]"));
        assertTrue(raw.contains("决定调用 shell 工具"));
    }

    @Test
    void testAction() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("command", "ls -la");
        logger.action("session1", "执行命令", metadata);
        String raw = logStore.getRawLogs(100);
        assertTrue(raw.contains("[ACTION]"));
        assertTrue(raw.contains("执行命令"));
    }

    @Test
    void testReflection() {
        logger.reflection("session1", "总结经验：这次任务完成得很好");
        String raw = logStore.getRawLogs(100);
        assertTrue(raw.contains("[REFLECTION]"));
        assertTrue(raw.contains("总结经验"));
    }

    @Test
    void testError() {
        Exception exception = new Exception("测试异常");
        logger.error("Test", "session1", "发生错误", exception);
        String raw = logStore.getRawLogs(100);
        assertTrue(raw.contains("[ERROR]"));
        assertTrue(raw.contains("发生错误"));
    }

    @Test
    void testErrorStatistics() {
        Exception ex = new RuntimeException("test");
        logger.error("Test", "s1", "err1", ex);
        logger.error("Test", "s1", "err2", ex);
        assertEquals(2, logger.getTotalErrorCount());
        assertTrue(logger.getErrorTypeStats().containsKey(RuntimeException.class.getName()));
        assertTrue(logger.getErrorSourceStats().containsKey("Test"));
    }

    @Test
    void testResetErrorStats() {
        logger.error("Test", "s1", "err", new Exception("e"));
        logger.resetErrorStats();
        assertEquals(0, logger.getTotalErrorCount());
    }
}
