package com.jimuqu.solonclaw.context;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Context 测试
 *
 * @author SolonClaw
 */
class ContextTest {

    @Test
    void testBuildPrompt_AllContexts() {
        Context context = Context.builder()
            .knowledge("知识内容")
            .system("系统提示")
            .session("会话历史")
            .tools("工具列表")
            .build();

        String prompt = context.buildPrompt("用户消息");

        assertTrue(prompt.contains("系统提示"));
        assertTrue(prompt.contains("工具列表"));
        assertTrue(prompt.contains("知识内容"));
        assertTrue(prompt.contains("会话历史"));
        assertTrue(prompt.contains("用户消息"));
    }

    @Test
    void testBuildPrompt_OnlySystem() {
        Context context = Context.builder()
            .system("系统提示")
            .build();

        String prompt = context.buildPrompt("用户消息");

        assertTrue(prompt.contains("系统提示"));
        assertTrue(prompt.contains("用户消息"));
        assertFalse(prompt.contains("知识内容"));
    }

    @Test
    void testBuildPrompt_EmptyContext() {
        Context context = Context.empty();

        String prompt = context.buildPrompt("用户消息");

        assertTrue(prompt.contains("用户消息"));
        assertTrue(prompt.contains("## 当前问题"));
    }

    @Test
    void testMetadata() {
        Context context = Context.builder()
            .putMetadata("key1", "value1")
            .putMetadata("key2", 123)
            .build();

        assertEquals("value1", context.getMetadata("key1"));
        assertEquals(123, (int) context.getMetadata("key2"));
        assertNull(context.getMetadata("key3"));
    }

    @Test
    void testGetters() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("test", "value");

        Context context = new Context("knowledge", "system", "session", "tools", metadata);

        assertEquals("knowledge", context.getKnowledge());
        assertEquals("system", context.getSystem());
        assertEquals("session", context.getSession());
        assertEquals("tools", context.getTools());
        assertEquals("value", context.getMetadata("test"));
    }

    @Test
    void testBuilderPattern() {
        Context context = Context.builder()
            .knowledge("k")
            .system("s")
            .session("sess")
            .tools("t")
            .build();

        assertEquals("k", context.getKnowledge());
        assertEquals("s", context.getSystem());
        assertEquals("sess", context.getSession());
        assertEquals("t", context.getTools());
    }
}