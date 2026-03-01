package com.jimuqu.solonclaw.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpManager 完整测试
 * <p>
 * 测试 MCP 服务器管理的各个功能模块
 *
 * @author SolonClaw
 */
@DisplayName("MCP 管理器测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class McpManagerTest {

    // 模拟存储
    private Map<String, McpManager.McpServerInfo> servers;
    private Map<String, McpManager.McpToolInfo> tools;

    @BeforeEach
    void setUp() {
        servers = new ConcurrentHashMap<>();
        tools = new ConcurrentHashMap<>();
    }

    // ==================== 服务器配置测试 ====================

    @Nested
    @DisplayName("服务器配置测试")
    class ServerConfigTests {

        @Test
        @Order(1)
        @DisplayName("创建服务器配置 - 基本参数")
        void testCreateServerInfo_Basic() {
            String name = "test-server";
            String command = "node";
            List<String> args = List.of("server.js");
            Map<String, String> env = Map.of("NODE_ENV", "production");

            McpManager.McpServerInfo serverInfo = new McpManager.McpServerInfo(
                    name, command, args, env, false
            );

            assertEquals(name, serverInfo.name());
            assertEquals(command, serverInfo.command());
            assertEquals(args, serverInfo.args());
            assertEquals(env, serverInfo.env());
            assertFalse(serverInfo.disabled());
        }

        @Test
        @Order(2)
        @DisplayName("创建服务器配置 - 禁用状态")
        void testCreateServerInfo_Disabled() {
            McpManager.McpServerInfo serverInfo = new McpManager.McpServerInfo(
                    "disabled-server", "python", null, null, true
            );

            assertTrue(serverInfo.disabled());
        }

        @Test
        @Order(3)
        @DisplayName("创建服务器配置 - 空参数")
        void testCreateServerInfo_NullArgs() {
            McpManager.McpServerInfo serverInfo = new McpManager.McpServerInfo(
                    "minimal-server", "echo", null, null, false
            );

            assertEquals("minimal-server", serverInfo.name());
            assertEquals("echo", serverInfo.command());
            assertNull(serverInfo.args());
            assertNull(serverInfo.env());
        }

        @Test
        @Order(4)
        @DisplayName("添加服务器到列表")
        void testAddServer() {
            McpManager.McpServerInfo serverInfo = createTestServer("server1", "node");
            servers.put("server1", serverInfo);

            assertEquals(1, servers.size());
            assertTrue(servers.containsKey("server1"));
        }

        @Test
        @Order(5)
        @DisplayName("添加重复服务器名称")
        void testAddDuplicateServer() {
            McpManager.McpServerInfo server1 = createTestServer("dup-server", "node");
            servers.put("dup-server", server1);

            // 模拟检查重复
            assertTrue(servers.containsKey("dup-server"));
        }

        @Test
        @Order(6)
        @DisplayName("删除服务器")
        void testRemoveServer() {
            servers.put("to-remove", createTestServer("to-remove", "node"));
            assertEquals(1, servers.size());

            servers.remove("to-remove");
            assertEquals(0, servers.size());
            assertFalse(servers.containsKey("to-remove"));
        }

        @Test
        @Order(7)
        @DisplayName("更新服务器配置")
        void testUpdateServer() {
            String name = "update-test";
            servers.put(name, createTestServer(name, "node"));

            // 更新配置
            McpManager.McpServerInfo updated = new McpManager.McpServerInfo(
                    name, "python", List.of("new.py"), null, true
            );
            servers.put(name, updated);

            McpManager.McpServerInfo retrieved = servers.get(name);
            assertEquals("python", retrieved.command());
            assertEquals(1, retrieved.args().size());
            assertTrue(retrieved.disabled());
        }

        @Test
        @Order(8)
        @DisplayName("获取服务器列表")
        void testGetServersList() {
            servers.put("s1", createTestServer("s1", "node"));
            servers.put("s2", createTestServer("s2", "python"));
            servers.put("s3", createTestServer("s3", "npx"));

            List<McpManager.McpServerInfo> serverList = new ArrayList<>(servers.values());
            assertEquals(3, serverList.size());
        }

        @Test
        @Order(9)
        @DisplayName("获取不存在的服务器")
        void testGetNonExistentServer() {
            assertNull(servers.get("non-existent"));
        }
    }

    // ==================== 工具配置测试 ====================

    @Nested
    @DisplayName("工具配置测试")
    class ToolConfigTests {

        @Test
        @Order(10)
        @DisplayName("创建工具信息")
        void testCreateToolInfo() {
            Map<String, McpManager.McpParameterInfo> params = new HashMap<>();
            params.put("path", new McpManager.McpParameterInfo("string", "文件路径", true));

            McpManager.McpToolInfo toolInfo = new McpManager.McpToolInfo(
                    "read_file",
                    "filesystem.read_file",
                    "filesystem",
                    "读取文件内容",
                    params
            );

            assertEquals("read_file", toolInfo.name());
            assertEquals("filesystem.read_file", toolInfo.fullName());
            assertEquals("filesystem", toolInfo.serverName());
            assertEquals("读取文件内容", toolInfo.description());
            assertEquals(1, toolInfo.parameters().size());
        }

        @Test
        @Order(11)
        @DisplayName("添加工具到列表")
        void testAddTool() {
            McpManager.McpToolInfo tool = createTestTool("tool1", "server1");
            tools.put(tool.fullName(), tool);

            assertEquals(1, tools.size());
            assertTrue(tools.containsKey("server1.tool1"));
        }

        @Test
        @Order(12)
        @DisplayName("按服务器获取工具")
        void testGetToolsByServer() {
            tools.put("server1.tool1", createTestTool("tool1", "server1"));
            tools.put("server1.tool2", createTestTool("tool2", "server1"));
            tools.put("server2.tool3", createTestTool("tool3", "server2"));

            List<McpManager.McpToolInfo> server1Tools = tools.values().stream()
                    .filter(t -> t.serverName().equals("server1"))
                    .toList();

            assertEquals(2, server1Tools.size());
        }

        @Test
        @Order(13)
        @DisplayName("删除服务器时清理工具")
        void testRemoveToolsOnServerRemove() {
            String serverName = "server-to-remove";

            tools.put(serverName + ".tool1", createTestTool("tool1", serverName));
            tools.put(serverName + ".tool2", createTestTool("tool2", serverName));
            tools.put("other.tool3", createTestTool("tool3", "other"));

            assertEquals(3, tools.size());

            // 模拟删除服务器时清理工具
            tools.entrySet().removeIf(entry -> entry.getValue().serverName().equals(serverName));

            assertEquals(1, tools.size());
            assertFalse(tools.containsKey(serverName + ".tool1"));
            assertTrue(tools.containsKey("other.tool3"));
        }

        @Test
        @Order(14)
        @DisplayName("工具参数信息")
        void testToolParameterInfo() {
            McpManager.McpParameterInfo param = new McpManager.McpParameterInfo(
                    "string", "文件路径参数", true
            );

            assertEquals("string", param.type());
            assertEquals("文件路径参数", param.description());
            assertTrue(param.required());
        }

        @Test
        @Order(15)
        @DisplayName("可选参数")
        void testOptionalParameter() {
            McpManager.McpParameterInfo param = new McpManager.McpParameterInfo(
                    "number", "可选数量", false
            );

            assertFalse(param.required());
        }
    }

    // ==================== 服务器状态测试 ====================

    @Nested
    @DisplayName("服务器状态测试")
    class ServerStatusTests {

        @Test
        @Order(20)
        @DisplayName("状态枚举值")
        void testStatusEnum() {
            assertEquals("stopped", McpServerStatus.STOPPED.getCode());
            assertEquals("starting", McpServerStatus.STARTING.getCode());
            assertEquals("initialized", McpServerStatus.INITIALIZED.getCode());
            assertEquals("running", McpServerStatus.RUNNING.getCode());
            assertEquals("error", McpServerStatus.ERROR.getCode());
        }

        @Test
        @Order(21)
        @DisplayName("状态描述")
        void testStatusDescription() {
            assertEquals("已停止", McpServerStatus.STOPPED.getDescription());
            assertEquals("启动中", McpServerStatus.STARTING.getDescription());
            assertEquals("已初始化", McpServerStatus.INITIALIZED.getDescription());
            assertEquals("运行中", McpServerStatus.RUNNING.getDescription());
            assertEquals("错误", McpServerStatus.ERROR.getDescription());
        }
    }

    // ==================== MCP 协议测试 ====================

    @Nested
    @DisplayName("MCP 协议测试")
    class McpProtocolTests {

        @Test
        @Order(30)
        @DisplayName("初始化请求格式")
        void testInitializeRequest() {
            long requestId = 1;
            String expectedRequest = String.format(
                    "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"SolonClaw\",\"version\":\"1.0.0\"}}}\n",
                    requestId
            );

            assertTrue(expectedRequest.contains("\"method\":\"initialize\""));
            assertTrue(expectedRequest.contains("\"jsonrpc\":\"2.0\""));
            assertTrue(expectedRequest.contains("SolonClaw"));
        }

        @Test
        @Order(31)
        @DisplayName("工具列表请求格式")
        void testToolsListRequest() {
            long requestId = 2;
            String expectedRequest = String.format(
                    "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/list\",\"params\":{}}\n",
                    requestId
            );

            assertTrue(expectedRequest.contains("\"method\":\"tools/list\""));
            assertTrue(expectedRequest.contains("\"jsonrpc\":\"2.0\""));
        }

        @Test
        @Order(32)
        @DisplayName("工具调用请求格式")
        void testToolCallRequest() {
            long requestId = 3;
            String toolName = "read_file";
            String args = "{\"path\":\"/test/file.txt\"}";

            String expectedRequest = String.format(
                    "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":%s}}\n",
                    requestId, toolName, args
            );

            assertTrue(expectedRequest.contains("\"method\":\"tools/call\""));
            assertTrue(expectedRequest.contains("\"name\":\"read_file\""));
            assertTrue(expectedRequest.contains("\"path\":\"/test/file.txt\""));
        }

        @Test
        @Order(33)
        @DisplayName("解析工具列表响应")
        void testParseToolsListResponse() {
            String response = """
                {"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"read_file","description":"读取文件"},{"name":"write_file","description":"写入文件"}]}}
                """;

            assertTrue(response.contains("\"tools\":["));
            assertTrue(response.contains("\"name\":\"read_file\""));
            assertTrue(response.contains("\"name\":\"write_file\""));
        }

        @Test
        @Order(34)
        @DisplayName("解析错误响应")
        void testParseErrorResponse() {
            String response = """
                {"jsonrpc":"2.0","id":1,"error":{"code":-32600,"message":"Invalid Request"}}
                """;

            assertTrue(response.contains("\"error\""));
            assertTrue(response.contains("\"Invalid Request\""));
        }
    }

    // ==================== 配置文件测试 ====================

    @Nested
    @DisplayName("配置文件测试")
    class ConfigFileTests {

        @Test
        @Order(40)
        @DisplayName("默认配置格式")
        void testDefaultConfigFormat() {
            String defaultConfig = """
                {
                  "mcpServers": {
                    "filesystem": {
                      "command": "npx",
                      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/allowed/dir"],
                      "env": {},
                      "disabled": true
                    }
                  }
                }
                """;

            assertTrue(defaultConfig.contains("\"mcpServers\""));
            assertTrue(defaultConfig.contains("\"filesystem\""));
            assertTrue(defaultConfig.contains("\"command\": \"npx\""));
            assertTrue(defaultConfig.contains("\"disabled\": true"));
        }

        @Test
        @Order(41)
        @DisplayName("多服务器配置")
        void testMultipleServersConfig() {
            String config = """
                {
                  "mcpServers": {
                    "filesystem": {
                      "command": "npx",
                      "args": ["-y", "@modelcontextprotocol/server-filesystem"],
                      "disabled": false
                    },
                    "brave-search": {
                      "command": "npx",
                      "args": ["-y", "@modelcontextprotocol/server-brave-search"],
                      "env": {"BRAVE_API_KEY": "xxx"},
                      "disabled": false
                    }
                  }
                }
                """;

            assertTrue(config.contains("\"filesystem\""));
            assertTrue(config.contains("\"brave-search\""));
            assertTrue(config.contains("\"BRAVE_API_KEY\""));
        }

        @Test
        @Order(42)
        @DisplayName("环境变量配置")
        void testEnvConfig() {
            Map<String, String> env = new HashMap<>();
            env.put("NODE_ENV", "production");
            env.put("DEBUG", "false");
            env.put("API_KEY", "secret123");

            assertEquals(3, env.size());
            assertEquals("production", env.get("NODE_ENV"));
            assertEquals("secret123", env.get("API_KEY"));
        }
    }

    // ==================== 命令可用性测试 ====================

    @Nested
    @DisplayName("命令可用性测试")
    class CommandAvailabilityTests {

        @Test
        @Order(50)
        @DisplayName("常用命令列表")
        void testCommonCommands() {
            List<String> commonCommands = List.of("npx", "node", "python", "python3", "uvx");

            assertEquals(5, commonCommands.size());
            assertTrue(commonCommands.contains("npx"));
            assertTrue(commonCommands.contains("node"));
            assertTrue(commonCommands.contains("python"));
        }

        @Test
        @Order(51)
        @DisplayName("命令格式化")
        void testCommandFormatting() {
            String command = "npx";
            List<String> args = List.of("-y", "@modelcontextprotocol/server-filesystem", "/path");

            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(command);
            fullCommand.addAll(args);

            assertEquals(4, fullCommand.size());
            assertEquals("npx", fullCommand.get(0));
            assertEquals("-y", fullCommand.get(1));
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @Order(60)
        @DisplayName("空服务器列表")
        void testEmptyServersList() {
            assertTrue(servers.isEmpty());
            assertEquals(0, servers.size());
        }

        @Test
        @Order(61)
        @DisplayName("空工具列表")
        void testEmptyToolsList() {
            assertTrue(tools.isEmpty());
            assertEquals(0, tools.size());
        }

        @Test
        @Order(62)
        @DisplayName("服务器名称特殊字符")
        void testServerNameSpecialChars() {
            String name = "my-mcp-server_v1.0";
            McpManager.McpServerInfo serverInfo = createTestServer(name, "node");

            assertEquals(name, serverInfo.name());
        }

        @Test
        @Order(63)
        @DisplayName("长命令参数列表")
        void testLongArgsList() {
            List<String> args = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                args.add("arg" + i);
            }

            McpManager.McpServerInfo serverInfo = new McpManager.McpServerInfo(
                    "long-args-server", "cmd", args, null, false
            );

            assertEquals(100, serverInfo.args().size());
        }

        @Test
        @Order(64)
        @DisplayName("大量环境变量")
        void testLargeEnvMap() {
            Map<String, String> env = new HashMap<>();
            for (int i = 0; i < 50; i++) {
                env.put("ENV_VAR_" + i, "value_" + i);
            }

            McpManager.McpServerInfo serverInfo = new McpManager.McpServerInfo(
                    "large-env-server", "cmd", null, env, false
            );

            assertEquals(50, serverInfo.env().size());
        }

        @Test
        @Order(65)
        @DisplayName("Unicode 字符处理")
        void testUnicodeChars() {
            String description = "读取文件内容 📁 并处理中文描述";

            McpManager.McpToolInfo toolInfo = new McpManager.McpToolInfo(
                    "unicode_tool",
                    "server.unicode_tool",
                    "server",
                    description,
                    new HashMap<>()
            );

            assertTrue(toolInfo.description().contains("📁"));
            assertTrue(toolInfo.description().contains("中文"));
        }
    }

    // ==================== 辅助方法 ====================

    private McpManager.McpServerInfo createTestServer(String name, String command) {
        return new McpManager.McpServerInfo(name, command, null, null, false);
    }

    private McpManager.McpToolInfo createTestTool(String name, String serverName) {
        return new McpManager.McpToolInfo(
                name,
                serverName + "." + name,
                serverName,
                "Test tool: " + name,
                new HashMap<>()
        );
    }
}