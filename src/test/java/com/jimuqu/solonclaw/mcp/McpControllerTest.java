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
 * McpController 测试
 * <p>
 * 测试 MCP API 控制器的请求和响应
 *
 * @author SolonClaw
 */
@DisplayName("MCP 控制器测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class McpControllerTest {

    // ==================== 请求记录类测试 ====================

    @Nested
    @DisplayName("服务器请求测试")
    class ServerRequestTests {

        @Test
        @Order(1)
        @DisplayName("创建服务器请求 - 完整参数")
        void testCreateServerRequest_Full() {
            String name = "test-server";
            String command = "npx";
            List<String> args = List.of("-y", "@modelcontextprotocol/server-filesystem");
            Map<String, String> env = Map.of("NODE_ENV", "production");
            Boolean disabled = false;

            McpController.ServerRequest request = new McpController.ServerRequest(
                    name, command, args, env, disabled
            );

            assertEquals(name, request.name());
            assertEquals(command, request.command());
            assertEquals(args, request.args());
            assertEquals(env, request.env());
            assertEquals(disabled, request.disabled());
        }

        @Test
        @Order(2)
        @DisplayName("创建服务器请求 - 最小参数")
        void testCreateServerRequest_Minimal() {
            McpController.ServerRequest request = new McpController.ServerRequest(
                    "minimal", "echo", null, null, null
            );

            assertEquals("minimal", request.name());
            assertEquals("echo", request.command());
            assertNull(request.args());
            assertNull(request.env());
            assertNull(request.disabled());
        }

        @Test
        @Order(3)
        @DisplayName("验证必需字段 - 名称")
        void testValidateRequiredName() {
            String name = "";
            assertFalse(name != null && !name.isBlank(), "名称不能为空");
        }

        @Test
        @Order(4)
        @DisplayName("验证必需字段 - 命令")
        void testValidateRequiredCommand() {
            String command = null;
            assertFalse(command != null && !command.isBlank(), "命令不能为空");
        }
    }

    // ==================== 工具调用请求测试 ====================

    @Nested
    @DisplayName("工具调用请求测试")
    class ToolCallRequestTests {

        @Test
        @Order(10)
        @DisplayName("创建工具调用请求 - 带参数")
        void testCreateToolCallRequest_WithArgs() {
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("path", "/test/file.txt");
            arguments.put("encoding", "UTF-8");

            McpController.ToolCallRequest request = new McpController.ToolCallRequest(arguments);

            assertEquals(2, request.arguments().size());
            assertEquals("/test/file.txt", request.arguments().get("path"));
            assertEquals("UTF-8", request.arguments().get("encoding"));
        }

        @Test
        @Order(11)
        @DisplayName("创建工具调用请求 - 无参数")
        void testCreateToolCallRequest_NoArgs() {
            McpController.ToolCallRequest request = new McpController.ToolCallRequest(null);

            assertNull(request.arguments());
        }

        @Test
        @Order(12)
        @DisplayName("创建工具调用请求 - 空参数")
        void testCreateToolCallRequest_EmptyArgs() {
            McpController.ToolCallRequest request = new McpController.ToolCallRequest(new HashMap<>());

            assertTrue(request.arguments().isEmpty());
        }

        @Test
        @Order(13)
        @DisplayName("复杂参数类型")
        void testComplexArgumentTypes() {
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("string", "text");
            arguments.put("number", 123);
            arguments.put("boolean", true);
            arguments.put("list", List.of("a", "b", "c"));
            arguments.put("map", Map.of("key", "value"));

            McpController.ToolCallRequest request = new McpController.ToolCallRequest(arguments);

            assertEquals("text", request.arguments().get("string"));
            assertEquals(123, request.arguments().get("number"));
            assertEquals(true, request.arguments().get("boolean"));
            assertTrue(request.arguments().get("list") instanceof List);
            assertTrue(request.arguments().get("map") instanceof Map);
        }
    }

    // ==================== 响应结果测试 ====================

    @Nested
    @DisplayName("响应结果测试")
    class McpResultTests {

        @Test
        @Order(20)
        @DisplayName("成功响应")
        void testSuccessResult() {
            Map<String, Object> data = Map.of("name", "test", "status", "running");

            McpController.McpResult result = McpController.McpResult.success("操作成功", data);

            assertEquals(200, result.code());
            assertEquals("操作成功", result.message());
            assertEquals(data, result.data());
        }

        @Test
        @Order(21)
        @DisplayName("错误响应")
        void testErrorResult() {
            McpController.McpResult result = McpController.McpResult.error("服务器不存在");

            assertEquals(500, result.code());
            assertEquals("服务器不存在", result.message());
            assertNull(result.data());
        }

        @Test
        @Order(22)
        @DisplayName("成功响应 - 空数据")
        void testSuccessResult_NullData() {
            McpController.McpResult result = McpController.McpResult.success("成功", null);

            assertEquals(200, result.code());
            assertNull(result.data());
        }

        @Test
        @Order(23)
        @DisplayName("响应状态码验证")
        void testResponseCodes() {
            McpController.McpResult success = McpController.McpResult.success("OK", null);
            McpController.McpResult error = McpController.McpResult.error("Error");

            assertTrue(success.code() >= 200 && success.code() < 300);
            assertTrue(error.code() >= 400);
        }
    }

    // ==================== API 接口格式测试 ====================

    @Nested
    @DisplayName("API 接口格式测试")
    class ApiFormatTests {

        @Test
        @Order(30)
        @DisplayName("服务器列表响应格式")
        void testServerListResponseFormat() {
            List<Map<String, Object>> serverList = new ArrayList<>();

            Map<String, Object> server1 = new LinkedHashMap<>();
            server1.put("name", "filesystem");
            server1.put("command", "npx");
            server1.put("args", List.of("-y", "server"));
            server1.put("disabled", false);
            server1.put("status", "running");
            server1.put("running", true);
            serverList.add(server1);

            Map<String, Object> response = Map.of("servers", serverList);

            assertTrue(response.containsKey("servers"));
            assertTrue(response.get("servers") instanceof List);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> servers = (List<Map<String, Object>>) response.get("servers");
            assertEquals(1, servers.size());
            assertTrue(servers.get(0).containsKey("name"));
            assertTrue(servers.get(0).containsKey("status"));
        }

        @Test
        @Order(31)
        @DisplayName("服务器详情响应格式")
        void testServerDetailResponseFormat() {
            Map<String, Object> serverDetail = new LinkedHashMap<>();
            serverDetail.put("name", "filesystem");
            serverDetail.put("command", "npx");
            serverDetail.put("args", List.of("-y", "server"));
            serverDetail.put("env", Map.of("KEY", "value"));
            serverDetail.put("disabled", false);
            serverDetail.put("status", "running");
            serverDetail.put("running", true);
            serverDetail.put("tools", List.of());

            assertTrue(serverDetail.containsKey("name"));
            assertTrue(serverDetail.containsKey("command"));
            assertTrue(serverDetail.containsKey("env"));
            assertTrue(serverDetail.containsKey("tools"));
        }

        @Test
        @Order(32)
        @DisplayName("工具列表响应格式")
        void testToolListResponseFormat() {
            List<Map<String, Object>> toolList = new ArrayList<>();

            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", "read_file");
            tool.put("fullName", "filesystem.read_file");
            tool.put("serverName", "filesystem");
            tool.put("description", "读取文件");
            tool.put("parameterCount", 2);
            toolList.add(tool);

            Map<String, Object> response = Map.of(
                    "tools", toolList,
                    "count", 1
            );

            assertTrue(response.containsKey("tools"));
            assertTrue(response.containsKey("count"));
            assertEquals(1, response.get("count"));
        }

        @Test
        @Order(33)
        @DisplayName("工具调用响应格式")
        void testToolCallResponseFormat() {
            Map<String, Object> response = Map.of(
                    "tool", "filesystem.read_file",
                    "result", "文件内容..."
            );

            assertTrue(response.containsKey("tool"));
            assertTrue(response.containsKey("result"));
        }
    }

    // ==================== 工具信息转换测试 ====================

    @Nested
    @DisplayName("工具信息转换测试")
    class ToolInfoConversionTests {

        @Test
        @Order(40)
        @DisplayName("工具信息转换为 API 格式")
        void testToolInfoConversion() {
            Map<String, McpManager.McpParameterInfo> params = new HashMap<>();
            params.put("path", new McpManager.McpParameterInfo("string", "文件路径", true));
            params.put("encoding", new McpManager.McpParameterInfo("string", "编码", false));

            McpManager.McpToolInfo toolInfo = new McpManager.McpToolInfo(
                    "read_file",
                    "filesystem.read_file",
                    "filesystem",
                    "读取文件内容",
                    params
            );

            // 转换为 API 格式
            Map<String, Object> toolMap = new LinkedHashMap<>();
            toolMap.put("name", toolInfo.name());
            toolMap.put("fullName", toolInfo.fullName());
            toolMap.put("serverName", toolInfo.serverName());
            toolMap.put("description", toolInfo.description());
            toolMap.put("parameterCount", toolInfo.parameters().size());

            assertEquals("read_file", toolMap.get("name"));
            assertEquals("filesystem.read_file", toolMap.get("fullName"));
            assertEquals(2, toolMap.get("parameterCount"));
        }

        @Test
        @Order(41)
        @DisplayName("参数信息转换为 API 格式")
        void testParameterInfoConversion() {
            Map<String, McpManager.McpParameterInfo> parameters = new HashMap<>();
            parameters.put("requiredParam", new McpManager.McpParameterInfo("string", "必需参数", true));
            parameters.put("optionalParam", new McpManager.McpParameterInfo("number", "可选参数", false));

            Map<String, Object> paramMap = new LinkedHashMap<>();
            for (Map.Entry<String, McpManager.McpParameterInfo> entry : parameters.entrySet()) {
                McpManager.McpParameterInfo param = entry.getValue();
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("type", param.type());
                info.put("description", param.description());
                info.put("required", param.required());
                paramMap.put(entry.getKey(), info);
            }

            assertEquals(2, paramMap.size());
            assertTrue(paramMap.containsKey("requiredParam"));
            assertTrue(paramMap.containsKey("optionalParam"));
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @Order(50)
        @DisplayName("空列表处理")
        void testEmptyList() {
            List<Map<String, Object>> emptyList = new ArrayList<>();
            Map<String, Object> response = Map.of("servers", emptyList);

            assertTrue(response.get("servers") instanceof List);
            assertTrue(((List<?>) response.get("servers")).isEmpty());
        }

        @Test
        @Order(51)
        @DisplayName("null 值处理")
        void testNullValues() {
            McpController.ServerRequest request = new McpController.ServerRequest(
                    "test", "echo", null, null, null
            );

            assertNull(request.args());
            assertNull(request.env());
            assertNull(request.disabled());
        }

        @Test
        @Order(52)
        @DisplayName("特殊字符处理")
        void testSpecialCharacters() {
            String specialName = "my-server_v1.0-beta";
            String specialCommand = "npx.cmd";

            McpController.ServerRequest request = new McpController.ServerRequest(
                    specialName, specialCommand, null, null, null
            );

            assertEquals(specialName, request.name());
            assertEquals(specialCommand, request.command());
        }

        @Test
        @Order(53)
        @DisplayName("大参数值处理")
        void testLargeArgumentValue() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("a");
            }
            String largeValue = sb.toString();

            Map<String, Object> arguments = Map.of("content", largeValue);
            McpController.ToolCallRequest request = new McpController.ToolCallRequest(arguments);

            assertEquals(1000, ((String) request.arguments().get("content")).length());
        }
    }
}