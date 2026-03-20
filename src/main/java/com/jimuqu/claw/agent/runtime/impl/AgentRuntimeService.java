package com.jimuqu.claw.agent.runtime.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.channel.ChannelAdapter;
import com.jimuqu.claw.agent.channel.ChannelRegistry;
import com.jimuqu.claw.agent.model.envelope.InboundEnvelope;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.enums.RunStatus;
import com.jimuqu.claw.agent.model.event.RunEvent;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.runtime.api.ConversationAgent;
import com.jimuqu.claw.agent.runtime.api.NotificationSupport;
import com.jimuqu.claw.agent.runtime.api.RunQuerySupport;
import com.jimuqu.claw.agent.runtime.api.SpawnTaskSupport;
import com.jimuqu.claw.agent.runtime.support.ConversationExecutionRequest;
import com.jimuqu.claw.agent.runtime.support.DeliveryResult;
import com.jimuqu.claw.agent.runtime.support.NotificationResult;
import com.jimuqu.claw.agent.runtime.support.ParentRunChildrenSummary;
import com.jimuqu.claw.agent.runtime.support.SpawnTaskResult;
import com.jimuqu.claw.agent.runtime.support.SystemEventRequest;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.config.SolonClawProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 处理用户消息主链，并为子任务与查询提供运行时能力。
 */
public class AgentRuntimeService {
    public static final String NO_REPLY = "NO_REPLY";
    public static final String FINAL_REPLY_ONCE_PREFIX = "FINAL_REPLY_ONCE:";

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeService.class);

    private final ConversationAgent conversationAgent;
    private final RuntimeStoreService runtimeStoreService;
    private final ConversationScheduler conversationScheduler;
    private final ChannelRegistry channelRegistry;
    private final SystemEventRunner systemEventRunner;
    private final SolonClawProperties properties;

    public AgentRuntimeService(
            ConversationAgent conversationAgent,
            RuntimeStoreService runtimeStoreService,
            ConversationScheduler conversationScheduler,
            ChannelRegistry channelRegistry,
            SystemEventRunner systemEventRunner,
            SolonClawProperties properties
    ) {
        this.conversationAgent = conversationAgent;
        this.runtimeStoreService = runtimeStoreService;
        this.conversationScheduler = conversationScheduler;
        this.channelRegistry = channelRegistry;
        this.systemEventRunner = systemEventRunner;
        this.properties = properties;
    }

    public String submitInbound(InboundEnvelope inboundEnvelope) {
        if (inboundEnvelope == null) {
            return null;
        }
        if (inboundEnvelope.getSourceKind() == null) {
            inboundEnvelope.setSourceKind(RuntimeSourceKind.USER_MESSAGE);
        }
        if (inboundEnvelope.getSourceKind() != RuntimeSourceKind.USER_MESSAGE) {
            throw new IllegalArgumentException("AgentRuntimeService 只接收 USER_MESSAGE");
        }

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
        inboundEnvelope.setHistoryAnchorVersion(nextConversationVersion);
        long version = runtimeStoreService.appendInboundConversationEvent(inboundEnvelope);
        inboundEnvelope.setSessionVersion(version);
        if (inboundEnvelope.getReplyTarget() != null) {
            runtimeStoreService.rememberReplyTarget(inboundEnvelope.getSessionKey(), inboundEnvelope.getReplyTarget());
        }
        log.info(
                "Accepted inbound user message. channelType={}, sessionKey={}, messageId={}, sessionVersion={}",
                inboundEnvelope.getChannelType(),
                inboundEnvelope.getSessionKey(),
                inboundEnvelope.getMessageId(),
                version
        );

        AgentRun run = new AgentRun();
        run.setRunId(runtimeStoreService.newRunId());
        run.setSessionKey(inboundEnvelope.getSessionKey());
        run.setSourceMessageId(inboundEnvelope.getMessageId());
        run.setSourceKind(RuntimeSourceKind.USER_MESSAGE);
        run.setSourceUserVersion(inboundEnvelope.getHistoryAnchorVersion());
        run.setReplyTarget(inboundEnvelope.getReplyTarget());
        run.setStatus(RunStatus.QUEUED);
        run.setCreatedAt(System.currentTimeMillis());
        runtimeStoreService.saveRun(run);
        runtimeStoreService.appendRunEvent(run.getRunId(), "status", "queued");

        if (properties.getAgent().getScheduler().isAckWhenBusy()
                && state.activeCount() > 0
                && inboundEnvelope.getReplyTarget() != null) {
            OutboundEnvelope ack = new OutboundEnvelope();
            ack.setRunId(run.getRunId());
            ack.setReplyTarget(inboundEnvelope.getReplyTarget());
            ack.setContent(state.queuedCount() > 0 ? "已收到，排队处理中。" : "已收到，正在并行处理中。");
            DeliveryResult deliveryResult = channelRegistry.send(ack);
            recordDeliveryResult(run.getRunId(), deliveryResult);
        }

        conversationScheduler.submit(inboundEnvelope.getSessionKey(), () -> processRun(inboundEnvelope, run.getRunId()));
        return run.getRunId();
    }

    public AgentRun getRun(String runId) {
        return runtimeStoreService.getRun(runId);
    }

    public List<RunEvent> getRunEvents(String runId, long afterSeq) {
        return runtimeStoreService.getRunEvents(runId, afterSeq);
    }

    public List<AgentRun> listChildRuns(String parentRunId, String batchKey) {
        return runtimeStoreService.listChildRunsByParentRun(parentRunId, StrUtil.blankToDefault(StrUtil.trim(batchKey), null));
    }

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
            request.setCurrentSourceKind(run.getSourceKind());
            request.setChildRun(StrUtil.isNotBlank(run.getParentRunId()));
            request.setParentRunId(run.getParentRunId());
            request.setHistory(runtimeStoreService.loadConversationHistoryBefore(
                    inboundEnvelope.getSessionKey(),
                    inboundEnvelope.getSessionVersion()
            ));
            request.setSpawnTaskSupport(buildSpawnTaskSupport(runId, run, inboundEnvelope));
            request.setRunQuerySupport(buildRunQuerySupport(inboundEnvelope.getSessionKey()));
            request.setNotificationSupport(buildNotificationSupport(inboundEnvelope.getSessionKey(), runId));

            final String[] latestProgress = {""};
            String response = conversationAgent.execute(request, progress -> {
                latestProgress[0] = progress;
                runtimeStoreService.appendRunEvent(runId, "progress", progress);
                dispatchProgressOutbound(runId, inboundEnvelope, progress);
            });
            if (StrUtil.isBlank(response)) {
                response = latestProgress[0];
            }

            run = runtimeStoreService.getRun(runId);
            if (run == null) {
                return;
            }

            String visibleResponse = normalizeVisibleResponse(response);
            if (StrUtil.isBlank(visibleResponse)) {
                handleEmptyUserResponse(run, inboundEnvelope, runId);
                return;
            }
            boolean suppressReply = isNoReply(response);
            run.setFinalResponse(visibleResponse);

            if (run.getStatus() == RunStatus.WAITING_CHILDREN) {
                run.setFinishedAt(System.currentTimeMillis());
                runtimeStoreService.saveRun(run);
                runtimeStoreService.appendRunEvent(runId, "reply", visibleResponse);
                runtimeStoreService.appendRunEvent(runId, "status", "waiting_children");
                log.info("Run {} is waiting child tasks for session {}", runId, inboundEnvelope.getSessionKey());
                return;
            }

            if (!suppressReply) {
                runtimeStoreService.appendAssistantConversationEvent(
                        inboundEnvelope.getSessionKey(),
                        runId,
                        inboundEnvelope.getMessageId(),
                        inboundEnvelope.getHistoryAnchorVersion(),
                        run.getSourceKind(),
                        visibleResponse
                );
            }

            run.setStatus(RunStatus.SUCCEEDED);
            run.setFinishedAt(System.currentTimeMillis());
            run.setFinalResponse(visibleResponse);
            runtimeStoreService.saveRun(run);
            runtimeStoreService.appendRunEvent(runId, "reply", visibleResponse);
            runtimeStoreService.appendRunEvent(runId, "status", "succeeded");
            log.info("Run {} succeeded for session {}", runId, inboundEnvelope.getSessionKey());
            handleChildRunCompletion(run);

            if (!suppressReply
                    && inboundEnvelope.getReplyTarget() != null) {
                OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
                outboundEnvelope.setRunId(runId);
                outboundEnvelope.setReplyTarget(inboundEnvelope.getReplyTarget());
                outboundEnvelope.setContent(visibleResponse);
                DeliveryResult deliveryResult = channelRegistry.send(outboundEnvelope);
                recordDeliveryResult(runId, deliveryResult);
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

            if (inboundEnvelope.getReplyTarget() != null) {
                OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
                outboundEnvelope.setRunId(runId);
                outboundEnvelope.setReplyTarget(inboundEnvelope.getReplyTarget());
                outboundEnvelope.setContent("抱歉，这次处理失败了：" + throwable.getMessage());
                DeliveryResult deliveryResult = channelRegistry.send(outboundEnvelope);
                recordDeliveryResult(runId, deliveryResult);
            }
        }
    }

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
        childInbound.setChannelInstanceId("subtask");
        childInbound.setSenderId("parent-run:" + parentRunId);
        childInbound.setConversationId(childSessionKey);
        childInbound.setConversationType(ConversationType.PRIVATE);
        childInbound.setContent(taskDescription.trim());
        childInbound.setReceivedAt(now);
        childInbound.setSessionKey(childSessionKey);
        childInbound.setReplyTarget(null);
        childInbound.setSourceKind(RuntimeSourceKind.USER_MESSAGE);

        long version = runtimeStoreService.appendInboundConversationEvent(childInbound);
        childInbound.setSessionVersion(version);
        childInbound.setHistoryAnchorVersion(version);

        AgentRun childRun = new AgentRun();
        childRun.setRunId(runtimeStoreService.newRunId());
        childRun.setSessionKey(childSessionKey);
        childRun.setSourceMessageId(childMessageId);
        childRun.setSourceKind(RuntimeSourceKind.USER_MESSAGE);
        childRun.setSourceUserVersion(version);
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

        conversationScheduler.submit(childSessionKey, () -> processRun(childInbound, childRun.getRunId()));

        SpawnTaskResult result = new SpawnTaskResult();
        result.setRunId(childRun.getRunId());
        result.setSessionKey(childSessionKey);
        result.setTaskDescription(taskDescription.trim());
        result.setBatchKey(childRun.getBatchKey());
        return result;
    }

    private void handleChildRunCompletion(AgentRun run) {
        if (run == null || StrUtil.isBlank(run.getParentRunId()) || StrUtil.isBlank(run.getParentSessionKey())) {
            return;
        }

        AgentRun parentRun = runtimeStoreService.getRun(run.getParentRunId());
        long sourceUserVersion = parentRun == null ? 0L : parentRun.getSourceUserVersion();
        ParentRunChildrenSummary overallSummary = runtimeStoreService.summarizeChildRuns(run.getParentRunId(), null);
        ParentRunChildrenSummary batchSummary = StrUtil.isBlank(run.getBatchKey())
                ? null
                : runtimeStoreService.summarizeChildRuns(run.getParentRunId(), run.getBatchKey());
        appendParentChildCompletionEvents(run, overallSummary, batchSummary);

        runtimeStoreService.appendChildRunCompletedEvent(run.getParentSessionKey(), run.getParentRunId(), sourceUserVersion, run);
        SystemEventRequest request = new SystemEventRequest();
        request.setSourceKind(RuntimeSourceKind.CHILD_CONTINUATION);
        request.setSessionKey(run.getParentSessionKey());
        request.setReplyTarget(run.getParentReplyTarget());
        request.setPolicy(com.jimuqu.claw.agent.model.enums.SystemEventPolicy.AGGREGATE_ONLY);
        request.setContent(buildChildCompletionMessage(run, overallSummary, batchSummary));
        request.setSourceUserVersion(sourceUserVersion);
        request.setRelatedRunId(run.getParentRunId());
        request.setAllowNotifyUser(false);
        String continuationRunId = systemEventRunner.submit(request);
        runtimeStoreService.appendRunEvent(
                run.getParentRunId(),
                "child_continuation_triggered",
                "childRunId=" + run.getRunId()
                        + ", continuationRunId=" + continuationRunId
                        + ", pendingChildren=" + (overallSummary == null ? 0 : overallSummary.getPendingChildren())
        );
    }

    private String buildChildCompletionMessage(
            AgentRun run,
            ParentRunChildrenSummary overallSummary,
            ParentRunChildrenSummary batchSummary
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("[子任务 continuation 事件]").append('\n');
        builder.append("父运行ID: ").append(run.getParentRunId()).append('\n');
        builder.append("子运行ID: ").append(run.getRunId()).append('\n');
        builder.append("状态: ").append(run.getStatus()).append('\n');
        if (StrUtil.isNotBlank(run.getTaskDescription())) {
            builder.append("任务: ").append(run.getTaskDescription()).append('\n');
        }
        if (overallSummary != null && overallSummary.getTotalChildren() > 0) {
            builder.append("全部子任务汇总: total=").append(overallSummary.getTotalChildren())
                    .append(", succeeded=").append(overallSummary.getSucceededChildren())
                    .append(", failed=").append(overallSummary.getFailedChildren())
                    .append(", pending=").append(overallSummary.getPendingChildren())
                    .append(", allCompleted=").append(overallSummary.isAllCompleted())
                    .append('\n');
        }
        if (batchSummary != null && batchSummary.getTotalChildren() > 0) {
            builder.append("当前批次汇总: batchKey=").append(StrUtil.blankToDefault(batchSummary.getBatchKey(), "(空)"))
                    .append(", total=").append(batchSummary.getTotalChildren())
                    .append(", succeeded=").append(batchSummary.getSucceededChildren())
                    .append(", failed=").append(batchSummary.getFailedChildren())
                    .append(", pending=").append(batchSummary.getPendingChildren())
                    .append(", allCompleted=").append(batchSummary.isAllCompleted())
                    .append('\n');
        }
        if (run.getStatus() == RunStatus.SUCCEEDED) {
            builder.append("结果:\n").append(StrUtil.blankToDefault(run.getFinalResponse(), "(空结果)"));
        } else {
            builder.append("错误:\n").append(StrUtil.blankToDefault(run.getErrorMessage(), "(未知错误)"));
        }
        return builder.toString().trim();
    }

    private SpawnTaskSupport buildSpawnTaskSupport(String runId, AgentRun run, InboundEnvelope inboundEnvelope) {
        if (run == null) {
            return null;
        }
        if (StrUtil.isBlank(run.getParentRunId()) || properties.getAgent().getSubtasks().isAllowNestedSpawn()) {
            return (taskDescription, batchKey) -> spawnTask(runId, inboundEnvelope, taskDescription, batchKey);
        }

        return (taskDescription, batchKey) -> {
            String reason = "当前子任务默认禁止继续派生子任务；请先返回结果给父任务，由父任务决定是否继续拆分";
            runtimeStoreService.appendRunEvent(
                    runId,
                    "spawn_task_blocked",
                    reason + (StrUtil.isBlank(taskDescription) ? "" : "，task=" + taskDescription.trim())
            );
            throw new IllegalStateException(reason);
        };
    }

    private void appendParentChildCompletionEvents(
            AgentRun childRun,
            ParentRunChildrenSummary overallSummary,
            ParentRunChildrenSummary batchSummary
    ) {
        if (childRun == null || StrUtil.isBlank(childRun.getParentRunId())) {
            return;
        }

        StringBuilder received = new StringBuilder();
        received.append("childRunId=").append(childRun.getRunId())
                .append(", status=").append(childRun.getStatus());
        if (StrUtil.isNotBlank(childRun.getBatchKey())) {
            received.append(", batchKey=").append(childRun.getBatchKey());
        }
        if (overallSummary != null) {
            received.append(", totalChildren=").append(overallSummary.getTotalChildren())
                    .append(", pendingChildren=").append(overallSummary.getPendingChildren());
        }
        runtimeStoreService.appendRunEvent(childRun.getParentRunId(), "child_completion_received", received.toString());

        if (batchSummary != null && batchSummary.getTotalChildren() > 0) {
            runtimeStoreService.appendRunEvent(
                    childRun.getParentRunId(),
                    "child_batch_progress",
                    "batchKey=" + StrUtil.blankToDefault(batchSummary.getBatchKey(), "(空)")
                            + ", total=" + batchSummary.getTotalChildren()
                            + ", succeeded=" + batchSummary.getSucceededChildren()
                            + ", failed=" + batchSummary.getFailedChildren()
                            + ", pending=" + batchSummary.getPendingChildren()
            );
        }

        if (overallSummary != null) {
            runtimeStoreService.appendRunEvent(
                    childRun.getParentRunId(),
                    overallSummary.isAllCompleted() ? "children_all_completed" : "children_pending",
                    "total=" + overallSummary.getTotalChildren()
                            + ", succeeded=" + overallSummary.getSucceededChildren()
                            + ", failed=" + overallSummary.getFailedChildren()
                            + ", pending=" + overallSummary.getPendingChildren()
            );
        }
    }

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
            DeliveryResult deliveryResult = channelRegistry.send(outboundEnvelope);
            recordDeliveryResult(runId, deliveryResult);
            applyDeliveryResult(result, deliveryResult);
            runtimeStoreService.appendRunEvent(runId, progress ? "notify_progress" : "notify", message);

            result.setDelivered(deliveryResult.isDelivered());
            if (StrUtil.isBlank(result.getMessage())) {
                result.setMessage("sent to " + replyTarget.getChannelType() + ":" + replyTarget.getConversationId());
            }
            return result;
        };
    }

    private void dispatchProgressOutbound(String runId, InboundEnvelope inboundEnvelope, String progress) {
        if (StrUtil.isBlank(progress)
                || inboundEnvelope == null
                || inboundEnvelope.getReplyTarget() == null) {
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
        DeliveryResult deliveryResult = channelRegistry.send(outboundEnvelope);
        recordDeliveryResult(runId, deliveryResult);
    }

    private boolean isNoReply(String response) {
        return StrUtil.equalsIgnoreCase(StrUtil.trim(response), NO_REPLY);
    }

    private String normalizeVisibleResponse(String response) {
        String trimmed = StrUtil.trim(response);
        if (StrUtil.startWithIgnoreCase(trimmed, FINAL_REPLY_ONCE_PREFIX)) {
            return StrUtil.trim(trimmed.substring(FINAL_REPLY_ONCE_PREFIX.length()));
        }
        return response;
    }

    private void handleEmptyUserResponse(AgentRun run, InboundEnvelope inboundEnvelope, String runId) {
        String fallback = "这次处理没有拿到有效结果，可能是模型响应超时或解析异常。请再试一次。";
        run.setStatus(RunStatus.FAILED);
        run.setFinishedAt(System.currentTimeMillis());
        run.setErrorMessage("模型未返回有效结果");
        run.setFinalResponse(fallback);
        runtimeStoreService.saveRun(run);
        runtimeStoreService.appendRunEvent(runId, "llm_empty_response", fallback);
        runtimeStoreService.appendRunEvent(runId, "reply", fallback);
        runtimeStoreService.appendRunEvent(runId, "status", "failed");

        runtimeStoreService.appendAssistantConversationEvent(
                inboundEnvelope.getSessionKey(),
                runId,
                inboundEnvelope.getMessageId(),
                inboundEnvelope.getHistoryAnchorVersion(),
                RuntimeSourceKind.USER_MESSAGE,
                fallback
        );

        if (inboundEnvelope.getReplyTarget() != null) {
            OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
            outboundEnvelope.setRunId(runId);
            outboundEnvelope.setReplyTarget(inboundEnvelope.getReplyTarget());
            outboundEnvelope.setContent(fallback);
            DeliveryResult deliveryResult = channelRegistry.send(outboundEnvelope);
            recordDeliveryResult(runId, deliveryResult);
            log.info(
                    "Run {} empty response fallback dispatched. channelType={}, conversationType={}, conversationId={}",
                    runId,
                    inboundEnvelope.getReplyTarget().getChannelType(),
                    inboundEnvelope.getReplyTarget().getConversationType(),
                    inboundEnvelope.getReplyTarget().getConversationId()
            );
        }
    }

    private void applyDeliveryResult(NotificationResult result, DeliveryResult deliveryResult) {
        if (result == null || deliveryResult == null) {
            return;
        }
        result.setTruncated(deliveryResult.isTruncated());
        result.setSegmented(deliveryResult.isSegmented());
        result.setSegmentCount(deliveryResult.getSegmentCount());
        result.setOriginalLength(deliveryResult.getOriginalLength());
        result.setFinalLength(deliveryResult.getFinalLength());
        result.setChannelType(deliveryResult.getChannelType() == null ? null : deliveryResult.getChannelType().name());
        result.setMessage(deliveryResult.getMessage());
    }

    private void recordDeliveryResult(String runId, DeliveryResult deliveryResult) {
        if (deliveryResult == null) {
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append("channel=").append(deliveryResult.getChannelType())
                .append(", segmentCount=").append(deliveryResult.getSegmentCount())
                .append(", originalLength=").append(deliveryResult.getOriginalLength())
                .append(", finalLength=").append(deliveryResult.getFinalLength());
        if (StrUtil.isNotBlank(deliveryResult.getMessage())) {
            message.append(", detail=").append(deliveryResult.getMessage());
        }

        if (!deliveryResult.isDelivered()) {
            runtimeStoreService.appendRunEvent(runId, "delivery_failed", message.toString());
            return;
        }
        runtimeStoreService.appendRunEvent(runId, "delivery_sent", message.toString());
        if (deliveryResult.isSegmented()) {
            runtimeStoreService.appendRunEvent(runId, "delivery_segmented", message.toString());
        }
        if (deliveryResult.isTruncated()) {
            runtimeStoreService.appendRunEvent(runId, "delivery_truncated", message.toString());
        }
    }
}
