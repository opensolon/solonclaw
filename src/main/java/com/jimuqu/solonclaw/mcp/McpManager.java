package com.jimuqu.solonclaw.mcp;

import com.jimuqu.solonclaw.config.WorkspaceConfig;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 管理器
 * <p>
 * 管理 Model Context Protocol (MCP) 服务器
 *
 * @author SolonClaw
 */
@Component
public class McpManager {

    private static final Logger log = LoggerFactory.getLogger(McpManager.class);

    @Inject
    private WorkspaceConfig.WorkspaceInfo workspaceInfo;

    /**
     * MCP 服务器列表：名称 -> 服务器信息
     */
    private final Map<String, McpServerInfo> servers = new ConcurrentHashMap<>();

    /**
     * MCP 进程列表：名称 -> 进程
     */
    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    /**
     * MCP 提供的工具列表：名称 -> 工具信息
     */
    private final Map<String, McpToolInfo> tools = new ConcurrentHashMap<>();

    /**
     * 加载 MCP 配置
     */
    public void loadConfig() {
        try {
            Path configFile = workspaceInfo.mcpConfigFile();
            if (Files.exists(configFile)) {
                String json = Files.readString(configFile);
                log.info("加载了 MCP 配置文件: {}", configFile);
            } else {
                log.info("MCP 配置文件不存在: {}", configFile);
                // 创建默认配置文件
                createDefaultConfig();
            }
        } catch (Exception e) {
            log.error("加载 MCP 配置失败", e);
        }
    }

    /**
     * 创建默认配置文件
     */
    private void createDefaultConfig() {
        try {
            Path configFile = workspaceInfo.mcpConfigFile();
            Files.createDirectories(configFile.getParent());

            // 创建默认配置（空的 servers 对象）
            String defaultConfig = "{\"servers\":{}}";
            Files.writeString(configFile, defaultConfig);

            log.info("创建了默认 MCP 配置文件: {}", configFile);
        } catch (Exception e) {
            log.error("创建默认 MCP 配置失败", e);
        }
    }

    /**
     * 启动 MCP 服务器
     *
     * @param name 服务器名称
     */
    public void startServer(String name) {
        if (processes.containsKey(name)) {
            log.warn("MCP 服务器已在运行: {}", name);
            return;
        }

        McpServerInfo serverInfo = servers.get(name);
        if (serverInfo == null) {
            throw new IllegalArgumentException("MCP 服务器不存在: " + name);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder();
            List<String> command = new ArrayList<>();
            command.add(serverInfo.command());
            if (serverInfo.args() != null) {
                command.addAll(serverInfo.args());
            }
            pb.command(command);

            // 设置环境变量
            Map<String, String> env = pb.environment();
            if (serverInfo.env() != null) {
                env.putAll(serverInfo.env());
            }

            // 启动进程
            Process process = pb.start();
            processes.put(name, process);

            log.info("启动 MCP 服务器: name={}, command={}", name, serverInfo.command());

            // 异步读取输出
            readOutput(name, process, process.getInputStream());
            readOutput(name, process, process.getErrorStream());

            // 等待服务器启动并发现工具
            discoverTools(name, process);

        } catch (Exception e) {
            log.error("启动 MCP 服务器失败: name={}", name, e);
            throw new RuntimeException("启动 MCP 服务器失败: " + name, e);
        }
    }

    /**
     * 停止 MCP 服务器
     *
     * @param name 服务器名称
     */
    public void stopServer(String name) {
        Process process = processes.remove(name);
        if (process != null) {
            process.destroy();
            log.info("停止 MCP 服务器: name={}", name);
        }

        // 移除该服务器的工具
        tools.entrySet().removeIf(entry -> entry.getValue().serverName().equals(name));
    }

    /**
     * 停止所有 MCP 服务器
     */
    public void stopAllServers() {
        List<String> serverNames = new ArrayList<>(processes.keySet());
        for (String name : serverNames) {
            stopServer(name);
        }
    }

    /**
     * 获取所有 MCP 服务器
     *
     * @return 服务器列表
     */
    public List<McpServerInfo> getServers() {
        return new ArrayList<>(servers.values());
    }

    /**
     * 获取 MCP 服务器信息
     *
     * @param name 服务器名称
     * @return 服务器信息
     */
    public McpServerInfo getServer(String name) {
        return servers.get(name);
    }

    /**
     * 添加 MCP 服务器
     *
     * @param name    服务器名称
     * @param command 命令
     * @param args    参数
     * @param env     环境变量
     */
    public void addServer(String name, String command, List<String> args, Map<String, String> env) {
        if (servers.containsKey(name)) {
            throw new IllegalArgumentException("MCP 服务器已存在: " + name);
        }

        McpServerInfo serverInfo = new McpServerInfo(name, command, args, env);
        servers.put(name, serverInfo);
        saveConfig();
        log.info("添加 MCP 服务器: name={}, command={}", name, command);
    }

    /**
     * 删除 MCP 服务器
     *
     * @param name 服务器名称
     */
    public void removeServer(String name) {
        stopServer(name);
        servers.remove(name);
        saveConfig();
        log.info("删除 MCP 服务器: name={}", name);
    }

    /**
     * 获取所有 MCP 工具
     *
     * @return 工具列表
     */
    public List<McpToolInfo> getTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 获取 MCP 工具信息
     *
     * @param name 工具名称
     * @return 工具信息
     */
    public McpToolInfo getTool(String name) {
        return tools.get(name);
    }

    /**
     * 保存配置到文件
     */
    private void saveConfig() {
        try {
            Path configFile = workspaceInfo.mcpConfigFile();
            Files.createDirectories(configFile.getParent());

            // 构建配置 JSON
            StringBuilder sb = new StringBuilder();
            sb.append("{\"servers\":{");

            boolean first = true;
            for (Map.Entry<String, McpServerInfo> entry : servers.entrySet()) {
                McpServerInfo server = entry.getValue();
                if (!first) sb.append(",");
                first = false;

                sb.append("\"").append(entry.getKey()).append("\":{");
                sb.append("\"name\":\"").append(server.name()).append("\",");
                sb.append("\"command\":\"").append(server.command()).append("\",");
                sb.append("\"args\":").append(serializeList(server.args())).append(",");
                sb.append("\"env\":").append(serializeMap(server.env())).append("}");
            }

            sb.append("}}");
            Files.writeString(configFile, sb.toString());

            log.debug("MCP 配置已保存");
        } catch (Exception e) {
            log.error("保存 MCP 配置失败", e);
        }
    }

    /**
     * 读取进程输出
     */
    private void readOutput(String serverName, Process process, java.io.InputStream inputStream) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[MCP {}: stdout] {}", serverName, line);

                    // 尝试解析 MCP 消息
                    parseMcpMessage(serverName, line);
                }
            } catch (Exception e) {
                log.error("读取 MCP 输出失败: serverName={}", serverName, e);
            }
        }).start();
    }

    /**
     * 解析 MCP 消息
     */
    private void parseMcpMessage(String serverName, String message) {
        try {
            // 简单的 JSON 解析，查找工具定义
            if (message.contains("\"method\":\"tools/list\"") || message.contains("\"name\"")) {
                // 这里应该实现完整的 MCP 协议解析
                // 简化实现：从消息中提取工具名称
                log.debug("发现 MCP 工具: serverName={}, message={}", serverName, message);
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
    }

    /**
     * 发现 MCP 提供的工具
     */
    private void discoverTools(String serverName, Process process) {
        try {
            // 发送工具列表请求（MCP 协议）
            String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}\n";

            process.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            process.getOutputStream().flush();

            log.debug("发送 MCP 工具列表请求: serverName={}", serverName);
        } catch (Exception e) {
            log.error("发现 MCP 工具失败: serverName={}", serverName, e);
        }
    }

    /**
     * 调用 MCP 工具
     *
     * @param toolName    工具名称
     * @param arguments   参数
     * @return 工具执行结果
     */
    public String callTool(String toolName, Map<String, Object> arguments) {
        McpToolInfo toolInfo = tools.get(toolName);
        if (toolInfo == null) {
            throw new IllegalArgumentException("MCP 工具不存在: " + toolName);
        }

        String serverName = toolInfo.serverName();
        Process process = processes.get(serverName);

        if (process == null || !process.isAlive()) {
            throw new IllegalStateException("MCP 服务器未运行: " + serverName);
        }

        try {
            // 构建 MCP 工具调用请求
            String request = String.format(
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":%s}}\n",
                System.currentTimeMillis(),
                toolName,
                serializeToJson(arguments)
            );

            process.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            process.getOutputStream().flush();

            log.info("调用 MCP 工具: toolName={}, serverName={}", toolName, serverName);

            // 这里应该等待并解析响应
            // 简化实现：返回提示信息
            return "MCP 工具调用已发送: " + toolName;

        } catch (Exception e) {
            log.error("调用 MCP 工具失败: toolName={}", toolName, e);
            throw new RuntimeException("调用 MCP 工具失败", e);
        }
    }

    /**
     * 简化的 JSON 序列化
     */
    private String serializeToJson(Object obj) {
        if (obj instanceof Map) {
            return serializeMap((Map<?, ?>) obj);
        } else if (obj instanceof List) {
            return serializeList((List<?>) obj);
        } else {
            return "\"" + obj.toString() + "\"";
        }
    }

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
        if (value instanceof Map || value instanceof List) {
            return serializeToJson(value);
        } else if (value instanceof String) {
            return "\"" + ((String) value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        } else if (value == null) {
            return "null";
        } else {
            return "\"" + value.toString() + "\"";
        }
    }

    /**
     * MCP 配置
     *
     * @param servers 服务器列表
     */
    public record McpConfig(Map<String, McpServerInfo> servers) {
    }

    /**
     * MCP 服务器信息
     *
     * @param name    服务器名称
     * @param command 命令
     * @param args    参数
     * @param env     环境变量
     */
    public record McpServerInfo(
            String name,
            String command,
            List<String> args,
            Map<String, String> env
    ) {
    }

    /**
     * MCP 工具信息
     *
     * @param name       工具名称
     * @param serverName 所属服务器名称
     * @param description 工具描述
     * @param parameters 参数定义
     */
    public record McpToolInfo(
            String name,
            String serverName,
            String description,
            Map<String, McpParameterInfo> parameters
    ) {
    }

    /**
     * MCP 参数信息
     *
     * @param type        参数类型
     * @param description 参数描述
     * @param required    是否必需
     */
    public record McpParameterInfo(
            String type,
            String description,
            boolean required
    ) {
    }
}