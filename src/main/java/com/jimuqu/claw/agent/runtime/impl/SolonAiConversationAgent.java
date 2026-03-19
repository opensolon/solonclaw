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
import org.noear.solon.ai.agent.react.ReActInterceptor;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.skills.cli.CliSkillProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 基于 Solon AI ReActAgent 的会话执行实现。
 */
public class SolonAiConversationAgent implements ConversationAgent {

    private static final Logger log = LoggerFactory.getLogger(SolonAiConversationAgent.class);
    /**
     * 聊天模型。
     */
    private final ChatModel chatModel;
    /**
     * 工作区提示词服务。
     */
    private final WorkspacePromptService workspacePromptService;
    /**
     * 工作区工具集。
     */
    private final WorkspaceAgentTools workspaceAgentTools;
    /**
     * CLI 技能提供者。
     */
    private final CliSkillProvider cliSkillProvider;
    /**
     * 定时任务工具。
     */
    private final JobTools jobTools;
    /**
     * ReAct 运行日志拦截器。
     */
    private final ReActInterceptor reActInterceptor;

    /**
     * 创建基于聊天模型的会话执行 Agent。
     *
     * @param chatModel              聊天模型
     * @param workspacePromptService 工作区提示词服务
     * @param workspaceAgentTools    工作区工具集
     * @param cliSkillProvider       CLI 技能提供者
     * @param jobTools               定时任务工具
     */
    public SolonAiConversationAgent(
            ChatModel chatModel,
            WorkspacePromptService workspacePromptService,
            WorkspaceAgentTools workspaceAgentTools,
            CliSkillProvider cliSkillProvider,
            JobTools jobTools,
            ReActInterceptor reActInterceptor
    ) {
        this.chatModel = chatModel;
        this.workspacePromptService = workspacePromptService;
        this.workspaceAgentTools = workspaceAgentTools;
        this.cliSkillProvider = cliSkillProvider;
        this.jobTools = jobTools;
        this.reActInterceptor = reActInterceptor;
    }

    /**
     * 执行一次对话请求。
     *
     * @param request          会话执行请求
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

        AgentChunk finalChunk;
        try {
            finalChunk = stream.doOnNext(chunk -> {
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
        } catch (Exception e) {
            log.error("Failed to execute conversation: {}", e.getMessage(), e);
            return "执行会话失败：" + e.getMessage();
        }

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
                .defaultSkillAdd(cliSkillProvider)
                .defaultInterceptorAdd(reActInterceptor)
                .maxSteps(50)
                .retryConfig(5, 1000L)
                .sessionWindowSize(64);

        if (shouldEnableJobTools(request)) {
            builder.defaultToolAdd(jobTools);
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
            if (isScheduledReminderTrigger(currentMessage)) {
                return "一个定时提醒已触发。提醒内容见最新的 system 消息。"
                        + "请把这条提醒自然友好地告知用户，不要把它当作新的用户消息。"
                        + "如果你已经通过 notify_user 发送了提醒，请返回 NO_REPLY。"
                        + "如果你直接给出面向用户的提醒文案，运行时会代为发送一次。"
                        + "除非用户明确要求，否则不要解释内部触发过程。";
            }

            return "这是一条内部事件，相关结果见最新的 system 消息。"
                    + "请先在内部处理，不要把它当作新的用户消息。"
                    + "除非用户明确要求，否则不要把这次内部处理过程转告用户。"
                    + "如果没有需要面向用户的后续动作，请直接返回 NO_REPLY。";
        }

        return "这是一条内部系统事件，相关内容见最新的 system 消息。"
                + "请结合既有上下文继续处理，不要把它当作新的用户消息。"
                + "只有在确实需要用户看到结果时，才直接给出面向用户的最终回复。";
    }

    /**
     * 静默系统触发只用于内部检查或定时任务执行，不应再次管理定时任务本身。
     *
     * @param request 当前执行请求
     * @return 是否挂载定时任务管理工具
     */
    private boolean shouldEnableJobTools(ConversationExecutionRequest request) {
        if (request == null) {
            return true;
        }
        return request.getCurrentMessageTriggerType() != InboundTriggerType.SYSTEM_SILENT;
    }

    /**
     * 判断当前静默事件是否为定时提醒触发。
     *
     * @param currentMessage 当前消息文本
     * @return 若为定时提醒触发则返回 true
     */
    private boolean isScheduledReminderTrigger(String currentMessage) {
        return StrUtil.contains(StrUtil.blankToDefault(currentMessage, ""), "[内部定时任务触发]");
    }
}


