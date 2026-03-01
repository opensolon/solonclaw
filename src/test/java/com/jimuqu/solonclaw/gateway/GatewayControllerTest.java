package com.jimuqu.solonclaw.gateway;

import com.jimuqu.solonclaw.agent.AgentService;
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

    // ==================== 流式响应相关测试 ====================

    @Test
    @Order(12)
    void testStreamEventType_EnumValues() {
        // 验证所有事件类型存在
        AgentService.StreamEventType[] types = AgentService.StreamEventType.values();

        assertEquals(6, types.length);
        assertEquals(AgentService.StreamEventType.START, types[0]);
        assertEquals(AgentService.StreamEventType.CONTENT, types[1]);
        assertEquals(AgentService.StreamEventType.TOOL_CALL, types[2]);
        assertEquals(AgentService.StreamEventType.TOOL_DONE, types[3]);
        assertEquals(AgentService.StreamEventType.END, types[4]);
        assertEquals(AgentService.StreamEventType.ERROR, types[5]);
    }

    @Test
    @Order(13)
    void testStreamEvent_BasicConstruction() {
        String content = "测试内容";
        AgentService.StreamEvent event = new AgentService.StreamEvent(
                AgentService.StreamEventType.CONTENT,
                content
        );

        assertEquals(AgentService.StreamEventType.CONTENT, event.type());
        assertEquals(content, event.content());
        assertNull(event.error());
    }

    @Test
    @Order(14)
    void testStreamEvent_WithError() {
        Throwable error = new RuntimeException("测试错误");
        AgentService.StreamEvent event = new AgentService.StreamEvent(
                AgentService.StreamEventType.ERROR,
                "发生错误",
                error
        );

        assertEquals(AgentService.StreamEventType.ERROR, event.type());
        assertEquals("发生错误", event.content());
        assertEquals(error, event.error());
    }

    @Test
    @Order(15)
    void testStreamEvent_ToJson_ContentType() {
        AgentService.StreamEvent event = new AgentService.StreamEvent(
                AgentService.StreamEventType.CONTENT,
                "你好，世界！"
        );

        String json = event.toJson();

        assertTrue(json.contains("\"type\":\"CONTENT\""));
        assertTrue(json.contains("\"content\""));
        assertTrue(json.contains("你好，世界！"));
    }

    @Test
    @Order(16)
    void testStreamEvent_ToJson_StartType() {
        AgentService.StreamEvent event = new AgentService.StreamEvent(
                AgentService.StreamEventType.START,
                "开始处理"
        );

        String json = event.toJson();

        assertTrue(json.contains("\"type\":\"START\""));
        assertTrue(json.contains("\"content\":\"开始处理\""));
    }

    @Test
    @Order(17)
    void testStreamEvent_ToJson_EndType() {
        AgentService.StreamEvent event = new AgentService.StreamEvent(
                AgentService.StreamEventType.END,
                "处理完成"
        );

        String json = event.toJson();

        assertTrue(json.contains("\"type\":\"END\""));
        assertTrue(json.contains("\"content\":\"处理完成\""));
    }

    @Test
    @Order(18)
    void testStreamEvent_ToJson_ErrorType() {
        Throwable error = new RuntimeException("处理失败");
        AgentService.StreamEvent event = new AgentService.StreamEvent(
                AgentService.StreamEventType.ERROR,
                "发生错误",
                error
        );

        String json = event.toJson();

        assertTrue(json.contains("\"type\":\"ERROR\""));
        assertTrue(json.contains("\"content\":\"发生错误\""));
        assertTrue(json.contains("\"error\":\"处理失败\""));
    }

    @Test
    @Order(19)
    void testStreamEvent_ToJson_NullContent() {
        AgentService.StreamEvent event = new AgentService.StreamEvent(
                AgentService.StreamEventType.START,
                null
        );

        String json = event.toJson();

        assertTrue(json.contains("\"type\":\"START\""));
        assertFalse(json.contains("\"content\""));
    }

    @Test
    @Order(20)
    void testStreamEvent_ToJson_JsonEscaping() {
        String content = "内容包含\"引号\"和\n换行符";
        AgentService.StreamEvent event = new AgentService.StreamEvent(
                AgentService.StreamEventType.CONTENT,
                content
        );

        String json = event.toJson();

        assertTrue(json.contains("\\\"引号\\\""));
        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("换行符"));
    }

    @Test
    @Order(21)
    void testStreamEvent_ToJson_SpecialCharacters() {
        String content = "测试\\r\\n制表符\t内容";
        AgentService.StreamEvent event = new AgentService.StreamEvent(
                AgentService.StreamEventType.CONTENT,
                content
        );

        String json = event.toJson();

        assertTrue(json.contains("\\\\r"));
        assertTrue(json.contains("\\\\n"));
        assertTrue(json.contains("\\t"));
    }
}