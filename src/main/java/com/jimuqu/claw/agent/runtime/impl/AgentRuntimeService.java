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
import com.jimuqu.claw.agent.runtime.api.ProgressReportSupport;
import com.jimuqu.claw.agent.runtime.api.RunQuerySupport;
import com.jimuqu.claw.agent.runtime.api.SpawnTaskSupport;
import com.jimuqu.claw.agent.runtime.api.TaskControlSupport;
import com.jimuqu.claw.agent.runtime.registry.ActiveTaskRegistry;
import com.jimuqu.claw.agent.runtime.support.ConversationExecutionRequest;
import com.jimuqu.claw.agent.runtime.support.DeliveryResult;
import com.jimuqu.claw.agent.runtime.support.NotificationResult;
import com.jimuqu.claw.agent.runtime.support.ParentRunChildrenSummary;
import com.jimuqu.claw.agent.runtime.support.RuntimeDeliveryHelper;
import com.jimuqu.claw.agent.runtime.support.RuntimeSupportFactory;
import com.jimuqu.claw.agent.runtime.support.RuntimeReplyProtocol;
import com.jimuqu.claw.agent.runtime.support.SpawnTaskResult;
import com.jimuqu.claw.agent.runtime.support.SystemEventRequest;
import com.jimuqu.claw.agent.runtime.support.TaskControlResult;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.config.SolonClawProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 处理用户消息主链，并为子任务与查询提供运行时能力。
 */
public class AgentRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeService.class);

    private final ConversationAgent conversationAgent;
    private final RuntimeStoreService runtimeStoreService;
    private final ConversationScheduler conversationScheduler;
    private final ChannelRegistry channelRegistry;
    private final SystemEventRunner systemEventRunner;
    private final ActiveTaskRegistry activeTaskRegistry;
    private final SolonClawProperties properties;
    private final ScheduledExecutorService cancelTimer = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "solonclaw-cancel-timer");
            thread.setDaemon(true);
            return thread;
        }
    });

    public AgentRuntimeService(
            ConversationAgent conversationAgent,
            RuntimeStoreService runtimeStoreService,
            ConversationScheduler conversationScheduler,
            ChannelRegistry channelRegistry,
            SystemEventRunner systemEventRunner,
            ActiveTaskRegistry activeTaskRegistry,
            SolonClawProperties properties
    ) {
        this.conversationAgent = conversationAgent;
        this.runtimeStoreService = runtimeStoreService;
        this.conversationScheduler = conversationScheduler;
        this.channelRegistry = channelRegistry;
        this.systemEventRunner = systemEventRunner;
        this.activeTaskRegistry = activeTaskRegistry;
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

        ConversationScheduler.SessionState state = conversationScheduler.inspectUserMessage(inboundEnvelope.getSessionKey());

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
            RuntimeDeliveryHelper.recordDeliveryResult(runtimeStoreService, run.getRunId(), deliveryResult);
        }

        conversationScheduler.submit(inboundEnvelope.getSessionKey(), ConversationScheduler.SchedulerRunType.USER_MESSAGE, () -> processRun(inboundEnvelope, run.getRunId()));
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

        boolean isChildRun = StrUtil.isNotBlank(run.getParentRunId());
        if (isChildRun) {
            activeTaskRegistry.setExecutionThread(runId, Thread.currentThread());
            activeTaskRegistry.updateStatus(runId, RunStatus.RUNNING);
        }

        try {
            run.setStatus(RunStatus.RUNNING);
            run.setStartedAt(System.currentTimeMillis());
            runtimeStoreService.saveRun(run);
            runtimeStoreService.appendRunEvent(runId, "status", "running");
            log.info("Run {} started for session {}", runId, inboundEnvelope.getSessionKey());

            ConversationExecutionRequest request = new ConversationExecutionRequest();
            request.setSessionKey(inboundEnvelope.getSessionKey());
            request.setRunId(runId);
            request.setCurrentMessage(inboundEnvelope.getContent());
            request.setCurrentSourceKind(run.getSourceKind());
            request.setChildRun(isChildRun);
            request.setParentRunId(run.getParentRunId());
            request.setTaskTitle(run.getTaskTitle());
            request.setHistory(runtimeStoreService.loadConversationHistoryBefore(
                    inboundEnvelope.getSessionKey(),
                    inboundEnvelope.getSessionVersion()
            ));
            request.setSpawnTaskSupport(buildSpawnTaskSupport(runId, run, inboundEnvelope));
            request.setRunQuerySupport(RuntimeSupportFactory.buildRunQuerySupport(runtimeStoreService, inboundEnvelope.getSessionKey()));
            request.setNotificationSupport(RuntimeSupportFactory.buildNotificationSupport(runtimeStoreService, channelRegistry, inboundEnvelope.getSessionKey(), runId));
            request.setTaskControlSupport(buildTaskControlSupport(inboundEnvelope.getSessionKey()));
            if (isChildRun) {
                request.setProgressReportSupport(buildProgressReportSupport(runId));
            }
            if (!isChildRun) {
                request.setActiveTasks(activeTaskRegistry.getActiveTasks(inboundEnvelope.getSessionKey()));
            }

            final String[] lastProgressText = {""};
            final String[] latestProgress = {""};
            String response = conversationAgent.execute(request, progress -> {
                latestProgress[0] = progress;
                if (StrUtil.isBlank(progress) || StrUtil.equals(progress, lastProgressText[0])) {
                    return;
                }
                lastProgressText[0] = progress;
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

            if (RuntimeDeliveryHelper.isNoReply(response) || activeTaskRegistry.isCancelRequested(runId)) {
                run.setStatus(RunStatus.CANCELLED);
                run.setFinishedAt(System.currentTimeMillis());
                run.setErrorMessage("被父任务主动取消");
                runtimeStoreService.saveRun(run);
                runtimeStoreService.appendRunEvent(runId, "status", "cancelled");
                return;
            }

            String visibleResponse = RuntimeDeliveryHelper.normalizeVisibleResponse(response);
            if (StrUtil.isBlank(visibleResponse)) {
                handleEmptyUserResponse(run, inboundEnvelope, runId);
                return;
            }
            boolean suppressReply = RuntimeDeliveryHelper.isNoReply(response);
            run.setFinalResponse(visibleResponse);

            if (run.getStatus() == RunStatus.WAITING_CHILDREN) {
                if (!suppressReply) {
                    runtimeStoreService.appendAssistantConversationEvent(
                            inboundEnvelope.getSessionKey(),
                            runId,
                            inboundEnvelope.getMessageId(),
                            inboundEnvelope.getHistoryAnchorVersion(),
                            run.getSourceKind(),
                            visibleResponse
                    );
                    dispatchFinalReply(runId, inboundEnvelope.getReplyTarget(), visibleResponse);
                }
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

            if (!suppressReply) {
                dispatchFinalReply(runId, inboundEnvelope.getReplyTarget(), visibleResponse);
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
                RuntimeDeliveryHelper.recordDeliveryResult(runtimeStoreService, runId, deliveryResult);
            }
        } finally {
            if (isChildRun) {
                activeTaskRegistry.markCompleted(runId, run.getStatus());
                activeTaskRegistry.setExecutionThread(runId, null);
            }
        }
    }

    public void shutdown() {
        cancelTimer.shutdownNow();
    }

    private SpawnTaskResult spawnTask(String parentRunId, InboundEnvelope parentInbound, String taskTitle, String taskDescription, String batchKey) {
        if (StrUtil.isBlank(taskTitle)) {
            throw new IllegalArgumentException("taskTitle 不能为空");
        }
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
        childInbound.setContent(buildChildTaskPrompt(taskTitle, taskDescription));
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
        childRun.setTaskTitle(taskTitle.trim());
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
                        + ", title=" + taskTitle.trim()
                        + ", task=" + taskDescription.trim()
                        + (StrUtil.isBlank(childRun.getBatchKey()) ? "" : ", batchKey=" + childRun.getBatchKey())
        );
        runtimeStoreService.appendChildRunSpawnedEvent(
                parentInbound.getSessionKey(),
                parentRunId,
                parentRun.getSourceUserVersion(),
                childRun
        );

        activeTaskRegistry.register(parentInbound.getSessionKey(), childRun);
        conversationScheduler.submit(childSessionKey, ConversationScheduler.SchedulerRunType.CHILD_TASK, () -> processRun(childInbound, childRun.getRunId()));

        SpawnTaskResult result = new SpawnTaskResult();
        result.setRunId(childRun.getRunId());
        result.setSessionKey(childSessionKey);
        result.setTaskTitle(taskTitle.trim());
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
        request.setPolicy(com.jimuqu.claw.agent.model.enums.SystemEventPolicy.USER_VISIBLE_OPTIONAL);
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
        if (StrUtil.isNotBlank(run.getTaskTitle())) {
            builder.append("任务标题: ").append(run.getTaskTitle()).append('\n');
        }
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
        builder.append('\n').append('\n');
        builder.append("调度要求:").append('\n');
        if (overallSummary != null && overallSummary.isAllCompleted()) {
            builder.append("- 当前父运行下的全部子任务已完成。").append('\n');
            builder.append("- 现在必须给用户输出最终汇总回复。").append('\n');
            builder.append("- 不要返回 NO_REPLY，不要继续等待，也不要重新派生子任务。");
        } else {
            builder.append("- 还有子任务未完成。").append('\n');
            builder.append("- 你可以基于本次新结果增量同步给用户，或者在确实无需对外说话时返回 NO_REPLY。").append('\n');
            builder.append("- 不要重新从头执行整个任务，也不要重新派生子任务。");
        }
        return builder.toString().trim();
    }

    private SpawnTaskSupport buildSpawnTaskSupport(String runId, AgentRun run, InboundEnvelope inboundEnvelope) {
        if (run == null) {
            return null;
        }
        if (StrUtil.isBlank(run.getParentRunId()) || properties.getAgent().getSubtasks().isAllowNestedSpawn()) {
            return (taskTitle, taskDescription, batchKey) -> spawnTask(runId, inboundEnvelope, taskTitle, taskDescription, batchKey);
        }

        return (taskTitle, taskDescription, batchKey) -> {
            String reason = "当前子任务默认禁止继续派生子任务；请先返回结果给父任务，由父任务决定是否继续拆分";
            runtimeStoreService.appendRunEvent(
                    runId,
                    "spawn_task_blocked",
                    reason
                            + (StrUtil.isBlank(taskTitle) ? "" : "，title=" + taskTitle.trim())
                            + (StrUtil.isBlank(taskDescription) ? "" : "，task=" + taskDescription.trim())
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

    private TaskControlSupport buildTaskControlSupport(String parentSessionKey) {
        return new TaskControlSupport() {
            @Override
            public TaskControlResult cancelTask(String runId) {
                TaskControlResult result = new TaskControlResult();
                if (StrUtil.isBlank(runId)) {
                    result.setCode("invalid_run_id");
                    result.setSuccess(false);
                    result.setMessage("runId 不能为空");
                    return result;
                }
                com.jimuqu.claw.agent.runtime.registry.ActiveTaskEntry entry = activeTaskRegistry.getEntry(runId);
                if (entry == null) {
                    result.setCode("not_found");
                    result.setSuccess(false);
                    result.setMessage("未找到活跃任务: " + runId);
                    return result;
                }
                if (!StrUtil.equals(parentSessionKey, entry.getParentSessionKey())) {
                    result.setCode("forbidden");
                    result.setSuccess(false);
                    result.setMessage("该任务不属于当前会话");
                    return result;
                }

                activeTaskRegistry.requestCancel(runId);
                activeTaskRegistry.updateStatus(runId, RunStatus.CANCELLED);
                runtimeStoreService.appendRunEvent(runId, "cancel_requested", "父任务请求取消");
                scheduleInterruptFallback(runId);
                result.setCode("cancel_requested");
                result.setSuccess(true);
                result.setMessage("已请求取消任务: " + runId);
                return result;
            }

            @Override
            public TaskControlResult appendInstruction(String runId, String instruction) {
                TaskControlResult result = new TaskControlResult();
                if (StrUtil.isBlank(runId)) {
                    result.setCode("invalid_run_id");
                    result.setSuccess(false);
                    result.setMessage("runId 不能为空");
                    return result;
                }
                com.jimuqu.claw.agent.runtime.registry.ActiveTaskEntry entry = activeTaskRegistry.getEntry(runId);
                if (entry == null) {
                    result.setCode("not_found");
                    result.setSuccess(false);
                    result.setMessage("未找到活跃任务: " + runId);
                    return result;
                }
                if (!StrUtil.equals(parentSessionKey, entry.getParentSessionKey())) {
                    result.setCode("forbidden");
                    result.setSuccess(false);
                    result.setMessage("该任务不属于当前会话");
                    return result;
                }
                if (StrUtil.isBlank(instruction)) {
                    result.setCode("invalid_instruction");
                    result.setSuccess(false);
                    result.setMessage("instruction 不能为空");
                    return result;
                }

                runtimeStoreService.appendSystemConversationEvent(
                        entry.getChildSessionKey(),
                        runId,
                        "[父任务追加指令]\n" + instruction.trim()
                );
                org.noear.solon.ai.chat.ChatSession session = activeTaskRegistry.getAgentSession(runId);
                if (session != null) {
                    synchronized (session) {
                        session.addMessage(org.noear.solon.ai.chat.message.ChatMessage.ofSystem("[父任务追加指令]\n" + instruction.trim()));
                    }
                }
                runtimeStoreService.appendRunEvent(runId, "instruction_injected", instruction.trim());
                result.setCode("instruction_injected");
                result.setSuccess(true);
                result.setMessage("指令已追加到子任务: " + runId);
                return result;
            }
        };
    }

    private void scheduleInterruptFallback(String runId) {
        int timeoutSeconds = properties.getAgent().getScheduler().getCancelCooperativeTimeoutSeconds();
        cancelTimer.schedule(() -> {
            Thread thread = activeTaskRegistry.getExecutionThread(runId);
            if (thread != null && thread.isAlive() && activeTaskRegistry.isCancelRequested(runId)) {
                thread.interrupt();
                runtimeStoreService.appendRunEvent(runId, "cancel_interrupt", "协作取消超时，已强制中断线程");
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
    }

    private ProgressReportSupport buildProgressReportSupport(String runId) {
        return (phase, detail) -> {
            AgentRun run = runtimeStoreService.getRun(runId);
            if (run != null) {
                if (StrUtil.equals(run.getLatestPhase(), phase) && StrUtil.equals(run.getLatestProgressDetail(), detail)) {
                    activeTaskRegistry.updateProgress(runId, phase, detail);
                    return;
                }
                run.setLatestPhase(phase);
                run.setLatestProgressDetail(detail);
                run.setLatestProgressAt(System.currentTimeMillis());
                runtimeStoreService.saveRun(run);
            }
            activeTaskRegistry.updateProgress(runId, phase, detail);
            runtimeStoreService.appendRunEvent(runId, "progress_report", "phase=" + phase + ", detail=" + detail);
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
        RuntimeDeliveryHelper.recordDeliveryResult(runtimeStoreService, runId, deliveryResult);
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
            RuntimeDeliveryHelper.recordDeliveryResult(runtimeStoreService, runId, deliveryResult);
            log.info(
                    "Run {} empty response fallback dispatched. channelType={}, conversationType={}, conversationId={}",
                    runId,
                    inboundEnvelope.getReplyTarget().getChannelType(),
                    inboundEnvelope.getReplyTarget().getConversationType(),
                    inboundEnvelope.getReplyTarget().getConversationId()
            );
        }
    }

    private void dispatchFinalReply(String runId, ReplyTarget replyTarget, String content) {
        if (replyTarget == null || StrUtil.isBlank(content)) {
            return;
        }

        OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
        outboundEnvelope.setRunId(runId);
        outboundEnvelope.setReplyTarget(replyTarget);
        outboundEnvelope.setContent(content);
        DeliveryResult deliveryResult = channelRegistry.send(outboundEnvelope);
        RuntimeDeliveryHelper.recordDeliveryResult(runtimeStoreService, runId, deliveryResult);
        log.info(
                "Run {} reply dispatched. channelType={}, conversationType={}, conversationId={}",
                runId,
                replyTarget.getChannelType(),
                replyTarget.getConversationType(),
                replyTarget.getConversationId()
        );
    }

    private String buildChildTaskPrompt(String taskTitle, String taskDescription) {
        StringBuilder builder = new StringBuilder();
        builder.append("任务标题: ").append(taskTitle.trim()).append('\n');
        builder.append("任务说明:").append('\n');
        builder.append(taskDescription.trim()).append('\n');
        builder.append('\n');
        builder.append("执行约束: 必须严格围绕这个任务标题和任务说明完成工作，不要串到其他项目或其他任务。");
        return builder.toString().trim();
    }

}
