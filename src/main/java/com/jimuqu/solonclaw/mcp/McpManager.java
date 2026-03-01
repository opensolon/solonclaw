package com.jimuqu.solonclaw.mcp;

import com.jimuqu.solonclaw.config.WorkspaceConfig;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP 管理器
 * <p>
 * 管理 Model Context Protocol (MCP) 服务器，包括：
 * - MCP 服务器配置管理
 * - MCP 服务器生命周期管理（启动、停止）
 * - MCP 工具发现和注册
 * - MCP 工具调用
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
     * MCP 进程列表：名称 -> 进程信息
     */
    private final Map<String, McpProcessInfo> processInfos = new ConcurrentHashMap<>();

    /**
     * MCP 提供的工具列表：工具全名 -> 工具信息
     */
    private final Map<String, McpToolInfo> tools = new ConcurrentHashMap<>();

    /**
     * 请求 ID 生成器
     */
    private final AtomicLong requestIdGenerator = new AtomicLong(1);

    /**
     * 响应等待队列：请求 ID -> 响应结果
     */
    private final Map<Long, McpResponse> pendingResponses = new ConcurrentHashMap<>();

    /**
     * 初始化：加载配置
     */
    @Init
    public void init() {
        log.info("初始化 MCP 管理器...");
        loadConfig();
        log.info("MCP 管理器初始化完成，已加载 {} 个服务器配置", servers.size());
    }

    /**
     * 加载 MCP 配置
     */
    public void loadConfig() {
        try {
            Path configFile = workspaceInfo.mcpConfigFile();
            if (!Files.exists(configFile)) {
                log.info("MCP 配置文件不存在，创建默认配置: {}", configFile);
                createDefaultConfig();
                return;
            }

            String json = Files.readString(configFile, StandardCharsets.UTF_8);
            ONode root = ONode.ofJson(json);

            ONode serversNode = root.get("mcpServers");
            if (serversNode != null && serversNode.isObject()) {
                servers.clear();
                Map<String, ONode> serverMap = serversNode.getObject();
                for (Map.Entry<String, ONode> entry : serverMap.entrySet()) {
                    try {
                        McpServerInfo serverInfo = parseServerInfo(entry.getKey(), entry.getValue());
                        servers.put(entry.getKey(), serverInfo);
                        log.debug("加载 MCP 服务器配置: {}", entry.getKey());
                    } catch (Exception e) {
                        log.error("解析 MCP 服务器配置失败: {}", entry.getKey(), e);
                    }
                }
            }

            log.info("加载 MCP 配置文件成功: {}，共 {} 个服务器", configFile, servers.size());
        } catch (Exception e) {
            log.error("加载 MCP 配置失败", e);
        }
    }

    /**
     * 解析服务器配置
     */
    private McpServerInfo parseServerInfo(String name, ONode node) {
        String command = node.get("command").getString();
        List<String> args = new ArrayList<>();
        Map<String, String> env = new HashMap<>();

        // 解析 args
        ONode argsNode = node.get("args");
        if (argsNode != null && argsNode.isArray()) {
            List<ONode> argsList = argsNode.getArray();
            for (ONode argNode : argsList) {
                args.add(argNode.getString());
            }
        }

        // 解析 env
        ONode envNode = node.get("env");
        if (envNode != null && envNode.isObject()) {
            Map<String, ONode> envMap = envNode.getObject();
            for (Map.Entry<String, ONode> envEntry : envMap.entrySet()) {
                env.put(envEntry.getKey(), envEntry.getValue().getString());
            }
        }

        // 解析 disabled
        boolean disabled = node.get("disabled").getBoolean() != null && node.get("disabled").getBoolean();

        return new McpServerInfo(name, command, args, env, disabled);
    }

    /**
     * 创建默认配置文件
     */
    private void createDefaultConfig() {
        try {
            Path configFile = workspaceInfo.mcpConfigFile();
            Files.createDirectories(configFile.getParent());

            // 创建默认配置示例
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

            Files.writeString(configFile, defaultConfig, StandardCharsets.UTF_8);
            log.info("创建了默认 MCP 配置文件: {}", configFile);
        } catch (Exception e) {
            log.error("创建默认 MCP 配置失败", e);
        }
    }

    /**
     * 保存配置到文件
     */
    public void saveConfig() {
        try {
            Path configFile = workspaceInfo.mcpConfigFile();
            Files.createDirectories(configFile.getParent());

            ONode root = new ONode().asObject();
            ONode serversNode = root.set("mcpServers", new ONode().asObject());

            for (Map.Entry<String, McpServerInfo> entry : servers.entrySet()) {
                McpServerInfo server = entry.getValue();
                ONode serverNode = new ONode().asObject();
                serverNode.set("command", server.command());

                if (server.args() != null && !server.args().isEmpty()) {
                    ONode argsNode = new ONode().asArray();
                    for (String arg : server.args()) {
                        argsNode.add(arg);
                    }
                    serverNode.set("args", argsNode);
                }

                if (server.env() != null && !server.env().isEmpty()) {
                    ONode envNode = new ONode().asObject();
                    for (Map.Entry<String, String> envEntry : server.env().entrySet()) {
                        envNode.set(envEntry.getKey(), envEntry.getValue());
                    }
                    serverNode.set("env", envNode);
                }

                if (server.disabled()) {
                    serverNode.set("disabled", true);
                }

                serversNode.set(entry.getKey(), serverNode);
            }

            Files.writeString(configFile, root.toJson(), StandardCharsets.UTF_8);
            log.info("MCP 配置已保存到: {}", configFile);
        } catch (Exception e) {
            log.error("保存 MCP 配置失败", e);
            throw new RuntimeException("保存 MCP 配置失败", e);
        }
    }

    /**
     * 添加 MCP 服务器
     *
     * @param name     服务器名称
     * @param command  启动命令
     * @param args     命令参数
     * @param env      环境变量
     * @return 添加的服务器信息
     */
    public McpServerInfo addServer(String name, String command, List<String> args, Map<String, String> env) {
        return addServer(name, command, args, env, false);
    }

    /**
     * 添加 MCP 服务器
     *
     * @param name     服务器名称
     * @param command  启动命令
     * @param args     命令参数
     * @param env      环境变量
     * @param disabled 是否禁用
     * @return 添加的服务器信息
     */
    public McpServerInfo addServer(String name, String command, List<String> args, Map<String, String> env, boolean disabled) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("服务器名称不能为空");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("命令不能为空");
        }

        if (servers.containsKey(name)) {
            throw new IllegalArgumentException("MCP 服务器已存在: " + name);
        }

        McpServerInfo serverInfo = new McpServerInfo(
                name,
                command,
                args != null ? new ArrayList<>(args) : new ArrayList<>(),
                env != null ? new HashMap<>(env) : new HashMap<>(),
                disabled
        );

        servers.put(name, serverInfo);
        saveConfig();

        log.info("添加 MCP 服务器: name={}, command={}", name, command);
        return serverInfo;
    }

    /**
     * 更新 MCP 服务器配置
     *
     * @param name     服务器名称
     * @param command  启动命令
     * @param args     命令参数
     * @param env      环境变量
     * @param disabled 是否禁用
     * @return 更新后的服务器信息
     */
    public McpServerInfo updateServer(String name, String command, List<String> args, Map<String, String> env, Boolean disabled) {
        McpServerInfo existing = servers.get(name);
        if (existing == null) {
            throw new IllegalArgumentException("MCP 服务器不存在: " + name);
        }

        // 如果服务器正在运行，先停止
        if (isServerRunning(name)) {
            stopServer(name);
        }

        McpServerInfo serverInfo = new McpServerInfo(
                name,
                command != null ? command : existing.command(),
                args != null ? new ArrayList<>(args) : existing.args(),
                env != null ? new HashMap<>(env) : existing.env(),
                disabled != null ? disabled : existing.disabled()
        );

        servers.put(name, serverInfo);
        saveConfig();

        log.info("更新 MCP 服务器配置: name={}", name);
        return serverInfo;
    }

    /**
     * 删除 MCP 服务器
     *
     * @param name 服务器名称
     * @return 是否删除成功
     */
    public boolean removeServer(String name) {
        if (!servers.containsKey(name)) {
            return false;
        }

        // 先停止服务器
        stopServer(name);

        servers.remove(name);
        saveConfig();

        log.info("删除 MCP 服务器: name={}", name);
        return true;
    }

    /**
     * 启动 MCP 服务器
     *
     * @param name 服务器名称
     * @return 是否启动成功
     */
    public boolean startServer(String name) {
        if (isServerRunning(name)) {
            log.warn("MCP 服务器已在运行: {}", name);
            return true;
        }

        McpServerInfo serverInfo = servers.get(name);
        if (serverInfo == null) {
            throw new IllegalArgumentException("MCP 服务器不存在: " + name);
        }

        if (serverInfo.disabled()) {
            throw new IllegalStateException("MCP 服务器已禁用: " + name);
        }

        try {
            // 构建命令
            List<String> command = new ArrayList<>();
            command.add(serverInfo.command());
            if (serverInfo.args() != null) {
                command.addAll(serverInfo.args());
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);

            // 设置环境变量
            Map<String, String> env = pb.environment();
            if (serverInfo.env() != null) {
                env.putAll(serverInfo.env());
            }

            // 启动进程
            Process process = pb.start();
            long startTime = System.currentTimeMillis();

            // 创建进程信息
            McpProcessInfo processInfo = new McpProcessInfo(
                    name,
                    process,
                    startTime,
                    McpServerStatus.STARTING
            );
            processInfos.put(name, processInfo);

            log.info("启动 MCP 服务器: name={}, command={}", name, String.join(" ", command));

            // 异步读取输出
            readOutputAsync(name, process);

            // 初始化 MCP 连接
            initializeMcpConnection(name, process);

            return true;

        } catch (Exception e) {
            log.error("启动 MCP 服务器失败: name={}", name, e);
            processInfos.remove(name);
            throw new RuntimeException("启动 MCP 服务器失败: " + name, e);
        }
    }

    /**
     * 初始化 MCP 连接
     */
    private void initializeMcpConnection(String name, Process process) {
        try {
            // 发送 initialize 请求
            long requestId = requestIdGenerator.getAndIncrement();
            String initRequest = String.format(
                    "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"SolonClaw\",\"version\":\"1.0.0\"}}}\n",
                    requestId
            );

            process.getOutputStream().write(initRequest.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();

            log.debug("发送 MCP 初始化请求: serverName={}, requestId={}", name, requestId);

            // 等待初始化响应（简化实现，实际应该等待响应）
            Thread.sleep(500);

            // 发送 initialized 通知
            String initializedNotification = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n";
            process.getOutputStream().write(initializedNotification.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();

            // 更新状态为已初始化
            McpProcessInfo processInfo = processInfos.get(name);
            if (processInfo != null) {
                processInfos.put(name, processInfo.withStatus(McpServerStatus.INITIALIZED));
            }

            // 发现工具
            discoverTools(name, process);

        } catch (Exception e) {
            log.error("初始化 MCP 连接失败: serverName={}", name, e);
        }
    }

    /**
     * 发现 MCP 提供的工具
     */
    private void discoverTools(String name, Process process) {
        try {
            long requestId = requestIdGenerator.getAndIncrement();
            String request = String.format(
                    "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/list\",\"params\":{}}\n",
                    requestId
            );

            process.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();

            log.debug("发送 MCP 工具列表请求: serverName={}, requestId={}", name, requestId);

            // 更新状态为运行中
            McpProcessInfo processInfo = processInfos.get(name);
            if (processInfo != null) {
                processInfos.put(name, processInfo.withStatus(McpServerStatus.RUNNING));
            }

        } catch (Exception e) {
            log.error("发现 MCP 工具失败: serverName={}", name, e);
        }
    }

    /**
     * 异步读取进程输出
     */
    private void readOutputAsync(String serverName, Process process) {
        // 读取标准输出
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[MCP {} stdout] {}", serverName, line);
                    parseMcpMessage(serverName, line);
                }
            } catch (IOException e) {
                if (e.getMessage() != null && !e.getMessage().contains("Stream closed")) {
                    log.error("读取 MCP stdout 失败: serverName={}", serverName, e);
                }
            }
        }, "mcp-stdout-" + serverName).start();

        // 读取错误输出
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.warn("[MCP {} stderr] {}", serverName, line);
                }
            } catch (IOException e) {
                if (e.getMessage() != null && !e.getMessage().contains("Stream closed")) {
                    log.error("读取 MCP stderr 失败: serverName={}", serverName, e);
                }
            }
        }, "mcp-stderr-" + serverName).start();
    }

    /**
     * 解析 MCP 消息
     */
    private void parseMcpMessage(String serverName, String message) {
        try {
            ONode node = ONode.ofJson(message);

            // 处理响应
            if (node.hasKey("id")) {
                long id = node.get("id").getLong();
                ONode result = node.get("result");
                ONode error = node.get("error");

                if (error != null && !error.isNull()) {
                    String errorMsg = error.get("message").getString();
                    pendingResponses.put(id, new McpResponse(id, null, errorMsg));
                    log.error("MCP 响应错误: serverName={}, id={}, error={}", serverName, id, errorMsg);
                } else if (result != null && !result.isNull()) {
                    pendingResponses.put(id, new McpResponse(id, result, null));

                    // 处理工具列表响应
                    ONode toolsNode = result.get("tools");
                    if (toolsNode != null && toolsNode.isArray()) {
                        registerToolsFromResponse(serverName, toolsNode);
                    }
                }
            }

            // 处理通知
            if (node.hasKey("method")) {
                String method = node.get("method").getString();
                log.debug("MCP 通知: serverName={}, method={}", serverName, method);
            }

        } catch (Exception e) {
            log.debug("解析 MCP 消息失败: serverName={}, message={}", serverName, message);
        }
    }

    /**
     * 从响应中注册工具
     */
    private void registerToolsFromResponse(String serverName, ONode toolsNode) {
        List<ONode> toolsList = toolsNode.getArray();
        for (ONode toolNode : toolsList) {
            try {
                String toolName = toolNode.get("name").getString();
                ONode descriptionNode = toolNode.get("description");
                String description = descriptionNode != null && !descriptionNode.isNull() ? descriptionNode.getString() : "";

                // 解析参数
                Map<String, McpParameterInfo> parameters = new HashMap<>();
                ONode inputSchema = toolNode.get("inputSchema");
                if (inputSchema != null && !inputSchema.isNull()) {
                    ONode properties = inputSchema.get("properties");
                    if (properties != null && properties.isObject()) {
                        List<String> required = new ArrayList<>();
                        ONode requiredNode = inputSchema.get("required");
                        if (requiredNode != null && requiredNode.isArray()) {
                            List<ONode> requiredList = requiredNode.getArray();
                            for (ONode r : requiredList) {
                                required.add(r.getString());
                            }
                        }

                        Map<String, ONode> propsMap = properties.getObject();
                        for (Map.Entry<String, ONode> prop : propsMap.entrySet()) {
                            String paramName = prop.getKey();
                            ONode paramNode = prop.getValue();
                            ONode typeNode = paramNode.get("type");
                            ONode descNode = paramNode.get("description");
                            String type = typeNode != null && !typeNode.isNull() ? typeNode.getString() : "string";
                            String desc = descNode != null && !descNode.isNull() ? descNode.getString() : "";
                            boolean isRequired = required.contains(paramName);

                            parameters.put(paramName, new McpParameterInfo(type, desc, isRequired));
                        }
                    }
                }

                // 生成工具全名
                String fullToolName = serverName + "." + toolName;

                McpToolInfo toolInfo = new McpToolInfo(
                        toolName,
                        fullToolName,
                        serverName,
                        description,
                        parameters
                );

                tools.put(fullToolName, toolInfo);
                log.info("注册 MCP 工具: {} -> {}", serverName, fullToolName);

            } catch (Exception e) {
                log.error("解析 MCP 工具失败: serverName={}", serverName, e);
            }
        }
    }

    /**
     * 停止 MCP 服务器
     *
     * @param name 服务器名称
     * @return 是否停止成功
     */
    public boolean stopServer(String name) {
        McpProcessInfo processInfo = processInfos.remove(name);
        if (processInfo == null) {
            log.debug("MCP 服务器未运行: {}", name);
            return false;
        }

        Process process = processInfo.process();
        if (process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 移除该服务器的工具
        tools.entrySet().removeIf(entry -> entry.getValue().serverName().equals(name));

        log.info("停止 MCP 服务器: name={}, 运行时长={}ms",
                name, System.currentTimeMillis() - processInfo.startTime());
        return true;
    }

    /**
     * 停止所有 MCP 服务器
     */
    public void stopAllServers() {
        List<String> serverNames = new ArrayList<>(processInfos.keySet());
        for (String name : serverNames) {
            stopServer(name);
        }
        log.info("停止所有 MCP 服务器，共 {} 个", serverNames.size());
    }

    /**
     * 启动所有已配置且未禁用的 MCP 服务器
     */
    public void startAllServers() {
        int started = 0;
        for (McpServerInfo server : servers.values()) {
            if (!server.disabled() && !isServerRunning(server.name())) {
                try {
                    startServer(server.name());
                    started++;
                } catch (Exception e) {
                    log.error("启动 MCP 服务器失败: {}", server.name(), e);
                }
            }
        }
        log.info("启动 MCP 服务器完成，成功启动 {} 个", started);
    }

    /**
     * 检查服务器是否运行中
     */
    public boolean isServerRunning(String name) {
        McpProcessInfo processInfo = processInfos.get(name);
        return processInfo != null && processInfo.process().isAlive();
    }

    /**
     * 获取服务器状态
     */
    public McpServerStatus getServerStatus(String name) {
        McpProcessInfo processInfo = processInfos.get(name);
        if (processInfo == null) {
            return McpServerStatus.STOPPED;
        }
        if (!processInfo.process().isAlive()) {
            return McpServerStatus.STOPPED;
        }
        return processInfo.status();
    }

    /**
     * 获取所有 MCP 服务器配置
     */
    public List<McpServerInfo> getServers() {
        return new ArrayList<>(servers.values());
    }

    /**
     * 获取 MCP 服务器配置
     */
    public McpServerInfo getServer(String name) {
        return servers.get(name);
    }

    /**
     * 获取所有运行中的 MCP 服务器
     */
    public List<McpServerInfo> getRunningServers() {
        return servers.values().stream()
                .filter(s -> isServerRunning(s.name()))
                .toList();
    }

    /**
     * 获取所有 MCP 工具
     */
    public List<McpToolInfo> getTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 获取指定服务器的工具
     */
    public List<McpToolInfo> getServerTools(String serverName) {
        return tools.values().stream()
                .filter(t -> t.serverName().equals(serverName))
                .toList();
    }

    /**
     * 获取 MCP 工具信息
     */
    public McpToolInfo getTool(String fullToolName) {
        return tools.get(fullToolName);
    }

    /**
     * 调用 MCP 工具
     *
     * @param fullToolName 工具全名（server.toolName）
     * @param arguments    参数
     * @return 工具执行结果
     */
    public String callTool(String fullToolName, Map<String, Object> arguments) {
        McpToolInfo toolInfo = tools.get(fullToolName);
        if (toolInfo == null) {
            throw new IllegalArgumentException("MCP 工具不存在: " + fullToolName);
        }

        String serverName = toolInfo.serverName();
        McpProcessInfo processInfo = processInfos.get(serverName);

        if (processInfo == null || !processInfo.process().isAlive()) {
            throw new IllegalStateException("MCP 服务器未运行: " + serverName);
        }

        Process process = processInfo.process();
        long requestId = requestIdGenerator.getAndIncrement();

        try {
            // 构建参数 JSON
            ONode argsNode = new ONode().asObject();
            if (arguments != null) {
                for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                    argsNode.set(entry.getKey(), entry.getValue());
                }
            }

            // 构建 MCP 工具调用请求
            String request = String.format(
                    "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":%s}}\n",
                    requestId,
                    toolInfo.name(),
                    argsNode.toJson()
            );

            process.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();

            log.info("调用 MCP 工具: tool={}, serverName={}", fullToolName, serverName);

            // 等待响应（简化实现，实际应该异步等待）
            int maxWait = 100;
            for (int i = 0; i < maxWait; i++) {
                McpResponse response = pendingResponses.remove(requestId);
                if (response != null) {
                    if (response.error() != null) {
                        throw new RuntimeException("MCP 工具调用失败: " + response.error());
                    }
                    // 解析结果
                    ONode resultNode = (ONode) response.result();
                    if (resultNode != null) {
                        ONode content = resultNode.get("content");
                        if (content != null && content.isArray()) {
                            List<ONode> contentList = content.getArray();
                            if (!contentList.isEmpty()) {
                                ONode firstContent = contentList.get(0);
                                String type = firstContent.get("type").getString();
                                if ("text".equals(type)) {
                                    return firstContent.get("text").getString();
                                }
                            }
                        }
                    }
                    return response.result().toString();
                }
                Thread.sleep(50);
            }

            return "MCP 工具调用已发送，等待响应超时";

        } catch (Exception e) {
            log.error("调用 MCP 工具失败: tool={}", fullToolName, e);
            throw new RuntimeException("调用 MCP 工具失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查命令行工具是否可用
     *
     * @param command 命令名称
     * @return 是否可用
     */
    public boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command, "--version");
            Process process = pb.start();
            boolean completed = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return completed && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取可用的 MCP 命令行工具
     */
    public Map<String, Boolean> getAvailableCommands() {
        Map<String, Boolean> commands = new LinkedHashMap<>();
        commands.put("npx", isCommandAvailable("npx"));
        commands.put("node", isCommandAvailable("node"));
        commands.put("python", isCommandAvailable("python"));
        commands.put("python3", isCommandAvailable("python3"));
        commands.put("uvx", isCommandAvailable("uvx"));
        return commands;
    }

    // ==================== 内部记录类 ====================

    /**
     * MCP 服务器信息
     *
     * @param name     服务器名称
     * @param command  启动命令
     * @param args     命令参数
     * @param env      环境变量
     * @param disabled 是否禁用
     */
    public record McpServerInfo(
            String name,
            String command,
            List<String> args,
            Map<String, String> env,
            boolean disabled
    ) {
    }

    /**
     * MCP 进程信息
     *
     * @param serverName 服务器名称
     * @param process    进程对象
     * @param startTime  启动时间
     * @param status     服务器状态
     */
    public record McpProcessInfo(
            String serverName,
            Process process,
            long startTime,
            McpServerStatus status
    ) {
        public McpProcessInfo withStatus(McpServerStatus newStatus) {
            return new McpProcessInfo(serverName, process, startTime, newStatus);
        }
    }

    /**
     * MCP 工具信息
     *
     * @param name        工具名称
     * @param fullName    工具全名（server.toolName）
     * @param serverName  所属服务器名称
     * @param description 工具描述
     * @param parameters  参数定义
     */
    public record McpToolInfo(
            String name,
            String fullName,
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

    /**
     * MCP 响应
     *
     * @param id     请求 ID
     * @param result 结果
     * @param error  错误信息
     */
    record McpResponse(
            long id,
            Object result,
            String error
    ) {
    }
}