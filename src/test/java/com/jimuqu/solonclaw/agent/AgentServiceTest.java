package com.jimuqu.solonclaw.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.noear.solon.annotation.Inject;
import org.noear.solon.test.SolonTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentService 测试
 * <p>
 * 测试 AgentService 的基本功能
 *
 * @author SolonClaw
 */
@SolonTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentServiceTest {

    @Inject
    private AgentService agentService;

    // ==================== 预热功能测试 ====================

    @Test
    @Order(1)
    @DisplayName("测试 ReActAgent 预热 - 启动后应已完成预热")
    void testWarmup() {
        // 由于 @Init 注解，应用启动时应该已经完成预热
        assertTrue(agentService.isWarmedUp(), "应用启动后应已完成预热");
        System.out.println("预热状态: " + agentService.isWarmedUp());
    }

    @Test
    @Order(2)
    @DisplayName("测试首次对话响应时间 - 预热后应快速响应")
    void testFirstChatResponseTime() {
        long startTime = System.currentTimeMillis();
        String response = agentService.chat("你好", "test-warmup-response");
        long elapsed = System.currentTimeMillis() - startTime;

        assertNotNull(response);
        assertFalse(response.isEmpty());

        System.out.println("首次对话响应时间: " + elapsed + " ms");
        System.out.println("响应内容: " + response);

        // 预热后首次响应应该较快（可根据实际情况调整阈值）
        // 注意：这里不设置严格阈值，因为实际响应时间取决于模型 API
    }

    // ==================== 基础对话功能测试 ====================

    @Test
    @Order(10)
    @DisplayName("测试简单对话")
    void testSimpleChat() {
        String response = agentService.chat("你好", "test-simple");

        assertNotNull(response);
        assertFalse(response.isEmpty());

        System.out.println("简单对话响应: " + response);
    }

    @Test
    @Order(11)
    @DisplayName("测试 Shell 命令执行")
    void testShellCommand() {
        String response = agentService.chat(
                "请执行 echo hello world 命令",
                "test-shell"
        );

        assertNotNull(response);
        assertFalse(response.isEmpty());

        System.out.println("Shell命令响应: " + response);
    }

    @Test
    @Order(12)
    @DisplayName("测试多轮对话")
    void testMultiTurnConversation() {
        String sessionId = "test-multi-turn";

        // 第一轮
        String response1 = agentService.chat("我的名字是张三", sessionId);
        assertNotNull(response1);

        // 第二轮
        String response2 = agentService.chat("我叫什么名字？", sessionId);
        assertNotNull(response2);

        System.out.println("多轮对话响应1: " + response1);
        System.out.println("多轮对话响应2: " + response2);
    }

    @Test
    @Order(13)
    @DisplayName("测试列出目录")
    void testListDirectory() {
        String response = agentService.chat(
                "列出当前目录的文件",
                "test-list-dir"
        );

        assertNotNull(response);
        assertFalse(response.isEmpty());

        System.out.println("列出目录响应: " + response);
    }

    @Test
    @Order(14)
    @DisplayName("测试清空历史")
    void testClearHistory() {
        String sessionId = "test-clear";

        // 发送消息
        agentService.chat("这是一条测试消息", sessionId);

        // 清空历史
        agentService.clearHistory(sessionId);

        // 验证历史已清空
        var history = agentService.getHistory(sessionId);
        assertTrue(history.isEmpty());

        System.out.println("历史已清空");
    }

    @Test
    @Order(15)
    @DisplayName("测试获取可用工具")
    void testGetAvailableTools() {
        var tools = agentService.getAvailableTools();

        assertNotNull(tools);
        assertFalse(tools.isEmpty());

        System.out.println("可用工具数量: " + tools.size());
        for (var entry : tools.entrySet()) {
            System.out.println("  - " + entry.getKey() + ": " + entry.getValue().description());
        }
    }

    @Test
    @Order(16)
    @DisplayName("测试复杂任务")
    void testComplexTask() {
        String response = agentService.chat(
                "查看当前目录，然后告诉我有多少个文件",
                "test-complex"
        );

        assertNotNull(response);
        assertFalse(response.isEmpty());

        System.out.println("复杂任务响应: " + response);
    }
}