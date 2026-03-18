package com.jimuqu.claw.agent.runtime;

import com.jimuqu.claw.agent.tool.ConversationRuntimeTools;
import com.jimuqu.claw.agent.tool.JobTools;
import com.jimuqu.claw.agent.tool.WorkspaceAgentTools;
import com.jimuqu.claw.agent.workspace.WorkspacePromptService;
import org.noear.solon.ai.agent.AgentChunk;
import org.noear.solon.ai.agent.react.ReActAgent;
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

    /**
     * 创建基于聊天模型的会话执行 Agent。
     *
     * @param chatModel 聊天模型
     * @param workspacePromptService 工作区提示词服务
     * @param workspaceAgentTools 工作区工具集
     * @param cliSkillProvider CLI 技能提供者
     * @param jobTools 定时任务工具
     */
    public SolonAiConversationAgent(
            ChatModel chatModel,
            WorkspacePromptService workspacePromptService,
            WorkspaceAgentTools workspaceAgentTools,
            CliSkillProvider cliSkillProvider,
            JobTools jobTools
    ) {
        this.chatModel = chatModel;
        this.workspacePromptService = workspacePromptService;
        this.workspaceAgentTools = workspaceAgentTools;
        this.cliSkillProvider = cliSkillProvider;
        this.jobTools = jobTools;
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

        Flux<AgentChunk> stream = buildAgent(request)
                .prompt(request.getCurrentMessage())
                .session(session)
                .stream();

        AgentChunk finalChunk = stream.doOnNext(chunk -> {
            String content = chunk.getContent();
            if (content != null && !content.isBlank() && !content.equals(latestChunk.get())) {
                latestChunk.set(content);
                progressConsumer.accept(content);
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
        return ReActAgent.of(chatModel)
                .name(workspacePromptService.resolveAgentName())
                .instruction(workspacePromptService.buildSystemPrompt())
                .defaultToolAdd(runtimeTools)
                .defaultToolAdd(jobTools)
                .defaultSkillAdd(cliSkillProvider)
                .maxSteps(50)
                .retryConfig(5, 1000L)
                .sessionWindowSize(64)
                .build();
    }
}
