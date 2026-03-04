package com.jimuqu.solonclaw.learning;

import org.junit.jupiter.api.Test;
import org.noear.solon.annotation.Inject;
import org.noear.solon.test.SolonTest;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AutoSkillService JSON 解析测试
 * <p>
 * 验证 parseJsonResponse 方法能正确解析 AI 返回的技能分析结果
 */
@SolonTest
public class AutoSkillServiceTest {

    @Inject
    private AutoSkillService autoSkillService;

    /**
     * 测试解析应该创建技能的响应
     */
    @Test
    public void testParseShouldCreateSkillResponse() throws Exception {
        String jsonResponse = """
            {
                "shouldCreate": true,
                "reason": "这个技能很有用",
                "skillConfig": {
                    "name": "测试技能",
                    "description": "这是一个测试技能",
                    "instruction": "技能指令",
                    "condition": "contains('测试')",
                    "tools": ["tool1", "tool2"],
                    "enabled": true
                }
            }
            """;

        Method method = AutoSkillService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(autoSkillService, jsonResponse);

        assertNotNull(result);
        assertEquals(true, result.get("shouldCreate"));
        assertEquals("这个技能很有用", result.get("reason"));
        assertTrue(result.containsKey("skillConfig"));
    }

    /**
     * 测试解析不应该创建技能的响应
     */
    @Test
    public void testParseShouldNotCreateSkillResponse() throws Exception {
        String jsonResponse = """
            {
                "shouldCreate": false,
                "reason": "技能已存在"
            }
            """;

        Method method = AutoSkillService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(autoSkillService, jsonResponse);

        assertNotNull(result);
        assertEquals(false, result.get("shouldCreate"));
        assertEquals("技能已存在", result.get("reason"));
    }

    /**
     * 测试解析包含嵌套工具列表的响应
     */
    @Test
    public void testParseSkillConfigWithTools() throws Exception {
        String jsonResponse = """
            {
                "shouldCreate": true,
                "reason": "测试",
                "skillConfig": {
                    "name": "复杂技能",
                    "description": "包含多个工具",
                    "instruction": "指令",
                    "condition": "contains('关键词')",
                    "tools": ["shell", "file", "http"],
                    "enabled": true
                }
            }
            """;

        Method method = AutoSkillService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(autoSkillService, jsonResponse);

        assertNotNull(result);

        @SuppressWarnings("unchecked")
        Map<String, Object> skillConfig = (Map<String, Object>) result.get("skillConfig");

        assertNotNull(skillConfig);
        assertEquals("复杂技能", skillConfig.get("name"));
        assertTrue(skillConfig.containsKey("tools"));
    }

    /**
     * 测试解析空响应
     */
    @Test
    public void testParseEmptyResponse() throws Exception {
        Method method = AutoSkillService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(autoSkillService, "");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * 测试解析 null 响应
     */
    @Test
    public void testParseNullResponse() throws Exception {
        Method method = AutoSkillService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(autoSkillService, (String) null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * 测试解析格式错误的 JSON
     */
    @Test
    public void testParseMalformedJson() throws Exception {
        String malformedJson = "{shouldCreate: true, reason: test}"; // 缺少引号

        Method method = AutoSkillService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(autoSkillService, malformedJson);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * 测试解析缺少 skillConfig 的响应
     */
    @Test
    public void testParseMissingSkillConfig() throws Exception {
        String jsonResponse = """
            {
                "shouldCreate": true,
                "reason": "应该创建"
            }
            """;

        Method method = AutoSkillService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(autoSkillService, jsonResponse);

        assertNotNull(result);
        assertEquals(true, result.get("shouldCreate"));
        assertFalse(result.containsKey("skillConfig"));
    }
}
