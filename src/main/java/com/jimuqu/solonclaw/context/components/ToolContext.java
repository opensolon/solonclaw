package com.jimuqu.solonclaw.context.components;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Init;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 工具上下文组件
 * <p>
 * 负责构建可用工具列表和描述
 *
 * @author SolonClaw
 */
@Component
public class ToolContext {

    private static final Logger log = LoggerFactory.getLogger(ToolContext.class);

    @Inject
    private com.jimuqu.solonclaw.tool.ToolRegistry toolRegistry;

    @Inject
    private com.jimuqu.solonclaw.context.config.ContextBuilderConfig config;

    @Init
    public void init() {
        log.info("工具上下文组件初始化完成");
    }

    /**
     * 构建工具上下文
     *
     * @param sessionId   会话ID（暂未使用，保留用于未来扩展）
     * @param userMessage 用户消息（暂未使用，保留用于未来扩展）
     * @param options     构建选项
     * @return 工具上下文文本，如果没有工具返回空字符串
     */
    public String build(String sessionId, String userMessage, Map<String, Object> options) {
        // 从配置获取启用状态
        boolean enabled = config != null && config.isToolsEnabled();
        if (!enabled) {
            log.debug("工具上下文已禁用，跳过构建");
            return "";
        }

        if (toolRegistry == null) {
            log.debug("工具注册器未初始化，跳过工具上下文构建");
            return "";
        }

        try {
            // 从配置获取参数或使用选项中的覆盖配置
            boolean includeParameters = getIncludeParameters(options);

            // 构建工具描述
            return buildToolsDescription(includeParameters);

        } catch (Exception e) {
            log.warn("构建工具上下文失败", e);
            return "";
        }
    }

    /**
     * 获取是否包含参数信息
     */
    private boolean getIncludeParameters(Map<String, Object> options) {
        if (options != null && options.containsKey("includeParameters")) {
            return (Boolean) options.get("includeParameters");
        }
        return config != null && config.isIncludeParameters();
    }

    /**
     * 构建工具描述
     */
    private String buildToolsDescription(boolean includeParameters) {
        StringBuilder description = new StringBuilder();
        var tools = toolRegistry.getTools();

        if (tools.isEmpty()) {
            return description.toString();
        }

        description.append("## 可用工具\n\n");
        description.append("以下是你可以使用的工具：\n\n");

        for (var entry : tools.entrySet()) {
            var tool = entry.getValue();
            String toolName = entry.getKey();

            description.append(String.format("### %s\n%s\n\n",
                toolName,
                tool.description()
            ));

            // 添加参数信息
            if (includeParameters) {
                var parameters = tool.getParameters();
                if (!parameters.isEmpty()) {
                    description.append("**参数**:\n");
                    for (var param : parameters) {
                        description.append(String.format("- `%s` (%s): %s\n",
                            param.name(),
                            param.type(),
                            param.description()
                        ));
                    }
                    description.append("\n");
                }
            }
        }

        description.append("## 使用指南\n\n");
        description.append("根据用户的请求，选择合适的工具来完成任务。");
        description.append("如果需要执行复杂操作，可以组合使用多个工具。\n");

        log.debug("构建工具描述: 工具数={}", tools.size());
        return description.toString();
    }
}