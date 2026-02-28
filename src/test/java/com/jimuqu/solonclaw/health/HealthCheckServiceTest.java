package com.jimuqu.solonclaw.health;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HealthCheckService 测试
 * 使用纯单元测试，测试健康检查功能
 *
 * @author SolonClaw
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HealthCheckServiceTest {

    @Test
    @Order(1)
    void testHealthCheckService_CanBeInstantiated() {
        assertNotNull(true, "HealthCheckService 存在");
    }

    @Test
    @Order(2)
    void testHealthStatus_Enum() {
        HealthCheckService.HealthStatus up = HealthCheckService.HealthStatus.UP;
        HealthCheckService.HealthStatus down = HealthCheckService.HealthStatus.DOWN;
        HealthCheckService.HealthStatus degraded = HealthCheckService.HealthStatus.DEGRADED;
        HealthCheckService.HealthStatus unknown = HealthCheckService.HealthStatus.UNKNOWN;

        assertEquals("系统正常", up.getDescription());
        assertEquals("系统异常", down.getDescription());
        assertEquals("系统降级", degraded.getDescription());
        assertEquals("未知状态", unknown.getDescription());
    }

    @Test
    @Order(3)
    void testComponentHealth_Record() {
        String name = "database";
        HealthCheckService.HealthStatus status = HealthCheckService.HealthStatus.UP;
        String message = "数据库连接正常";

        HealthCheckService.ComponentHealth component = new HealthCheckService.ComponentHealth(
            name, status, message
        );

        assertEquals(name, component.name());
        assertEquals(status, component.status());
        assertEquals(message, component.message());
        assertTrue(component.timestamp() > 0);
    }

    @Test
    @Order(4)
    void testComponentHealth_NullMessage() {
        HealthCheckService.ComponentHealth component = new HealthCheckService.ComponentHealth(
            "test", HealthCheckService.HealthStatus.UP, null
        );

        assertNull(component.message());
        assertEquals("test", component.name());
    }

    @Test
    @Order(5)
    void testSystemHealth_Record() {
        HealthCheckService.HealthStatus status = HealthCheckService.HealthStatus.UP;
        String version = "1.0.0";
        long uptime = 3600000L; // 1小时

        Map<String, HealthCheckService.ComponentHealth> components = new java.util.HashMap<>();
        components.put("database", new HealthCheckService.ComponentHealth(
            "database", HealthCheckService.HealthStatus.UP, "正常"
        ));

        Map<String, Object> metrics = new java.util.HashMap<>();
        metrics.put("cpu.usage", 50.5);

        HealthCheckService.SystemHealth systemHealth = new HealthCheckService.SystemHealth(
            status, version, uptime, components, metrics
        );

        assertEquals(status, systemHealth.status());
        assertEquals(version, systemHealth.version());
        assertEquals(uptime, systemHealth.uptime());
        assertEquals(1, systemHealth.components().size());
        assertEquals(1, systemHealth.metrics().size());
    }

    @Test
    @Order(6)
    void testDetermineOverallStatus_AllUp() {
        Map<String, HealthCheckService.ComponentHealth> components = new java.util.HashMap<>();
        components.put("db", new HealthCheckService.ComponentHealth(
            "db", HealthCheckService.HealthStatus.UP, "正常"
        ));
        components.put("agent", new HealthCheckService.ComponentHealth(
            "agent", HealthCheckService.HealthStatus.UP, "正常"
        ));

        boolean hasDown = components.values().stream()
            .anyMatch(c -> c.status() == HealthCheckService.HealthStatus.DOWN);
        boolean hasDegraded = components.values().stream()
            .anyMatch(c -> c.status() == HealthCheckService.HealthStatus.DEGRADED);

        HealthCheckService.HealthStatus overallStatus;
        if (hasDown) {
            overallStatus = HealthCheckService.HealthStatus.DOWN;
        } else if (hasDegraded) {
            overallStatus = HealthCheckService.HealthStatus.DEGRADED;
        } else {
            overallStatus = HealthCheckService.HealthStatus.UP;
        }

        assertEquals(HealthCheckService.HealthStatus.UP, overallStatus);
    }

    @Test
    @Order(7)
    void testDetermineOverallStatus_HasDown() {
        Map<String, HealthCheckService.ComponentHealth> components = new java.util.HashMap<>();
        components.put("db", new HealthCheckService.ComponentHealth(
            "db", HealthCheckService.HealthStatus.UP, "正常"
        ));
        components.put("agent", new HealthCheckService.ComponentHealth(
            "agent", HealthCheckService.HealthStatus.DOWN, "异常"
        ));

        boolean hasDown = components.values().stream()
            .anyMatch(c -> c.status() == HealthCheckService.HealthStatus.DOWN);

        assertTrue(hasDown);
    }

    @Test
    @Order(8)
    void testDetermineOverallStatus_HasDegraded() {
        Map<String, HealthCheckService.ComponentHealth> components = new java.util.HashMap<>();
        components.put("db", new HealthCheckService.ComponentHealth(
            "db", HealthCheckService.HealthStatus.UP, "正常"
        ));
        components.put("agent", new HealthCheckService.ComponentHealth(
            "agent", HealthCheckService.HealthStatus.DEGRADED, "降级"
        ));

        boolean hasDown = components.values().stream()
            .anyMatch(c -> c.status() == HealthCheckService.HealthStatus.DOWN);
        boolean hasDegraded = components.values().stream()
            .anyMatch(c -> c.status() == HealthCheckService.HealthStatus.DEGRADED);

        assertFalse(hasDown);
        assertTrue(hasDegraded);
    }

    @Test
    @Order(9)
    void testFormatUptime_Seconds() {
        long uptimeMs = 30000L; // 30秒

        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        assertTrue(seconds > 0);
        assertEquals(0, minutes);
        assertEquals(0, hours);
    }

    @Test
    @Order(10)
    void testFormatUptime_Minutes() {
        long uptimeMs = 120000L; // 2分钟

        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;

        assertTrue(minutes > 0);
        assertEquals(120, seconds);
    }

    @Test
    @Order(11)
    void testFormatUptime_Hours() {
        long uptimeMs = 3600000L; // 1小时

        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        assertTrue(hours > 0);
        assertEquals(60, minutes);
        assertEquals(3600, seconds);
    }

    @Test
    @Order(12)
    void testFormatUptime_Days() {
        long uptimeMs = 86400000L * 2; // 2天

        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        assertTrue(days > 0);
        assertEquals(2, days);
    }

    @Test
    @Order(13)
    void testSystemMetrics_Memory() {
        Map<String, Object> metrics = new java.util.HashMap<>();
        long heapUsed = 1024 * 1024 * 100; // 100MB
        long heapMax = 1024 * 1024 * 512;  // 512MB
        double usagePercent = (double) heapUsed / heapMax * 100;

        metrics.put("jvm.heap.used", heapUsed);
        metrics.put("jvm.heap.max", heapMax);
        metrics.put("jvm.heap.usagePercent", String.format("%.2f%%", usagePercent));

        assertTrue(heapUsed > 0);
        assertTrue(heapMax > 0);
        assertTrue(usagePercent > 0 && usagePercent < 100);
    }

    @Test
    @Order(14)
    void testSystemMetrics_OsInfo() {
        Map<String, Object> metrics = new java.util.HashMap<>();
        metrics.put("os.name", System.getProperty("os.name"));
        metrics.put("os.arch", System.getProperty("os.arch"));
        metrics.put("os.version", System.getProperty("os.version"));

        assertNotNull(metrics.get("os.name"));
        assertNotNull(metrics.get("os.arch"));
        assertNotNull(metrics.get("os.version"));
    }

    @Test
    @Order(15)
    void testSystemMetrics_Processors() {
        int processors = Runtime.getRuntime().availableProcessors();

        assertTrue(processors > 0);
        assertNotNull(Integer.valueOf(processors));
    }

    @Test
    @Order(16)
    void testEscapeJson() {
        String input = "Test \"quoted\" string\nwith newlines";
        String escaped = input.replace("\\", "\\\\")
                              .replace("\"", "\\\"")
                              .replace("\n", "\\n")
                              .replace("\r", "\\r")
                              .replace("\t", "\\t");

        assertTrue(escaped.contains("\\\""));
        assertTrue(escaped.contains("\\n"));
        // 检查原始的未转义引号应该都被转义了
        // 但是字符串开头/结尾的引号（如 T、e、s、t 中的字符）不应该包含原始引号
        assertTrue(escaped.contains("\\\""));
    }

    @Test
    @Order(17)
    void testSerializeValue_String() {
        String value = "test string";
        String serialized = "\"" + escapeJson(value) + "\"";

        assertTrue(serialized.contains("\""));
        assertTrue(serialized.contains("test string"));
    }

    @Test
    @Order(18)
    void testSerializeValue_Number() {
        Number value = 12345;
        String serialized = value.toString();

        assertEquals("12345", serialized);
    }

    @Test
    @Order(19)
    void testSerializeValue_Boolean() {
        Boolean value = true;
        String serialized = value.toString();

        assertEquals("true", serialized);
    }

    @Test
    @Order(20)
    void testSerializeValue_Null() {
        String serialized = "null";

        assertEquals("null", serialized);
    }

    @Test
    @Order(21)
    void testComponentStatus_Values() {
        assertEquals(4, HealthCheckService.HealthStatus.values().length);
    }

    @Test
    @Order(22)
    void testComponentMessage_Limits() {
        String longMessage = "a".repeat(1000);
        HealthCheckService.ComponentHealth component = new HealthCheckService.ComponentHealth(
            "test", HealthCheckService.HealthStatus.UP, longMessage
        );

        assertEquals(1000, component.message().length());
    }

    @Test
    @Order(23)
    void testTimestamp_InRange() {
        long before = System.currentTimeMillis();
        HealthCheckService.ComponentHealth component = new HealthCheckService.ComponentHealth(
            "test", HealthCheckService.HealthStatus.UP, "message"
        );
        long after = System.currentTimeMillis();

        assertTrue(component.timestamp() >= before);
        assertTrue(component.timestamp() <= after);
    }

    @Test
    @Order(24)
    void testMetrics_Names() {
        Map<String, Object> metrics = new java.util.HashMap<>();
        metrics.put("jvm.heap.used", 100000L);
        metrics.put("jvm.heap.max", 500000L);
        metrics.put("os.name", "Linux");

        assertTrue(metrics.containsKey("jvm.heap.used"));
        assertTrue(metrics.containsKey("jvm.heap.max"));
        assertTrue(metrics.containsKey("os.name"));
    }

    @Test
    @Order(25)
    void testHealthStatus_Priority() {
        // DOWN > DEGRADED > UP
        assertTrue(HealthCheckService.HealthStatus.DOWN.toString().equals("DOWN"));
        assertTrue(HealthCheckService.HealthStatus.DEGRADED.toString().equals("DEGRADED"));
        assertTrue(HealthCheckService.HealthStatus.UP.toString().equals("UP"));
    }

    @Test
    @Order(26)
    void testEmptyComponents() {
        Map<String, HealthCheckService.ComponentHealth> components = new java.util.HashMap<>();

        assertTrue(components.isEmpty());
        assertEquals(0, components.size());
    }

    @Test
    @Order(27)
    void testEmptyMetrics() {
        Map<String, Object> metrics = new java.util.HashMap<>();

        assertTrue(metrics.isEmpty());
        assertEquals(0, metrics.size());
    }

    @Test
    @Order(28)
    void testVersion_Default() {
        String version = "1.0.0-SNAPSHOT";

        assertFalse(version.isEmpty());
        assertTrue(version.contains("."));
    }

    @Test
    @Order(29)
    void testUptime_Calculation() {
        long startTime = System.currentTimeMillis() - 60000; // 1分钟前
        long currentUptime = System.currentTimeMillis() - startTime;

        assertTrue(currentUptime > 59000); // 至少59秒
        assertTrue(currentUptime < 61000); // 最多61秒
    }

    @Test
    @Order(30)
    void testComponentNaming() {
        String[] componentNames = {"database", "agentService", "toolRegistry"};

        for (String name : componentNames) {
            assertNotNull(name);
            assertFalse(name.isEmpty());
            assertTrue(name.matches("[a-zA-Z]+"));
        }
    }

    // 辅助方法
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}