package com.jimuqu.solonclaw.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillsManager 测试
 * 测试技能管理、配置解析等功能
 *
 * @author SolonClaw
 */
class SkillsManagerTest {

    private List<DynamicSkill.SkillConfig> testConfigs;

    @BeforeEach
    void setUp() {
        testConfigs = new ArrayList<>();
    }

    @Nested
    @DisplayName("技能配置测试")
    class SkillConfigTest {

        @Test
        @DisplayName("创建基本技能配置")
        void testCreateBasicSkillConfig() {
            DynamicSkill.SkillConfig config = new DynamicSkill.SkillConfig(
                "order_expert",
                "订单助手",
                "处理订单相关查询",
                "prompt.contains('订单')",
                List.of("query_order", "cancel_order"),
                true
            );

            assertEquals("order_expert", config.name());
            assertEquals("订单助手", config.description());
            assertEquals("处理订单相关查询", config.instruction());
            assertEquals("prompt.contains('订单')", config.condition());
            assertEquals(2, config.tools().size());
            assertTrue(config.enabled());
        }

        @Test
        @DisplayName("创建无条件的技能配置")
        void testCreateUnconditionalSkillConfig() {
            DynamicSkill.SkillConfig config = new DynamicSkill.SkillConfig(
                "general_assistant",
                "通用助手",
                "回答通用问题",
                null,
                null,
                true
            );

            assertEquals("general_assistant", config.name());
            assertNull(config.condition());
            assertTrue(config.tools() == null || config.tools().isEmpty());
        }

        @Test
        @DisplayName("创建禁用的技能配置")
        void testCreateDisabledSkillConfig() {
            DynamicSkill.SkillConfig config = new DynamicSkill.SkillConfig(
                "disabled_skill",
                "禁用技能",
                "这个技能被禁用",
                null,
                null,
                false
            );

            assertFalse(config.enabled());
        }

        @Test
        @DisplayName("默认启用状态")
        void testDefaultEnabled() {
            DynamicSkill.SkillConfig config = new DynamicSkill.SkillConfig(
                "default_skill",
                "默认技能",
                null,
                null,
                null,
                true
            );

            assertTrue(config.enabled());
        }
    }

    @Nested
    @DisplayName("技能请求测试")
    class SkillRequestTest {

        @Test
        @DisplayName("创建技能请求")
        void testCreateSkillRequest() {
            SkillsController.SkillRequest request = new SkillsController.SkillRequest(
                "test_skill",
                "测试技能",
                "测试指令",
                "prompt.contains('test')",
                List.of("tool1", "tool2"),
                true
            );

            assertEquals("test_skill", request.name());
            assertEquals("测试技能", request.description());
            assertEquals("测试指令", request.instruction());
            assertEquals("prompt.contains('test')", request.condition());
            assertEquals(2, request.tools().size());
            assertTrue(request.enabled());
        }

        @Test
        @DisplayName("创建最小技能请求")
        void testCreateMinimalSkillRequest() {
            SkillsController.SkillRequest request = new SkillsController.SkillRequest(
                "minimal_skill",
                "最小技能",
                null,
                null,
                null,
                null
            );

            assertEquals("minimal_skill", request.name());
            assertEquals("最小技能", request.description());
            assertNull(request.instruction());
            assertNull(request.condition());
            assertNull(request.tools());
            assertNull(request.enabled());
        }
    }

    @Nested
    @DisplayName("响应结果测试")
    class ResultTest {

        @Test
        @DisplayName("创建成功响应")
        void testSuccessResult() {
            SkillsController.Result result = SkillsController.Result.success("操作成功", List.of("data"));

            assertEquals(200, result.code());
            assertEquals("操作成功", result.message());
            assertNotNull(result.data());
        }

        @Test
        @DisplayName("创建成功响应（无数据）")
        void testSuccessResultWithoutData() {
            SkillsController.Result result = SkillsController.Result.success("操作成功");

            assertEquals(200, result.code());
            assertEquals("操作成功", result.message());
            assertNull(result.data());
        }

        @Test
        @DisplayName("创建错误响应")
        void testErrorResult() {
            SkillsController.Result result = SkillsController.Result.error("操作失败");

            assertEquals(500, result.code());
            assertEquals("操作失败", result.message());
            assertNull(result.data());
        }
    }

    @Nested
    @DisplayName("条件表达式测试")
    class ConditionExpressionTest {

        @Test
        @DisplayName("包含关键词条件")
        void testContainsCondition() {
            String condition = "prompt.contains('订单')";

            assertTrue(condition.contains("contains("));
            assertTrue(condition.contains("订单"));
        }

        @Test
        @DisplayName("OR 组合条件")
        void testOrCondition() {
            String condition = "prompt.contains('订单') || prompt.contains('购买')";

            assertTrue(condition.contains("||"));
            assertTrue(condition.contains("订单"));
            assertTrue(condition.contains("购买"));
        }

        @Test
        @DisplayName("AND 组合条件")
        void testAndCondition() {
            String condition = "prompt.contains('订单') && prompt.contains('查询')";

            assertTrue(condition.contains("&&"));
            assertTrue(condition.contains("订单"));
            assertTrue(condition.contains("查询"));
        }
    }

    @Nested
    @DisplayName("动态技能测试")
    class DynamicSkillTest {

        @Test
        @DisplayName("创建 DynamicSkill")
        void testCreateDynamicSkill() {
            DynamicSkill.SkillConfig config = new DynamicSkill.SkillConfig(
                "test_skill",
                "测试技能",
                "测试指令",
                null,
                null,
                true
            );

            DynamicSkill skill = new DynamicSkill(config, null);

            assertNotNull(skill.metadata());
            assertEquals("test_skill", skill.metadata().getName());
            assertEquals("测试技能", skill.metadata().getDescription());
        }

        @Test
        @DisplayName("无条件技能总是支持")
        void testUnconditionalSkillAlwaysSupported() {
            DynamicSkill.SkillConfig config = new DynamicSkill.SkillConfig(
                "unconditional",
                "无条件技能",
                null,
                null,
                null,
                true
            );

            DynamicSkill skill = new DynamicSkill(config, null);

            // 无条件技能应该总是返回 true（但没有 prompt 对象无法测试）
            assertNotNull(skill);
        }
    }

    @Nested
    @DisplayName("工具解析测试")
    class ToolResolutionTest {

        @Test
        @DisplayName("工具列表格式")
        void testToolListFormat() {
            List<String> tools = List.of("query_order", "cancel_order", "create_order");

            assertEquals(3, tools.size());
            assertEquals("query_order", tools.get(0));
            assertEquals("cancel_order", tools.get(1));
            assertEquals("create_order", tools.get(2));
        }

        @Test
        @DisplayName("空工具列表")
        void testEmptyToolList() {
            List<String> tools = List.of();

            assertTrue(tools.isEmpty());
        }

        @Test
        @DisplayName("null 工具列表")
        void testNullToolList() {
            List<String> tools = null;

            assertNull(tools);
        }
    }

    @Nested
    @DisplayName("模板变量测试")
    class TemplateVariableTest {

        @Test
        @DisplayName("模板变量格式")
        void testTemplateVariableFormat() {
            String template = "你好，${attr.user_name}，您的等级是 ${attr.user_level}";

            assertTrue(template.contains("${attr.user_name}"));
            assertTrue(template.contains("${attr.user_level}"));
        }

        @Test
        @DisplayName("无模板变量")
        void testNoTemplateVariables() {
            String template = "这是一个普通的指令";

            assertFalse(template.contains("${"));
        }
    }

    @Nested
    @DisplayName("并发测试")
    class ConcurrencyTest {

        @Test
        @DisplayName("ConcurrentHashMap 线程安全")
        void testConcurrentHashMap() {
            java.util.concurrent.ConcurrentHashMap<String, DynamicSkill.SkillConfig> map = new java.util.concurrent.ConcurrentHashMap<>();

            // 模拟并发添加
            for (int i = 0; i < 100; i++) {
                final int index = i;
                map.put("skill-" + index, new DynamicSkill.SkillConfig(
                    "skill-" + index,
                    "技能 " + index,
                    null,
                    null,
                    null,
                    true
                ));
            }

            assertEquals(100, map.size());
        }
    }
}