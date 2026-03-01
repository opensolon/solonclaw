package com.jimuqu.solonclaw.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 日志级别测试
 */
class LogLevelTest {

    @Test
    void testLogLevelValues() {
        assertEquals(0, LogLevel.INFO.getPriority());
        assertEquals("INFO", LogLevel.INFO.getCode());

        assertEquals(10, LogLevel.USER_CHAT.getPriority());
        assertEquals("USER_CHAT", LogLevel.USER_CHAT.getCode());

        assertEquals(20, LogLevel.AGENT_THINK.getPriority());
        assertEquals("AGENT_THINK", LogLevel.AGENT_THINK.getCode());

        assertEquals(30, LogLevel.DECISION.getPriority());
        assertEquals("DECISION", LogLevel.DECISION.getCode());

        assertEquals(40, LogLevel.ACTION.getPriority());
        assertEquals("ACTION", LogLevel.ACTION.getCode());

        assertEquals(50, LogLevel.REFLECTION.getPriority());
        assertEquals("REFLECTION", LogLevel.REFLECTION.getCode());

        assertEquals(100, LogLevel.ERROR.getPriority());
        assertEquals("ERROR", LogLevel.ERROR.getCode());
    }

    @Test
    void testLogLevelDescription() {
        assertEquals("普通信息", LogLevel.INFO.getDescription());
        assertEquals("用户对话", LogLevel.USER_CHAT.getDescription());
        assertEquals("Agent 思考", LogLevel.AGENT_THINK.getDescription());
        assertEquals("决策", LogLevel.DECISION.getDescription());
        assertEquals("行动", LogLevel.ACTION.getDescription());
        assertEquals("反省", LogLevel.REFLECTION.getDescription());
        assertEquals("错误", LogLevel.ERROR.getDescription());
    }

    @Test
    void testLogLevelValueOf() {
        assertEquals(LogLevel.INFO, LogLevel.valueOf("INFO"));
        assertEquals(LogLevel.USER_CHAT, LogLevel.valueOf("USER_CHAT"));
        assertEquals(LogLevel.AGENT_THINK, LogLevel.valueOf("AGENT_THINK"));
        assertEquals(LogLevel.DECISION, LogLevel.valueOf("DECISION"));
        assertEquals(LogLevel.ACTION, LogLevel.valueOf("ACTION"));
        assertEquals(LogLevel.REFLECTION, LogLevel.valueOf("REFLECTION"));
        assertEquals(LogLevel.ERROR, LogLevel.valueOf("ERROR"));
    }
}