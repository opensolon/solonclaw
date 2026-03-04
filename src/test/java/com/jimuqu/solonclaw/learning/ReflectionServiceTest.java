package com.jimuqu.solonclaw.learning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.noear.solon.annotation.Inject;
import org.noear.solon.test.SolonTest;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReflectionService JSON 解析测试
 * <p>
 * 验证 parseJsonResponse 方法能正确解析 AI 返回的 JSON 响应
 */
@SolonTest
public class ReflectionServiceTest {

    @Inject
    private ReflectionService reflectionService;

    /**
     * 测试解析有效的定时反省响应
     */
    @Test
    public void testParseScheduledReflectionResponse() throws Exception {
        String jsonResponse = """
            {
                "summary": "测试总结",
                "successes": ["成功点1", "成功点2"],
                "failures": ["失败点1"],
                "improvements": ["改进建议1", "改进建议2"],
                "neededSkills": [
                    {"name": "测试技能", "description": "技能描述", "priority": 5}
                ]
            }
            """;

        Method method = ReflectionService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(reflectionService, jsonResponse);

        assertNotNull(result);
        assertEquals("测试总结", result.get("summary"));
        assertFalse(result.isEmpty());
    }

    /**
     * 测试解析有效的错误反省响应
     */
    @Test
    public void testParseErrorReflectionResponse() throws Exception {
        String jsonResponse = """
            {
                "rootCause": "根本原因",
                "solution": "解决方案",
                "prevention": "预防措施",
                "neededSkill": {
                    "name": "错误处理技能",
                    "description": "处理此类错误",
                    "priority": 8
                }
            }
            """;

        Method method = ReflectionService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(reflectionService, jsonResponse);

        assertNotNull(result);
        assertEquals("根本原因", result.get("rootCause"));
        assertEquals("解决方案", result.get("solution"));
    }

    /**
     * 测试解析空响应
     */
    @Test
    public void testParseEmptyResponse() throws Exception {
        Method method = ReflectionService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(reflectionService, "");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * 测试解析 null 响应
     */
    @Test
    public void testParseNullResponse() throws Exception {
        Method method = ReflectionService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(reflectionService, (String) null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * 测试解析无效 JSON
     */
    @Test
    public void testParseInvalidJson() throws Exception {
        String invalidJson = "这不是有效的 JSON {invalid";

        Method method = ReflectionService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(reflectionService, invalidJson);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * 测试解析部分缺失字段的响应
     */
    @Test
    public void testParsePartialResponse() throws Exception {
        String jsonResponse = """
            {
                "summary": "只有总结"
            }
            """;

        Method method = ReflectionService.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(reflectionService, jsonResponse);

        assertNotNull(result);
        assertEquals("只有总结", result.get("summary"));
        assertFalse(result.containsKey("successes"));
    }
}
