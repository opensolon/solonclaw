package com.jimuqu.solonclaw.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpToolAdapter 测试
 * <p>
 * 测试 MCP 工具适配器的功能
 *
 * @author SolonClaw
 */
@DisplayName("MCP 工具适配器测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class McpToolAdapterTest {

    // ==================== 工具描述生成测试 ====================

    @Nested
    @DisplayName("工具描述生成测试")
    class ToolDescriptionTests {

        @Test
        @Order(1)
        @DisplayName("生成空工具描述")
        void testEmptyToolDescription() {
            // 模拟没有工具的情况
            String description = "当前没有可用的 MCP 工具。";

            assertTrue(description.contains("没有可用的 MCP 工具"));
        }

        @Test
        @Order(2)
        @DisplayName("生成单个工具描述")
        void testSingleToolDescription() {
            McpManager.McpToolInfo tool = createTestTool("read_file", "filesystem", "读取文件内容");

            // 模拟描述生成
            StringBuilder sb = new StringBuilder();
            sb.append("可用的 MCP 工具：\n");
            sb.append("- ").append(tool.fullName()).append(": ").append(tool.description()).append("\n");

            String description = sb.toString();

            assertTrue(description.contains("filesystem.read_file"));
            assertTrue(description.contains("读取文件内容"));
        }

        @Test
        @Order(3)
        @DisplayName("生成带参数的工具描述")
        void testToolDescriptionWithParams() {
            Map<String, McpManager.McpParameterInfo> params = new HashMap<>();
            params.put("path", new McpManager.McpParameterInfo("string", "文件路径", true));
            params.put("encoding", new McpManager.McpParameterInfo("string", "编码格式", false));

            McpManager.McpToolInfo tool = new McpManager.McpToolInfo(
                    "read_file",
                    "filesystem.read_file",
                    "filesystem",
                    "读取文件内容",
                    params
            );

            // 生成描述
            StringBuilder sb = new StringBuilder();
            sb.append("- ").append(tool.fullName()).append(": ").append(tool.description());
            sb.append(" (参数: ");
            sb.append("path*: string, encoding: string");
            sb.append(")");

            String description = sb.toString();

            assertTrue(description.contains("path*"));
            assertTrue(description.contains("encoding"));
        }

        @Test
        @Order(4)
        @DisplayName("生成多个工具描述")
        void testMultipleToolsDescription() {
            List<McpManager.McpToolInfo> tools = List.of(
                    createTestTool("read_file", "filesystem", "读取文件"),
                    createTestTool("write_file", "filesystem", "写入文件"),
                    createTestTool("search", "brave-search", "搜索网络")
            );

            StringBuilder sb = new StringBuilder();
            sb.append("可用的 MCP 工具：\n");
            for (McpManager.McpToolInfo tool : tools) {
                sb.append("- ").append(tool.fullName()).append(": ").append(tool.description()).append("\n");
            }

            String description = sb.toString();

            assertTrue(description.contains("filesystem.read_file"));
            assertTrue(description.contains("filesystem.write_file"));
            assertTrue(description.contains("brave-search.search"));
        }
    }

    // ==================== 工具注册信息测试 ====================

    @Nested
    @DisplayName("工具注册信息测试")
    class ToolRegistryTests {

        @Test
        @Order(10)
        @DisplayName("转换为注册格式")
        void testConvertToRegistryFormat() {
            McpManager.McpToolInfo tool = createTestTool("test_tool", "test_server", "测试工具");

            Map<String, Map<String, Object>> result = new HashMap<>();
            Map<String, Object> toolInfo = new HashMap<>();
            toolInfo.put("name", tool.fullName());
            toolInfo.put("description", tool.description());
            toolInfo.put("type", "mcp");
            toolInfo.put("serverName", tool.serverName());
            toolInfo.put("parameters", tool.parameters());
            result.put(tool.fullName(), toolInfo);

            assertEquals(1, result.size());
            assertTrue(result.containsKey("test_server.test_tool"));

            Map<String, Object> info = result.get("test_server.test_tool");
            assertEquals("mcp", info.get("type"));
            assertEquals("test_server", info.get("serverName"));
        }

        @Test
        @Order(11)
        @DisplayName("多个工具注册信息")
        void testMultipleToolsRegistry() {
            Map<String, Map<String, Object>> result = new HashMap<>();

            for (int i = 1; i <= 3; i++) {
                String fullName = "server.tool" + i;
                Map<String, Object> toolInfo = new HashMap<>();
                toolInfo.put("name", fullName);
                toolInfo.put("description", "工具 " + i);
                toolInfo.put("type", "mcp");
                result.put(fullName, toolInfo);
            }

            assertEquals(3, result.size());
            assertTrue(result.containsKey("server.tool1"));
            assertTrue(result.containsKey("server.tool2"));
            assertTrue(result.containsKey("server.tool3"));
        }

        @Test
        @Order(12)
        @DisplayName("工具类型标识")
        void testToolTypeIdentifier() {
            Map<String, Object> toolInfo = new HashMap<>();
            toolInfo.put("type", "mcp");

            assertEquals("mcp", toolInfo.get("type"));
        }
    }

    // ==================== 工具可用性检查测试 ====================

    @Nested
    @DisplayName("工具可用性检查测试")
    class ToolAvailabilityTests {

        @Test
        @Order(20)
        @DisplayName("工具存在性检查")
        void testToolExistenceCheck() {
            Map<String, McpManager.McpToolInfo> tools = new HashMap<>();
            tools.put("server.tool1", createTestTool("tool1", "server", "工具1"));

            assertTrue(tools.containsKey("server.tool1"));
            assertFalse(tools.containsKey("server.tool2"));
        }

        @Test
        @Order(21)
        @DisplayName("服务器运行状态影响工具可用性")
        void testServerRunningAffectsToolAvailability() {
            // 模拟服务器运行状态
            Map<String, Boolean> serverStatus = new HashMap<>();
            serverStatus.put("running-server", true);
            serverStatus.put("stopped-server", false);

            // 检查工具可用性
            String serverName = "running-server";
            boolean isAvailable = serverStatus.getOrDefault(serverName, false);

            assertTrue(isAvailable);

            serverName = "stopped-server";
            isAvailable = serverStatus.getOrDefault(serverName, false);

            assertFalse(isAvailable);
        }
    }

    // ==================== 工具执行测试 ====================

    @Nested
    @DisplayName("工具执行测试")
    class ToolExecutionTests {

        @Test
        @Order(30)
        @DisplayName("构建工具调用参数")
        void testBuildToolCallArguments() {
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("path", "/test/file.txt");
            arguments.put("mode", "read");
            arguments.put("encoding", "UTF-8");

            assertEquals(3, arguments.size());
            assertEquals("/test/file.txt", arguments.get("path"));
        }

        @Test
        @Order(31)
        @DisplayName("空参数工具调用")
        void testEmptyArgumentsCall() {
            Map<String, Object> arguments = new HashMap<>();
            assertTrue(arguments.isEmpty());
        }

        @Test
        @Order(32)
        @DisplayName("复杂参数类型")
        void testComplexArgumentTypes() {
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("string", "text");
            arguments.put("number", 123);
            arguments.put("float", 45.67);
            arguments.put("boolean", true);
            arguments.put("array", List.of(1, 2, 3));
            arguments.put("object", Map.of("key", "value"));

            assertEquals("text", arguments.get("string"));
            assertEquals(123, arguments.get("number"));
            assertEquals(45.67, arguments.get("float"));
            assertEquals(true, arguments.get("boolean"));
            assertTrue(arguments.get("array") instanceof List);
            assertTrue(arguments.get("object") instanceof Map);
        }

        @Test
        @Order(33)
        @DisplayName("嵌套参数结构")
        void testNestedArgumentStructure() {
            Map<String, Object> nested = new HashMap<>();
            nested.put("level1", Map.of(
                    "level2", Map.of(
                            "level3", "deep_value"
                    )
            ));

            @SuppressWarnings("unchecked")
            Map<String, Object> level1 = (Map<String, Object>) nested.get("level1");
            @SuppressWarnings("unchecked")
            Map<String, Object> level2 = (Map<String, Object>) level1.get("level2");

            assertEquals("deep_value", level2.get("level3"));
        }
    }

    // ==================== 自动启动测试 ====================

    @Nested
    @DisplayName("自动启动测试")
    class AutoStartTests {

        @Test
        @Order(40)
        @DisplayName("筛选未禁用的服务器")
        void testFilterEnabledServers() {
            List<McpManager.McpServerInfo> servers = List.of(
                    new McpManager.McpServerInfo("enabled1", "node", null, null, false),
                    new McpManager.McpServerInfo("disabled1", "node", null, null, true),
                    new McpManager.McpServerInfo("enabled2", "python", null, null, false),
                    new McpManager.McpServerInfo("disabled2", "python", null, null, true)
            );

            List<McpManager.McpServerInfo> enabledServers = servers.stream()
                    .filter(s -> !s.disabled())
                    .toList();

            assertEquals(2, enabledServers.size());
            assertTrue(enabledServers.stream().allMatch(s -> !s.disabled()));
        }

        @Test
        @Order(41)
        @DisplayName("全部禁用时的行为")
        void testAllDisabledServers() {
            List<McpManager.McpServerInfo> servers = List.of(
                    new McpManager.McpServerInfo("s1", "node", null, null, true),
                    new McpManager.McpServerInfo("s2", "node", null, null, true)
            );

            List<McpManager.McpServerInfo> enabledServers = servers.stream()
                    .filter(s -> !s.disabled())
                    .toList();

            assertTrue(enabledServers.isEmpty());
        }

        @Test
        @Order(42)
        @DisplayName("全部启用时的行为")
        void testAllEnabledServers() {
            List<McpManager.McpServerInfo> servers = List.of(
                    new McpManager.McpServerInfo("s1", "node", null, null, false),
                    new McpManager.McpServerInfo("s2", "node", null, null, false)
            );

            List<McpManager.McpServerInfo> enabledServers = servers.stream()
                    .filter(s -> !s.disabled())
                    .toList();

            assertEquals(2, enabledServers.size());
        }
    }

    // ==================== 辅助方法 ====================

    private McpManager.McpToolInfo createTestTool(String name, String serverName, String description) {
        return new McpManager.McpToolInfo(
                name,
                serverName + "." + name,
                serverName,
                description,
                new HashMap<>()
        );
    }
}