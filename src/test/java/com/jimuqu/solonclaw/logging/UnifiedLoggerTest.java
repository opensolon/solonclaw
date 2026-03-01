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

        LogQuery query = new LogQuery()
                .addSource("Test")
                .setSessionId("session1");

        var results = logStore.queryLogs(query);

        assertFalse(results.isEmpty());
        assertEquals(LogLevel.INFO, results.get(0).getLevel());
    }

    @Test
    void testLogWithMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", "value");
        metadata.put("number", 123);

        logger.log(LogLevel.INFO, "Test", "session1", "测试消息", metadata);

        LogQuery query = new LogQuery()
                .addSource("Test")
                .setSessionId("session1");

        var results = logStore.queryLogs(query);

        assertFalse(results.isEmpty());
        assertEquals("value", results.get(0).getMetadata("key"));
        assertEquals(123, results.get(0).getMetadata("number"));
    }

    @Test
    void testInfo() {
        logger.info("Test", "session1", "INFO 消息");

        LogQuery query = new LogQuery()
                .addSource("Test")
                .setSessionId("session1");

        var results = logStore.queryLogs(query);

        assertFalse(results.isEmpty());
        assertEquals(LogLevel.INFO, results.get(0).getLevel());
        assertEquals("INFO 消息", results.get(0).getMessage());
    }

    @Test
    void testUserChat() {
        logger.userChat("session1", "用户输入的消息");

        LogQuery query = new LogQuery()
                .addSource("Gateway")
                .setSessionId("session1");

        var results = logStore.queryLogs(query);

        assertFalse(results.isEmpty());
        assertEquals(LogLevel.USER_CHAT, results.get(0).getLevel());
        assertEquals("用户输入的消息", results.get(0).getMessage());
    }

    @Test
    void testAgentThink() {
        logger.agentThink("session1", "Agent 正在思考...");

        LogQuery query = new LogQuery()
                .addSource("Agent")
                .setSessionId("session1");

        var results = logStore.queryLogs(query);

        assertFalse(results.isEmpty());
        assertEquals(LogLevel.AGENT_THINK, results.get(0).getLevel());
        assertEquals("Agent 正在思考...", results.get(0).getMessage());
    }

    @Test
    void testDecision() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("action", "call_tool");
        metadata.put("tool", "shell");

        logger.decision("session1", "决定调用 shell 工具", metadata);

        LogQuery query = new LogQuery()
                .addSource("DecisionEngine")
                .setSessionId("session1");

        var results = logStore.queryLogs(query);

        assertFalse(results.isEmpty());
        assertEquals(LogLevel.DECISION, results.get(0).getLevel());
        assertEquals("call_tool", results.get(0).getMetadata("action"));
    }

    @Test
    void testAction() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("command", "ls -la");
        metadata.put("exitCode", 0);

        logger.action("session1", "执行命令", metadata);

        LogQuery query = new LogQuery()
                .addSource("Action")
                .setSessionId("session1");

        var results = logStore.queryLogs(query);

        assertFalse(results.isEmpty());
        assertEquals(LogLevel.ACTION, results.get(0).getLevel());
        assertEquals("ls -la", results.get(0).getMetadata("command"));
    }

    @Test
    void testReflection() {
        logger.reflection("session1", "总结经验：这次任务完成得很好");

        LogQuery query = new LogQuery()
                .addSource("Reflection")
                .setSessionId("session1");

        var results = logStore.queryLogs(query);

        assertFalse(results.isEmpty());
        assertEquals(LogLevel.REFLECTION, results.get(0).getLevel());
        assertEquals("总结经验：这次任务完成得很好", results.get(0).getMessage());
    }

    @Test
    void testError() {
        Exception exception = new Exception("测试异常");

        logger.error("Test", "session1", "发生错误", exception);

        LogQuery query = new LogQuery()
                .addSource("Test")
                .setSessionId("session1");

        var results = logStore.queryLogs(query);

        assertFalse(results.isEmpty());
        assertEquals(LogLevel.ERROR, results.get(0).getLevel());
        assertEquals("发生错误", results.get(0).getMessage());
        assertEquals("Exception", results.get(0).getMetadata("exception"));
    }
}