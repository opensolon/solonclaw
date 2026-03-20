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
import com.jimuqu.claw.agent.runtime.support.SystemEventRequest;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.config.SolonClawProperties;
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
            executionRequest.setHistory(runtimeStoreService.loadConversationHistoryBefore(
                    request.getSessionKey(),
                    runtimeStoreService.getLatestConversationVersion(request.getSessionKey()) + 1L
            ));
            executionRequest.setRunQuerySupport(buildRunQuerySupport(request.getSessionKey()));
            executionRequest.setNotificationSupport(request.isAllowNotifyUser()
                    ? buildNotificationSupport(request.getSessionKey(), request.getReplyTarget(), runId)
                    : null);

            final String[] latestProgress = {""};
            String response = conversationAgent.execute(executionRequest, progress -> {
                latestProgress[0] = progress;
                runtimeStoreService.appendRunEvent(runId, "progress", progress);
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
            } else if (!notifyUsed && StrUtil.isNotBlank(visibleResponse) && !isNoReply(response)) {
                runtimeStoreService.appendRunEvent(runId, "delivery_suppressed", visibleResponse);
            }

            run.setStatus(RunStatus.SUCCEEDED);
            run.setFinishedAt(System.currentTimeMillis());
            run.setFinalResponse(visibleResponse);
            runtimeStoreService.saveRun(run);
            runtimeStoreService.appendRunEvent(runId, "reply", visibleResponse);
            runtimeStoreService.appendRunEvent(runId, "status", "succeeded");

            if (delivered && request.getPolicy() == SystemEventPolicy.AGGREGATE_ONLY) {
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
        if (notifyUsed || isNoReply(response) || StrUtil.isBlank(visibleResponse)) {
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
        recordDeliveryResult(runId, deliveryResult);
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
        if (!isFinalReplyOnce(response) || StrUtil.isBlank(visibleResponse)) {
            if (!isNoReply(response) && StrUtil.isNotBlank(visibleResponse)) {
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
        recordDeliveryResult(runId, deliveryResult);
        runtimeStoreService.appendRunEvent(request.getRelatedRunId(), "children_aggregated", "aggregateRunId=" + runId);
        runtimeStoreService.appendRunEvent(runId, "delivery_fallback_sent", visibleResponse);
        return deliveryResult.isDelivered();
    }

    private NotificationSupport buildNotificationSupport(String sessionKey, ReplyTarget replyTarget, String runId) {
        return (message, progress) -> {
            NotificationResult result = new NotificationResult();
            result.setSessionKey(sessionKey);

            if (StrUtil.isBlank(message)) {
                result.setDelivered(false);
                result.setMessage("message 不能为空");
                return result;
            }
            if (replyTarget == null) {
                result.setDelivered(false);
                result.setMessage("当前系统事件没有可用的 ReplyTarget");
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

    private boolean isNoReply(String response) {
        return StrUtil.equalsIgnoreCase(StrUtil.trim(response), AgentRuntimeService.NO_REPLY);
    }

    private boolean isFinalReplyOnce(String response) {
        return StrUtil.startWithIgnoreCase(StrUtil.trim(response), AgentRuntimeService.FINAL_REPLY_ONCE_PREFIX);
    }

    private String normalizeVisibleResponse(String response) {
        String trimmed = StrUtil.trim(response);
        if (StrUtil.startWithIgnoreCase(trimmed, AgentRuntimeService.FINAL_REPLY_ONCE_PREFIX)) {
            return StrUtil.trim(trimmed.substring(AgentRuntimeService.FINAL_REPLY_ONCE_PREFIX.length()));
        }
        return response;
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
