package com.jimuqu.solonclaw.context.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextBuilderConfig 测试
 *
 * @author SolonClaw
 */
class ContextBuilderConfigTest {

    private ContextBuilderConfig config;

    @BeforeEach
    void setUp() {
        config = new ContextBuilderConfig();
    }

    @Test
    void testDefaultValues() {
        assertTrue(config.isSystemEnabled());
        assertFalse(config.isToolsEnabled()); // 默认值是 false，因为工具包含在系统上下文中
        assertTrue(config.isKnowledgeEnabled());
        assertTrue(config.isSessionEnabled());
        assertTrue(config.isIncludeToolsInSystem());
    }

    @Test
    void testSystemEnabled() {
        config.setSystemEnabled(false);
        assertFalse(config.isSystemEnabled());

        config.setSystemEnabled(true);
        assertTrue(config.isSystemEnabled());
    }

    @Test
    void testToolsEnabled() {
        config.setToolsEnabled(false);
        assertFalse(config.isToolsEnabled());

        config.setToolsEnabled(true);
        assertTrue(config.isToolsEnabled());
    }

    @Test
    void testKnowledgeEnabled() {
        config.setKnowledgeEnabled(false);
        assertFalse(config.isKnowledgeEnabled());

        config.setKnowledgeEnabled(true);
        assertTrue(config.isKnowledgeEnabled());
    }

    @Test
    void testSessionEnabled() {
        config.setSessionEnabled(false);
        assertFalse(config.isSessionEnabled());

        config.setSessionEnabled(true);
        assertTrue(config.isSessionEnabled());
    }

    @Test
    void testIncludeToolsInSystem() {
        config.setIncludeToolsInSystem(false);
        assertFalse(config.isIncludeToolsInSystem());

        config.setIncludeToolsInSystem(true);
        assertTrue(config.isIncludeToolsInSystem());
    }

    @Test
    void testMaxSearchResults() {
        config.setMaxSearchResults(10);
        assertEquals(10, config.getMaxSearchResults());
    }

    @Test
    void testMinConfidenceThreshold() {
        config.setMinConfidenceThreshold(0.8);
        assertEquals(0.8, config.getMinConfidenceThreshold());
    }

    @Test
    void testMaxHistoryMessages() {
        config.setMaxHistoryMessages(20);
        assertEquals(20, config.getMaxHistoryMessages());
    }

    @Test
    void testMaxSummaryLength() {
        config.setMaxSummaryLength(1000);
        assertEquals(1000, config.getMaxSummaryLength());
    }

    @Test
    void testToString() {
        String str = config.toString();

        assertTrue(str.contains("systemEnabled=true"));
        assertTrue(str.contains("toolsEnabled=false")); // 默认值是 false
        assertTrue(str.contains("knowledgeEnabled=true"));
        assertTrue(str.contains("sessionEnabled=true"));
    }

    @Test
    void testIncludeParameters() {
        config.setIncludeParameters(false);
        assertFalse(config.isIncludeParameters());

        config.setIncludeParameters(true);
        assertTrue(config.isIncludeParameters());
    }
}