package com.jimuqu.solonclaw.monitor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PerformanceStats 单元测试
 *
 * @author SolonClaw
 */
@DisplayName("PerformanceStats 单元测试")
class PerformanceStatsTest {

    @Nested
    @DisplayName("默认值测试")
    class DefaultValueTests {

        @Test
        @DisplayName("新实例应初始化统计数据")
        void newInstanceOfShouldInitializeStats() {
            PerformanceStats stats = new PerformanceStats();

            assertEquals(0, stats.getTotalRequests());
            assertEquals(0, stats.getSuccessRequests());
            assertEquals(0, stats.getFailedRequests());
            assertEquals(0, stats.getAverageResponseTime());
            assertEquals(0.0, stats.getSuccessRate(), 0.001);
            assertEquals(0, stats.getTotalConversations());
            assertEquals(0, stats.getActiveConversations());
            assertNull(stats.getToolCallCounts());
            assertNull(stats.getToolCallErrors());
        }
    }

    @Nested
    @DisplayName("Getter/Setter 测试")
    class GetterSetterTests {

        @Test
        @DisplayName("应能设置和获取总请求数")
        void shouldSetAndGetTotalRequests() {
            PerformanceStats stats = new PerformanceStats();
            stats.setTotalRequests(1000);

            assertEquals(1000, stats.getTotalRequests());
        }

        @Test
        @DisplayName("应能设置和获取成功请求数")
        void shouldSetAndGetSuccessRequests() {
            PerformanceStats stats = new PerformanceStats();
            stats.setSuccessRequests(950);

            assertEquals(950, stats.getSuccessRequests());
        }

        @Test
        @DisplayName("应能设置和获取失败请求数")
        void shouldSetAndGetFailedRequests() {
            PerformanceStats stats = new PerformanceStats();
            stats.setFailedRequests(50);

            assertEquals(50, stats.getFailedRequests());
        }

        @Test
        @DisplayName("应能设置和获取平均响应时间")
        void shouldSetAndGetAverageResponseTime() {
            PerformanceStats stats = new PerformanceStats();
            stats.setAverageResponseTime(150);

            assertEquals(150, stats.getAverageResponseTime());
        }

        @Test
        @DisplayName("应能设置和获取成功率")
        void shouldSetAndGetSuccessRate() {
            PerformanceStats stats = new PerformanceStats();
            stats.setSuccessRate(95.5);

            assertEquals(95.5, stats.getSuccessRate(), 0.001);
        }

        @Test
        @DisplayName("应能设置和获取总对话数")
        void shouldSetAndGetTotalConversations() {
            PerformanceStats stats = new PerformanceStats();
            stats.setTotalConversations(200);

            assertEquals(200, stats.getTotalConversations());
        }

        @Test
        @DisplayName("应能设置和获取活跃对话数")
        void shouldSetAndGetActiveConversations() {
            PerformanceStats stats = new PerformanceStats();
            stats.setActiveConversations(15);

            assertEquals(15, stats.getActiveConversations());
        }

        @Test
        @DisplayName("应能设置和获取工具调用统计")
        void shouldSetAndGetToolCallCounts() {
            PerformanceStats stats = new PerformanceStats();
            Map<String, Long> toolCounts = Map.of(
                    "ShellTool", 50L,
                    "ReadTool", 30L
            );

            stats.setToolCallCounts(toolCounts);

            assertNotNull(stats.getToolCallCounts());
            assertEquals(50L, stats.getToolCallCounts().get("ShellTool"));
            assertEquals(30L, stats.getToolCallCounts().get("ReadTool"));
        }

        @Test
        @DisplayName("应能设置和获取工具错误统计")
        void shouldSetAndGetToolCallErrors() {
            PerformanceStats stats = new PerformanceStats();
            Map<String, Long> toolErrors = Map.of(
                    "ShellTool", 5L,
                    "ReadTool", 2L
            );

            stats.setToolCallErrors(toolErrors);

            assertNotNull(stats.getToolCallErrors());
            assertEquals(5L, stats.getToolCallErrors().get("ShellTool"));
            assertEquals(2L, stats.getToolCallErrors().get("ReadTool"));
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("应能设置零值")
        void shouldAllowZeroValues() {
            PerformanceStats stats = new PerformanceStats();
            stats.setTotalRequests(0);
            stats.setSuccessRequests(0);
            stats.setFailedRequests(0);
            stats.setAverageResponseTime(0);
            stats.setSuccessRate(0.0);
            stats.setTotalConversations(0);
            stats.setActiveConversations(0);

            assertEquals(0, stats.getTotalRequests());
            assertEquals(0, stats.getSuccessRequests());
            assertEquals(0, stats.getFailedRequests());
            assertEquals(0, stats.getAverageResponseTime());
            assertEquals(0.0, stats.getSuccessRate(), 0.001);
            assertEquals(0, stats.getTotalConversations());
            assertEquals(0, stats.getActiveConversations());
        }

        @Test
        @DisplayName("应能设置负数")
        void shouldAllowNegativeValues() {
            PerformanceStats stats = new PerformanceStats();
            stats.setTotalRequests(-1);
            stats.setSuccessRequests(-1);
            stats.setFailedRequests(-1);
            stats.setAverageResponseTime(-1);
            stats.setTotalConversations(-1);
            stats.setActiveConversations(-1);

            assertEquals(-1, stats.getTotalRequests());
            assertEquals(-1, stats.getSuccessRequests());
            assertEquals(-1, stats.getFailedRequests());
            assertEquals(-1, stats.getAverageResponseTime());
            assertEquals(-1, stats.getTotalConversations());
            assertEquals(-1, stats.getActiveConversations());
        }

        @Test
        @DisplayName("应能设置大数值")
        void shouldAllowLargeValues() {
            PerformanceStats stats = new PerformanceStats();
            stats.setTotalRequests(Long.MAX_VALUE);
            stats.setSuccessRequests(Long.MAX_VALUE);
            stats.setFailedRequests(Long.MAX_VALUE);
            stats.setAverageResponseTime(Long.MAX_VALUE);
            stats.setSuccessRate(100.0);
            stats.setTotalConversations(Long.MAX_VALUE);
            stats.setActiveConversations(Long.MAX_VALUE);

            assertEquals(Long.MAX_VALUE, stats.getTotalRequests());
            assertEquals(Long.MAX_VALUE, stats.getSuccessRequests());
            assertEquals(Long.MAX_VALUE, stats.getFailedRequests());
            assertEquals(Long.MAX_VALUE, stats.getAverageResponseTime());
            assertEquals(100.0, stats.getSuccessRate(), 0.001);
            assertEquals(Long.MAX_VALUE, stats.getTotalConversations());
            assertEquals(Long.MAX_VALUE, stats.getActiveConversations());
        }

        @Test
        @DisplayName("应能设置空Map")
        void shouldAllowEmptyMaps() {
            PerformanceStats stats = new PerformanceStats();
            Map<String, Long> emptyMap = Map.of();

            assertDoesNotThrow(() -> stats.setToolCallCounts(emptyMap));
            assertDoesNotThrow(() -> stats.setToolCallErrors(emptyMap));

            assertNotNull(stats.getToolCallCounts());
            assertNotNull(stats.getToolCallErrors());
        }
    }

    @Nested
    @DisplayName("成功率边界测试")
    class SuccessRateEdgeTests {

        @Test
        @DisplayName("应能设置成功率为0")
        void shouldAllowSuccessRateZero() {
            PerformanceStats stats = new PerformanceStats();
            stats.setSuccessRate(0.0);

            assertEquals(0.0, stats.getSuccessRate(), 0.001);
        }

        @Test
        @DisplayName("应能设置成功率为100")
        void shouldAllowSuccessRateHundred() {
            PerformanceStats stats = new PerformanceStats();
            stats.setSuccessRate(100.0);

            assertEquals(100.0, stats.getSuccessRate(), 0.001);
        }

        @Test
        @DisplayName("应能设置成功率为负数")
        void shouldAllowNegativeSuccessRate() {
            PerformanceStats stats = new PerformanceStats();
            stats.setSuccessRate(-10.0);

            assertEquals(-10.0, stats.getSuccessRate(), 0.001);
        }

        @Test
        @DisplayName("应能设置成功率为超过100的值")
        void shouldAllowSuccessRateOverHundred() {
            PerformanceStats stats = new PerformanceStats();
            stats.setSuccessRate(150.0);

            assertEquals(150.0, stats.getSuccessRate(), 0.001);
        }
    }

    @Nested
    @DisplayName("完整统计场景测试")
    class CompleteStatsScenarioTests {

        @Test
        @DisplayName("应能构建完整的性能统计")
        void shouldBuildCompletePerformanceStats() {
            PerformanceStats stats = new PerformanceStats();
            stats.setTotalRequests(1000L);
            stats.setSuccessRequests(950L);
            stats.setFailedRequests(50L);
            stats.setAverageResponseTime(150L);
            stats.setSuccessRate(95.0);
            stats.setTotalConversations(200L);
            stats.setActiveConversations(15L);
            stats.setToolCallCounts(Map.of(
                    "ShellTool", 100L,
                    "ReadTool", 80L
            ));
            stats.setToolCallErrors(Map.of(
                    "ShellTool", 10L,
                    "ReadTool", 5L
            ));

            assertEquals(1000L, stats.getTotalRequests());
            assertEquals(950L, stats.getSuccessRequests());
            assertEquals(50L, stats.getFailedRequests());
            assertEquals(150L, stats.getAverageResponseTime());
            assertEquals(95.0, stats.getSuccessRate(), 0.001);
            assertEquals(200L, stats.getTotalConversations());
            assertEquals(15L, stats.getActiveConversations());
            assertEquals(180L, calculateToolCallTotal(stats));
            assertEquals(15L, calculateToolErrorTotal(stats));
        }

        @Test
        @DisplayName("应能更新现有的统计信息")
        void shouldUpdateExistingStatistics() {
            PerformanceStats stats = new PerformanceStats();
            stats.setTotalRequests(100L);
            stats.setSuccessRequests(90L);

            // 更新统计
            stats.setTotalRequests(200L);
            stats.setSuccessRequests(190L);
            stats.setFailedRequests(10L);

            assertEquals(200L, stats.getTotalRequests());
            assertEquals(190L, stats.getSuccessRequests());
            assertEquals(10L, stats.getFailedRequests());
        }

        private long calculateToolCallTotal(PerformanceStats stats) {
            if (stats.getToolCallCounts() == null) return 0;
            return stats.getToolCallCounts().values().stream()
                    .mapToLong(Long::longValue)
                    .sum();
        }

        private long calculateToolErrorTotal(PerformanceStats stats) {
            if (stats.getToolCallErrors() == null) return 0;
            return stats.getToolCallErrors().values().stream()
                    .mapToLong(Long::longValue)
                    .sum();
        }
    }
}
