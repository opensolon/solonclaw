package com.jimuqu.claw.agent.runtime;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.channel.ChannelAdapter;
import com.jimuqu.claw.agent.channel.ChannelRegistry;
import com.jimuqu.claw.agent.model.AgentRun;
import com.jimuqu.claw.agent.model.ChannelType;
import com.jimuqu.claw.agent.model.ConversationType;
import com.jimuqu.claw.agent.model.InboundEnvelope;
import com.jimuqu.claw.agent.model.InboundTriggerType;
import com.jimuqu.claw.agent.model.OutboundEnvelope;
import com.jimuqu.claw.agent.model.ReplyTarget;
import com.jimuqu.claw.agent.model.RunEvent;
import com.jimuqu.claw.agent.model.RunStatus;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.config.SolonClawProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 协调消息入站、任务调度、状态落盘和出站发送的核心运行时服务。
 */
public class AgentRuntimeService {
    /** 父会话可用来抑制中间回复的保留字。 */
    public static final String NO_REPLY = "NO_REPLY";
    /** 父会话可用来声明“仅发送一次最终汇总”的保留前缀。 */
    public static final String FINAL_REPLY_ONCE_PREFIX = "FINAL_REPLY_ONCE:";
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeService.class);
    /** 会话执行 Agent。 */
    private final ConversationAgent conversationAgent;
    /** 运行时存储服务。 */
    private final RuntimeStoreService runtimeStoreService;
    /** 会话调度器。 */
    private final ConversationScheduler conversationScheduler;
    /** 渠道注册表。 */
    private final ChannelRegistry channelRegistry;
    /** 项目配置。 */
    private final SolonClawProperties properties;

    /**
     * 创建 Agent 运行时服务。
     *
     * @param conversationAgent 会话执行 Agent
     * @param runtimeStoreService 运行时存储服务
     * @param conversationScheduler 会话调度器
     * @param channelRegistry 渠道注册表
     * @param properties 项目配置
     */
    public AgentRuntimeService(
            ConversationAgent conversationAgent,
            RuntimeStoreService runtimeStoreService,
            ConversationScheduler conversationScheduler,
            ChannelRegistry channelRegistry,
            SolonClawProperties properties
    ) {
        this.conversationAgent = conversationAgent;
        this.runtimeStoreService = runtimeStoreService;
        this.conversationScheduler = conversationScheduler;
        this.channelRegistry = channelRegistry;
        this.properties = properties;
    }

    /**
     * 向调试页渠道提交一条消息。
     *
     * @param sessionId 调试会话标识
     * @param message 文本消息
     * @return 运行任务标识
     */
    public String submitDebugMessage(String sessionId, String message) {
        InboundEnvelope inboundEnvelope = new InboundEnvelope();
        inboundEnvelope.setMessageId("debug-" + IdUtil.fastSimpleUUID());
        inboundEnvelope.setChannelType(ChannelType.DEBUG_WEB);
        inboundEnvelope.setChannelInstanceId("debug-web");
        inboundEnvelope.setSenderId("debug-user");
        inboundEnvelope.setConversationId(sessionId);
        inboundEnvelope.setConversationType(ConversationType.PRIVATE);
        inboundEnvelope.setContent(message);
        inboundEnvelope.setReceivedAt(System.currentTimeMillis());
        inboundEnvelope.setSessionKey("debug-web:" + sessionId);
        inboundEnvelope.setReplyTarget(new ReplyTarget(ChannelType.DEBUG_WEB, ConversationType.PRIVATE, sessionId, "debug-user"));
        inboundEnvelope.setTriggerType(InboundTriggerType.USER);
        return submitInbound(inboundEnvelope);
    }

    /**
     * 向指定外部路由提交一条系统消息。
     *
     * @param sessionKey 会话键
     * @param replyTarget 回复目标
     * @param content 文本内容
     * @return 运行任务标识
     */
    public String submitSystemMessage(String sessionKey, ReplyTarget replyTarget, String content) {
        return submitVisibleSystemMessage(sessionKey, replyTarget, content, "system");
    }

    /**
     * 向指定外部路由提交一条可见系统消息。
     *
     * @param sessionKey 会话键
     * @param replyTarget 回复目标
     * @param content 文本内容
     * @return 运行任务标识
     */
    public String submitVisibleSystemMessage(String sessionKey, ReplyTarget replyTarget, String content) {
        return submitVisibleSystemMessage(sessionKey, replyTarget, content, "system");
    }

    /**
     * 向指定外部路由提交一条仅用于内部处理的静默系统消息。
     *
     * @param sessionKey 会话键
     * @param replyTarget 回复目标
     * @param content 文本内容
     * @return 运行任务标识
     */
    public String submitSilentSystemMessage(String sessionKey, ReplyTarget replyTarget, String content) {
        return submitSilentSystemMessage(sessionKey, replyTarget, content, "system");
    }

    /**
     * 向指定外部路由提交一条带自定义发送者的系统消息。
     *
     * @param sessionKey 会话键
     * @param replyTarget 回复目标
     * @param content 文本内容
     * @param senderId 发送者标识
     * @return 运行任务标识
     */
    public String submitSystemMessage(String sessionKey, ReplyTarget replyTarget, String content, String senderId) {
        return submitVisibleSystemMessage(sessionKey, replyTarget, content, senderId);
    }

    /**
     * 向指定外部路由提交一条带自定义发送者的可见系统消息。
     *
     * @param sessionKey 会话键
     * @param replyTarget 回复目标
     * @param content 文本内容
     * @param senderId 发送者标识
     * @return 运行任务标识
     */
    public String submitVisibleSystemMessage(String sessionKey, ReplyTarget replyTarget, String content, String senderId) {
        return submitSystemMessage(
                sessionKey,
                replyTarget,
                content,
                senderId,
                InboundTriggerType.SYSTEM_VISIBLE,
                true,
                true,
                true
        );
    }

    /**
     * 向指定外部路由提交一条仅用于内部处理的静默系统消息。
     *
     * @param sessionKey 会话键
     * @param replyTarget 回复目标
     * @param content 文本内容
     * @param senderId 发送者标识
     * @return 运行任务标识
     */
    public String submitSilentSystemMessage(String sessionKey, ReplyTarget replyTarget, String content, String senderId) {
        return submitSystemMessage(
                sessionKey,
                replyTarget,
                content,
                senderId,
                InboundTriggerType.SYSTEM_SILENT,
                false,
                false,
                false
        );
    }

    /**
     * 统一构造系统入站消息。
     *
     * @param sessionKey 会话键
     * @param replyTarget 回复目标
     * @param content 文本内容
     * @param senderId 发送者标识
     * @param externalReplyEnabled 是否允许外部回发
     * @param persistInboundConversationEvent 是否写入入站会话事件
     * @param persistAssistantConversationEvent 是否写入助手回复会话事件
     * @return 运行任务标识
     */
    private String submitSystemMessage(
            String sessionKey,
            ReplyTarget replyTarget,
            String content,
            String senderId,
            InboundTriggerType triggerType,
            boolean externalReplyEnabled,
            boolean persistInboundConversationEvent,
            boolean persistAssistantConversationEvent
    ) {
        InboundEnvelope inboundEnvelope = new InboundEnvelope();
        inboundEnvelope.setMessageId("system-" + IdUtil.fastSimpleUUID());
        inboundEnvelope.setChannelType(ChannelType.SYSTEM);
        inboundEnvelope.setChannelInstanceId("system");
        inboundEnvelope.setSenderId(StrUtil.blankToDefault(senderId, "system"));
        inboundEnvelope.setConversationId(replyTarget == null ? sessionKey : replyTarget.getConversationId());
        inboundEnvelope.setConversationType(replyTarget == null ? ConversationType.PRIVATE : replyTarget.getConversationType());
        inboundEnvelope.setContent(content);
        inboundEnvelope.setReceivedAt(System.currentTimeMillis());
        inboundEnvelope.setSessionKey(sessionKey);
        inboundEnvelope.setReplyTarget(replyTarget);
        inboundEnvelope.setTriggerType(triggerType);
        inboundEnvelope.setExternalReplyEnabled(externalReplyEnabled);
        inboundEnvelope.setPersistInboundConversationEvent(persistInboundConversationEvent);
        inboundEnvelope.setPersistAssistantConversationEvent(persistAssistantConversationEvent);
        return submitInbound(inboundEnvelope);
    }

    /**
     * 提交一条标准化后的入站消息。
     *
     * @param inboundEnvelope 入站消息
     * @return 新建运行任务标识；若命中去重则返回 null
     */
    public String submitInbound(InboundEnvelope inboundEnvelope) {
        if (!runtimeStoreService.registerInbound(inboundEnvelope.getChannelType(), inboundEnvelope.getMessageId())) {
            log.info(
                    "Ignore duplicated inbound message. channelType={}, messageId={}",
                    inboundEnvelope.getChannelType(),
                    inboundEnvelope.getMessageId()
            );
            return null;
        }

        ConversationScheduler.SessionState state = conversationScheduler.inspect(inboundEnvelope.getSessionKey());

        long latestConversationVersion = runtimeStoreService.getLatestConversationVersion(inboundEnvelope.getSessionKey());
        long nextConversationVersion = latestConversationVersion + 1L;
        inboundEnvelope.setHistoryAnchorVersion(resolveHistoryAnchorVersion(inboundEnvelope, nextConversationVersion));
        long version = inboundEnvelope.isPersistInboundConversationEvent()
                ? runtimeStoreService.appendInboundConversationEvent(inboundEnvelope)
                : nextConversationVersion;
        inboundEnvelope.setSessionVersion(version);
        if (inboundEnvelope.getChannelType() != ChannelType.SYSTEM) {
            runtimeStoreService.rememberReplyTarget(inboundEnvelope.getSessionKey(), inboundEnvelope.getReplyTarget());
        }
        log.info(
                "Accepted inbound message. channelType={}, sessionKey={}, messageId={}, sessionVersion={}",
                inboundEnvelope.getChannelType(),
                inboundEnvelope.getSessionKey(),
                inboundEnvelope.getMessageId(),
                version
        );

        AgentRun run = new AgentRun();
        run.setRunId(runtimeStoreService.newRunId());
        run.setSessionKey(inboundEnvelope.getSessionKey());
        run.setSourceMessageId(inboundEnvelope.getMessageId());
        run.setSourceUserVersion(inboundEnvelope.getHistoryAnchorVersion());
        run.setReplyTarget(inboundEnvelope.getReplyTarget());
        run.setStatus(RunStatus.QUEUED);
        run.setCreatedAt(System.currentTimeMillis());
        runtimeStoreService.saveRun(run);
        runtimeStoreService.appendRunEvent(run.getRunId(), "status", "queued");
        log.info("Created run {} for session {}", run.getRunId(), run.getSessionKey());

        if (properties.getAgent().getScheduler().isAckWhenBusy()
                && state.activeCount() > 0
                && inboundEnvelope.isExternalReplyEnabled()
                && inboundEnvelope.getReplyTarget() != null
                && !inboundEnvelope.getReplyTarget().isDebugWeb()) {
            OutboundEnvelope ack = new OutboundEnvelope();
            ack.setRunId(run.getRunId());
            ack.setReplyTarget(inboundEnvelope.getReplyTarget());
            ack.setContent(state.queuedCount() > 0 ? "已收到，排队处理中。" : "已收到，正在并行处理中。");
            channelRegistry.send(ack);
        }

        conversationScheduler.submit(inboundEnvelope.getSessionKey(), () -> processRun(inboundEnvelope, run.getRunId()));
        return run.getRunId();
    }

    /**
     * 查询单个运行任务。
     *
     * @param runId 运行任务标识
     * @return 运行任务
     */
    public AgentRun getRun(String runId) {
        return runtimeStoreService.getRun(runId);
    }

    /**
     * 查询某个运行任务的增量事件。
     *
     * @param runId 运行任务标识
     * @param afterSeq 起始序号
     * @return 运行事件列表
     */
    public List<RunEvent> getRunEvents(String runId, long afterSeq) {
        return runtimeStoreService.getRunEvents(runId, afterSeq);
    }

    /**
     * 查询某个父运行下的子任务列表。
     *
     * @param parentRunId 父运行标识
     * @param batchKey 批次键；为空时返回全部
     * @return 子任务列表
     */
    public List<AgentRun> listChildRuns(String parentRunId, String batchKey) {
        return runtimeStoreService.listChildRunsByParentRun(parentRunId, StrUtil.blankToDefault(StrUtil.trim(batchKey), null));
    }

    /**
     * 聚合某个父运行下的子任务状态。
     *
     * @param parentRunId 父运行标识
     * @param batchKey 批次键；为空时聚合全部
     * @return 聚合结果；若不存在则返回 null
     */
    public ParentRunChildrenSummary getChildSummary(String parentRunId, String batchKey) {
        if (StrUtil.isBlank(parentRunId)) {
            return null;
        }
        ParentRunChildrenSummary summary = runtimeStoreService.summarizeChildRuns(
                parentRunId,
                StrUtil.blankToDefault(StrUtil.trim(batchKey), null)
        );
        return summary.getTotalChildren() == 0 ? null : summary;
    }

    /**
     * 执行一次真正的运行任务处理。
     *
     * @param inboundEnvelope 入站消息
     * @param runId 运行任务标识
     */
    private void processRun(InboundEnvelope inboundEnvelope, String runId) {
        AgentRun run = runtimeStoreService.getRun(runId);
        if (run == null) {
            return;
        }

        try {
            run.setStatus(RunStatus.RUNNING);
            run.setStartedAt(System.currentTimeMillis());
            runtimeStoreService.saveRun(run);
            runtimeStoreService.appendRunEvent(runId, "status", "running");
            log.info("Run {} started for session {}", runId, inboundEnvelope.getSessionKey());

            ConversationExecutionRequest request = new ConversationExecutionRequest();
            request.setSessionKey(inboundEnvelope.getSessionKey());
            request.setCurrentMessage(inboundEnvelope.getContent());
            request.setCurrentMessageTriggerType(inboundEnvelope.getTriggerType());
            request.setChildRun(StrUtil.isNotBlank(run.getParentRunId()));
            request.setParentRunId(run.getParentRunId());
            request.setHistory(runtimeStoreService.loadConversationHistoryBefore(inboundEnvelope.getSessionKey(), inboundEnvelope.getSessionVersion()));
            request.setSpawnTaskSupport((taskDescription, batchKey) -> spawnTask(runId, inboundEnvelope, taskDescription, batchKey));
            request.setRunQuerySupport(buildRunQuerySupport(inboundEnvelope.getSessionKey()));
            request.setNotificationSupport(buildNotificationSupport(inboundEnvelope.getSessionKey(), runId));

            final String[] latestProgress = {""};
            String response = conversationAgent.execute(request, progress -> {
                latestProgress[0] = progress;
                runtimeStoreService.appendRunEvent(runId, "progress", progress);
                dispatchProgressOutbound(runId, inboundEnvelope, progress);
            });
            AgentRun latestRun = runtimeStoreService.getRun(runId);
            if (latestRun != null) {
                run = latestRun;
            }

            if (StrUtil.isBlank(response)) {
                response = latestProgress[0];
            }
            String childCompletionParentRunId = resolveChildCompletionParentRunId(inboundEnvelope);
            boolean finalReplyOnce = isFinalReplyOnce(response);
            String visibleResponse = normalizeVisibleResponse(response);
            run.setFinalResponse(visibleResponse);
            boolean suppressReply = isNoReply(response)
                    || (finalReplyOnce
                    && StrUtil.isNotBlank(childCompletionParentRunId)
                    && runtimeStoreService.hasRunEventType(childCompletionParentRunId, "children_aggregated"));

            if (run.getStatus() == RunStatus.WAITING_CHILDREN) {
                run.setFinishedAt(System.currentTimeMillis());
                runtimeStoreService.saveRun(run);
                runtimeStoreService.appendRunEvent(runId, "reply", visibleResponse);
                runtimeStoreService.appendRunEvent(runId, "status", "waiting_children");
                log.info("Run {} is waiting child tasks for session {}", runId, inboundEnvelope.getSessionKey());
                return;
            }

            if (!suppressReply && inboundEnvelope.isPersistAssistantConversationEvent()) {
                runtimeStoreService.appendAssistantConversationEvent(
                        inboundEnvelope.getSessionKey(),
                        runId,
                        inboundEnvelope.getMessageId(),
                        resolveAssistantSourceUserVersion(inboundEnvelope),
                        visibleResponse
                );
            }

            run.setStatus(RunStatus.SUCCEEDED);
            run.setFinishedAt(System.currentTimeMillis());
            run.setFinalResponse(visibleResponse);
            runtimeStoreService.saveRun(run);
            runtimeStoreService.appendRunEvent(runId, "reply", visibleResponse);
            runtimeStoreService.appendRunEvent(runId, "status", "succeeded");
            if (!suppressReply && finalReplyOnce && StrUtil.isNotBlank(childCompletionParentRunId)) {
                runtimeStoreService.appendRunEvent(childCompletionParentRunId, "children_aggregated", "aggregateRunId=" + runId);
            }
            log.info("Run {} succeeded for session {}", runId, inboundEnvelope.getSessionKey());
            handleChildRunCompletion(run);

            if (!suppressReply
                    && inboundEnvelope.isExternalReplyEnabled()
                    && inboundEnvelope.getReplyTarget() != null
                    && !inboundEnvelope.getReplyTarget().isDebugWeb()) {
                OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
                outboundEnvelope.setRunId(runId);
                outboundEnvelope.setReplyTarget(inboundEnvelope.getReplyTarget());
                outboundEnvelope.setContent(visibleResponse);
                channelRegistry.send(outboundEnvelope);
                log.info(
                        "Run {} reply dispatched. channelType={}, conversationType={}, conversationId={}",
                        runId,
                        inboundEnvelope.getReplyTarget().getChannelType(),
                        inboundEnvelope.getReplyTarget().getConversationType(),
                        inboundEnvelope.getReplyTarget().getConversationId()
                );
            }
        } catch (Throwable throwable) {
            run.setStatus(RunStatus.FAILED);
            run.setFinishedAt(System.currentTimeMillis());
            run.setErrorMessage(throwable.getMessage());
            runtimeStoreService.saveRun(run);
            runtimeStoreService.appendRunEvent(runId, "error", throwable.getMessage());
            runtimeStoreService.appendRunEvent(runId, "status", "failed");
            log.warn("Run {} failed for session {}: {}", runId, inboundEnvelope.getSessionKey(), throwable.getMessage(), throwable);
            handleChildRunCompletion(run);

            if (inboundEnvelope.isExternalReplyEnabled()
                    && inboundEnvelope.getReplyTarget() != null
                    && !inboundEnvelope.getReplyTarget().isDebugWeb()) {
                OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
                outboundEnvelope.setRunId(runId);
                outboundEnvelope.setReplyTarget(inboundEnvelope.getReplyTarget());
                outboundEnvelope.setContent("抱歉，这次处理失败了：" + throwable.getMessage());
                channelRegistry.send(outboundEnvelope);
            }
        }
    }

    /**
     * 从父运行中派生一个独立子任务运行。
     *
     * @param parentRunId 父运行任务标识
     * @param parentInbound 父运行入站消息
     * @param taskDescription 子任务描述
     * @return 子任务创建结果
     */
    private SpawnTaskResult spawnTask(String parentRunId, InboundEnvelope parentInbound, String taskDescription, String batchKey) {
        if (StrUtil.isBlank(taskDescription)) {
            throw new IllegalArgumentException("taskDescription 不能为空");
        }

        AgentRun parentRun = runtimeStoreService.getRun(parentRunId);
        if (parentRun == null) {
            throw new IllegalStateException("父运行不存在: " + parentRunId);
        }

        String childSessionKey = parentInbound.getSessionKey() + ":subtask:" + IdUtil.fastSimpleUUID();
        String childMessageId = "spawn-" + IdUtil.fastSimpleUUID();
        long now = System.currentTimeMillis();

        InboundEnvelope childInbound = new InboundEnvelope();
        childInbound.setMessageId(childMessageId);
        childInbound.setChannelType(ChannelType.SYSTEM);
        childInbound.setChannelInstanceId("system");
        childInbound.setSenderId("parent-run:" + parentRunId);
        childInbound.setConversationId(childSessionKey);
        childInbound.setConversationType(ConversationType.PRIVATE);
        childInbound.setContent(taskDescription.trim());
        childInbound.setReceivedAt(now);
        childInbound.setSessionKey(childSessionKey);
        childInbound.setReplyTarget(null);
        childInbound.setTriggerType(InboundTriggerType.SYSTEM_VISIBLE);

        long version = runtimeStoreService.appendInboundConversationEvent(childInbound);
        childInbound.setSessionVersion(version);
        childInbound.setHistoryAnchorVersion(resolveHistoryAnchorVersion(childInbound, version));

        AgentRun childRun = new AgentRun();
        childRun.setRunId(runtimeStoreService.newRunId());
        childRun.setSessionKey(childSessionKey);
        childRun.setSourceMessageId(childMessageId);
        childRun.setSourceUserVersion(childInbound.getHistoryAnchorVersion());
        childRun.setStatus(RunStatus.QUEUED);
        childRun.setCreatedAt(now);
        childRun.setParentRunId(parentRunId);
        childRun.setParentSessionKey(parentInbound.getSessionKey());
        childRun.setParentReplyTarget(parentInbound.getReplyTarget());
        childRun.setTaskDescription(taskDescription.trim());
        childRun.setBatchKey(StrUtil.blankToDefault(StrUtil.trim(batchKey), null));
        runtimeStoreService.saveRun(childRun);
        runtimeStoreService.appendRunEvent(childRun.getRunId(), "status", "queued");

        parentRun.setStatus(RunStatus.WAITING_CHILDREN);
        runtimeStoreService.saveRun(parentRun);
        runtimeStoreService.appendRunEvent(
                parentRunId,
                "spawn_task",
                "childRunId=" + childRun.getRunId()
                        + ", childSessionKey=" + childSessionKey
                        + ", task=" + taskDescription.trim()
                        + (StrUtil.isBlank(childRun.getBatchKey()) ? "" : ", batchKey=" + childRun.getBatchKey())
        );
        runtimeStoreService.appendChildRunSpawnedEvent(
                parentInbound.getSessionKey(),
                parentRunId,
                parentRun.getSourceUserVersion(),
                childRun
        );
        log.info(
                "Spawned child run {} for parent run {}. parentSession={}, childSession={}",
                childRun.getRunId(),
                parentRunId,
                parentInbound.getSessionKey(),
                childSessionKey
        );

        conversationScheduler.submit(childSessionKey, () -> processRun(childInbound, childRun.getRunId()));

        SpawnTaskResult result = new SpawnTaskResult();
        result.setRunId(childRun.getRunId());
        result.setSessionKey(childSessionKey);
        result.setTaskDescription(taskDescription.trim());
        result.setBatchKey(childRun.getBatchKey());
        return result;
    }

    /**
     * 在子运行结束后，向父会话回写内部事件并触发 continuation run。
     *
     * @param run 已完成的运行任务
     */
    private void handleChildRunCompletion(AgentRun run) {
        if (run == null || StrUtil.isBlank(run.getParentRunId()) || StrUtil.isBlank(run.getParentSessionKey())) {
            return;
        }

        String internalMessage = buildChildCompletionMessage(run);
        AgentRun parentRun = runtimeStoreService.getRun(run.getParentRunId());
        long sourceUserVersion = parentRun == null ? 0L : parentRun.getSourceUserVersion();
        runtimeStoreService.appendChildRunCompletedEvent(run.getParentSessionKey(), run.getParentRunId(), sourceUserVersion, run);
        submitSystemMessage(
                run.getParentSessionKey(),
                run.getParentReplyTarget(),
                internalMessage,
                "child-complete:" + run.getParentRunId()
        );
    }

    /**
     * 计算当前入站消息对应的历史锚点版本。
     *
     * @param inboundEnvelope 入站消息
     * @param version 当前入站事件版本
     * @return 历史锚点版本
     */
    private long resolveHistoryAnchorVersion(InboundEnvelope inboundEnvelope, long version) {
        if (inboundEnvelope.getHistoryAnchorVersion() > 0) {
            return inboundEnvelope.getHistoryAnchorVersion();
        }
        if (inboundEnvelope.getTriggerType() == null || inboundEnvelope.getTriggerType() == InboundTriggerType.USER) {
            return version;
        }
        return runtimeStoreService.getLatestUserConversationVersion(inboundEnvelope.getSessionKey());
    }

    /**
     * 计算助手回复事件要挂载到的来源用户版本。
     *
     * @param inboundEnvelope 入站消息
     * @return 来源用户版本
     */
    private long resolveAssistantSourceUserVersion(InboundEnvelope inboundEnvelope) {
        if (inboundEnvelope.getHistoryAnchorVersion() > 0) {
            return inboundEnvelope.getHistoryAnchorVersion();
        }
        return inboundEnvelope.getSessionVersion();
    }

    /**
     * 构造子运行完成后回流父会话的内部消息。
     *
     * @param run 子运行
     * @return 内部消息文本
     */
    private String buildChildCompletionMessage(AgentRun run) {
        StringBuilder builder = new StringBuilder();
        builder.append("[内部事件] 子任务已完成").append('\n');
        builder.append("父运行ID: ").append(run.getParentRunId()).append('\n');
        builder.append("子运行ID: ").append(run.getRunId()).append('\n');
        builder.append("子会话: ").append(run.getSessionKey()).append('\n');
        builder.append("状态: ").append(run.getStatus()).append('\n');
        if (StrUtil.isNotBlank(run.getTaskDescription())) {
            builder.append("任务: ").append(run.getTaskDescription()).append('\n');
        }
        if (run.getStatus() == RunStatus.SUCCEEDED) {
            builder.append("结果:\n").append(StrUtil.blankToDefault(run.getFinalResponse(), "(空结果)"));
        } else {
            builder.append("错误:\n").append(StrUtil.blankToDefault(run.getErrorMessage(), "(未知错误)"));
        }
        builder.append("\n\n请基于已有上下文继续处理，必要时再派生新的子任务。");
        return builder.toString();
    }

    /**
     * 为当前会话构造任务状态查询能力。
     *
     * @param sessionKey 会话键
     * @return 查询能力
     */
    private RunQuerySupport buildRunQuerySupport(String sessionKey) {
        return new RunQuerySupport() {
            @Override
            public List<AgentRun> listChildRuns(int limit) {
                return runtimeStoreService.listChildRuns(sessionKey, limit);
            }

            @Override
            public AgentRun getRun(String runId) {
                AgentRun run = runtimeStoreService.getRun(runId);
                if (run == null) {
                    return null;
                }
                if (StrUtil.equals(sessionKey, run.getParentSessionKey()) || StrUtil.equals(sessionKey, run.getSessionKey())) {
                    return run;
                }
                return null;
            }

            @Override
            public AgentRun getLatestChildRun() {
                return runtimeStoreService.getLatestChildRun(sessionKey);
            }

            @Override
            public ParentRunChildrenSummary getChildSummary(String parentRunId, String batchKey) {
                String resolvedParentRunId = parentRunId;
                if (StrUtil.isBlank(resolvedParentRunId)) {
                    AgentRun latestParent = runtimeStoreService.getLatestParentRunWithChildren(sessionKey);
                    resolvedParentRunId = latestParent == null ? null : latestParent.getRunId();
                }
                if (StrUtil.isBlank(resolvedParentRunId)) {
                    return null;
                }

                ParentRunChildrenSummary summary = runtimeStoreService.summarizeChildRuns(
                        resolvedParentRunId,
                        StrUtil.blankToDefault(StrUtil.trim(batchKey), null)
                );
                return summary.getTotalChildren() == 0 ? null : summary;
            }
        };
    }

    /**
     * 为当前会话构造主动通知能力。
     *
     * @param sessionKey 会话键
     * @param runId 当前运行标识
     * @return 通知能力
     */
    private NotificationSupport buildNotificationSupport(String sessionKey, String runId) {
        return (message, progress) -> {
            NotificationResult result = new NotificationResult();
            result.setSessionKey(sessionKey);

            if (StrUtil.isBlank(message)) {
                result.setDelivered(false);
                result.setMessage("message 不能为空");
                return result;
            }

            ReplyTarget replyTarget = runtimeStoreService.getReplyTarget(sessionKey);
            if (replyTarget == null) {
                result.setDelivered(false);
                result.setMessage("当前会话没有可用的 ReplyTarget，无法主动通知");
                return result;
            }

            OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
            outboundEnvelope.setRunId(runId);
            outboundEnvelope.setReplyTarget(replyTarget);
            outboundEnvelope.setContent(message);
            outboundEnvelope.setProgress(progress);
            channelRegistry.send(outboundEnvelope);
            runtimeStoreService.appendRunEvent(runId, progress ? "notify_progress" : "notify", message);

            result.setDelivered(true);
            result.setMessage("sent to " + replyTarget.getChannelType() + ":" + replyTarget.getConversationId());
            return result;
        };
    }

    /**
     * 若当前渠道支持进度更新，则将运行中的增量内容透传到外部渠道。
     *
     * @param runId 运行任务标识
     * @param inboundEnvelope 当前入站消息
     * @param progress 增量内容
     */
    private void dispatchProgressOutbound(String runId, InboundEnvelope inboundEnvelope, String progress) {
        if (StrUtil.isBlank(progress)
                || inboundEnvelope == null
                || !inboundEnvelope.isExternalReplyEnabled()
                || inboundEnvelope.getReplyTarget() == null
                || inboundEnvelope.getReplyTarget().isDebugWeb()) {
            return;
        }

        ChannelAdapter adapter = channelRegistry.get(inboundEnvelope.getReplyTarget().getChannelType());
        if (adapter == null || !adapter.supportsProgressUpdates()) {
            return;
        }

        OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
        outboundEnvelope.setRunId(runId);
        outboundEnvelope.setReplyTarget(inboundEnvelope.getReplyTarget());
        outboundEnvelope.setContent(progress);
        outboundEnvelope.setProgress(true);
        channelRegistry.send(outboundEnvelope);
    }

    /**
     * 判断当前回复是否表示“不要对外回复”。
     *
     * @param response 最终回复
     * @return 若为 NO_REPLY 则返回 true
     */
    private boolean isNoReply(String response) {
        return StrUtil.equalsIgnoreCase(StrUtil.trim(response), NO_REPLY);
    }

    /**
     * 判断当前回复是否声明为“仅发送一次的最终汇总”。
     *
     * @param response 最终回复
     * @return 若命中最终汇总前缀则返回 true
     */
    private boolean isFinalReplyOnce(String response) {
        return StrUtil.startWithIgnoreCase(StrUtil.trim(response), FINAL_REPLY_ONCE_PREFIX);
    }

    /**
     * 移除运行时保留前缀，得到真正对模型历史和外部渠道可见的回复文本。
     *
     * @param response 原始回复
     * @return 可见回复
     */
    private String normalizeVisibleResponse(String response) {
        String trimmed = StrUtil.trim(response);
        if (StrUtil.startWithIgnoreCase(trimmed, FINAL_REPLY_ONCE_PREFIX)) {
            return StrUtil.trim(trimmed.substring(FINAL_REPLY_ONCE_PREFIX.length()));
        }
        return response;
    }

    /**
     * 若当前入站消息是子任务完成 continuation，则解析其父运行标识。
     *
     * @param inboundEnvelope 入站消息
     * @return 父运行标识；否则返回 null
     */
    private String resolveChildCompletionParentRunId(InboundEnvelope inboundEnvelope) {
        if (inboundEnvelope == null || inboundEnvelope.getChannelType() != ChannelType.SYSTEM) {
            return null;
        }
        String senderId = StrUtil.blankToDefault(inboundEnvelope.getSenderId(), "");
        String prefix = "child-complete:";
        return senderId.startsWith(prefix) ? senderId.substring(prefix.length()) : null;
    }
}
