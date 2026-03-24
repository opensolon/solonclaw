package com.jimuqu.claw.agent.runtime.impl;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.runtime.api.ConversationAgent;
import com.jimuqu.claw.agent.runtime.registry.ActiveTaskRegistry;
import com.jimuqu.claw.agent.runtime.support.ConversationExecutionRequest;
import com.jimuqu.claw.agent.runtime.support.RunTurnControl;
import com.jimuqu.claw.agent.runtime.support.SystemAwareAgentSession;
import com.jimuqu.claw.agent.runtime.support.VisibleProgressAccumulator;
import com.jimuqu.claw.agent.tool.ConversationRuntimeTools;
import com.jimuqu.claw.agent.tool.JobTools;
import com.jimuqu.claw.agent.tool.WorkspaceAgentTools;
import com.jimuqu.claw.agent.workspace.WorkspacePromptService;
import lombok.Setter;
import org.noear.solon.ai.agent.AgentChunk;
import org.noear.solon.ai.agent.react.ReActAgent;
import org.noear.solon.ai.agent.react.ReActInterceptor;
import org.noear.solon.ai.agent.react.intercept.HITLInterceptor;
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
    private static final int MAX_PROMPT_LOG_LENGTH = 500;
    private final ChatModel chatModel;
    private final WorkspacePromptService workspacePromptService;
    private final WorkspaceAgentTools workspaceAgentTools;
    private final CliSkillProvider cliSkillProvider;
    @Setter
    private JobTools jobTools;
    private final ReActInterceptor reActInterceptor;
    private final HITLInterceptor blacklistInterceptor;
    @Setter
    private CancellationInterceptor cancellationInterceptor;
    @Setter
    private ActiveTaskRegistry activeTaskRegistry;

    public SolonAiConversationAgent(
            ChatModel chatModel,
            WorkspacePromptService workspacePromptService,
            WorkspaceAgentTools workspaceAgentTools,
            CliSkillProvider cliSkillProvider,
            ReActInterceptor reActInterceptor,
            HITLInterceptor blacklistInterceptor
    ) {
        this.chatModel = chatModel;
        this.workspacePromptService = workspacePromptService;
        this.workspaceAgentTools = workspaceAgentTools;
        this.cliSkillProvider = cliSkillProvider;
        this.reActInterceptor = reActInterceptor;
        this.blacklistInterceptor = blacklistInterceptor;
    }

    @Override
    public String execute(ConversationExecutionRequest request, Consumer<String> progressConsumer) throws Throwable {
        SystemAwareAgentSession session = SystemAwareAgentSession.of(request.getSessionKey());
        if (StrUtil.isNotBlank(request.getRunId())) {
            session.getSnapshot().put("runId", request.getRunId());
        }
        if (StrUtil.isNotBlank(request.getTaskTitle())) {
            session.getSnapshot().put("taskTitle", request.getTaskTitle());
        }
        if (request.isChildRun() && StrUtil.isNotBlank(request.getRunId()) && activeTaskRegistry != null) {
            activeTaskRegistry.setAgentSession(request.getRunId(), session);
        }
        for (ChatMessage historyMessage : request.getHistory()) {
            session.addMessage(historyMessage);
        }
        RunTurnControl turnControl = RunTurnControl.begin(session, request.getCurrentSourceKind());

        try {
            AtomicReference<String> latestChunk = new AtomicReference<>("");
            VisibleProgressAccumulator progressAccumulator = new VisibleProgressAccumulator();

            String prompt = resolvePrompt(request, session);
            logResolvedPrompt(request, prompt);

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

            String finalContent = finalChunk == null ? null : finalChunk.getContent();
            if (StrUtil.isNotBlank(finalContent)) {
                return finalContent;
            }
            if (StrUtil.isNotBlank(latestChunk.get())) {
                return latestChunk.get();
            }
            if (StrUtil.isNotBlank(turnControl.getForcedResponse())) {
                return turnControl.getForcedResponse();
            }
            return finalContent;
        } finally {
            if (request.isChildRun() && StrUtil.isNotBlank(request.getRunId()) && activeTaskRegistry != null) {
                activeTaskRegistry.setAgentSession(request.getRunId(), null);
            }
            RunTurnControl.clear();
        }
    }

    private ReActAgent buildAgent(ConversationExecutionRequest request) {
        ConversationRuntimeTools runtimeTools = new ConversationRuntimeTools(
                workspaceAgentTools,
                request == null ? null : request.getSpawnTaskSupport(),
                request == null ? null : request.getRunQuerySupport(),
                request == null ? null : request.getNotificationSupport(),
                request == null ? null : request.getProgressReportSupport(),
                request == null ? null : request.getTaskControlSupport()
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

        if (cancellationInterceptor != null) {
            builder.defaultInterceptorAdd(cancellationInterceptor);
        }
        if (blacklistInterceptor != null) {
            builder.defaultInterceptorAdd(blacklistInterceptor);
        }
        if (jobTools != null && shouldEnableJobTools(request)) {
            builder.defaultToolAdd(jobTools);
        }

        return builder.build();
    }

    private boolean shouldEnableJobTools(ConversationExecutionRequest request) {
        if (request == null) {
            return true;
        }
        return request.getCurrentSourceKind() == RuntimeSourceKind.USER_MESSAGE;
    }

    private String resolvePrompt(ConversationExecutionRequest request, SystemAwareAgentSession session) {
        RuntimeSourceKind sourceKind = request == null ? RuntimeSourceKind.USER_MESSAGE : request.getCurrentSourceKind();
        String currentMessage = request == null ? null : request.getCurrentMessage();
        if (sourceKind == RuntimeSourceKind.USER_MESSAGE) {
            return currentMessage;
        }

        if (StrUtil.isNotBlank(currentMessage)) {
            session.addMessage(ChatMessage.ofSystem(currentMessage));
        }

        if (sourceKind == RuntimeSourceKind.JOB_SYSTEM_EVENT) {
            return "一条定时任务事件已触发，具体内容见最新的 system 消息。"
                    + "请直接处理这条内部事件；如果需要提醒用户，就生成一条自然友好的提醒文本，"
                    + "或显式调用 notify_user。不要解释内部机制。";
        }
        if (sourceKind == RuntimeSourceKind.HEARTBEAT_EVENT) {
            return "这是一条心跳内部检查事件，相关内容见最新的 system 消息。"
                    + "请先在内部处理；如果没有明确的用户可见动作，请直接返回 NO_REPLY。";
        }
        if (sourceKind == RuntimeSourceKind.CHILD_CONTINUATION) {
            return "一条 continuation 事件已到达，结构化结果见最新的 system 消息。"
                    + "请严格遵守最新 system 消息中的“调度要求”。"
                    + "按本次结果做总结，或者在确实无需对外说话时返回 NO_REPLY。"
                    + "当前 continuation 不支持再次派生子任务，也不要把任务重新从头做一遍。";
        }
        if (sourceKind == RuntimeSourceKind.JOB_AGENT_TURN) {
            return "这是一次隔离的自动化 agent turn。请根据当前任务描述完成工作。"
                    + "不要把它当作新的用户对话，也不要解释内部调度过程。";
        }

        return currentMessage;
    }

    private void logResolvedPrompt(ConversationExecutionRequest request, String prompt) {
        String sessionKey = request == null ? "未知会话" : StrUtil.blankToDefault(request.getSessionKey(), "未知会话");
        RuntimeSourceKind sourceKind = request == null ? RuntimeSourceKind.USER_MESSAGE : request.getCurrentSourceKind();
        int historyCount = request == null || request.getHistory() == null ? 0 : request.getHistory().size();
        boolean childRun = request != null && request.isChildRun();
        String parentRunId = request == null ? null : request.getParentRunId();

        log.info(
                "Resolved prompt. sourceKind={}, session={}, childRun={}, parentRunId={}, historyMessages={}, prompt={}",
                sourceKind,
                sessionKey,
                childRun,
                StrUtil.blankToDefault(parentRunId, "-"),
                historyCount,
                compactForLog(prompt)
        );

        if (log.isDebugEnabled()) {
            log.debug(
                    "Resolved prompt detail. sourceKind={}, session={}, childRun={}, parentRunId={}\n{}",
                    sourceKind,
                    sessionKey,
                    childRun,
                    StrUtil.blankToDefault(parentRunId, "-"),
                    StrUtil.blankToDefault(prompt, "")
            );
        }
    }

    private String compactForLog(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }

        String normalized = text.replace("\r", " ").replace("\n", "\\n").trim();
        return StrUtil.maxLength(normalized, MAX_PROMPT_LOG_LENGTH);
    }
}

