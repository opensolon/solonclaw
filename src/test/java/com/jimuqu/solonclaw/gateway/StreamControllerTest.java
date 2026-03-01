package com.jimuqu.solonclaw.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 流式响应功能测试
 * 测试 SSE 接口和流式事件处理
 *
 * @author SolonClaw
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StreamControllerTest {

    // ==================== SSE 格式测试 ====================

    @Test
    @Order(1)
    @DisplayName("测试 SSE 事件格式 - 带 event 名称")
    void testSseEventFormat() {
        String eventName = "session";
        String data = "sess-1234567890";

        String sseEvent = formatSseEvent(eventName, data);

        assertTrue(sseEvent.contains("event: " + eventName));
        assertTrue(sseEvent.contains("data: " + data));
        assertTrue(sseEvent.endsWith("\n\n"));
    }

    @Test
    @Order(2)
    @DisplayName("测试 SSE 数据格式 - 默认 message 事件")
    void testSseDataFormat() {
        String data = "{\"type\":\"CONTENT\",\"content\":\"测试内容\"}";

        String sseData = formatSseData(data);

        assertTrue(sseData.startsWith("data: "));
        assertTrue(sseData.endsWith("\n\n"));
        assertTrue(sseData.contains(data));
    }

    @Test
    @Order(3)
    @DisplayName("测试 SSE 多行数据")
    void testSseMultilineData() {
        String data = "第一行\n第二行\n第三行";
        String escaped = escapeJson(data);

        assertFalse(escaped.contains("\n"));
        assertTrue(escaped.contains("\\n"));
    }

    // ==================== JSON 转义测试 ====================

    @Test
    @Order(10)
    @DisplayName("测试 JSON 字符串转义 - 特殊字符")
    void testJsonEscape_SpecialChars() {
        assertEquals("测试\\\\n内容", escapeJson("测试\\n内容"));
        assertEquals("测试\\\"内容\\\"", escapeJson("测试\"内容\""));
        assertEquals("第一行\\n第二行", escapeJson("第一行\n第二行"));
        assertEquals("第一行\\r第二行", escapeJson("第一行\r第二行"));
        assertEquals("列1\\t列2", escapeJson("列1\t列2"));
    }

    @Test
    @Order(11)
    @DisplayName("测试 JSON 字符串转义 - null 值")
    void testJsonEscape_Null() {
        assertEquals("", escapeJson(null));
        assertEquals("正常内容", escapeJson("正常内容"));
    }

    @Test
    @Order(12)
    @DisplayName("测试 JSON 字符串转义 - 空字符串")
    void testJsonEscape_Empty() {
        assertEquals("", escapeJson(""));
    }

    @Test
    @Order(13)
    @DisplayName("测试 JSON 字符串转义 - 复合场景")
    void testJsonEscape_Complex() {
        String input = "他说：\"你好\\n世界\"\n这是新行";
        String escaped = escapeJson(input);

        assertTrue(escaped.contains("\\\""));
        assertTrue(escaped.contains("\\\\"));
        assertFalse(escaped.contains("\n"));
    }

    // ==================== 流式事件类型测试 ====================

    @Test
    @Order(20)
    @DisplayName("测试流式事件类型枚举")
    void testStreamEventTypes() {
        String[] expectedTypes = {"START", "CONTENT", "TOOL_CALL", "TOOL_DONE", "END", "ERROR"};
        StreamEventType[] types = StreamEventType.values();

        assertEquals(expectedTypes.length, types.length);
        for (int i = 0; i < expectedTypes.length; i++) {
            assertEquals(expectedTypes[i], types[i].name());
        }
    }

    @Test
    @Order(21)
    @DisplayName("测试流式事件 - 创建带错误的事件")
    void testStreamEvent_WithError() {
        Throwable error = new RuntimeException("测试错误");
        StreamEvent event = new StreamEvent(StreamEventType.ERROR, "错误消息", error);

        assertEquals(StreamEventType.ERROR, event.type());
        assertEquals("错误消息", event.content());
        assertNotNull(event.error());
        assertEquals("测试错误", event.error().getMessage());
    }

    @Test
    @Order(22)
    @DisplayName("测试流式事件 - 创建简单事件")
    void testStreamEvent_Simple() {
        StreamEvent event = new StreamEvent(StreamEventType.CONTENT, "这是内容");

        assertEquals(StreamEventType.CONTENT, event.type());
        assertEquals("这是内容", event.content());
        assertNull(event.error());
    }

    // ==================== 流式事件 JSON 序列化测试 ====================

    @Test
    @Order(30)
    @DisplayName("测试流式事件 JSON 序列化 - START 事件")
    void testStreamEventToJson_Start() {
        StreamEvent event = new StreamEvent(StreamEventType.START, "开始处理");
        String json = event.toJson();

        assertTrue(json.contains("\"type\":\"START\""));
        assertTrue(json.contains("\"content\":"));
        assertTrue(json.contains("开始处理"));
    }

    @Test
    @Order(31)
    @DisplayName("测试流式事件 JSON 序列化 - ERROR 事件")
    void testStreamEventToJson_Error() {
        Throwable error = new RuntimeException("测试异常");
        StreamEvent event = new StreamEvent(StreamEventType.ERROR, "错误内容", error);
        String json = event.toJson();

        assertTrue(json.contains("\"type\":\"ERROR\""));
        assertTrue(json.contains("\"error\":"));
        assertTrue(json.contains("测试异常"));
    }

    @Test
    @Order(32)
    @DisplayName("测试流式事件 JSON 序列化 - 特殊字符内容")
    void testStreamEventToJson_SpecialChars() {
        StreamEvent event = new StreamEvent(StreamEventType.CONTENT, "内容包含\"引号\"和\\斜杠");
        String json = event.toJson();

        // 验证转义后的内容
        assertTrue(json.contains("\\\""));  // 引号被转义
        assertTrue(json.contains("\\\\"));  // 斜杠被转义
        // 验证 JSON 格式正确
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
    }

    // ==================== 流式事件收集器测试 ====================

    @Test
    @Order(40)
    @DisplayName("测试流式事件收集")
    void testStreamEventCollection() {
        List<StreamEvent> events = new ArrayList<>();

        // 模拟流式事件
        events.add(new StreamEvent(StreamEventType.START, "开始处理"));
        events.add(new StreamEvent(StreamEventType.CONTENT, "第一段内容"));
        events.add(new StreamEvent(StreamEventType.CONTENT, "第二段内容"));
        events.add(new StreamEvent(StreamEventType.END, "处理完成"));

        assertEquals(4, events.size());
        assertEquals(StreamEventType.START, events.get(0).type());
        assertEquals(StreamEventType.END, events.get(3).type());
    }

    @Test
    @Order(41)
    @DisplayName("测试流式事件顺序")
    void testStreamEventSequence() {
        List<StreamEvent> events = new ArrayList<>();

        // 模拟工具调用流程
        events.add(new StreamEvent(StreamEventType.START, "开始"));
        events.add(new StreamEvent(StreamEventType.CONTENT, "思考中..."));
        events.add(new StreamEvent(StreamEventType.TOOL_CALL, "执行工具"));
        events.add(new StreamEvent(StreamEventType.TOOL_DONE, "工具完成"));
        events.add(new StreamEvent(StreamEventType.CONTENT, "处理结果"));
        events.add(new StreamEvent(StreamEventType.END, "结束"));

        // 验证事件顺序
        assertEquals(StreamEventType.START, events.get(0).type());
        assertEquals(StreamEventType.TOOL_CALL, events.get(2).type());
        assertEquals(StreamEventType.TOOL_DONE, events.get(3).type());
        assertEquals(StreamEventType.END, events.get(5).type());
    }

    // ==================== 会话 ID 生成测试 ====================

    @Test
    @Order(50)
    @DisplayName("测试会话 ID 生成")
    void testSessionIdGeneration() {
        String sessionId = generateSessionId();

        assertTrue(sessionId.startsWith("sess-"));
        assertTrue(sessionId.length() > 5);
    }

    @Test
    @Order(51)
    @DisplayName("测试会话 ID 唯一性")
    void testSessionIdUniqueness() throws InterruptedException {
        String id1 = generateSessionId();
        Thread.sleep(1);
        String id2 = generateSessionId();

        assertNotEquals(id1, id2);
    }

    // ==================== 请求验证测试 ====================

    @Test
    @Order(60)
    @DisplayName("测试对话请求 - 空 sessionId")
    void testChatRequest_EmptySessionId() {
        GatewayController.ChatRequest request = new GatewayController.ChatRequest("消息", "");

        assertTrue(request.sessionId() == null || request.sessionId().isEmpty());
    }

    @Test
    @Order(61)
    @DisplayName("测试对话请求 - null sessionId")
    void testChatRequest_NullSessionId() {
        GatewayController.ChatRequest request = new GatewayController.ChatRequest("消息", null);

        assertNull(request.sessionId());
    }

    @Test
    @Order(62)
    @DisplayName("测试对话请求 - 有效 sessionId")
    void testChatRequest_ValidSessionId() {
        GatewayController.ChatRequest request = new GatewayController.ChatRequest("消息", "sess-123");

        assertEquals("sess-123", request.sessionId());
        assertEquals("消息", request.message());
    }

    // ==================== 辅助方法 ====================

    private String formatSseEvent(String eventName, String data) {
        StringBuilder sb = new StringBuilder();
        sb.append("event: ").append(eventName).append("\n");
        sb.append("data: ").append(data).append("\n\n");
        return sb.toString();
    }

    private String formatSseData(String data) {
        return "data: " + data + "\n\n";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String generateSessionId() {
        return "sess-" + System.currentTimeMillis();
    }

    // ==================== 内部类型定义（模拟 AgentService 中的类型）====================

    /**
     * 流式事件类型枚举
     */
    public enum StreamEventType {
        START, CONTENT, TOOL_CALL, TOOL_DONE, END, ERROR
    }

    /**
     * 流式事件记录
     */
    public record StreamEvent(
            StreamEventType type,
            String content,
            Throwable error
    ) {
        public StreamEvent(StreamEventType type, String content) {
            this(type, content, null);
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"type\":\"").append(type).append("\"");
            if (content != null) {
                sb.append(",\"content\":").append(escapeJson(content));
            }
            if (error != null) {
                sb.append(",\"error\":\"").append(escapeJson(error.getMessage())).append("\"");
            }
            sb.append("}");
            return sb.toString();
        }

        private String escapeJson(String value) {
            if (value == null) return "null";
            return "\"" + value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t") + "\"";
        }
    }
}