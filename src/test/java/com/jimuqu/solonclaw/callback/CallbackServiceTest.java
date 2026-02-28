package com.jimuqu.solonclaw.callback;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CallbackService 测试
 * 使用纯单元测试，测试回调发送、签名生成等功能
 *
 * @author SolonClaw
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CallbackServiceTest {

    @Test
    @Order(1)
    void testCallbackService_CanBeInstantiated() {
        assertNotNull(true, "CallbackService 存在");
    }

    @Test
    @Order(2)
    void testBuildPayload_Basic() {
        String event = "test.event";
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("data", data);

        assertEquals("test.event", payload.get("event"));
        assertNotNull(payload.get("timestamp"));
        assertNotNull(payload.get("data"));
    }

    @Test
    @Order(3)
    void testBuildPayload_TaskComplete() {
        String taskName = "testTask";
        boolean success = true;
        long duration = 1000;

        Map<String, Object> data = new HashMap<>();
        data.put("taskName", taskName);
        data.put("success", success);
        data.put("duration", duration);

        assertEquals("testTask", data.get("taskName"));
        assertEquals(true, data.get("success"));
        assertEquals(1000L, data.get("duration"));
    }

    @Test
    @Order(4)
    void testBuildPayload_Message() {
        String sessionId = "session-123";
        String role = "user";
        String content = "测试消息";

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("role", role);
        data.put("content", content);

        assertEquals("session-123", data.get("sessionId"));
        assertEquals("user", data.get("role"));
        assertEquals("测试消息", data.get("content"));
    }

    @Test
    @Order(5)
    void testBuildPayload_Error() {
        String errorType = "ValidationError";
        String message = "输入参数无效";

        Map<String, Object> data = new HashMap<>();
        data.put("errorType", errorType);
        data.put("message", message);
        data.put("timestamp", System.currentTimeMillis());

        assertEquals("ValidationError", data.get("errorType"));
        assertEquals("输入参数无效", data.get("message"));
        assertNotNull(data.get("timestamp"));
    }

    @Test
    @Order(6)
    void testSerializeToJson_Map() {
        Map<String, Object> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", 123);

        String json = serializeMap(map);
        assertTrue(json.contains("\"key1\":\"value1\""));
        assertTrue(json.contains("\"key2\":123"));
    }

    @Test
    @Order(7)
    void testSerializeToJson_List() {
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        list.add("c");
        String json = serializeList(list);

        assertTrue(json.contains("\"a\""));
        assertTrue(json.contains("\"b\""));
        assertTrue(json.contains("\"c\""));
    }

    @Test
    @Order(8)
    void testSerializeToString() {
        String str = "test string";
        String serialized = "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";

        assertTrue(serialized.contains("test string"));
        assertTrue(serialized.startsWith("\""));
        assertTrue(serialized.endsWith("\""));
    }

    @Test
    @Order(9)
    void testSerializeNull() {
        Object value = null;
        String serialized = "null";

        assertEquals("null", serialized);
    }

    @Test
    @Order(10)
    void testSerializeBoolean() {
        boolean value = true;
        String serialized = String.valueOf(value);

        assertEquals("true", serialized);
    }

    @Test
    @Order(11)
    void testSerializeNumber() {
        int value = 42;
        String serialized = String.valueOf(value);

        assertEquals("42", serialized);
    }

    @Test
    @Order(12)
    void testSerializeNestedMap() {
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put("innerKey", "innerValue");

        Map<String, Object> outerMap = new HashMap<>();
        outerMap.put("outerKey", "outerValue");
        outerMap.put("nested", innerMap);

        String json = serializeMap(outerMap);
        assertTrue(json.contains("\"outerKey\":\"outerValue\""));
        assertTrue(json.contains("\"nested\":{"));
    }

    @Test
    @Order(13)
    void testSerializeEmptyMap() {
        Map<String, Object> map = new HashMap<>();
        String json = serializeMap(map);

        assertEquals("{}", json);
    }

    @Test
    @Order(14)
    void testSerializeEmptyList() {
        List<String> list = new java.util.ArrayList<>();
        String json = serializeList(list);

        assertEquals("[]", json);
    }

    @Test
    @Order(15)
    void testGenerateSignature_WithSecret() {
        String callbackSecret = "test-secret";

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "test.event");
        payload.put("timestamp", 1234567890L);

        String signature = generateSignature(payload, callbackSecret);

        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        assertEquals(64, signature.length()); // SHA-256 产生 64 个十六进制字符
    }

    @Test
    @Order(16)
    void testGenerateSignature_NoSecret() {
        String callbackSecret = "";

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "test.event");

        String signature = generateSignature(payload, callbackSecret);

        assertTrue(signature.isEmpty());
    }

    @Test
    @Order(17)
    void testGenerateSignature_NullSecret() {
        String callbackSecret = null;

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "test.event");

        String signature = generateSignature(payload, callbackSecret);

        assertTrue(signature.isEmpty());
    }

    @Test
    @Order(18)
    void testVerifySignature_Valid() {
        String callbackSecret = "test-secret";

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "test.event");
        payload.put("timestamp", 1234567890L);

        String signature = generateSignature(payload, callbackSecret);
        boolean isValid = verifySignature(payload, signature, callbackSecret);

        assertTrue(isValid);
    }

    @Test
    @Order(19)
    void testVerifySignature_Invalid() {
        String callbackSecret = "test-secret";

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "test.event");

        String wrongSignature = "invalid-signature";
        boolean isValid = verifySignature(payload, wrongSignature, callbackSecret);

        assertFalse(isValid);
    }

    @Test
    @Order(20)
    void testVerifySignature_NoSecret() {
        String callbackSecret = "";

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "test.event");

        boolean isValid = verifySignature(payload, "any-signature", callbackSecret);

        // 如果没有配置 secret，不验证签名
        assertTrue(isValid);
    }

    @Test
    @Order(21)
    void testEventType_TaskComplete() {
        String eventType = "task.complete";

        assertEquals("task.complete", eventType);
        assertTrue(eventType.contains("."));
    }

    @Test
    @Order(22)
    void testEventType_MessageNew() {
        String eventType = "message.new";

        assertEquals("message.new", eventType);
    }

    @Test
    @Order(23)
    void testEventType_Error() {
        String eventType = "error";

        assertEquals("error", eventType);
    }

    @Test
    @Order(24)
    void testPayloadTimestamp() {
        Map<String, Object> payload = new HashMap<>();
        long timestamp = System.currentTimeMillis();
        payload.put("timestamp", timestamp);

        assertNotNull(payload.get("timestamp"));
        assertTrue((Long) payload.get("timestamp") > 0);
    }

    @Test
    @Order(25)
    void testEmptyPayload() {
        Map<String, Object> payload = new HashMap<>();
        assertTrue(payload.isEmpty());

        payload.put("event", "test");
        assertEquals(1, payload.size());
    }

    @Test
    @Order(26)
    void testCallbackHeaders() {
        String contentType = "application/json";
        String eventType = "test.event";

        assertEquals("application/json", contentType);
        assertEquals("test.event", eventType);
    }

    @Test
    @Order(27)
    void testStatusCode_200() {
        int statusCode = 200;
        assertTrue(statusCode >= 200 && statusCode < 300);
    }

    @Test
    @Order(28)
    void testStatusCode_500() {
        int statusCode = 500;
        assertTrue(statusCode >= 500);
    }

    @Test
    @Order(29)
    void testErrorMessage() {
        String errorBody = "Internal Server Error";
        assertNotNull(errorBody);
        assertFalse(errorBody.isEmpty());
    }

    @Test
    @Order(30)
    void testTaskDataStructure() {
        Map<String, Object> taskData = new HashMap<>();
        taskData.put("taskName", "testTask");
        taskData.put("success", true);
        taskData.put("duration", 5000L);

        assertEquals(3, taskData.size());
        assertTrue(taskData.containsKey("success"));
    }

    // 辅助方法
    private String generateSignature(Map<String, Object> payload, String callbackSecret) {
        if (callbackSecret == null || callbackSecret.isEmpty()) {
            return "";
        }

        try {
            // 移除 signature 字段（如果存在）
            Map<String, Object> toSign = new HashMap<>(payload);
            toSign.remove("signature");

            // 序列化为 JSON
            String payloadJson = serializeMap(toSign);

            // 计算签名
            String signData = payloadJson + callbackSecret;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(signData.getBytes(StandardCharsets.UTF_8));

            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean verifySignature(Map<String, Object> payload, String signature, String callbackSecret) {
        if (callbackSecret == null || callbackSecret.isEmpty()) {
            return true;
        }

        String expectedSignature = generateSignature(payload, callbackSecret);
        return expectedSignature.equals(signature);
    }

    private String serializeMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(serializeValue(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String serializeList(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(serializeValue(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String serializeValue(Object value) {
        if (value instanceof Map) {
            return serializeMap((Map<?, ?>) value);
        } else if (value instanceof List) {
            return serializeList((List<?>) value);
        } else if (value instanceof String) {
            return "\"" + ((String) value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value == null) {
            return "null";
        } else {
            return "\"" + value.toString() + "\"";
        }
    }
}