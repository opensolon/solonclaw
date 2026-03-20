package com.jimuqu.claw.agent.runtime.impl;

import com.jimuqu.claw.agent.model.enums.InboundTriggerType;
import com.jimuqu.claw.agent.runtime.api.ConversationAgent;
import com.jimuqu.claw.agent.runtime.support.ConversationExecutionRequest;
import com.jimuqu.claw.agent.runtime.support.SystemAwareAgentSession;
import com.jimuqu.claw.agent.runtime.support.VisibleProgressAccumulator;
import com.jimuqu.claw.agent.tool.ConversationRuntimeTools;
import com.jimuqu.claw.agent.tool.JobTools;
import com.jimuqu.claw.agent.tool.WorkspaceAgentTools;
import com.jimuqu.claw.agent.workspace.WorkspacePromptService;
import cn.hutool.core.util.StrUtil;
import org.noear.solon.ai.agent.AgentChunk;
import org.noear.solon.ai.agent.react.ReActAgent;
import org.noear.solon.ai.agent.react.intercept.HITLInterceptor;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.skills.cli.CliSkillProvider;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 基于 Solon AI ReActAgent 的会话执行实现。
 */
public class SolonAiConversationAgent implements ConversationAgent {
    /** 聊天模型。 */
    private final ChatModel chatModel;
    /** 工作区提示词服务。 */
    private final WorkspacePromptService workspacePromptService;
    /** 工作区工具集。 */
    private final WorkspaceAgentTools workspaceAgentTools;
    /** CLI 技能提供者。 */
    private final CliSkillProvider cliSkillProvider;
    /** 定时任务工具。 */
    private final JobTools jobTools;
    /** 黑名单拦截器（可选）。 */
    private final HITLInterceptor blacklistInterceptor;

    /**
     * 创建基于聊天模型的会话执行 Agent。
     *
     * @param chatModel 聊天模型
     * @param workspacePromptService 工作区提示词服务
     * @param workspaceAgentTools 工作区工具集
     * @param cliSkillProvider CLI 技能提供者
     * @param jobTools 定时任务工具
     * @param blacklistInterceptor 黑名单拦截器，为 null 时不启用
     */
    public SolonAiConversationAgent(
            ChatModel chatModel,
            WorkspacePromptService workspacePromptService,
            WorkspaceAgentTools workspaceAgentTools,
            CliSkillProvider cliSkillProvider,
            JobTools jobTools,
            HITLInterceptor blacklistInterceptor
    ) {
        this.chatModel = chatModel;
        this.workspacePromptService = workspacePromptService;
        this.workspaceAgentTools = workspaceAgentTools;
        this.cliSkillProvider = cliSkillProvider;
        this.jobTools = jobTools;
        this.blacklistInterceptor = blacklistInterceptor;
    }

    /**
     * 执行一次对话请求。
     *
     * @param request 会话执行请求
     * @param progressConsumer 进度回调
     * @return 最终回复内容
     * @throws Throwable 流式执行过程中的异常
     */
    @Override
    public String execute(ConversationExecutionRequest request, Consumer<String> progressConsumer) throws Throwable {
        SystemAwareAgentSession session = SystemAwareAgentSession.of(request.getSessionKey());
        for (ChatMessage historyMessage : request.getHistory()) {
            session.addMessage(historyMessage);
        }

        AtomicReference<String> latestChunk = new AtomicReference<>("");
        VisibleProgressAccumulator progressAccumulator = new VisibleProgressAccumulator();

        String prompt = resolvePrompt(request, session);
        Flux<AgentChunk> stream = buildAgent(request)
                .prompt(prompt)
                .session(session)
                .stream();

        AgentChunk finalChunk = stream.doOnNext(chunk -> {
            ChatMessage message = chunk.getMessage();
            String content = message.getContent();
            boolean thinking = message.isThinking();
            boolean toolCalls = message.isToolCalls();

            String visibleProgress = progressAccumulator.append(content, thinking, toolCalls);
            if (StrUtil.isNotBlank(visibleProgress) && !visibleProgress.equals(latestChunk.get())) {
                latestChunk.set(visibleProgress);
                progressConsumer.accept(visibleProgress);
            }
        }).blockLast();

        if (finalChunk == null) {
            return latestChunk.get();
        }

        return finalChunk.getContent();
    }

    /**
     * 为本次运行创建一个带最新工作区引导内容的 Agent。
     *
     * @return 会话执行 Agent
     */
    private ReActAgent buildAgent(ConversationExecutionRequest request) {
        ConversationRuntimeTools runtimeTools = new ConversationRuntimeTools(
                workspaceAgentTools,
                request == null ? null : request.getSpawnTaskSupport(),
                request == null ? null : request.getRunQuerySupport(),
                request == null ? null : request.getNotificationSupport()
        );
        ReActAgent.Builder builder = ReActAgent.of(chatModel)
                .name(workspacePromptService.resolveAgentName())
                .instruction(workspacePromptService.buildSystemPrompt(request))
                .defaultToolAdd(runtimeTools)
                .defaultToolAdd(jobTools)
                .defaultSkillAdd(cliSkillProvider)
                .maxSteps(50)
                .retryConfig(5, 1000L)
                .sessionWindowSize(64);

        if (blacklistInterceptor != null) {
            builder.defaultInterceptorAdd(blacklistInterceptor);
        }

        return builder.build();
    }

    /**
     * 为不同类型的触发生成合适的当前轮次提示。
     *
     * @param request 当前执行请求
     * @param session 会话上下文
     * @return 当前轮次提示
     */
    private String resolvePrompt(ConversationExecutionRequest request, SystemAwareAgentSession session) {
        InboundTriggerType triggerType = request == null ? InboundTriggerType.USER : request.getCurrentMessageTriggerType();
        String currentMessage = request == null ? null : request.getCurrentMessage();
        if (triggerType == null || triggerType == InboundTriggerType.USER) {
            return currentMessage;
        }

        if (currentMessage != null && currentMessage.trim().length() > 0) {
            session.addMessage(ChatMessage.ofSystem(currentMessage));
        }

        if (triggerType == InboundTriggerType.SYSTEM_SILENT) {
            return "这是一次静默内部检查。请结合最新的 system 消息和既有上下文继续处理，不要把它当作用户新消息。"
                    + "如果当前没有需要对外说明的事项，请返回简洁状态或 NO_REPLY。";
        }

        return "这是一次内部系统触发。请优先依据最新的 system 消息和既有上下文继续处理，不要把它当作用户新消息。";
    }
}


