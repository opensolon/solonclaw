package com.jimuqu.solonclaw.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solonclaw.context.Context;
import com.jimuqu.solonclaw.context.ContextBuilder;
import com.jimuqu.solonclaw.memory.MemoryService;
import com.jimuqu.solonclaw.memory.summary.MemorySummarizationConfig;
import com.jimuqu.solonclaw.memory.summary.SummarizationStrategyFactory;
import com.jimuqu.solonclaw.tool.ToolRegistry;
import org.noear.solon.ai.agent.AgentChunk;
import org.noear.solon.ai.agent.AgentSession;
import org.noear.solon.ai.agent.react.ReActAgent;
import org.noear.solon.ai.agent.react.ReActChunk;
import org.noear.solon.ai.agent.react.ReActInterceptor;
import org.noear.solon.ai.agent.react.ReActTrace;
import org.noear.solon.ai.agent.react.intercept.SummarizationInterceptor;
import org.noear.solon.ai.agent.react.intercept.SummarizationStrategy;
import org.noear.solon.ai.agent.react.task.ActionChunk;
import org.noear.solon.ai.agent.react.task.ReasonChunk;
import org.noear.solon.ai.agent.session.InMemoryAgentSession;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

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

    @Inject(required = false)
    private ChatModel chatModel;

    @Inject(required = false)
    private MemoryService memoryService;

    @Inject(required = false)
    private ToolRegistry toolRegistry;

    @Inject
    private AgentConfig agentConfig;

    @Inject(required = false)
    private com.jimuqu.solonclaw.learning.KnowledgeStore knowledgeStore;

    @Inject(required = false)
    private com.jimuqu.solonclaw.learning.LearningOrchestrator learningOrchestrator;

    @Inject(required = false)
    private ContextBuilder contextBuilder;

    @Inject(required = false)
    private com.jimuqu.solonclaw.skill.SkillsManager skillsManager;

    @Inject(required = false)
    private MemorySummarizationConfig summarizationConfig;

    @Inject(required = false)
    private SummarizationStrategyFactory summarizationStrategyFactory;

    /**
     * ReActAgent 实例（延迟初始化）
     */
    private volatile ReActAgent reactAgent;

    /**
     * 预热完成标志
     */
    private volatile boolean warmedUp = false;

    /**
     * 预热 ReActAgent
     */
    @Init
    public void warmup() {
        log.info("开始 ReActAgent 预热...");
        long startTime = System.currentTimeMillis();

        try {
            // 检查 chatModel 是否可用
            if (chatModel == null) {
                log.warn("ChatModel 未注入，跳过 ReActAgent 预热");
                warmedUp = false;
                return;
            }

            getOrCreateAgent();
            warmedUp = true;
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("ReActAgent 预热完成，耗时：{} ms", elapsed);
        } catch (Exception e) {
            log.error("ReActAgent 预热失败", e);
            warmedUp = false;
        }
    }

    public boolean isWarmedUp() {
        return warmedUp;
    }

    private ReActAgent getOrCreateAgent() {
        if (ObjUtil.isNull(reactAgent)) {
            synchronized (this) {
                if (ObjUtil.isNull(reactAgent)) {
                    reactAgent = buildReActAgent();
                }
            }
        }
        return reactAgent;
    }

    private ReActAgent buildReActAgent() {
        log.info("开始构建 ReActAgent...");

        if (chatModel == null) {
            throw new IllegalStateException("ChatModel 未注入，无法构建 ReActAgent");
        }

        // 注册内置工具
        List<Object> toolObjects = (toolRegistry != null) ? toolRegistry.getToolObjects() : new ArrayList<>();

        var builder = ReActAgent.of(chatModel)
                .name("solonclaw_agent")
                .role("SolonClaw 智能助手，能够理解用户需求、执行工具命令并提供专业回答")
                .instruction(buildAgentInstruction())
                .maxSteps(agentConfig.getMaxToolIterations())
                .sessionWindowSize(agentConfig.getMaxHistoryMessages())
                .retryConfig(5, 3000L)
                .modelOptions(options -> options.temperature(0.7));

        // 注册内置工具
        for (Object tool : toolObjects) {
            builder.defaultToolAdd(tool);
        }

        // 注册外部技能
        if (skillsManager != null) {
            List<org.noear.solon.ai.chat.skill.Skill> skills = skillsManager.getSkills();
            log.info("注册 {} 个外部技能", skills.size());
            for (org.noear.solon.ai.chat.skill.Skill skill : skills) {
                builder.defaultSkillAdd(skill);
            }
        }

        builder.defaultInterceptorAdd(new LoggingInterceptor());

        // 添加记忆摘要拦截器（如果配置启用）
        if (summarizationConfig != null && summarizationStrategyFactory != null) {
            SummarizationStrategy strategy = summarizationStrategyFactory.createStrategy(summarizationConfig, chatModel);
            if (strategy != null) {
                SummarizationInterceptor summarizationInterceptor = new SummarizationInterceptor(
                        summarizationConfig.getMaxMessages(),
                        strategy
                );
                builder.defaultInterceptorAdd(summarizationInterceptor);
                log.info("已启用记忆摘要系统，策略: {}，最大消息数: {}",
                        summarizationConfig.getStrategy(),
                        summarizationConfig.getMaxMessages());
            } else {
                log.info("记忆摘要策略为 null，跳过添加摘要拦截器");
            }
        } else {
            log.info("记忆摘要配置或工厂未注入，摘要功能未启用");
        }

        ReActAgent agent = builder.build();
        log.info("ReActAgent 构建完成，已注册 {} 个工具, {} 个技能", toolObjects.size(),
                skillsManager != null ? skillsManager.getSkills().size(): 0);

        return agent;
    }

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

                注意：详细的工具列表和参数说明已在系统上下文中提供，请参考使用。
                """;
    }

    /**
     * 智能对话
     */
    public String chat(String message, String sessionId) {
        log.info("Agent chat: sessionId={}, message={}", sessionId, message);

        try {
            memoryService.saveUserMessage(sessionId, message);

            String enhancedMessage = buildEnhancedMessage(message, sessionId);
            AgentSession session = buildSession(sessionId);

            ReActAgent agent = getOrCreateAgent();
            String response = agent.prompt(enhancedMessage)
                    .session(session)
                    .call()
                    .getContent();

            memoryService.saveAssistantMessage(sessionId, response);
            log.info("Agent 响应：sessionId={}, length={}", sessionId, response.length());

            triggerLearning(sessionId, response, null);
            return response;

        } catch (Throwable e) {
            log.error("Agent 对话异常", e);
            triggerLearning(sessionId, null, e);
            throw new RuntimeException("AI 对话失败：" + e.getMessage(), e);
        }
    }

    /**
     * 构建增强消息（带上下文）
     */
    private String buildEnhancedMessage(String message, String sessionId) {
        if (ObjUtil.isNull(contextBuilder)) {
            return message;
        }
        try {
            Context context = contextBuilder.build(sessionId, message, null);
            String enhanced = context.buildPrompt(message);
            log.debug("已使用 ContextBuilder 构建上下文：sessionId={}", sessionId);
            return enhanced;
        } catch (Exception e) {
            log.warn("使用 ContextBuilder 构建上下文失败，回退到原始消息", e);
            return message;
        }
    }

    /**
     * 构建 AgentSession 并加载历史记录
     */
    private AgentSession buildSession(String sessionId) {
        List<Map<String, String>> history = memoryService.getSessionHistory(sessionId);
        log.info("加载历史记录：sessionId={}, 历史消息数={}", sessionId, history.size());

        AgentSession session = InMemoryAgentSession.of(sessionId);

        if (CollUtil.isNotEmpty(history)) {
            List<ChatMessage> historyMessages = new ArrayList<>();
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if (StrUtil.equals("user", role)) {
                    historyMessages.add(ChatMessage.ofUser(content));
                } else if (StrUtil.equals("assistant", role)) {
                    historyMessages.add(ChatMessage.ofAssistant(content));
                }
            }
            session.addMessage(historyMessages);
        }
        return session;
    }

    /**
     * 触发学习流程
     */
    private void triggerLearning(String sessionId, String response, Throwable error) {
        if (ObjUtil.isNull(learningOrchestrator)) {
            return;
        }
        try {
            learningOrchestrator.onChatComplete(sessionId, response, error);
        } catch (Exception e) {
            log.warn("触发学习流程失败", e);
        }
    }

    public List<Map<String, String>> getHistory(String sessionId) {
        return memoryService.getSessionHistory(sessionId);
    }

    public void clearHistory(String sessionId) {
        memoryService.deleteSession(sessionId);
        log.info("清空会话历史：sessionId={}", sessionId);
    }

    public Map<String, ToolRegistry.ToolInfo> getAvailableTools() {
        return toolRegistry.getTools();
    }

    /**
     * 重新创建 Agent（用于技能/工具更新后）
     */
    public synchronized void reloadAgent() {
        log.info("触发 Agent 重新加载...");
        this.reactAgent = null;
        getOrCreateAgent();
        log.info("Agent 重新加载完成");
    }

    /**
     * 流式对话（支持 ReAct 思考过程和工具调用实时推送）
     * <p>
     * 使用 ReActAgent.stream() 方法，通过 Flux<AgentChunk> 实时推送思考过程和工具调用事件
     */
    public void chatStream(String message, String sessionId, Consumer<StreamEvent> eventConsumer) {
        log.info("Agent chatStream: sessionId={}, message={}", sessionId, message);

        try {
            memoryService.saveUserMessage(sessionId, message);
            eventConsumer.accept(new StreamEvent(StreamEventType.START, "开始处理"));

            String enhancedMessage = buildEnhancedMessage(message, sessionId);
            AgentSession session = buildSession(sessionId);

            ReActAgent agent = getOrCreateAgent();

            // 使用 stream() 方法获取流式输出
            Flux<AgentChunk> chunks = agent.prompt(enhancedMessage)
                    .session(session)
                    .stream();

            StringBuilder finalAnswer = new StringBuilder();

            chunks.doOnNext(chunk -> {
                        if (chunk instanceof ReasonChunk reasonChunk) {
                            // 思考过程或最终答案 - 直接使用 isThinking() 判断
                            ChatMessage chatMessage = reasonChunk.getMessage();
                            String content = chatMessage.getContent();
                            Boolean isThinking = chatMessage.isThinking();

                            // 调试日志
                            String preview = content.length() > 50 ? content.substring(0, 50) + "..." : content;
                            log.info("[Stream] isThinking={}, content={}", isThinking, preview);

                            // 前端逻辑：isThinking=true 显示思考，isThinking=false 显示答案
                            // 直接使用 isThinking 标志
                            eventConsumer.accept(new StreamEvent(StreamEventType.CONTENT, content, null, isThinking, null, null));

                            // 最终答案保存到 finalAnswer
                            if (!isThinking) {
                                finalAnswer.append(content);
                            }
                        } else if (chunk instanceof ActionChunk actionChunk) {
                            // 工具调用 - 只记录日志，不推送到前端
                            String toolName = actionChunk.getToolName();
                            Map<String, Object> args = actionChunk.getArgs();
                            log.info("[ToolCall] 工具调用：{}，参数：{}", toolName, args);
                        } else if (chunk instanceof ReActChunk reActChunk) {
                            // 记录执行历史
                            log.debug("ReAct 执行完成，历史记录：{}", reActChunk.getTrace().getFormattedHistory());
                        }
                    })
                    .doOnError(error -> {
                        log.error("流式对话异常", error);
                        eventConsumer.accept(new StreamEvent(StreamEventType.ERROR, "处理失败：" + error.getMessage(), error));
                    })
                    .doOnComplete(() -> {
                        memoryService.saveAssistantMessage(sessionId, finalAnswer.toString());
                        eventConsumer.accept(new StreamEvent(StreamEventType.END, "处理完成"));
                    })
                    .blockLast();

        } catch (Throwable e) {
            log.error("Agent 对话异常", e);
            eventConsumer.accept(new StreamEvent(StreamEventType.ERROR, "处理失败：" + e.getMessage(), e));
        }
    }

    /**
     * 流式事件类型
     */
    public enum StreamEventType {
        START,
        CONTENT,
        TOOL_CALL,
        TOOL_DONE,
        END,
        ERROR
    }

    /**
     * 流式事件
     */
    public record StreamEvent(
            StreamEventType type,
            String content,
            Throwable error,
            Boolean isThinking,
            String toolName,
            Map<String, Object> toolArgs
    ) {
        public StreamEvent(StreamEventType type, String content) {
            this(type, content, null, null, null, null);
        }

        public StreamEvent(StreamEventType type, String content, Boolean isThinking) {
            this(type, content, null, isThinking, null, null);
        }

        public StreamEvent(StreamEventType type, String content, String toolName, Map<String, Object> toolArgs) {
            this(type, content, null, null, toolName, toolArgs);
        }

        public StreamEvent(StreamEventType type, String content, Throwable error) {
            this(type, content, error, null, null, null);
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"").append(type).append("\"");
            if (content != null) {
                sb.append(",\"content\":").append(escapeJson(content));
            }
            if (error != null) {
                sb.append(",\"error\":").append(escapeJson(error.getMessage()));
            }
            if (isThinking != null) {
                sb.append(",\"isThinking\":").append(isThinking);
            }
            if (toolName != null) {
                sb.append(",\"toolName\":").append(escapeJson(toolName));
            }
            if (toolArgs != null) {
                sb.append(",\"toolArgs\":").append(escapeJsonMap(toolArgs));
            }
            sb.append("}");
            return sb.toString();
        }

        private static String escapeJson(String value) {
            if (value == null) return "null";
            return "\"" + StrUtil.replace(value, "\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t") + "\"";
        }

        private static String escapeJsonMap(Map<String, Object> map) {
            if (map == null || map.isEmpty()) return "{}";
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeJson(entry.getKey()).replace("\"", "")).append("\":");
                Object value = entry.getValue();
                if (value == null) {
                    sb.append("null");
                } else if (value instanceof String) {
                    sb.append(escapeJson((String) value));
                } else if (value instanceof Number || value instanceof Boolean) {
                    sb.append(value);
                } else {
                    sb.append(escapeJson(value.toString()));
                }
            }
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * 日志拦截器
     */
    private static class LoggingInterceptor implements ReActInterceptor {

        private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);
        private static final int MAX_LOG_LENGTH = 2000;

        @Override
        public void onAgentStart(ReActTrace trace) {
            String traceId = ObjUtil.isNotNull(trace) ? extractTraceId(trace): null;
            if (StrUtil.isBlank(traceId)) {
                traceId = com.jimuqu.solonclaw.trace.TraceContext.generateTraceId();
            }
            com.jimuqu.solonclaw.trace.TraceContext.setTraceId(traceId);
            log.info("[TraceID: {}] Agent 开始执行", traceId);
        }

        @Override
        public void onThought(ReActTrace trace, String thought) {
            String traceId = com.jimuqu.solonclaw.trace.TraceContext.getTraceId();
            String truncatedThought = truncate(thought);
            if (StrUtil.isNotEmpty(traceId)) {
                log.debug("[TraceID: {}] Agent 思考：{}", traceId, truncatedThought);
                if (thought.length() > MAX_LOG_LENGTH) {
                    log.debug("[TraceID: {}] Agent 思考（完整）: {}", traceId, thought);
                }
            } else {
                log.debug("Agent 思考：{}", truncatedThought);
            }
        }

        @Override
        public void onAction(ReActTrace trace, String toolName, Map<String, Object> args) {
            String traceId = com.jimuqu.solonclaw.trace.TraceContext.getTraceId();
            if (StrUtil.isNotEmpty(traceId)) {
                log.info("[TraceID: {}] Agent 执行工具：{} 参数：{}", traceId, toolName, args);
                if (ObjUtil.isNotNull(args) && args.values().stream().anyMatch(v -> ObjUtil.isNotNull(v) && v.toString().length() > 500)) {
                    log.debug("[TraceID: {}] Agent 执行工具参数详情：{} 参数：{}", traceId, toolName, formatDetailedArgs(args));
                }
            } else {
                log.info("Agent 执行工具：{} 参数：{}", toolName, args);
            }
        }

        @Override
        public void onAgentEnd(ReActTrace trace) {
            String traceId = com.jimuqu.solonclaw.trace.TraceContext.getTraceId();
            if (StrUtil.isNotEmpty(traceId)) {
                log.debug("[TraceID: {}] Agent 执行结束", traceId);
                com.jimuqu.solonclaw.trace.TraceContext.clear();
            } else {
                log.debug("Agent 执行结束");
            }
        }

        /**
         * 从 ReActTrace 中提取 Trace ID（如果存在）
         */
        private String extractTraceId(ReActTrace trace) {
            // 尝试从 trace 的 sessionId 或其他属性中提取
            // 这里使用 sessionId 作为 Trace ID 的基础
            try {
                java.lang.reflect.Method sessionIdMethod = trace.getClass().getMethod("sessionId");
                if (ObjUtil.isNotNull(sessionIdMethod)) {
                    Object sessionId = sessionIdMethod.invoke(trace);
                    if (ObjUtil.isNotNull(sessionId)) {
                        return sessionId.toString().hashCode() + "-" + System.currentTimeMillis();
                    }
                }
            } catch (Exception e) {
                // 忽略反射异常
            }
            return null;
        }

        private String truncate(String text) {
            if (StrUtil.isBlank(text)) return null;
            if (text.length() <= MAX_LOG_LENGTH) return text;
            return StrUtil.sub(text, 0, MAX_LOG_LENGTH) + "... (截断，总长度：" + text.length() + ")";
        }

        private String formatDetailedArgs(Map<String, Object> args) {
            if (ObjUtil.isNull(args) || CollUtil.isEmpty(args)) return "{}";

            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                if (!first) sb.append(", ");
                first = false;

                String valueStr = String.valueOf(entry.getValue());
                if (valueStr.length() > 200) {
                    valueStr = StrUtil.sub(valueStr, 0, 200) + "... (长度：" + valueStr.length() + ")";
                }
                sb.append(entry.getKey()).append("=").append(valueStr);
            }
            sb.append("}");
            return sb.toString();
        }
    }
}
