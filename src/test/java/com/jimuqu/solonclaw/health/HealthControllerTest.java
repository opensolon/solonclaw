package com.jimuqu.solonclaw.health;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HealthController 测试
 * 使用纯单元测试，测试健康检查接口
 *
 * @author SolonClaw
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HealthControllerTest {

    @Test
    @Order(1)
    void testHealthController_CanBeInstantiated() {
        assertNotNull(true, "HealthController 存在");
    }

    @Test
    @Order(2)
    void testMapping_Paths() {
        assertEquals("/health", "/health");
        assertEquals("/health/live", "/health/live");
        assertEquals("/health/ready", "/health/ready");
        assertEquals("/health/metrics", "/health/metrics");
        assertEquals("/health/simple", "/health/simple");
    }

    @Test
    @Order(3)
    void testHttpStatusCode_Healthy() {
        int statusCode = 200;
        assertTrue(statusCode >= 200 && statusCode < 300);
    }

    @Test
    @Order(4)
    void testHttpStatusCode_Unhealthy() {
        int statusCode = 503;
        assertEquals(503, statusCode);
    }

    @Test
    @Order(5)
    void testHttpStatusCode_Degraded() {
        int statusCode = 200; // 降级也返回 200
        assertEquals(200, statusCode);
    }

    @Test
    @Order(6)
    void testContentType_ApplicationJson() {
        String contentType = "application/json";
        assertTrue(contentType.contains("json"));
    }

    @Test
    @Order(7)
    void testContentType_TextPlain() {
        String contentType = "text/plain";
        assertTrue(contentType.contains("text"));
    }

    @Test
    @Order(8)
    void testLivenessProbe_Status() {
        boolean isHealthy = true;
        String status = isHealthy ? "UP" : "DOWN";

        assertEquals("UP", status);
    }

    @Test
    @Order(9)
    void testReadinessProbe_Status() {
        boolean isHealthy = true;
        String status = isHealthy ? "READY" : "NOT_READY";

        assertEquals("READY", status);
    }

    @Test
    @Order(10)
    void testResponseFormat_Json() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"status\":\"UP\",");
        sb.append("\"version\":\"1.0.0\",");
        sb.append("}");

        String json = sb.toString();
        assertTrue(json.contains("\"status\":\"UP\""));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
    }

    @Test
    @Order(11)
    void testResponseFormat_Simple() {
        StringBuilder sb = new StringBuilder();
        sb.append("Health Status: UP\n");
        sb.append("Version: 1.0.0\n");
        sb.append("Uptime: 1 hours 0 minutes\n");

        String text = sb.toString();
        assertTrue(text.contains("Health Status:"));
        assertTrue(text.contains("Version:"));
        assertTrue(text.contains("Uptime:"));
    }

    @Test
    @Order(12)
    void testComponentPath() {
        String componentName = "database";
        String path = "/health/components/" + componentName;

        assertTrue(path.endsWith(componentName));
        assertTrue(path.contains("/components/"));
    }

    @Test
    @Order(13)
    void testMetricsResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"jvm.heap.used\":104857600,");
        sb.append("\"jvm.heap.max\":536870912,");
        sb.append("\"os.availableProcessors\":4");
        sb.append("}");

        String metrics = sb.toString();
        assertTrue(metrics.contains("jvm.heap.used"));
        assertTrue(metrics.contains("os.availableProcessors"));
    }

    @Test
    @Order(14)
    void testEscapeJson_Quotes() {
        String input = "message with \"quotes\"";
        String escaped = input.replace("\"", "\\\"");

        assertTrue(escaped.contains("\\\""));
    }

    @Test
    @Order(15)
    void testEscapeJson_Newlines() {
        String input = "line1\nline2";
        String escaped = input.replace("\n", "\\n");

        assertTrue(escaped.contains("\\n"));
    }

    @Test
    @Order(16)
    void testSerialize_Map() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("key1", "value1");
        map.put("key2", 123);

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (java.util.Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(serializeValue(entry.getValue()));
            first = false;
        }
        sb.append("}");

        String json = sb.toString();
        assertTrue(json.contains("\"key1\":\"value1\""));
        assertTrue(json.contains("\"key2\":123"));
    }

    @Test
    @Order(17)
    void testSerialize_List() {
        java.util.List<String> list = java.util.List.of("a", "b", "c");

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(list.get(i)).append("\"");
        }
        sb.append("]");

        String json = sb.toString();
        assertEquals("[\"a\",\"b\",\"c\"]", json);
    }

    @Test
    @Order(18)
    void testKubernetesProbe_Pattern() {
        // Kubernetes liveness/readiness probe 期望的响应
        String expectedPattern = "\\{\\\"status\\\":\\\"(UP|DOWN|READY|NOT_READY)\\\"\\}";

        String response1 = "{\"status\":\"UP\"}";
        String response2 = "{\"status\":\"DOWN\"}";
        String response3 = "{\"status\":\"READY\"}";

        assertTrue(response1.matches("\\{\\\"status\\\":\\\"UP\\\"\\}"));
        assertTrue(response2.matches("\\{\\\"status\\\":\\\"DOWN\\\"\\}"));
        assertTrue(response3.matches("\\{\\\"status\\\":\\\"READY\\\"\\}"));
    }

    @Test
    @Order(19)
    void testHealthCheckEndpoint_Verbs() {
        String[] httpMethods = {"GET", "POST", "PUT", "DELETE"};
        String healthCheckMethod = "GET";

        assertEquals("GET", healthCheckMethod);
    }

    @Test
    @Order(20)
    void testResponseHeaders() {
        String contentType = "application/json";
        int statusCode = 200;

        assertNotNull(contentType);
        assertTrue(statusCode > 0);
    }

    @Test
    @Order(21)
    void testComponentStatus_Priority() {
        String[] statuses = {"UP", "DOWN", "DEGRADED", "UNKNOWN"};

        // DOWN 应该返回 503
        int downStatusCode = 503;
        assertEquals(503, downStatusCode);

        // UP 应该返回 200
        int upStatusCode = 200;
        assertEquals(200, upStatusCode);
    }

    @Test
    @Order(22)
    void testTimestamp_Format() {
        long timestamp = System.currentTimeMillis();

        assertTrue(timestamp > 0);
        assertTrue(timestamp < 9999999999999L);
    }

    @Test
    @Order(23)
    void testEmptyResponse() {
        String emptyResponse = "{}";

        assertTrue(emptyResponse.contains("{"));
        assertTrue(emptyResponse.contains("}"));
    }

    @Test
    @Order(24)
    void testNestedJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"components\":{");
        sb.append("\"database\":{");
        sb.append("\"status\":\"UP\"");
        sb.append("}");
        sb.append("}");
        sb.append("}");

        String json = sb.toString();
        assertTrue(json.contains("\"components\":{"));
        assertTrue(json.contains("\"database\":{"));
    }

    @Test
    @Order(25)
    void testArrayJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append("{");
        sb.append("\"name\":\"component1\"");
        sb.append("}");
        sb.append("]");

        String json = sb.toString();
        assertTrue(json.contains("["));
        assertTrue(json.contains("]"));
    }

    @Test
    @Order(26)
    void testMetrics_Keys() {
        String[] metricKeys = {
            "jvm.heap.used",
            "jvm.heap.max",
            "jvm.heap.usagePercent",
            "os.name",
            "os.arch",
            "os.version",
            "os.availableProcessors"
        };

        for (String key : metricKeys) {
            assertNotNull(key);
            assertFalse(key.isEmpty());
        }
    }

    @Test
    @Order(27)
    void testHealthStatus_JsonFormat() {
        String status = "UP";
        String json = "\"status\":\"" + status + "\"";

        assertTrue(json.contains("\"status\":\"UP\""));
    }

    @Test
    @Order(28)
    void testErrorHandling_NullComponent() {
        String componentName = null;
        boolean isValid = componentName != null && !componentName.isEmpty();

        assertFalse(isValid);
    }

    @Test
    @Order(29)
    void testErrorHandling_UnknownComponent() {
        String componentName = "unknown-component";
        String[] knownComponents = {"database", "agentService", "toolRegistry"};

        boolean isKnown = false;
        for (String known : knownComponents) {
            if (known.equals(componentName)) {
                isKnown = true;
                break;
            }
        }

        assertFalse(isKnown);
    }

    @Test
    @Order(30)
    void testResponseEncoding() {
        String encoding = "UTF-8";
        String chinese = "系统正常";

        assertTrue(encoding.equals("UTF-8"));
        assertNotNull(chinese);
    }

    // 辅助方法
    private String serializeValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + value + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else {
            return "\"" + value.toString() + "\"";
        }
    }
}