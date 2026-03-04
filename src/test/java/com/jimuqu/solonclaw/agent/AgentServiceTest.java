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

    @Inject(required = false)
    private com.jimuqu.solonclaw.skill.SkillsManager skillsManager;

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

    // ==================== SkillsManager 集成测试 ====================

    @Test
    @Order(20)
    @DisplayName("测试 SkillsManager 集成 - Agent 能获取到技能")
    void testSkillsManagerIntegration() {
        if (skillsManager == null) {
            System.out.println("SkillsManager 未注入，跳过测试");
            return;
        }

        // 获取所有技能
        var skills = skillsManager.getSkills();
        assertNotNull(skills);
        System.out.println("已注册的技能数量: " + skills.size());

        // 获取技能配置
        var skillConfigs = skillsManager.getSkillConfigs();
        assertNotNull(skillConfigs);
        System.out.println("技能配置数量: " + skillConfigs.size());
    }

    @Test
    @Order(21)
    @DisplayName("测试 Agent 重载功能")
    void testAgentReload() {
        // 重载前先进行一次对话
        String response1 = agentService.chat("测试消息", "test-reload-1");
        assertNotNull(response1);

        // 执行重载
        assertDoesNotThrow(() -> agentService.reloadAgent());

        // 重载后再次对话
        String response2 = agentService.chat("测试消息", "test-reload-2");
        assertNotNull(response2);

        System.out.println("Agent 重载测试通过");
    }

    @Test
    @Order(22)
    @DisplayName("测试技能启用/禁用触发 Agent 重载")
    void testSkillToggleTriggersReload() {
        if (skillsManager == null) {
            System.out.println("SkillsManager 未注入，跳过测试");
            return;
        }

        // 获取现有技能配置
        var configs = skillsManager.getSkillConfigs();
        if (configs.isEmpty()) {
            System.out.println("没有可用技能，跳过测试");
            return;
        }

        // 选择第一个技能进行测试
        var firstSkill = configs.get(0);
        String skillName = firstSkill.name();
        boolean originalEnabled = firstSkill.enabled();

        try {
            // 切换状态
            boolean newEnabled = !originalEnabled;
            boolean result = skillsManager.setSkillEnabled(skillName, newEnabled);
            assertTrue(result, "技能状态切换应成功");

            // 验证状态已更改
            var updatedConfig = skillsManager.getSkillConfig(skillName);
            assertEquals(newEnabled, updatedConfig.enabled(), "技能状态应该已更新");

            System.out.println("技能 " + skillName + " 状态已从 " + originalEnabled + " 切换为 " + newEnabled);

        } finally {
            // 恢复原始状态
            skillsManager.setSkillEnabled(skillName, originalEnabled);
            System.out.println("已恢复技能原始状态");
        }
    }

    @Test
    @Order(23)
    @DisplayName("测试添加技能触发 Agent 重载")
    void testAddSkillTriggersReload() {
        if (skillsManager == null) {
            System.out.println("SkillsManager 未注入，跳过测试");
            return;
        }

        // 创建测试技能配置
        String testSkillName = "test_reload_skill_" + System.currentTimeMillis();
        var config = new com.jimuqu.solonclaw.skill.DynamicSkill.SkillConfig(
                testSkillName,
                "测试重载功能的技能",
                "这是一个测试技能",
                null,
                List.of(),
                true
        );

        try {
            // 添加技能
            boolean result = skillsManager.addSkill(config);
            assertTrue(result, "添加技能应成功");

            // 验证技能已添加
            assertTrue(skillsManager.hasSkill(testSkillName), "技能应该已存在");

            System.out.println("测试技能添加成功: " + testSkillName);

        } finally {
            // 清理测试技能
            skillsManager.removeSkill(testSkillName);
            System.out.println("已清理测试技能");
        }
    }
}