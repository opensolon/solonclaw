package com.jimuqu.solonclaw.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogEntry 单元测试
 *
 * @author SolonClaw
 */
@DisplayName("LogEntry 单元测试")
class LogEntryTest {

    @Nested
    @DisplayName("默认构造函数测试")
    class DefaultConstructorTests {

        @Test
        @DisplayName("默认构造函数应初始化基本字段")
        void defaultConstructorShouldInitializeBasicFields() {
            LogEntry entry = new LogEntry();

            assertNotNull(entry.getId(), "ID 不应为空");
            assertEquals(16, entry.getId().length(), "ID 长度应为16");
            assertNotNull(entry.getTimestamp(), "时间戳不应为空");
            assertNotNull(entry.getMetadata(), "元数据不应为空");
            assertTrue(entry.getMetadata().isEmpty(), "元数据初始应为空");
            assertEquals("DEFAULT", entry.getCategory(), "默认分类应为 DEFAULT");
        }

        @Test
        @DisplayName("多个实例的ID应不同")
        void multipleInstancesShouldHaveDifferentIds() {
            LogEntry entry1 = new LogEntry();
            LogEntry entry2 = new LogEntry();

            assertNotEquals(entry1.getId(), entry2.getId(), "不同实例应有不同的ID");
        }

        @Test
        @DisplayName("应获取主机名和IP地址")
        void shouldGetHostnameAndIpAddress() {
            assertNotNull(LogEntry.getHOSTNAME(), "主机名不应为空");
            assertNotNull(LogEntry.getIpAddress(), "IP地址不应为空");

            LogEntry entry = new LogEntry();
            assertEquals(LogEntry.getHOSTNAME(), entry.getHostname());
            assertEquals(LogEntry.getIpAddress(), entry.getIp());
        }
    }

    @Nested
    @DisplayName("参数构造函数测试")
    class ParameterizedConstructorTests {

        @Test
        @DisplayName("应能使用参数创建日志条目")
        void shouldCreateLogEntryWithParameters() {
            LogLevel level = LogLevel.ERROR;
            String source = "Agent";
            String sessionId = "session-123";
            String message = "测试错误消息";

            LogEntry entry = new LogEntry(level, source, sessionId, message);

            assertEquals(level, entry.getLevel());
            assertEquals(source, entry.getSource());
            assertEquals(sessionId, entry.getSessionId());
            assertEquals(message, entry.getMessage());
        }

        @Test
        @DisplayName("应自动分类日志来源")
        void shouldAutoCategorizeLogSource() {
            assertEquals("AGENT", new LogEntry(null, "AgentService", null, null).getCategory());
            assertEquals("AGENT", new LogEntry(null, "agent", null, null).getCategory());
            assertEquals("TOOL", new LogEntry(null, "ShellTool", null, null).getCategory());
            assertEquals("API", new LogEntry(null, "GatewayController", null, null).getCategory());
            assertEquals("MEMORY", new LogEntry(null, "MemoryService", null, null).getCategory());
            assertEquals("SCHEDULER", new LogEntry(null, "SchedulerService", null, null).getCategory());
            assertEquals("MCP", new LogEntry(null, "MCP", null, null).getCategory());
            assertEquals("MCP", new LogEntry(null, "mcp", null, null).getCategory());
            assertEquals("SKILL", new LogEntry(null, "SkillsManager", null, null).getCategory());
            assertEquals("SYSTEM", new LogEntry(null, "Other", null, null).getCategory());
            assertEquals("DEFAULT", new LogEntry(null, null, null, null).getCategory());
        }

        @Test
        @DisplayName("使用参数构造的日志条目ID应唯一")
        void parameterizedConstructorShouldCreateUniqueId() {
            LogEntry entry1 = new LogEntry(LogLevel.INFO, "Source", "session1", "msg1");
            LogEntry entry2 = new LogEntry(LogLevel.INFO, "Source", "session1", "msg1");

            assertNotEquals(entry1.getId(), entry2.getId());
        }
    }

    @Nested
    @DisplayName("Getter/Setter 测试")
    class GetterSetterTests {

        @Test
        @DisplayName("应能设置和获取所有字段")
        void shouldSetAndGetAllFields() {
            LogEntry entry = new LogEntry();

            entry.setId("test-id-123");
            entry.setLevel(LogLevel.ERROR);
            entry.setSource("TestSource");
            entry.setSessionId("session-456");
            entry.setMessage("测试消息");
            entry.setCategory("TEST");
            entry.setTraceId("trace-789");
            entry.setHostname("test-host");
            entry.setIp("192.168.1.1");
            entry.setDuration(100L);

            assertEquals("test-id-123", entry.getId());
            assertEquals(LogLevel.ERROR, entry.getLevel());
            assertEquals("TestSource", entry.getSource());
            assertEquals("session-456", entry.getSessionId());
            assertEquals("测试消息", entry.getMessage());
            assertEquals("TEST", entry.getCategory());
            assertEquals("trace-789", entry.getTraceId());
            assertEquals("test-host", entry.getHostname());
            assertEquals("192.168.1.1", entry.getIp());
            assertEquals(100L, entry.getDuration());
        }

        @Test
        @DisplayName("应能设置时间戳")
        void shouldSetTimestamp() {
            LogEntry entry = new LogEntry();
            LocalDateTime testTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0);

            entry.setTimestamp(testTime);

            assertEquals(testTime, entry.getTimestamp());
        }
    }

    @Nested
    @DisplayName("元数据操作测试")
    class MetadataOperationTests {

        @Test
        @DisplayName("应能添加元数据")
        void shouldAddMetadata() {
            LogEntry entry = new LogEntry();
            entry.addMetadata("key1", "value1");
            entry.addMetadata("key2", 123);

            assertEquals("value1", entry.getMetadata("key1"));
            assertEquals(123, entry.getMetadata("key2"));
        }

        @Test
        @DisplayName("应能覆盖已有的元数据")
        void shouldOverwriteExistingMetadata() {
            LogEntry entry = new LogEntry();
            entry.addMetadata("key", "value1");
            entry.addMetadata("key", "value2");

            assertEquals("value2", entry.getMetadata("key"));
        }

        @Test
        @DisplayName("获取不存在的元数据应返回null")
        void gettingNonExistentMetadataShouldReturnNull() {
            LogEntry entry = new LogEntry();

            assertNull(entry.getMetadata("nonexistent"));
        }

        @Test
        @DisplayName("应能添加各种类型的元数据")
        void shouldAddVariousMetadataTypes() {
            LogEntry entry = new LogEntry();
            entry.addMetadata("string", "value");
            entry.addMetadata("integer", 123);
            entry.addMetadata("double", 45.67);
            entry.addMetadata("boolean", true);
            entry.addMetadata("null", null);

            assertEquals("value", entry.getMetadata("string"));
            assertEquals(123, entry.getMetadata("integer"));
            assertEquals(45.67, entry.getMetadata("double"));
            assertEquals(true, entry.getMetadata("boolean"));
            assertNull(entry.getMetadata("null"));
        }

        @Test
        @DisplayName("应能设置完整的元数据Map")
        void shouldSetEntireMetadataMap() {
            LogEntry entry = new LogEntry();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("key1", "value1");
            metadata.put("key2", "value2");

            entry.setMetadata(metadata);

            assertEquals(2, entry.getMetadata().size());
            assertEquals("value1", entry.getMetadata("key1"));
            assertEquals("value2", entry.getMetadata("key2"));
        }

        @Test
        @DisplayName("应能清空元数据")
        void shouldClearMetadata() {
            LogEntry entry = new LogEntry();
            entry.addMetadata("key", "value");
            entry.setMetadata(new HashMap<>());

            assertTrue(entry.getMetadata().isEmpty());
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("应能处理空字符串")
        void shouldHandleEmptyStrings() {
            LogEntry entry = new LogEntry();
            entry.setId("");
            entry.setSource("");
            entry.setSessionId("");
            entry.setMessage("");
            entry.setCategory("");
            entry.setTraceId("");
            entry.setHostname("");
            entry.setIp("");

            assertEquals("", entry.getId());
            assertEquals("", entry.getSource());
            assertEquals("", entry.getSessionId());
            assertEquals("", entry.getMessage());
            assertEquals("", entry.getCategory());
            assertEquals("", entry.getTraceId());
            assertEquals("", entry.getHostname());
            assertEquals("", entry.getIp());
        }

        @Test
        @DisplayName("应能处理null值")
        void shouldHandleNullValues() {
            LogEntry entry = new LogEntry();
            assertDoesNotThrow(() -> {
                entry.setId(null);
                entry.setLevel(null);
                entry.setSource(null);
                entry.setSessionId(null);
                entry.setMessage(null);
                entry.setCategory(null);
                entry.setTraceId(null);
                entry.setHostname(null);
                entry.setIp(null);
                entry.setMetadata(null);
            });
        }

        @Test
        @DisplayName("应能设置负的持续时间")
        void shouldAllowNegativeDuration() {
            LogEntry entry = new LogEntry();
            entry.setDuration(-100L);

            assertEquals(-100L, entry.getDuration());
        }

        @Test
        @DisplayName("应能设置零的持续时间")
        void shouldAllowZeroDuration() {
            LogEntry entry = new LogEntry();
            entry.setDuration(0L);

            assertEquals(0L, entry.getDuration());
        }

        @Test
        @DisplayName("向null元数据添加不应抛出异常")
        void addingToNullMetadataShouldNotThrow() {
            LogEntry entry = new LogEntry();
            entry.setMetadata(null);

            assertDoesNotThrow(() -> entry.addMetadata("key", "value"));
            assertNotNull(entry.getMetadata());
            assertEquals("value", entry.getMetadata("key"));
        }
    }

    @Nested
    @DisplayName("完整日志条目场景测试")
    class CompleteLogEntryScenarioTests {

        @Test
        @DisplayName("应能构建完整的日志条目")
        void shouldBuildCompleteLogEntry() {
            LogEntry entry = new LogEntry(LogLevel.ERROR, "AgentService", "session-123", "处理失败");

            entry.setTraceId("trace-456");
            entry.setDuration(1500L);
            entry.addMetadata("errorCode", 500);
            entry.addMetadata("errorMessage", "内部错误");

            assertEquals(LogLevel.ERROR, entry.getLevel());
            assertEquals("AGENT", entry.getCategory());
            assertEquals("session-123", entry.getSessionId());
            assertEquals("处理失败", entry.getMessage());
            assertEquals("trace-456", entry.getTraceId());
            assertEquals(1500L, entry.getDuration());
            assertEquals(500, entry.getMetadata("errorCode"));
            assertEquals("内部错误", entry.getMetadata("errorMessage"));
        }

        @Test
        @DisplayName("应能克隆日志条目属性")
        void shouldBeAbleToCloneLogEntryProperties() {
            LogEntry entry1 = new LogEntry(LogLevel.INFO, "Source", "session1", "message");
            entry1.addMetadata("key", "value");

            LogEntry entry2 = new LogEntry();
            entry2.setId(entry1.getId());
            entry2.setLevel(entry1.getLevel());
            entry2.setSource(entry1.getSource());
            entry2.setSessionId(entry1.getSessionId());
            entry2.setMessage(entry1.getMessage());
            entry2.setCategory(entry1.getCategory());
            entry2.setMetadata(new HashMap<>(entry1.getMetadata()));

            assertEquals(entry1.getId(), entry2.getId());
            assertEquals(entry1.getLevel(), entry2.getLevel());
            assertEquals(entry1.getSource(), entry2.getSource());
            assertEquals(entry1.getSessionId(), entry2.getSessionId());
            assertEquals(entry1.getMessage(), entry2.getMessage());
            assertEquals(entry1.getCategory(), entry2.getCategory());
            assertEquals(entry1.getMetadata().get("key"), entry2.getMetadata().get("key"));
        }
    }
}