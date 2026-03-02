package com.jimuqu.solonclaw.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 监控服务测试
 */
class MonitorTest {

    private PerformanceMonitor performanceMonitor;
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService();
        performanceMonitor = new PerformanceMonitor();
    }

    @Test
    void testRecordRequest() {
        performanceMonitor.recordRequest(100, true);
        performanceMonitor.recordRequest(200, true);
        performanceMonitor.recordRequest(500, false);

        PerformanceStats stats = performanceMonitor.getStats();

        assertEquals(3, stats.getTotalRequests());
        assertEquals(2, stats.getSuccessRequests());
        assertEquals(1, stats.getFailedRequests());
        assertEquals(266, stats.getAverageResponseTime()); // (100+200+500)/3 = 266
    }

    @Test
    void testRecordConversation() {
        performanceMonitor.recordConversationStart("session-1");
        performanceMonitor.recordConversationStart("session-2");
        performanceMonitor.recordConversationEnd("session-1");

        PerformanceStats stats = performanceMonitor.getStats();

        assertEquals(2, stats.getTotalConversations());
        assertEquals(1, stats.getActiveConversations());
    }

    @Test
    void testRecordToolCall() {
        performanceMonitor.recordToolCall("shell", true);
        performanceMonitor.recordToolCall("shell", true);
        performanceMonitor.recordToolCall("shell", false);

        PerformanceStats stats = performanceMonitor.getStats();

        assertEquals(3L, stats.getToolCallCounts().get("shell"));
        assertEquals(1L, stats.getToolCallErrors().get("shell"));
    }

    @Test
    void testAlertService() {
        alertService.info("测试信息", "这是一条测试信息");
        alertService.warning("测试警告", "这是一条测试警告");
        alertService.critical("测试严重", "这是一条严重告警");

        var history = alertService.getAlertHistory();
        assertEquals(3, history.size());

        var counts = alertService.getAlertCounts();
        assertEquals(1L, counts.get(AlertLevel.INFO));
        assertEquals(1L, counts.get(AlertLevel.WARNING));
        assertEquals(1L, counts.get(AlertLevel.CRITICAL));
    }

    @Test
    void testAlertCooldown() throws InterruptedException {
        alertService.sendAlert(AlertLevel.WARNING, "测试", "消息1");
        alertService.sendAlert(AlertLevel.WARNING, "测试", "消息2"); // 应该被冷却
        Thread.sleep(100); // 等待一下
        var history = alertService.getAlertHistory();

        // 由于冷却时间，只有第一条告警被记录
        assertEquals(1, history.size());
    }

    @Test
    void testResetStats() {
        performanceMonitor.recordRequest(100, true);
        performanceMonitor.recordConversationStart("session-1");

        performanceMonitor.reset();

        PerformanceStats stats = performanceMonitor.getStats();
        assertEquals(0, stats.getTotalRequests());
        assertEquals(0, stats.getTotalConversations());
    }

    @Test
    void testClearAlertHistory() {
        alertService.info("测试", "消息");
        assertFalse(alertService.getAlertHistory().isEmpty());

        alertService.clearHistory();
        assertTrue(alertService.getAlertHistory().isEmpty());
    }

    @Test
    void testAlertLevel() {
        assertEquals("信息", AlertLevel.INFO.getDescription());
        assertEquals("警告", AlertLevel.WARNING.getDescription());
        assertEquals("严重", AlertLevel.CRITICAL.getDescription());
        assertEquals("紧急", AlertLevel.EMERGENCY.getDescription());
    }
}