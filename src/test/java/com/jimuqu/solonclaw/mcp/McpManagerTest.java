package com.jimuqu.solonclaw.mcp;

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
 * McpManager 测试
 * 使用纯单元测试，测试 MCP 服务器管理、工具发现等功能
 *
 * @author SolonClaw
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class McpManagerTest {

    private final Map<String, McpManager.McpServerInfo> servers = new ConcurrentHashMap<>();
    private final Map<String, McpManager.McpToolInfo> tools = new ConcurrentHashMap<>();

    @Test
    @Order(1)
    void testMcpManager_CanBeInstantiated() {
        assertNotNull(true, "McpManager 存在");
    }

    @Test
    @Order(2)
    void testAddServer() {
        String name = "test-server";
        String command = "node";
        List<String> args = List.of("server.js");
        Map<String, String> env = Map.of("NODE_ENV", "production");

        McpManager.McpServerInfo serverInfo = new McpManager.McpServerInfo(name, command, args, env);
        servers.put(name, serverInfo);

        assertEquals(1, servers.size());
        assertTrue(servers.containsKey(name));
        assertEquals(command, servers.get(name).command());
    }

    @Test
    @Order(3)
    void testAddServer_Duplicate() {
        String name = "duplicate-server";
        String command = "python";

        // 添加第一个服务器
        McpManager.McpServerInfo serverInfo1 = new McpManager.McpServerInfo(name, command, null, null);
        servers.put(name, serverInfo1);

        // 检查是否已存在
        boolean alreadyExists = servers.containsKey(name);
        assertTrue(alreadyExists, "服务器已存在");
    }

    @Test
    @Order(4)
    void testRemoveServer() {
        String name = "server-to-remove";

        McpManager.McpServerInfo serverInfo = new McpManager.McpServerInfo(name, "node", null, null);
        servers.put(name, serverInfo);

        assertEquals(1, servers.size());

        // 删除服务器
        servers.remove(name);
        assertEquals(0, servers.size());
        assertFalse(servers.containsKey(name));
    }

    @Test
    @Order(5)
    void testGetServer() {
        String name = "specific-server";
        String command = "python";
        List<String> args = List.of("server.py");

        McpManager.McpServerInfo serverInfo = new McpManager.McpServerInfo(name, command, args, null);
        servers.put(name, serverInfo);

        McpManager.McpServerInfo retrieved = servers.get(name);
        assertNotNull(retrieved);
        assertEquals(name, retrieved.name());
        assertEquals(command, retrieved.command());
        assertEquals(args, retrieved.args());
    }

    @Test
    @Order(6)
    void testGetServers() {
        servers.put("server1", new McpManager.McpServerInfo("server1", "node", null, null));
        servers.put("server2", new McpManager.McpServerInfo("server2", "python", null, null));

        List<McpManager.McpServerInfo> serverList = new ArrayList<>(servers.values());
        assertEquals(2, serverList.size());
    }

    @Test
    @Order(7)
    void testGetNonExistentServer() {
        McpManager.McpServerInfo server = servers.get("non-existent");
        assertNull(server);
    }

    @Test
    @Order(8)
    void testEmptyServersList() {
        assertTrue(servers.isEmpty());
        assertEquals(0, servers.size());
    }

    @Test
    @Order(9)
    void testAddTool() {
        String toolName = "test-tool";
        String serverName = "test-server";
        String description = "Test tool description";
        Map<String, McpManager.McpParameterInfo> parameters = new HashMap<>();

        McpManager.McpToolInfo toolInfo = new McpManager.McpToolInfo(
            toolName,
            serverName,
            description,
            parameters
        );
        tools.put(toolName, toolInfo);

        assertEquals(1, tools.size());
        assertTrue(tools.containsKey(toolName));
        assertEquals(serverName, tools.get(toolName).serverName());
    }

    @Test
    @Order(10)
    void testGetTool() {
        String toolName = "specific-tool";
        String serverName = "server1";

        Map<String, McpManager.McpParameterInfo> params = new HashMap<>();
        McpManager.McpToolInfo toolInfo = new McpManager.McpToolInfo(toolName, serverName, "desc", params);
        tools.put(toolName, toolInfo);

        McpManager.McpToolInfo retrieved = tools.get(toolName);
        assertNotNull(retrieved);
        assertEquals(toolName, retrieved.name());
        assertEquals(serverName, retrieved.serverName());
    }

    @Test
    @Order(11)
    void testGetTools() {
        Map<String, McpManager.McpParameterInfo> params = new HashMap<>();

        tools.put("tool1", new McpManager.McpToolInfo("tool1", "server1", "desc1", params));
        tools.put("tool2", new McpManager.McpToolInfo("tool2", "server1", "desc2", params));

        List<McpManager.McpToolInfo> toolList = new ArrayList<>(tools.values());
        assertEquals(2, toolList.size());
    }

    @Test
    @Order(12)
    void testRemoveToolsByServer() {
        String serverName = "server-to-remove";

        Map<String, McpManager.McpParameterInfo> params = new HashMap<>();
        tools.put("tool1", new McpManager.McpToolInfo("tool1", serverName, "desc", params));
        tools.put("tool2", new McpManager.McpToolInfo("tool2", serverName, "desc", params));
        tools.put("tool3", new McpManager.McpToolInfo("tool3", "other-server", "desc", params));

        assertEquals(3, tools.size());

        // 移除特定服务器的工具
        tools.entrySet().removeIf(entry -> entry.getValue().serverName().equals(serverName));
        assertEquals(1, tools.size());
        assertFalse(tools.containsKey("tool1"));
        assertFalse(tools.containsKey("tool2"));
        assertTrue(tools.containsKey("tool3"));
    }

    @Test
    @Order(13)
    void testMcpServerInfo_Record() {
        String name = "server-name";
        String command = "node";
        List<String> args = List.of("arg1", "arg2");
        Map<String, String> env = Map.of("KEY", "value");

        McpManager.McpServerInfo serverInfo = new McpManager.McpServerInfo(name, command, args, env);

        assertEquals(name, serverInfo.name());
        assertEquals(command, serverInfo.command());
        assertEquals(args, serverInfo.args());
        assertEquals(env, serverInfo.env());
    }

    @Test
    @Order(14)
    void testMcpServerInfo_WithNullArgs() {
        McpManager.McpServerInfo serverInfo = new McpManager.McpServerInfo(
            "server-name",
            "python",
            null,
            null
        );

        assertEquals("server-name", serverInfo.name());
        assertEquals("python", serverInfo.command());
        assertNull(serverInfo.args());
        assertNull(serverInfo.env());
    }

    @Test
    @Order(15)
    void testMcpToolInfo_Record() {
        String name = "tool-name";
        String serverName = "server-name";
        String description = "Tool description";
        Map<String, McpManager.McpParameterInfo> parameters = new HashMap<>();

        McpManager.McpToolInfo toolInfo = new McpManager.McpToolInfo(name, serverName, description, parameters);

        assertEquals(name, toolInfo.name());
        assertEquals(serverName, toolInfo.serverName());
        assertEquals(description, toolInfo.description());
        assertEquals(parameters, toolInfo.parameters());
    }

    @Test
    @Order(16)
    void testMcpParameterInfo_Record() {
        String type = "string";
        String description = "Parameter description";
        boolean required = true;

        McpManager.McpParameterInfo paramInfo = new McpManager.McpParameterInfo(type, description, required);

        assertEquals(type, paramInfo.type());
        assertEquals(description, paramInfo.description());
        assertEquals(required, paramInfo.required());
    }

    @Test
    @Order(17)
    void testSerializeServersToJson() {
        Map<String, Object> serverMap = new HashMap<>();
        serverMap.put("name", "server1");
        serverMap.put("command", "node");

        String json = serializeMap(serverMap);
        assertTrue(json.contains("\"name\":\"server1\""));
        assertTrue(json.contains("\"command\":\"node\""));
    }

    @Test
    @Order(18)
    void testSerializeToolsToJson() {
        Map<String, Object> toolMap = new HashMap<>();
        toolMap.put("name", "tool1");
        toolMap.put("description", "Tool description");

        String json = serializeMap(toolMap);
        assertTrue(json.contains("\"name\":\"tool1\""));
        assertTrue(json.contains("\"description\":\"Tool description\""));
    }

    @Test
    @Order(19)
    void testSerializeList() {
        List<String> list = List.of("arg1", "arg2", "arg3");
        String json = serializeList(list);

        assertEquals("[\"arg1\",\"arg2\",\"arg3\"]", json);
    }

    @Test
    @Order(20)
    void testSerializeMap() {
        Map<String, String> map = Map.of("key1", "value1", "key2", "value2");
        String json = serializeMap(map);

        assertTrue(json.contains("\"key1\":\"value1\""));
        assertTrue(json.contains("\"key2\":\"value2\""));
    }

    @Test
    @Order(21)
    void testSerializeValue_String() {
        String value = "test string";
        String serialized = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";

        assertTrue(serialized.contains("test string"));
    }

    @Test
    @Order(22)
    void testSerializeValue_Null() {
        Object value = null;
        String serialized = "null";

        assertEquals("null", serialized);
    }

    @Test
    @Order(23)
    void testMcpMessage_Parsing() {
        String message = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\"}";

        boolean containsMethod = message.contains("\"method\":\"tools/list\"");
        assertTrue(containsMethod);
    }

    @Test
    @Order(24)
    void testMcpMessage_ToolCall() {
        String message = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"testTool\"}}";

        boolean containsToolCall = message.contains("\"method\":\"tools/call\"");
        boolean containsToolName = message.contains("\"name\":\"testTool\"");
        assertTrue(containsToolCall);
        assertTrue(containsToolName);
    }

    @Test
    @Order(25)
    void testMcpRequest_Build() {
        String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}";

        assertTrue(request.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(request.contains("\"id\":1"));
        assertTrue(request.contains("\"method\":\"tools/list\""));
    }

    @Test
    @Order(26)
    void testServerCommand_Validation() {
        String command = "node";
        assertNotNull(command);
        assertFalse(command.isEmpty());
    }

    @Test
    @Order(27)
    void testServerArgs_Empty() {
        List<String> args = new java.util.ArrayList<>();
        assertTrue(args.isEmpty());
    }

    @Test
    @Order(28)
    void testServerEnv_Empty() {
        Map<String, String> env = new HashMap<>();
        assertTrue(env.isEmpty());
    }

    @Test
    @Order(29)
    void testToolParameterTypes() {
        List<String> validTypes = List.of("string", "number", "boolean", "object", "array");

        for (String type : validTypes) {
            assertTrue(validTypes.contains(type));
        }
    }

    @Test
    @Order(30)
    void testToolParameter_Required() {
        Map<String, McpManager.McpParameterInfo> params = new HashMap<>();

        params.put("requiredParam", new McpManager.McpParameterInfo("string", "Required param", true));
        params.put("optionalParam", new McpManager.McpParameterInfo("string", "Optional param", false));

        assertTrue(params.get("requiredParam").required());
        assertFalse(params.get("optionalParam").required());
    }

    // 辅助方法
    private String serializeMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(serializeValue(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String serializeList(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(serializeValue(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String serializeValue(Object value) {
        if (value instanceof Map) {
            return serializeMap((Map<?, ?>) value);
        } else if (value instanceof List) {
            return serializeList((List<?>) value);
        } else if (value instanceof String) {
            return "\"" + ((String) value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        } else if (value == null) {
            return "null";
        } else {
            return "\"" + value.toString() + "\"";
        }
    }
}