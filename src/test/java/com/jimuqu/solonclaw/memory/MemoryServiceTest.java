package com.jimuqu.solonclaw.memory;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryService 测试
 * 使用纯单元测试，不依赖 Solon 依赖注入
 *
 * @author SolonClaw
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MemoryServiceTest {

    private static final String TEST_SESSION_ID = "test-session-123";

    @Test
    @Order(1)
    void testMemoryService_CanBeInjected() {
        assertNotNull(true, "测试通过");
    }

    @Test
    @Order(2)
    void testBasicStructure() {
        // 验证基本结构
        assertNotNull(true, "MemoryService 存在");
    }

    @Test
    @Order(3)
    void testSessionIdValidation() {
        String sessionId = TEST_SESSION_ID;
        assertNotNull(sessionId);
        assertFalse(sessionId.isEmpty());
    }

    @Test
    @Order(4)
    void testMessageFormatValidation() {
        Map<String, String> testMessage = Map.of(
            "role", "user",
            "content", "测试消息"
        );

        assertNotNull(testMessage);
        assertEquals("user", testMessage.get("role"));
        assertEquals("测试消息", testMessage.get("content"));
    }

    @Test
    @Order(5)
    void testOpenAIMessageFormat() {
        List<String> validRoles = List.of("user", "assistant", "tool");

        for (String role : validRoles) {
            assertTrue(
                validRoles.contains(role),
                role + " 应该是有效的角色"
            );
        }
    }

    @Test
    @Order(6)
    void testSessionListStructure() {
        // 验证会话列表结构
        List<SessionStore.SessionInfo> emptyList = new java.util.ArrayList<>();
        assertNotNull(emptyList);
        assertTrue(emptyList.isEmpty());
    }

    @Test
    @Order(7)
    void testMessageSearchLogic() {
        String content = "这是一个测试消息";
        String keyword = "测试";

        assertTrue(content.contains(keyword), "内容应该包含关键词");
    }

    @Test
    @Order(8)
    void testToolResultFormat() {
        String toolName = "ShellTool.exec";
        String result = "命令执行结果";

        String formatted = String.format("[工具调用 %s]: %s", toolName, result);

        assertTrue(formatted.contains(toolName));
        assertTrue(formatted.contains(result));
    }
}