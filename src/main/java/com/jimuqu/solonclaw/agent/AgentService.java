package com.jimuqu.solonclaw.agent;

import com.jimuqu.solonclaw.memory.MemoryService;
import com.jimuqu.solonclaw.tool.ToolRegistry;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.ChatResponse;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Agent 服务
 * <p>
 * 使用 ChatModel 进行 AI 对话，配合工具注册系统
 * 后续可升级为 ReActAgent 或 TeamAgent
 *
 * @author SolonClaw
 */
@Component
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    @Inject
    private ChatModel chatModel;

    @Inject
    private MemoryService memoryService;

    @Inject
    private ToolRegistry toolRegistry;

    /**
     * 智能对话
     *
     * @param message   用户消息
     * @param sessionId 会话ID
     * @return AI 响应
     */
    public String chat(String message, String sessionId) {
        log.info("Agent chat: sessionId={}, message={}", sessionId, message);

        try {
            // 保存用户消息
            memoryService.saveUserMessage(sessionId, message);

            // 构建带有工具信息的系统提示
            String systemPrompt = buildSystemPrompt();

            // 构建完整消息（包含历史上下文）
            String fullPrompt = buildFullPrompt(message, sessionId);

            // 调用 ChatModel
            ChatResponse response = chatModel.prompt(systemPrompt + "\n\n" + fullPrompt).call();

            // 获取响应内容
            String content = response.getContent();

            // 保存 AI 响应
            memoryService.saveAssistantMessage(sessionId, content);

            log.info("Agent 响应: sessionId={}, length={}", sessionId, content.length());
            return content;

        } catch (Exception e) {
            log.error("Agent 对话异常", e);
            throw new RuntimeException("AI 对话失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建系统提示（包含工具信息）
     */
    private String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 SolonClaw 智能助手。\n\n");
        prompt.append("你的职责是：\n");
        prompt.append("1. 理解用户的需求和问题\n");
        prompt.append("2. 提供准确、有用的回答\n");
        prompt.append("3. 保持友好、专业的态度\n\n");

        // 添加可用工具信息
        if (!toolRegistry.getTools().isEmpty()) {
            prompt.append("当前已注册的工具：\n");
            for (Map.Entry<String, ToolRegistry.ToolInfo> entry : toolRegistry.getTools().entrySet()) {
                ToolRegistry.ToolInfo tool = entry.getValue();
                prompt.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
            }
            prompt.append("\n注意：用户可能希望使用这些工具，但当前版本暂不自动调用工具。");
        }

        return prompt.toString();
    }

    /**
     * 构建完整提示（包含历史上下文）
     */
    private String buildFullPrompt(String message, String sessionId) {
        List<Map<String, String>> history = memoryService.getSessionHistory(sessionId);

        StringBuilder prompt = new StringBuilder();

        // 添加历史消息（最近10条）
        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            Map<String, String> msg = history.get(i);
            String role = msg.get("role");
            String content = msg.get("content");

            if ("tool".equals(role)) {
                continue;
            }

            switch (role) {
                case "user" -> prompt.append("用户: ").append(content).append("\n");
                case "assistant" -> prompt.append("助手: ").append(content).append("\n");
                case "system" -> prompt.append("系统: ").append(content).append("\n");
            }
        }

        // 添加当前消息
        prompt.append("用户: ").append(message);

        return prompt.toString();
    }

    /**
     * 获取会话历史
     */
    public List<Map<String, String>> getHistory(String sessionId) {
        return memoryService.getSessionHistory(sessionId);
    }

    /**
     * 清空会话历史
     */
    public void clearHistory(String sessionId) {
        memoryService.deleteSession(sessionId);
        log.info("清空会话历史: sessionId={}", sessionId);
    }

    /**
     * 获取可用工具列表
     */
    public Map<String, ToolRegistry.ToolInfo> getAvailableTools() {
        return toolRegistry.getTools();
    }
}