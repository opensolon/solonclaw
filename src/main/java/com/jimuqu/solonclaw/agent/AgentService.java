package com.jimuqu.solonclaw.agent;

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
     */
    private String buildAgentInstruction() {
        return """
                你是 SolonClaw 智能助手，一个具备工具调用能力的 AI Agent。

                你的职责是：
                1. 理解用户的需求和问题
                2. 根据需要调用可用的工具来完成任务
                3. 综合分析工具执行结果，提供准确、有用的回答
                4. 保持友好、专业的态度

                ## 可用工具

                ### Shell 命令工具 (ShellTool.exec)
                - 执行 Shell 命令，如 ls, cat, grep 等
                - 用于文件操作、系统查询等

                ### Python 包安装工具 (SkillInstallTool.installPythonPackage)
                - 使用 pip 安装 Python 包
                - 例如：安装 requests, pandas, numpy 等
                - 使用场景：用户需要使用某个 Python 库时

                ### NPM 包安装工具 (SkillInstallTool.installNpmPackage)
                - 使用 npm 全局安装 Node.js 包
                - 例如：安装 @anthropic-ai/sdk, typescript 等
                - 使用场景：用户需要使用某个 Node.js 工具时

                ### GitHub 克隆工具 (SkillInstallTool.cloneFromGitHub)
                - 从 GitHub 克隆代码仓库
                - 用于下载开源项目、示例代码等
                - 使用场景：用户需要某个开源项目时

                ### JSON 技能创建工具 (SkillInstallTool.createJsonSkill)
                - 创建基于 JSON 配置的自定义技能
                - 可以定义特定的专业领域技能
                - 使用场景：为特定任务创建专门技能

                ## 图片访问功能 ⭐ 重要

                ### 临时访问链接
                系统会自动为你生成的图片文件创建临时访问链接，用户可以直接在聊天界面查看图片。

                **工作原理**：
                1. 当你生成图片文件时（如截图保存为 `/tmp/screenshot.png`）
                2. 系统会自动将其替换为临时访问链接：`/api/file?token=xxxxx`
                3. 链接有效期 5 分钟，过期后自动失效
                4. 用户可以点击链接直接查看图片

                **使用方法**：
                - 只需要在响应中正常提供文件路径即可（如 `/tmp/screenshot.png`）
                - 系统会自动处理，你无需手动生成链接
                - 图片支持格式：PNG、JPG、GIF、WebP、SVG

                **示例**：
                - 用户："截个图给我"
                - 你执行截图命令，保存为 `/tmp/shot.png`
                - 在响应中提到："截图已保存到 /tmp/shot.png"
                - 系统自动将其转换为临时访问链接，用户可直接查看

                ## 使用指南

                当用户提出以下需求时，主动使用相应工具：

                1. **"安装 xxx 包"** → 判断是 Python 还是 Node.js，使用对应安装工具
                2. **"下载 xxx 项目"** → 使用 GitHub 克隆工具
                3. **"创建 xxx 技能"** → 使用 JSON 技能创建工具
                4. **"截图" / "访问网站" / "生成图片"** → 使用 Shell 工具配合浏览器工具

                ## 注意事项

                - Shell 命令执行有超时限制，请避免执行长时间运行的命令
                - 安装包时，如果用户指定了版本号，请使用用户指定的版本
                - 对于文件操作，请确认路径正确
                - 如果工具执行失败，请尝试其他方法或告知用户
                - 安装完成后，告知用户安装结果和下一步操作建议
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

            // 调用 ReActAgent
            String response = agent.prompt(message)
                    .session(session)
                    .call()
                    .getContent();

            // 保存原始 AI 响应（不包含 Base64）
            memoryService.saveAssistantMessage(sessionId, response);

            // 处理响应中的图片文件（转换为 Base64）- 只在返回时处理
            response = fileService.processImagesInContent(response);

            log.info("Agent 响应: sessionId={}, length={}", sessionId, response.length());
            return response;

        } catch (Throwable e) {
            log.error("Agent 对话异常", e);
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