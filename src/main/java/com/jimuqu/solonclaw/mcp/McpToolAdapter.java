package com.jimuqu.solonclaw.mcp;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Init;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具适配器
 * <p>
 * 将 MCP 服务器提供的工具适配为 Solon AI 的工具格式，
 * 并注册到 ToolRegistry 中供 Agent 使用。
 *
 * @author SolonClaw
 */
@Component
public class McpToolAdapter {

    private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);

    @Inject
    private McpManager mcpManager;

    /**
     * 初始化：自动启动配置的 MCP 服务器
     */
    @Init
    public void init() {
        log.info("初始化 MCP 工具适配器...");

        // 自动启动未禁用的 MCP 服务器
        List<McpManager.McpServerInfo> servers = mcpManager.getServers();
        for (McpManager.McpServerInfo server : servers) {
            if (!server.disabled()) {
                try {
                    log.info("自动启动 MCP 服务器: {}", server.name());
                    mcpManager.startServer(server.name());
                } catch (Exception e) {
                    log.warn("自动启动 MCP 服务器失败: {} - {}", server.name(), e.getMessage());
                }
            }
        }

        log.info("MCP 工具适配器初始化完成");
    }

    /**
     * 获取所有可用的 MCP 工具
     * 用于显示给 Agent 或用户
     */
    public List<McpManager.McpToolInfo> getAvailableTools() {
        return mcpManager.getTools();
    }

    /**
     * 执行 MCP 工具调用
     * 这是动态工具调用的入口方法
     *
     * @param fullToolName 工具全名（server.toolName）
     * @param arguments    参数
     * @return 执行结果
     */
    public String executeTool(String fullToolName, Map<String, Object> arguments) {
        log.debug("执行 MCP 工具: tool={}, args={}", fullToolName, arguments);
        return mcpManager.callTool(fullToolName, arguments);
    }

    /**
     * 检查 MCP 工具是否可用
     *
     * @param fullToolName 工具全名
     * @return 是否可用
     */
    public boolean isToolAvailable(String fullToolName) {
        McpManager.McpToolInfo tool = mcpManager.getTool(fullToolName);
        if (tool == null) {
            return false;
        }
        return mcpManager.isServerRunning(tool.serverName());
    }

    /**
     * 获取 MCP 工具的描述信息
     * 用于生成系统提示
     */
    public String getToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        List<McpManager.McpToolInfo> tools = mcpManager.getTools();

        if (tools.isEmpty()) {
            return "当前没有可用的 MCP 工具。";
        }

        sb.append("可用的 MCP 工具：\n");
        for (McpManager.McpToolInfo tool : tools) {
            sb.append("- ").append(tool.fullName()).append(": ").append(tool.description());

            // 添加参数信息
            Map<String, McpManager.McpParameterInfo> params = tool.parameters();
            if (!params.isEmpty()) {
                sb.append(" (参数: ");
                boolean first = true;
                for (Map.Entry<String, McpManager.McpParameterInfo> entry : params.entrySet()) {
                    if (!first) sb.append(", ");
                    first = false;
                    McpManager.McpParameterInfo param = entry.getValue();
                    sb.append(entry.getKey());
                    if (param.required()) {
                        sb.append("*");
                    }
                    sb.append(": ").append(param.type());
                }
                sb.append(")");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 将 MCP 工具转换为 ToolRegistry 可识别的格式
     * 返回工具信息 Map，供 AgentService 使用
     */
    public Map<String, Map<String, Object>> getToolsForRegistry() {
        Map<String, Map<String, Object>> result = new HashMap<>();

        for (McpManager.McpToolInfo tool : mcpManager.getTools()) {
            Map<String, Object> toolInfo = new HashMap<>();
            toolInfo.put("name", tool.fullName());
            toolInfo.put("description", tool.description());
            toolInfo.put("type", "mcp");
            toolInfo.put("serverName", tool.serverName());
            toolInfo.put("parameters", tool.parameters());
            result.put(tool.fullName(), toolInfo);
        }

        return result;
    }
}