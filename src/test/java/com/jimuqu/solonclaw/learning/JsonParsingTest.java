package com.jimuqu.solonclaw.learning;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON 解析工具测试
 * <p>
 * 独立测试 AutoSkillService 和 ReflectionService 的 JSON 解析功能
 */
public class JsonParsingTest {

    /**
     * 测试 AutoSkillService 的 JSON 解析
     */
    @Test
    public void testAutoSkillServiceJsonParsing() throws Exception {
        // 创建服务实例
        AutoSkillService service = new AutoSkillService();
        
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
        Map<String, Object> result = (Map<String, Object>) method.invoke(service, jsonResponse);

        assertNotNull(result);
        assertEquals(true, result.get("shouldCreate"));
        assertEquals("这个技能很有用", result.get("reason"));
        assertTrue(result.containsKey("skillConfig"));
    }

    /**
     * 测试 ReflectionService 的 JSON 解析
     */
    @Test
    public void testReflectionServiceJsonParsing() throws Exception {
        // 创建服务实例
        ReflectionService service = new ReflectionService();
        
        String jsonResponse = """
            {
                "summary": "总体表现良好",
                "successes": ["成功点1", "成功点2"],
                "failures": ["失败点1"],
                "improvements": ["改进建议1", "改进建议2"]
            }
            """;

        Method method = ReflectionService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(service, jsonResponse);

        assertNotNull(result);
        assertEquals("总体表现良好", result.get("summary"));
        assertTrue(result.containsKey("successes"));
        assertTrue(result.containsKey("failures"));
        assertTrue(result.containsKey("improvements"));
    }

    /**
     * 测试空 JSON 响应
     */
    @Test
    public void testEmptyJsonResponse() throws Exception {
        AutoSkillService service = new AutoSkillService();
        
        Method method = AutoSkillService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(service, "");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * 测试 null JSON 响应
     */
    @Test
    public void testNullJsonResponse() throws Exception {
        ReflectionService service = new ReflectionService();
        
        Method method = ReflectionService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(service, (String) null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * 测试格式错误的 JSON
     */
    @Test
    public void testMalformedJson() throws Exception {
        AutoSkillService service = new AutoSkillService();
        
        String malformedJson = "{shouldCreate: true, reason: test}"; // 缺少引号

        Method method = AutoSkillService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(service, malformedJson);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
