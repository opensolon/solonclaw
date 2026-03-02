package com.jimuqu.solonclaw.agent;

import com.jimuqu.solonclaw.context.Context;
import com.jimuqu.solonclaw.context.ContextBuilder;
import com.jimuqu.solonclaw.memory.MemoryService;
import com.jimuqu.solonclaw.tool.ToolRegistry;
import com.jimuqu.solonclaw.util.FileService;
import org.noear.solon.ai.agent.AgentSession;
import org.noear.solon.ai.agent.react.ReActAgent;
import org.noear.solon.ai.agent.react.ReActInterceptor;
import org.noear.solon.ai.agent.react.ReActTrace;
import org.noear.solon.ai.agent.session.InMemoryAgentSession;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent 服务
 * <p>
 * 使用 Solon AI 的 ReActAgent 实现智能对话和工具调用
 * 支持自动推理、工具调用和会话记忆
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

    @Inject
    private AgentConfig agentConfig;

    @Inject
    private FileService fileService;

    @Inject(required = false)
    private com.jimuqu.solonclaw.learning.KnowledgeStore knowledgeStore;

    @Inject(required = false)
    private com.jimuqu.solonclaw.learning.LearningOrchestrator learningOrchestrator;

    @Inject(required = false)
    private ContextBuilder contextBuilder;

    /**
     * ReActAgent 实例（延迟初始化）
     */
    private volatile ReActAgent reactAgent;

    /**
     * 获取或创建 ReActAgent 实例
     */
    private ReActAgent getOrCreateAgent() {
        if (reactAgent == null) {
            synchronized (this) {
                if (reactAgent == null) {
                    reactAgent = buildReActAgent();
                }
            }
        }
        return reactAgent;
    }

    /**
     * 构建 ReActAgent
     */
    private ReActAgent buildReActAgent() {
        log.info("开始构建 ReActAgent...");

        // 获取所有工具对象
        List<Object> toolObjects = toolRegistry.getToolObjects();
        log.debug("准备注册 {} 个工具", toolObjects.size());

        // 使用链式构建器构建 ReActAgent
        var builder = ReActAgent.of(chatModel)
                .name("solonclaw_agent")
                .role("SolonClaw 智能助手，能够理解用户需求、执行工具命令并提供专业回答")
                .instruction(buildAgentInstruction())
                .maxSteps(agentConfig.getMaxToolIterations())
                .sessionWindowSize(agentConfig.getMaxHistoryMessages())
                .retryConfig(3, 1000L)
                .modelOptions(options -> options.temperature(0.7));

        // 注册所有工具（在 build 之前链式调用）
        for (Object tool : toolObjects) {
            builder.defaultToolAdd(tool);
            log.debug("注册工具: {}", tool.getClass().getSimpleName());
        }

        // 添加日志拦截器（在 build 之前链式调用）
        builder.defaultInterceptorAdd(new LoggingInterceptor());

        // 构建 Agent
        ReActAgent agent = builder.build();

        log.info("ReActAgent 构建完成，已注册 {} 个工具", toolObjects.size());

        return agent;
    }

    /**
     * 构建 Agent 指令
     * <p>
     * 注意：详细的工具描述已由 ToolContext 组件提供
     */
    private String buildAgentInstruction() {
        return """
                你是 SolonClaw 智能助手，一个具备工具调用能力的 AI Agent。

                你的职责是：
                1. 理解用户的需求和问题
                2. 根据需要调用可用的工具来完成任务
                3. 综合分析工具执行结果，提供准确、有用的回答
                4. 保持友好、专业的态度

                ## 使用指南

                当用户提出以下需求时，主动使用相应工具：

                1. **"安装 xxx 包"** → 判断是 Python 还是 Node.js，使用对应安装工具
                2. **"下载 xxx 项目"** → 使用 GitHub 克隆工具
                3. **"创建 xxx 技能"** → 使用 JSON 技能创建工具
                4. **"截图" / "访问网站" / "生成图片"** → 使用 Shell 工具配合浏览器工具

                ## 图片访问功能 ⭐ 重要

                系统会自动为你生成的图片文件创建临时访问链接，用户可以直接在聊天界面查看图片。

                **工作原理**：
                1. 当你生成图片文件时（如截图保存为 `/tmp/screenshot.png`）
                2. 系统会自动将其替换为临时访问链接：`/api/file?token=xxxxx`
                3. 链接有效期 5 分钟，过期后自动失效
                4. 用户可以点击链接直接查看图片

                **使用方法**：
                - 只需要在响应中正常提供文件路径即可（如 `/tmp/screenshot.png`）
                - 系统会自动处理，你无需手动生成链接

                ## 注意事项

                - Shell 命令执行有超时限制，请避免执行长时间运行的命令
                - 安装包时，如果用户指定了版本号，请使用用户指定的版本
                - 对于文件操作，请确认路径正确
                - 如果工具执行失败，请尝试其他方法或告知用户
                - 图片文件路径会被自动转换为临时访问链接，无需手动处理

                回答问题时请：
                - 使用中文回复
                - 结构化输出，便于阅读
                - 如果使用了工具，请说明执行了什么操作和结果
                """;
    }

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

            // ========== 使用上下文构建器构建完整的上下文 ==========
            String enhancedMessage = message;
            if (contextBuilder != null) {
                try {
                    Context context = contextBuilder.build(sessionId, message, null);
                    enhancedMessage = context.buildPrompt(message);
                    log.debug("已使用 ContextBuilder 构建上下文: sessionId={}", sessionId);
                } catch (Exception e) {
                    log.warn("使用 ContextBuilder 构建上下文失败，回退到原始消息", e);
                    enhancedMessage = message;
                }
            } else {
                // ========== 回退到旧逻辑：学习系统集成（知识检索） ==========
                if (knowledgeStore != null) {
                    try {
                        String knowledgeContext = retrieveRelevantKnowledge(message, sessionId);
                        if (knowledgeContext != null && !knowledgeContext.isEmpty()) {
                            enhancedMessage = "[相关经验]\n" + knowledgeContext + "\n[用户问题]\n" + message;
                            log.debug("已注入相关知识上下文: sessionId={}", sessionId);
                        }
                    } catch (Exception e) {
                        log.warn("检索知识失败，继续正常对话", e);
                    }
                }
            }

            // 获取历史记录
            List<Map<String, String>> history = memoryService.getSessionHistory(sessionId);
            log.info("加载历史记录: sessionId={}, 历史消息数={}", sessionId, history.size());

            // 获取 ReActAgent
            ReActAgent agent = getOrCreateAgent();

            // 创建会话并添加历史消息
            AgentSession session = InMemoryAgentSession.of(sessionId);

            // 将历史消息转换为 ChatMessage 并添加到 session 中
            if (!history.isEmpty()) {
                List<ChatMessage> historyMessages = new ArrayList<>();
                for (Map<String, String> msg : history) {
                    String role = msg.get("role");
                    String content = msg.get("content");
                    if ("user".equals(role)) {
                        historyMessages.add(ChatMessage.ofUser(content));
                        log.debug("历史用户消息: {}", truncate(content, 50));
                    } else if ("assistant".equals(role)) {
                        historyMessages.add(ChatMessage.ofAssistant(content));
                        log.debug("历史助手消息: {}", truncate(content, 50));
                    }
                }
                session.addMessage(historyMessages);
                log.info("已将 {} 条历史消息添加到 session", historyMessages.size());
            }

            // 调用 ReActAgent（使用增强后的消息）
            String response = agent.prompt(enhancedMessage)
                    .session(session)
                    .call()
                    .getContent();

            // 保存原始 AI 响应（不包含 Base64）
            memoryService.saveAssistantMessage(sessionId, response);

            // 处理响应中的图片文件（转换为 Base64）- 只在返回时处理
            response = fileService.processImagesInContent(response);

            log.info("Agent 响应: sessionId={}, length={}", sessionId, response.length());

            // ========== 学习系统集成：对话完成触发学习 ==========
            if (learningOrchestrator != null) {
                try {
                    learningOrchestrator.onChatComplete(sessionId, response, null);
                } catch (Exception e) {
                    log.warn("触发学习流程失败", e);
                }
            }

            return response;

        } catch (Throwable e) {
            log.error("Agent 对话异常", e);

            // ========== 学习系统集成：错误触发学习 ==========
            if (learningOrchestrator != null) {
                try {
                    learningOrchestrator.onChatComplete(sessionId, null, e);
                } catch (Exception learnException) {
                    log.warn("错误学习流程失败", learnException);
                }
            }

            throw new RuntimeException("AI 对话失败: " + e.getMessage(), e);
        }
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

    /**
     * 流式对话
     *
     * @param message   用户消息
     * @param sessionId 会话ID
     * @param eventConsumer 事件消费者，接收流式事件
     */
    public void chatStream(String message, String sessionId, Consumer<StreamEvent> eventConsumer) {
        log.info("Agent chatStream: sessionId={}, message={}", sessionId, message);

        // 保存用户消息
        memoryService.saveUserMessage(sessionId, message);

        // 发送开始事件
        eventConsumer.accept(new StreamEvent(StreamEventType.START, "开始处理", null));

        try {
            // 获取历史记录
            List<Map<String, String>> history = memoryService.getSessionHistory(sessionId);
            log.info("加载历史记录: sessionId={}, 历史消息数={}", sessionId, history.size());

            // 获取 ReActAgent
            ReActAgent agent = getOrCreateAgent();

            // 创建会话并添加历史消息
            AgentSession session = InMemoryAgentSession.of(sessionId);

            // 将历史消息转换为 ChatMessage 并添加到 session 中
            if (!history.isEmpty()) {
                List<ChatMessage> historyMessages = new ArrayList<>();
                for (Map<String, String> msg : history) {
                    String role = msg.get("role");
                    String content = msg.get("content");
                    if ("user".equals(role)) {
                        historyMessages.add(ChatMessage.ofUser(content));
                        log.debug("历史用户消息: {}", truncate(content, 50));
                    } else if ("assistant".equals(role)) {
                        historyMessages.add(ChatMessage.ofAssistant(content));
                        log.debug("历史助手消息: {}", truncate(content, 50));
                    }
                }
                session.addMessage(historyMessages);
                log.info("已将 {} 条历史消息添加到 session", historyMessages.size());
            }

            // 调用 ReActAgent 并获取响应
            String response = agent.prompt(message)
                    .session(session)
                    .call()
                    .getContent();

            // 保存原始 AI 响应（不包含 Base64）
            memoryService.saveAssistantMessage(sessionId, response);

            // 处理响应中的图片文件（转换为 Base64）- 只在返回时处理
            response = fileService.processImagesInContent(response);

            // 发送内容事件（模拟流式输出，将响应分段发送）
            sendContentInChunks(response, eventConsumer);

            // 发送结束事件
            eventConsumer.accept(new StreamEvent(StreamEventType.END, "处理完成", null));

        } catch (Throwable e) {
            log.error("Agent 对话异常", e);
            eventConsumer.accept(new StreamEvent(StreamEventType.ERROR, "处理失败: " + e.getMessage(), e));
        }
    }

    /**
     * 将内容分块发送，模拟流式输出效果
     */
    private void sendContentInChunks(String content, Consumer<StreamEvent> eventConsumer) {
        if (content == null || content.isEmpty()) {
            return;
        }

        // 按句子分割内容
        String[] sentences = content.split("(?<=[。！？\\.!?])\\s*");

        for (String sentence : sentences) {
            if (!sentence.isEmpty()) {
                eventConsumer.accept(new StreamEvent(StreamEventType.CONTENT, sentence));
                // 添加短暂延迟，模拟打字效果
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * 流式事件类型
     */
    public enum StreamEventType {
        /** 开始处理 */
        START,
        /** 文本内容 */
        CONTENT,
        /** 工具调用 */
        TOOL_CALL,
        /** 工具调用完成 */
        TOOL_DONE,
        /** 处理完成 */
        END,
        /** 错误 */
        ERROR
    }

    /**
     * 流式事件
     *
     * @param type 事件类型
     * @param content 事件内容
     * @param error 错误信息（仅 ERROR 类型）
     */
    public record StreamEvent(
            StreamEventType type,
            String content,
            Throwable error
    ) {
        public StreamEvent(StreamEventType type, String content) {
            this(type, content, null);
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"type\":\"").append(type).append("\"");
            if (content != null) {
                sb.append(",\"content\":").append(escapeJson(content));
            }
            if (error != null) {
                sb.append(",\"error\":").append(escapeJson(error.getMessage()));
            }
            sb.append("}");
            return sb.toString();
        }

        private String escapeJson(String value) {
            if (value == null) return "null";
            return "\"" + value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t") + "\"";
        }
    }

    /**
     * 截断文本（用于日志）
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "... (已截断，总长度: " + text.length() + ")";
    }

    /**
     * 检索相关知识
     * <p>
     * 从知识库中检索与当前问题相关的经验
     * <p>
     * 注意：此方法已废弃，请使用 ContextBuilder 构建上下文
     *
     * @param message   用户消息
     * @param sessionId 会话ID
     * @return 相关知识内容，如果没有相关知识返回 null
     * @deprecated 使用 ContextBuilder 代替
     */
    @Deprecated
    private String retrieveRelevantKnowledge(String message, String sessionId) {
        if (knowledgeStore == null) {
            return null;
        }

        try {
            // 提取关键词（简化实现，使用前50个字符）
            String keyword = extractKeyword(message);

            // 搜索相关经验
            List<com.jimuqu.solonclaw.memory.SessionStore.Experience> experiences =
                knowledgeStore.searchAllExperiences(keyword, 5);

            if (experiences == null || experiences.isEmpty()) {
                log.debug("未找到相关知识: sessionId={}, keyword={}", sessionId, keyword);
                return null;
            }

            // 构建知识上下文
            StringBuilder knowledgeContext = new StringBuilder();
            knowledgeContext.append("基于历史经验，以下信息可能对你有帮助：\n\n");

            for (com.jimuqu.solonclaw.memory.SessionStore.Experience exp : experiences) {
                if (exp.success() && exp.confidence() >= 0.6) {
                    String content = exp.content();
                    int contentLength = content != null ? content.length() : 0;
                    knowledgeContext.append(String.format("- **%s**: %s (置信度: %.1f%%)\n",
                        exp.title(),
                        content != null ? content.substring(0, Math.min(100, contentLength)) : "",
                        exp.confidence() * 100
                    ));
                }
            }

            log.debug("检索到相关知识: sessionId={}, 经验数={}", sessionId, experiences.size());
            return knowledgeContext.toString();

        } catch (Exception e) {
            log.warn("检索知识失败: sessionId={}", sessionId, e);
            return null;
        }
    }

    /**
     * 提取关键词
     * <p>
     * 从消息中提取关键词用于知识检索
     * <p>
     * 注意：此方法已废弃
     *
     * @param message 用户消息
     * @return 关键词
     * @deprecated 使用 ContextBuilder 代替
     */
    @Deprecated
    private String extractKeyword(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // 简化实现：使用前20个字符作为关键词
        // 实际项目中可以使用更复杂的 NLP 技术
        int maxLength = Math.min(20, message.length());
        return message.substring(0, maxLength).trim();
    }

    /**
     * 日志拦截器
     */
    private static class LoggingInterceptor implements ReActInterceptor {

        private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

        /**
         * 记录内容的最大长度（避免日志过大）
         */
        private static final int MAX_LOG_LENGTH = 2000;

        @Override
        public void onAgentStart(ReActTrace trace) {
            log.debug("Agent 开始执行");
        }

        @Override
        public void onThought(ReActTrace trace, String thought) {
            // 记录 Agent 的思考过程
            String truncatedThought = truncate(thought, MAX_LOG_LENGTH);
            log.debug("Agent 思考: {}", truncatedThought);

            // 如果思考内容过长，额外记录完整内容到 DEBUG 级别
            if (thought.length() > MAX_LOG_LENGTH) {
                log.debug("Agent 思考（完整）: {}", thought);
            }
        }

        @Override
        public void onAction(ReActTrace trace, String toolName, Map<String, Object> args) {
            // 记录工具调用
            log.info("Agent 执行工具: {} 参数: {}", toolName, args);

            // 如果参数包含大段内容（如代码），额外记录详情
            if (args != null && args.values().stream().anyMatch(v -> v != null && v.toString().length() > 500)) {
                log.debug("Agent 执行工具参数详情: {} 参数: {}", toolName, formatDetailedArgs(args));
            }
        }

        @Override
        public void onAgentEnd(ReActTrace trace) {
            log.debug("Agent 执行结束");
        }

        /**
         * 截断长文本
         */
        private String truncate(String text, int maxLength) {
            if (text == null) return null;
            if (text.length() <= maxLength) return text;
            return text.substring(0, maxLength) + "... (截断，总长度: " + text.length() + ")";
        }

        /**
         * 格式化详细参数（用于 DEBUG 日志）
         */
        private String formatDetailedArgs(Map<String, Object> args) {
            if (args == null || args.isEmpty()) return "{}";

            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                if (!first) sb.append(", ");
                first = false;

                String valueStr = String.valueOf(entry.getValue());
                if (valueStr.length() > 200) {
                    valueStr = valueStr.substring(0, 200) + "... (长度: " + valueStr.length() + ")";
                }
                sb.append(entry.getKey()).append("=").append(valueStr);
            }
            sb.append("}");
            return sb.toString();
        }
    }
}