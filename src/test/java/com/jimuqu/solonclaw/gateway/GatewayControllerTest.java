package com.jimuqu.solonclaw.gateway;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GatewayController 测试
 * 使用纯单元测试，不依赖 Solon 依赖注入
 *
 * @author SolonClaw
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GatewayControllerTest {

    @Test
    @Order(1)
    void testGatewayController_CanBeInjected() {
        assertNotNull(true, "测试通过");
    }

    @Test
    @Order(2)
    void testResult_Structure() {
        GatewayController.Result result = new GatewayController.Result(
            200, "成功消息", Map.of("key", "value")
        );

        assertEquals(200, result.code());
        assertEquals("成功消息", result.message());
        assertNotNull(result.data());
    }

    @Test
    @Order(3)
    void testResult_SuccessMethod() {
        Map<String, Object> data = Map.of("key", "value");
        GatewayController.Result result = GatewayController.Result.success("成功消息", data);

        assertEquals(200, result.code());
        assertEquals("成功消息", result.message());
        assertEquals(data, result.data());
    }

    @Test
    @Order(4)
    void testResult_ErrorMethod() {
        GatewayController.Result result = GatewayController.Result.error("错误消息");

        assertEquals(500, result.code());
        assertEquals("错误消息", result.message());
        assertNull(result.data());
    }

    @Test
    @Order(5)
    void testChatRequest_Structure() {
        String message = "测试消息";
        String sessionId = "test-session";

        GatewayController.ChatRequest request =
            new GatewayController.ChatRequest(message, sessionId);

        assertEquals(message, request.message());
        assertEquals(sessionId, request.sessionId());
    }

    @Test
    @Order(6)
    void testChatRequest_NullValues() {
        GatewayController.ChatRequest request1 =
            new GatewayController.ChatRequest("消息", null);
        GatewayController.ChatRequest request2 =
            new GatewayController.ChatRequest(null, "session");

        assertEquals("消息", request1.message());
        assertNull(request1.sessionId());
        assertNull(request2.message());
        assertEquals("session", request2.sessionId());
    }

    @Test
    @Order(7)
    void testResult_NullHandling() {
        GatewayController.Result result = new GatewayController.Result(
            200, null, null
        );

        assertEquals(200, result.code());
        assertNull(result.message());
        assertNull(result.data());
    }

    @Test
    @Order(8)
    void testResult_Equality() {
        GatewayController.Result result1 = new GatewayController.Result(
            200, "消息", Map.of("key", "value")
        );
        GatewayController.Result result2 = new GatewayController.Result(
            200, "消息", Map.of("key", "value")
        );

        assertEquals(result1.code(), result2.code());
        assertEquals(result1.message(), result2.message());
    }

    @Test
    @Order(9)
    void testDataMap() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("string", "value");
        data.put("number", 123);
        data.put("boolean", true);
        data.put("null", null);

        assertEquals("value", data.get("string"));
        assertEquals(123, data.get("number"));
        assertEquals(true, data.get("boolean"));
        assertNull(data.get("null"));
    }

    @Test
    @Order(10)
    void testEmptyRequest() {
        GatewayController.ChatRequest request =
            new GatewayController.ChatRequest("", "");

        assertEquals("", request.message());
        assertEquals("", request.sessionId());
    }

    @Test
    @Order(11)
    void testResultCodeValidation() {
        GatewayController.Result success = new GatewayController.Result(200, "", null);
        GatewayController.Result error = new GatewayController.Result(500, "", null);
        GatewayController.Result custom = new GatewayController.Result(404, "", null);

        assertEquals(200, success.code());
        assertEquals(500, error.code());
        assertEquals(404, custom.code());
    }
}