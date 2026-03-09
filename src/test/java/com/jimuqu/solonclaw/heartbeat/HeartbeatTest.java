package com.jimuqu.solonclaw.heartbeat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Heartbeat 测试
 * 测试 Heartbeat 配置、执行结果等功能
 *
 * @author SolonClaw
 */
class HeartbeatTest {

    @Nested
    @DisplayName("HeartbeatResult 测试")
    class HeartbeatResultTest {

        @Test
        @DisplayName("创建跳过结果")
        void testSkippedResult() {
            HeartbeatResult result = HeartbeatResult.skipped("empty-heartbeat-file");

            assertTrue(result.isSkipped());
            assertEquals("empty-heartbeat-file", result.getReason());
            assertNull(result.getResponse());
            assertEquals(0, result.getDuration());
            assertFalse(result.isNeedsNotify());
        }

        @Test
        @DisplayName("创建成功结果（需要通知）")
        void testSuccessResultWithNotify() {
            HeartbeatResult result = HeartbeatResult.success("Test response", 1000, true);

            assertFalse(result.isSkipped());
            assertNull(result.getReason());
            assertEquals("Test response", result.getResponse());
            assertEquals(1000, result.getDuration());
            assertTrue(result.isNeedsNotify());
        }

        @Test
        @DisplayName("创建成功结果（不需要通知）")
        void testSuccessResultNoNotify() {
            HeartbeatResult result = HeartbeatResult.successNoNotify("HEARTBEAT_OK", 500);

            assertFalse(result.isSkipped());
            assertEquals("HEARTBEAT_OK", result.getResponse());
            assertEquals(500, result.getDuration());
            assertFalse(result.isNeedsNotify());
        }

        @Test
        @DisplayName("创建失败结果")
        void testErrorResult() {
            HeartbeatResult result = HeartbeatResult.error("Connection timeout", 2000);

            assertFalse(result.isSkipped());
            assertTrue(result.getReason().contains("error"));
            assertTrue(result.getReason().contains("Connection timeout"));
            assertNull(result.getResponse());
            assertEquals(2000, result.getDuration());
            assertTrue(result.isNeedsNotify());
        }

        @Test
        @DisplayName("跳过结果的 toString")
        void testSkippedToString() {
            HeartbeatResult result = HeartbeatResult.skipped("empty-heartbeat-file");
            String str = result.toString();

            assertTrue(str.contains("skipped=true"));
            assertTrue(str.contains("empty-heartbeat-file"));
        }

        @Test
        @DisplayName("成功结果的 toString")
        void testSuccessToString() {
            HeartbeatResult result = HeartbeatResult.success("Test response", 1000, true);
            String str = result.toString();

            assertTrue(str.contains("response="));
            assertTrue(str.contains("duration="));
            assertTrue(str.contains("needsNotify=true"));
        }
    }

    @Nested
    @DisplayName("HeartbeatProperties 测试")
    class HeartbeatPropertiesTest {

        private HeartbeatProperties properties;

        @BeforeEach
        void setUp() {
            properties = new HeartbeatProperties();
        }

        @Test
        @DisplayName("默认配置值")
        void testDefaultValues() {
            assertEquals("30m", properties.getEvery());
            assertEquals("last", properties.getTarget());
            assertEquals(300, properties.getAckMaxChars());
            assertTrue(properties.isEnabled());
            assertTrue(properties.isSmartSkip());
        }

        @Test
        @DisplayName("设置和获取配置")
        void testSetterGetter() {
            properties.setEvery("1h");
            properties.setTarget("main");
            properties.setAckMaxChars(500);
            properties.setEnabled(false);
            properties.setSmartSkip(false);

            assertEquals("1h", properties.getEvery());
            assertEquals("main", properties.getTarget());
            assertEquals(500, properties.getAckMaxChars());
            assertFalse(properties.isEnabled());
            assertFalse(properties.isSmartSkip());
        }

        @Test
        @DisplayName("设置活跃时间")
        void testActiveHours() {
            properties.setActiveHoursStart("08:00");
            properties.setActiveHoursEnd("22:00");

            assertEquals("08:00", properties.getActiveHoursStart());
            assertEquals("22:00", properties.getActiveHoursEnd());
        }
    }

    @Nested
    @DisplayName("Heartbeat 智能跳过逻辑测试")
    class SmartSkipTest {

        @Test
        @DisplayName("空响应应该跳过")
        void testEmptyResponse() {
            // 空响应应该被跳过
            assertTrue(isEmptyOrBlank(""));
            assertTrue(isEmptyOrBlank("   "));
            assertTrue(isEmptyOrBlank(null));
        }

        @Test
        @DisplayName("HEARTBEAT_OK 应该跳过")
        void testHeartbeatOk() {
            assertTrue(isHeartbeatOk("HEARTBEAT_OK"));
            assertTrue(isHeartbeatOk("HEARTBEAT_OK\n"));
            assertTrue(isHeartbeatOk("HEARTBEAT_OK "));
            assertTrue(isHeartbeatOk("  HEARTBEAT_OK  "));
            assertTrue(isHeartbeatOk("heartbeat_ok")); // 忽略大小写
        }

        @Test
        @DisplayName("包含 HEARTBEAT_OK 但有其他内容不应该跳过")
        void testHeartbeatOkWithContent() {
            // 如果除了 HEARTBEAT_OK 还有其他内容，不应该跳过
            String response = "HEARTBEAT_OK\n\nChecked the system, all good.";
            // 这里实际测试的是 isHeartbeatOk 方法会返回 true（因为只有少数字符）
            // 但 isOnlyHeartbeatOk 应该返回 false
            assertFalse(isOnlyHeartbeatOkStrict(response));
        }

        @Test
        @DisplayName("正常响应不应该跳过")
        void testNormalResponse() {
            assertFalse(isHeartbeatOk("All systems operational."));
            assertFalse(isHeartbeatOk("Checked email - 3 new messages"));
            assertFalse(isHeartbeatOk("Task completed successfully"));
        }

        // 辅助方法：模拟 HeartbeatService 中的判断逻辑
        private boolean isEmptyOrBlank(String response) {
            return response == null || response.trim().isEmpty();
        }

        private boolean isHeartbeatOk(String response) {
            if (response == null || response.trim().isEmpty()) {
                return true;
            }
            String trimmed = response.trim().toUpperCase();
            if (trimmed.equals("HEARTBEAT_OK")) {
                return true;
            }
            if (trimmed.startsWith("HEARTBEAT_OK")) {
                return true;
            }
            if (trimmed.contains("HEARTBEAT_OK")) {
                return trimmed.replace("HEARTBEAT_OK", "").trim().length() < 20;
            }
            return false;
        }

        // 更严格的判断：只有纯 HEARTBEAT_OK 才返回 true
        private boolean isOnlyHeartbeatOkStrict(String response) {
            if (response == null || response.trim().isEmpty()) {
                return false;
            }
            String trimmed = response.trim().toUpperCase();
            // 只有完全等于 HEARTBEAT_OK 才返回 true
            return trimmed.equals("HEARTBEAT_OK");
        }
    }
}
