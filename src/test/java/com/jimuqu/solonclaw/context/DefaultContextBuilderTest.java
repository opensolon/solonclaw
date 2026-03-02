package com.jimuqu.solonclaw.context;

import com.jimuqu.solonclaw.context.config.ContextBuilderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultContextBuilder 测试
 *
 * @author SolonClaw
 */
class DefaultContextBuilderTest {

    private DefaultContextBuilder builder;
    private ContextBuilderConfig config;

    @BeforeEach
    void setUp() {
        builder = new DefaultContextBuilder();
        config = new ContextBuilderConfig();
        builder.setConfig(config);

        // 禁用所有组件，避免空指针异常
        config.setSystemEnabled(false);
        config.setToolsEnabled(false);
        config.setKnowledgeEnabled(false);
        config.setSessionEnabled(false);
    }

    @Test
    void testBuild_WithAllContextsDisabled() {
        Context context = builder.build("session-1", "hello world");

        assertNotNull(context);
        // 所有组件都被禁用，应该返回空上下文
        assertTrue(context.getSystem().isEmpty());
        assertTrue(context.getKnowledge().isEmpty());
        assertTrue(context.getSession().isEmpty());
        assertTrue(context.getTools().isEmpty());
        assertEquals("session-1", context.getMetadata("sessionId"));
    }

    @Test
    void testBuild_SystemDisabled() {
        config.setSystemEnabled(false);

        Context context = builder.build("session-1", "hello world");

        assertNotNull(context);
        assertTrue(context.getSystem().isEmpty());
    }

    @Test
    void testBuild_KnowledgeDisabled() {
        config.setKnowledgeEnabled(false);

        Context context = builder.build("session-1", "hello world");

        assertNotNull(context);
        assertTrue(context.getKnowledge().isEmpty());
    }

    @Test
    void testBuild_SessionDisabled() {
        config.setSessionEnabled(false);

        Context context = builder.build("session-1", "hello world");

        assertNotNull(context);
        assertTrue(context.getSession().isEmpty());
    }

    @Test
    void testBuild_ToolsDisabled() {
        config.setToolsEnabled(false);

        Context context = builder.build("session-1", "hello world");

        assertNotNull(context);
        assertTrue(context.getTools().isEmpty());
    }

    @Test
    void testBuild_WithOptions() {
        Map<String, Object> options = Map.of("maxHistoryMessages", 20);

        Context context = builder.build("session-1", "hello world", options);

        assertNotNull(context);
        assertEquals("session-1", context.getMetadata("sessionId"));
    }

    @Test
    void testBuildPrompt() {
        Context context = builder.build("session-1", "hello world");

        String prompt = context.buildPrompt("hello world");

        assertNotNull(prompt);
        assertTrue(prompt.contains("hello world"));
    }

    @Test
    void testGetConfig() {
        ContextBuilderConfig returnedConfig = builder.getConfig();

        assertSame(config, returnedConfig);
    }

    @Test
    void testSetConfig() {
        ContextBuilderConfig newConfig = new ContextBuilderConfig();
        newConfig.setSystemEnabled(false);

        builder.setConfig(newConfig);

        assertSame(newConfig, builder.getConfig());
    }

    @Test
    void testIncludeToolsInSystem() {
        config.setIncludeToolsInSystem(true);
        config.setToolsEnabled(false); // 工具包含在系统上下文中，所以工具上下文禁用

        assertTrue(config.isIncludeToolsInSystem());
        assertFalse(config.isToolsEnabled());
    }
}