package com.jimuqu.solonclaw.mcp;

import org.noear.solon.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * MCP 管理控制器
 * <p>
 * 提供 MCP 服务器管理的 HTTP API 接口
 *
 * @author SolonClaw
 */
@Controller
@Mapping("/api/mcp")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    @Inject
    private McpManager mcpManager;

    /**
     * 获取所有 MCP 服务器配置
     */
    @Get
    @Mapping("/servers")
    public McpResult getServers() {
        try {
            List<McpManager.McpServerInfo> servers = mcpManager.getServers();
            List<Map<String, Object>> serverList = new ArrayList<>();

            for (McpManager.McpServerInfo server : servers) {
                Map<String, Object> serverMap = new LinkedHashMap<>();
                serverMap.put("name", server.name());
                serverMap.put("command", server.command());
                serverMap.put("args", server.args());
                serverMap.put("disabled", server.disabled());
                serverMap.put("status", mcpManager.getServerStatus(server.name()).getCode());
                serverMap.put("running", mcpManager.isServerRunning(server.name()));
                serverList.add(serverMap);
            }

            return McpResult.success("获取成功", Map.of("servers", serverList));
        } catch (Exception e) {
            log.error("获取 MCP 服务器列表失败", e);
            return McpResult.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个 MCP 服务器配置
     */
    @Get
    @Mapping("/servers/{name}")
    public McpResult getServer(String name) {
        try {
            McpManager.McpServerInfo server = mcpManager.getServer(name);
            if (server == null) {
                return McpResult.error("服务器不存在: " + name);
            }

            Map<String, Object> serverMap = new LinkedHashMap<>();
            serverMap.put("name", server.name());
            serverMap.put("command", server.command());
            serverMap.put("args", server.args());
            serverMap.put("env", server.env());
            serverMap.put("disabled", server.disabled());
            serverMap.put("status", mcpManager.getServerStatus(name).getCode());
            serverMap.put("running", mcpManager.isServerRunning(name));

            // 获取服务器工具
            List<Map<String, Object>> toolList = getToolInfoList(mcpManager.getServerTools(name));

            serverMap.put("tools", toolList);

            return McpResult.success("获取成功", serverMap);
        } catch (Exception e) {
            log.error("获取 MCP 服务器失败: {}", name, e);
            return McpResult.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 添加 MCP 服务器
     */
    @Post
    @Mapping("/servers")
    public McpResult addServer(@Body ServerRequest request) {
        log.info("添加 MCP 服务器: name={}, command={}", request.name(), request.command());

        try {
            if (request.name() == null || request.name().isBlank()) {
                return McpResult.error("服务器名称不能为空");
            }
            if (request.command() == null || request.command().isBlank()) {
                return McpResult.error("命令不能为空");
            }

            McpManager.McpServerInfo server = mcpManager.addServer(
                    request.name(),
                    request.command(),
                    request.args(),
                    request.env(),
                    request.disabled() != null ? request.disabled() : false
            );

            return McpResult.success("添加成功", Map.of(
                    "name", server.name(),
                    "command", server.command()
            ));
        } catch (IllegalArgumentException e) {
            return McpResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("添加 MCP 服务器失败", e);
            return McpResult.error("添加失败: " + e.getMessage());
        }
    }

    /**
     * 更新 MCP 服务器配置
     */
    @Put
    @Mapping("/servers/{name}")
    public McpResult updateServer(String name, @Body ServerRequest request) {
        log.info("更新 MCP 服务器: name={}", name);

        try {
            McpManager.McpServerInfo server = mcpManager.updateServer(
                    name,
                    request.command(),
                    request.args(),
                    request.env(),
                    request.disabled()
            );

            return McpResult.success("更新成功", Map.of(
                    "name", server.name(),
                    "command", server.command()
            ));
        } catch (IllegalArgumentException e) {
            return McpResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新 MCP 服务器失败: {}", name, e);
            return McpResult.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除 MCP 服务器
     */
    @Delete
    @Mapping("/servers/{name}")
    public McpResult removeServer(String name) {
        log.info("删除 MCP 服务器: name={}", name);

        try {
            boolean removed = mcpManager.removeServer(name);
            if (!removed) {
                return McpResult.error("服务器不存在: " + name);
            }
            return McpResult.success("删除成功", Map.of("name", name));
        } catch (Exception e) {
            log.error("删除 MCP 服务器失败: {}", name, e);
            return McpResult.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 启动 MCP 服务器
     */
    @Post
    @Mapping("/servers/{name}/start")
    public McpResult startServer(String name) {
        log.info("启动 MCP 服务器: name={}", name);

        try {
            boolean started = mcpManager.startServer(name);
            return McpResult.success(started ? "启动成功" : "服务器已在运行", Map.of(
                    "name", name,
                    "running", mcpManager.isServerRunning(name),
                    "status", mcpManager.getServerStatus(name).getCode()
            ));
        } catch (Exception e) {
            log.error("启动 MCP 服务器失败: {}", name, e);
            return McpResult.error("启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止 MCP 服务器
     */
    @Post
    @Mapping("/servers/{name}/stop")
    public McpResult stopServer(String name) {
        log.info("停止 MCP 服务器: name={}", name);

        try {
            boolean stopped = mcpManager.stopServer(name);
            return McpResult.success(stopped ? "停止成功" : "服务器未运行", Map.of(
                    "name", name,
                    "running", mcpManager.isServerRunning(name),
                    "status", mcpManager.getServerStatus(name).getCode()
            ));
        } catch (Exception e) {
            log.error("停止 MCP 服务器失败: {}", name, e);
            return McpResult.error("停止失败: " + e.getMessage());
        }
    }

    /**
     * 启动所有 MCP 服务器
     */
    @Post
    @Mapping("/servers/start-all")
    public McpResult startAllServers() {
        log.info("启动所有 MCP 服务器");

        try {
            mcpManager.startAllServers();
            return McpResult.success("启动完成", Map.of(
                    "runningCount", mcpManager.getRunningServers().size()
            ));
        } catch (Exception e) {
            log.error("启动所有 MCP 服务器失败", e);
            return McpResult.error("启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止所有 MCP 服务器
     */
    @Post
    @Mapping("/servers/stop-all")
    public McpResult stopAllServers() {
        log.info("停止所有 MCP 服务器");

        try {
            mcpManager.stopAllServers();
            return McpResult.success("停止完成", Map.of(
                    "runningCount", 0
            ));
        } catch (Exception e) {
            log.error("停止所有 MCP 服务器失败", e);
            return McpResult.error("停止失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有 MCP 工具
     */
    @Get
    @Mapping("/tools")
    public McpResult getTools() {
        try {
            List<McpManager.McpToolInfo> tools = mcpManager.getTools();
            return McpResult.success("获取成功", Map.of(
                    "tools", getToolInfoList(tools),
                    "count", tools.size()
            ));
        } catch (Exception e) {
            log.error("获取 MCP 工具列表失败", e);
            return McpResult.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个 MCP 工具详情
     */
    @Get
    @Mapping("/tools/{fullName}")
    public McpResult getTool(String fullName) {
        try {
            McpManager.McpToolInfo tool = mcpManager.getTool(fullName);
            if (tool == null) {
                return McpResult.error("工具不存在: " + fullName);
            }

            Map<String, Object> toolMap = new LinkedHashMap<>();
            toolMap.put("name", tool.name());
            toolMap.put("fullName", tool.fullName());
            toolMap.put("serverName", tool.serverName());
            toolMap.put("description", tool.description());
            toolMap.put("parameters", getParameterInfoMap(tool.parameters()));

            return McpResult.success("获取成功", toolMap);
        } catch (Exception e) {
            log.error("获取 MCP 工具失败: {}", fullName, e);
            return McpResult.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 调用 MCP 工具
     */
    @Post
    @Mapping("/tools/{fullName}/call")
    public McpResult callTool(String fullName, @Body ToolCallRequest request) {
        log.info("调用 MCP 工具: tool={}", fullName);

        try {
            String result = mcpManager.callTool(fullName, request.arguments());
            return McpResult.success("调用成功", Map.of(
                    "tool", fullName,
                    "result", result
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return McpResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("调用 MCP 工具失败: {}", fullName, e);
            return McpResult.error("调用失败: " + e.getMessage());
        }
    }

    /**
     * 获取可用的命令行工具
     */
    @Get
    @Mapping("/commands")
    public McpResult getAvailableCommands() {
        try {
            Map<String, Boolean> commands = mcpManager.getAvailableCommands();
            return McpResult.success("获取成功", Map.of("commands", commands));
        } catch (Exception e) {
            log.error("获取可用命令失败", e);
            return McpResult.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 重新加载 MCP 配置
     */
    @Post
    @Mapping("/reload")
    public McpResult reloadConfig() {
        log.info("重新加载 MCP 配置");

        try {
            mcpManager.loadConfig();
            return McpResult.success("重新加载成功", Map.of(
                    "serverCount", mcpManager.getServers().size()
            ));
        } catch (Exception e) {
            log.error("重新加载 MCP 配置失败", e);
            return McpResult.error("重新加载失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private List<Map<String, Object>> getToolInfoList(List<McpManager.McpToolInfo> tools) {
        List<Map<String, Object>> toolList = new ArrayList<>();
        for (McpManager.McpToolInfo tool : tools) {
            Map<String, Object> toolMap = new LinkedHashMap<>();
            toolMap.put("name", tool.name());
            toolMap.put("fullName", tool.fullName());
            toolMap.put("serverName", tool.serverName());
            toolMap.put("description", tool.description());
            toolMap.put("parameterCount", tool.parameters().size());
            toolList.add(toolMap);
        }
        return toolList;
    }

    private Map<String, Object> getParameterInfoMap(Map<String, McpManager.McpParameterInfo> parameters) {
        Map<String, Object> paramMap = new LinkedHashMap<>();
        for (Map.Entry<String, McpManager.McpParameterInfo> entry : parameters.entrySet()) {
            McpManager.McpParameterInfo param = entry.getValue();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("type", param.type());
            info.put("description", param.description());
            info.put("required", param.required());
            paramMap.put(entry.getKey(), info);
        }
        return paramMap;
    }

    // ==================== 请求/响应记录类 ====================

    /**
     * 服务器配置请求
     */
    public record ServerRequest(
            String name,
            String command,
            List<String> args,
            Map<String, String> env,
            Boolean disabled
    ) {
    }

    /**
     * 工具调用请求
     */
    public record ToolCallRequest(
            Map<String, Object> arguments
    ) {
    }

    /**
     * 统一响应结果
     */
    public record McpResult(
            int code,
            String message,
            Object data
    ) {
        public static McpResult success(String message, Object data) {
            return new McpResult(200, message, data);
        }

        public static McpResult error(String message) {
            return new McpResult(500, message, null);
        }
    }
}