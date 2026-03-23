package com.jimuqu.claw.agent.runtime.impl;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.channel.ChannelRegistry;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.enums.SystemEventPolicy;
import com.jimuqu.claw.agent.model.enums.RunStatus;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.runtime.api.ConversationAgent;
import com.jimuqu.claw.agent.runtime.api.NotificationSupport;
import com.jimuqu.claw.agent.runtime.api.RunQuerySupport;
import com.jimuqu.claw.agent.runtime.support.ConversationExecutionRequest;
import com.jimuqu.claw.agent.runtime.support.DeliveryResult;
import com.jimuqu.claw.agent.runtime.support.NotificationResult;
import com.jimuqu.claw.agent.runtime.support.ParentRunChildrenSummary;
import com.jimuqu.claw.agent.runtime.support.RuntimeDeliveryHelper;
import com.jimuqu.claw.agent.runtime.support.RuntimeSupportFactory;
import com.jimuqu.claw.agent.runtime.support.SystemEventRequest;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.config.SolonClawProperties;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 负责执行 systemEvent、heartbeat 和 child continuation 等内部事件。
 */
public class SystemEventRunner {
    private static final Logger log = LoggerFactory.getLogger(SystemEventRunner.class);

    private final ConversationAgent conversationAgent;
    private final RuntimeStoreService runtimeStoreService;
    private final ConversationScheduler conversationScheduler;
    private final ChannelRegistry channelRegistry;
    private final SolonClawProperties properties;
    private final Queue<SystemEventRequest> pendingRequests = new ConcurrentLinkedQueue<SystemEventRequest>();
    private ScheduledExecutorService scheduler;

    public SystemEventRunner(
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

    public void start() {
        if (scheduler != null) {
            return;
        }

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "solonclaw-system-event-runner");
            thread.setDaemon(true);
            return thread;
        };
        scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
        scheduler.scheduleWithFixedDelay(this::safeDrainPending, 1L, 1L, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public String submit(SystemEventRequest request) {
        validate(request);
        if (request.isWakeImmediately()) {
            return dispatch(request);
        }

        pendingRequests.offer(copyRequest(request));
        return null;
    }

    private void safeDrainPending() {
        try {
            drainPending();
        } catch (Throwable throwable) {
            log.warn("Failed to drain pending system events: {}", throwable.getMessage(), throwable);
        }
    }

    private void drainPending() {
        SystemEventRequest request;
        while ((request = pendingRequests.poll()) != null) {
            dispatch(request);
        }
    }

    private String dispatch(SystemEventRequest request) {
        long sourceUserVersion = request.getSourceUserVersion() > 0
                ? request.getSourceUserVersion()
                : runtimeStoreService.getLatestUserConversationVersion(request.getSessionKey());

        AgentRun run = new AgentRun();
        run.setRunId(runtimeStoreService.newRunId());
        run.setSessionKey(request.getSessionKey());
        run.setSourceMessageId("system-event-" + java.util.UUID.randomUUID().toString().replace("-", ""));
        run.setSourceKind(request.getSourceKind());
        run.setSourceUserVersion(sourceUserVersion);
        run.setReplyTarget(request.getReplyTarget());
        run.setRelatedRunId(request.getRelatedRunId());
        run.setStatus(RunStatus.QUEUED);
        run.setCreatedAt(System.currentTimeMillis());
        runtimeStoreService.saveRun(run);
        runtimeStoreService.appendRunEvent(run.getRunId(), eventTypeFor(request.getSourceKind()), request.getContent());
        runtimeStoreService.appendRunEvent(run.getRunId(), "status", "queued");

        conversationScheduler.submit(
                request.getSessionKey(),
                () -> processRun(copyRequest(request), run.getRunId())
        );
        return run.getRunId();
    }

    private void processRun(SystemEventRequest request, String runId) {
        AgentRun run = runtimeStoreService.getRun(runId);
        if (run == null) {
            return;
        }

        try {
            run.setStatus(RunStatus.RUNNING);
            run.setStartedAt(System.currentTimeMillis());
            runtimeStoreService.saveRun(run);
            runtimeStoreService.appendRunEvent(runId, "status", "running");

            ConversationExecutionRequest executionRequest = new ConversationExecutionRequest();
            executionRequest.setSessionKey(request.getSessionKey());
            executionRequest.setCurrentMessage(request.getContent());
            executionRequest.setCurrentSourceKind(request.getSourceKind());
            List<ChatMessage> history = runtimeStoreService.loadConversationHistoryBefore(
                    request.getSessionKey(),
                    runtimeStoreService.getLatestConversationVersion(request.getSessionKey()) + 1L
            );
            if (request.getSourceKind() == RuntimeSourceKind.CHILD_CONTINUATION) {
                history = trimHistoryForContinuation(history, 6);
                executionRequest.setLightContext(true);
            }
            executionRequest.setHistory(history);
            executionRequest.setRunQuerySupport(RuntimeSupportFactory.buildRunQuerySupport(runtimeStoreService, request.getSessionKey()));
            executionRequest.setNotificationSupport(request.isAllowNotifyUser()
                    ? RuntimeSupportFactory.buildNotificationSupport(runtimeStoreService, channelRegistry, request.getSessionKey(), request.getReplyTarget(), runId)
                    : null);

            final String[] lastProgressText = {""};
            final String[] latestProgress = {""};
            String response = conversationAgent.execute(executionRequest, progress -> {
                latestProgress[0] = progress;
                if (StrUtil.isBlank(progress) || StrUtil.equals(progress, lastProgressText[0])) {
                    return;
                }
                lastProgressText[0] = progress;
                runtimeStoreService.appendRunEvent(runId, "progress", progress);
            });
            if (StrUtil.isBlank(response)) {
                response = latestProgress[0];
            }

            run = runtimeStoreService.getRun(runId);
            if (run == null) {
                return;
            }

            String visibleResponse = RuntimeDeliveryHelper.normalizeVisibleResponse(response);
            if (StrUtil.isBlank(visibleResponse)) {
                run.setStatus(RunStatus.FAILED);
                run.setFinishedAt(System.currentTimeMillis());
                run.setErrorMessage("模型未返回有效结果");
                runtimeStoreService.saveRun(run);
                runtimeStoreService.appendRunEvent(runId, "llm_empty_response", "system event 未返回有效结果");
                runtimeStoreService.appendRunEvent(runId, "status", "failed");
                return;
            }
            boolean notifyUsed = runtimeStoreService.hasRunEventType(runId, "notify")
                    || runtimeStoreService.hasRunEventType(runId, "notify_progress");
            boolean delivered = false;

            if (request.getPolicy() == SystemEventPolicy.AGGREGATE_ONLY) {
                delivered = tryDeliverAggregateReply(runId, request, response, visibleResponse);
            } else if (request.getPolicy() == SystemEventPolicy.USER_VISIBLE_OPTIONAL) {
                delivered = tryDeliverUserVisibleReply(runId, request, response, visibleResponse, notifyUsed);
            } else if (!notifyUsed && StrUtil.isNotBlank(visibleResponse) && !RuntimeDeliveryHelper.isNoReply(response)) {
                runtimeStoreService.appendRunEvent(runId, "delivery_suppressed", visibleResponse);
            }

            run.setStatus(RunStatus.SUCCEEDED);
            run.setFinishedAt(System.currentTimeMillis());
            run.setFinalResponse(visibleResponse);
            runtimeStoreService.saveRun(run);
            runtimeStoreService.appendRunEvent(runId, "reply", visibleResponse);
            runtimeStoreService.appendRunEvent(runId, "status", "succeeded");

            if (delivered
                    && (request.getPolicy() == SystemEventPolicy.AGGREGATE_ONLY
                    || request.getPolicy() == SystemEventPolicy.USER_VISIBLE_OPTIONAL)) {
                runtimeStoreService.appendAssistantConversationEvent(
                        request.getSessionKey(),
                        runId,
                        run.getSourceMessageId(),
                        run.getSourceUserVersion(),
                        run.getSourceKind(),
                        visibleResponse
                );
            }
        } catch (Throwable throwable) {
            run.setStatus(RunStatus.FAILED);
            run.setFinishedAt(System.currentTimeMillis());
            run.setErrorMessage(throwable.getMessage());
            runtimeStoreService.saveRun(run);
            runtimeStoreService.appendRunEvent(runId, "error", throwable.getMessage());
            runtimeStoreService.appendRunEvent(runId, "status", "failed");
            log.warn("System event run {} failed: {}", runId, throwable.getMessage(), throwable);
        }
    }

    private boolean tryDeliverUserVisibleReply(
            String runId,
            SystemEventRequest request,
            String response,
            String visibleResponse,
            boolean notifyUsed
    ) {
        if (notifyUsed || RuntimeDeliveryHelper.isNoReply(response) || StrUtil.isBlank(visibleResponse)) {
            return false;
        }
        if (request.getReplyTarget() == null) {
            runtimeStoreService.appendRunEvent(runId, "delivery_suppressed", visibleResponse);
            return false;
        }

        OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
        outboundEnvelope.setRunId(runId);
        outboundEnvelope.setReplyTarget(request.getReplyTarget());
        outboundEnvelope.setContent(visibleResponse);
        DeliveryResult deliveryResult = channelRegistry.send(outboundEnvelope);
        RuntimeDeliveryHelper.recordDeliveryResult(runtimeStoreService, runId, deliveryResult);
        runtimeStoreService.appendRunEvent(runId, "delivery_fallback_sent", visibleResponse);
        return deliveryResult.isDelivered();
    }

    private boolean tryDeliverAggregateReply(
            String runId,
            SystemEventRequest request,
            String response,
            String visibleResponse
    ) {
        if (StrUtil.isBlank(request.getRelatedRunId())) {
            return false;
        }
        if (!RuntimeDeliveryHelper.isFinalReplyOnce(response) || StrUtil.isBlank(visibleResponse)) {
            if (!RuntimeDeliveryHelper.isNoReply(response) && StrUtil.isNotBlank(visibleResponse)) {
                runtimeStoreService.appendRunEvent(runId, "delivery_suppressed", visibleResponse);
            }
            return false;
        }
        if (runtimeStoreService.hasRunEventType(request.getRelatedRunId(), "children_aggregated")) {
            runtimeStoreService.appendRunEvent(runId, "delivery_suppressed", "already_aggregated");
            return false;
        }
        ParentRunChildrenSummary summary = runtimeStoreService.summarizeChildRuns(request.getRelatedRunId(), null);
        if (summary.getTotalChildren() == 0 || !summary.isAllCompleted()) {
            runtimeStoreService.appendRunEvent(runId, "delivery_suppressed", "children_not_completed");
            return false;
        }
        if (request.getReplyTarget() == null) {
            runtimeStoreService.appendRunEvent(runId, "delivery_suppressed", "missing_reply_target");
            return false;
        }

        OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
        outboundEnvelope.setRunId(runId);
        outboundEnvelope.setReplyTarget(request.getReplyTarget());
        outboundEnvelope.setContent(visibleResponse);
        DeliveryResult deliveryResult = channelRegistry.send(outboundEnvelope);
        RuntimeDeliveryHelper.recordDeliveryResult(runtimeStoreService, runId, deliveryResult);
        runtimeStoreService.appendRunEvent(request.getRelatedRunId(), "children_aggregated", "aggregateRunId=" + runId);
        runtimeStoreService.appendRunEvent(runId, "delivery_fallback_sent", visibleResponse);
        return deliveryResult.isDelivered();
    }

    private void validate(SystemEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        if (request.getSourceKind() != RuntimeSourceKind.JOB_SYSTEM_EVENT
                && request.getSourceKind() != RuntimeSourceKind.HEARTBEAT_EVENT
                && request.getSourceKind() != RuntimeSourceKind.CHILD_CONTINUATION) {
            throw new IllegalArgumentException("SystemEventRunner 仅支持 system event 来源");
        }
        if (StrUtil.isBlank(request.getSessionKey())) {
            throw new IllegalArgumentException("sessionKey 不能为空");
        }
        if (StrUtil.isBlank(request.getContent())) {
            throw new IllegalArgumentException("content 不能为空");
        }
    }

    private String eventTypeFor(RuntimeSourceKind sourceKind) {
        if (sourceKind == RuntimeSourceKind.JOB_SYSTEM_EVENT) {
            return "job_system_event_triggered";
        }
        if (sourceKind == RuntimeSourceKind.HEARTBEAT_EVENT) {
            return "heartbeat_event_triggered";
        }
        return "child_continuation_triggered";
    }

    private SystemEventRequest copyRequest(SystemEventRequest request) {
        SystemEventRequest copy = new SystemEventRequest();
        copy.setSourceKind(request.getSourceKind());
        copy.setPolicy(request.getPolicy());
        copy.setSessionKey(request.getSessionKey());
        copy.setReplyTarget(request.getReplyTarget());
        copy.setContent(request.getContent());
        copy.setSourceUserVersion(request.getSourceUserVersion());
        copy.setRelatedRunId(request.getRelatedRunId());
        copy.setAllowNotifyUser(request.isAllowNotifyUser());
        copy.setWakeImmediately(request.isWakeImmediately());
        return copy;
    }

    private List<ChatMessage> trimHistoryForContinuation(List<ChatMessage> history, int maxMessages) {
        if (history == null || history.size() <= maxMessages) {
            return history;
        }
        return history.subList(history.size() - maxMessages, history.size());
    }

}
