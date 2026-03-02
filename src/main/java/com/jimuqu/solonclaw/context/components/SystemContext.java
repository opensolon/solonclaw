package com.jimuqu.solonclaw.context.components;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Init;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 系统上下文组件
 * <p>
 * 负责构建系统提示词和配置信息
 *
 * @author SolonClaw
 */
@Component
public class SystemContext {

    private static final Logger log = LoggerFactory.getLogger(SystemContext.class);

    @Inject
    private com.jimuqu.solonclaw.tool.ToolRegistry toolRegistry;

    @Inject
    private com.jimuqu.solonclaw.context.config.ContextBuilderConfig config;

    /**
     * 默认系统提示词
     */
    private static final String DEFAULT_SYSTEM_PROMPT = """
        你是 SolonClaw 智能助手，一个具备工具调用能力的 AI Agent。

        你的职责是：
        1. 理解用户的需求和问题
        2. 根据需要调用可用的工具来完成任务
        3. 综合分析工具执行结果，提供准确、有用的回答
        4. 保持友好、专业的态度

        回答问题时请：
        - 使用中文回复
        - 结构化输出，便于阅读
        - 如果使用了工具，请说明执行了什么操作和结果
        """;

    /**
     * 自定义系统提示词
     */
    private String customSystemPrompt;

    @Init
    public void init() {
        log.info("系统上下文组件初始化完成");
    }

    /**
     * 构建系统上下文
     *
     * @param sessionId   会话ID（暂未使用，保留用于未来扩展）
     * @param userMessage 用户消息（暂未使用，保留用于未来扩展）
     * @param options     构建选项
     * @return 系统上下文文本
     */
    public String build(String sessionId, String userMessage, Map<String, Object> options) {
        // 如果有自定义提示词，使用自定义的
        if (customSystemPrompt != null && !customSystemPrompt.isEmpty()) {
            return customSystemPrompt;
        }

        // 否则使用默认提示词
        return DEFAULT_SYSTEM_PROMPT;
    }

    /**
     * 构建包含工具描述的系统上下文
     *
     * @param sessionId   会话ID
     * @param userMessage 用户消息
     * @param options     构建选项
     * @return 完整的系统上下文（包含工具描述）
     */
    public String buildWithTools(String sessionId, String userMessage, Map<String, Object> options) {
        StringBuilder context = new StringBuilder();

        // 添加基本系统提示词
        context.append(build(sessionId, userMessage, options));

        // 添加工具描述
        if (toolRegistry != null) {
            String toolsDescription = buildToolsDescription();
            if (!toolsDescription.isEmpty()) {
                context.append("\n\n## 可用工具\n\n");
                context.append(toolsDescription);
            }
        }

        return context.toString();
    }

    /**
     * 构建工具描述
     */
    private String buildToolsDescription() {
        StringBuilder description = new StringBuilder();
        var tools = toolRegistry.getTools();

        if (tools.isEmpty()) {
            return description.toString();
        }

        for (var entry : tools.entrySet()) {
            var tool = entry.getValue();
            description.append(String.format("### %s\n- %s\n\n",
                entry.getKey(),
                tool.description()
            ));

            // 添加参数信息
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

        log.debug("构建工具描述: 工具数={}", tools.size());
        return description.toString();
    }

    // Getters and Setters

    public String getCustomSystemPrompt() {
        return customSystemPrompt;
    }

    public void setCustomSystemPrompt(String customSystemPrompt) {
        this.customSystemPrompt = customSystemPrompt;
    }
}