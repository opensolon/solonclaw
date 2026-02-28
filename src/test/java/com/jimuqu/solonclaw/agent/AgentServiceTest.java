package com.jimuqu.solonclaw.agent;

import org.junit.jupiter.api.Test;
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
class AgentServiceTest {

    @Inject
    private AgentService agentService;

    @Test
    void testSimpleChat() {
        String response = agentService.chat("你好", "test-simple");

        assertNotNull(response);
        assertFalse(response.isEmpty());

        System.out.println("简单对话响应: " + response);
    }

    @Test
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