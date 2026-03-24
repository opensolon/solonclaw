package com.jimuqu.claw.agent.runtime.impl;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.channel.ChannelRegistry;
import com.jimuqu.claw.agent.job.AgentTurnSpec;
import com.jimuqu.claw.agent.job.JobDeliveryMode;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.enums.RunStatus;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.runtime.api.ConversationAgent;
import com.jimuqu.claw.agent.runtime.api.NotificationSupport;
import com.jimuqu.claw.agent.runtime.support.AgentTurnRequest;
import com.jimuqu.claw.agent.runtime.support.ConversationExecutionRequest;
import com.jimuqu.claw.agent.runtime.support.DeliveryResult;
import com.jimuqu.claw.agent.runtime.support.NotificationResult;
import com.jimuqu.claw.agent.runtime.support.RuntimeDeliveryHelper;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.config.SolonClawProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 负责执行隔离 agent turn 类型的定时任务。
 */
public class IsolatedAgentRunService {
    private static final Logger log = LoggerFactory.getLogger(IsolatedAgentRunService.class);

    private final ConversationAgent conversationAgent;
    private final RuntimeStoreService runtimeStoreService;
    private final ConversationScheduler conversationScheduler;
    private final ChannelRegistry channelRegistry;
    private final SolonClawProperties properties;

    public IsolatedAgentRunService(
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

    public String submit(AgentTurnRequest request) {
        validate(request);

        AgentRun run = new AgentRun();
        run.setRunId(runtimeStoreService.newRunId());
        run.setSessionKey(buildIsolatedSessionKey(request, run.getRunId()));
        run.setSourceMessageId("agent-turn-" + java.util.UUID.randomUUID().toString().replace("-", ""));
        run.setSourceKind(RuntimeSourceKind.JOB_AGENT_TURN);
        run.setTaskDescription(request.getAgentTurn().getMessage());
        run.setReplyTarget(request.getBoundReplyTarget());
        run.setStatus(RunStatus.QUEUED);
        run.setCreatedAt(System.currentTimeMillis());
        runtimeStoreService.saveRun(run);
        runtimeStoreService.appendRunEvent(run.getRunId(), "job_agent_turn_triggered", request.getAgentTurn().getMessage());
        runtimeStoreService.appendRunEvent(run.getRunId(), "status", "queued");

        conversationScheduler.submit(run.getSessionKey(), () -> processRun(copyRequest(request), run.getRunId()));
        return run.getRunId();
    }

    private void processRun(AgentTurnRequest request, String runId) {
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
            executionRequest.setSessionKey(run.getSessionKey());
            executionRequest.setCurrentMessage(request.getAgentTurn().getMessage());
            executionRequest.setCurrentSourceKind(RuntimeSourceKind.JOB_AGENT_TURN);
            executionRequest.setLightContext(request.getAgentTurn().isLightContext());
            executionRequest.setNotificationSupport(buildNotificationSupport(request, runId));

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

            String visibleResponse = StrUtil.blankToDefault(response, "");
            if (StrUtil.isBlank(visibleResponse)) {
                handleEmptyAgentTurnResponse(run, runId, request);
                return;
            }
            boolean notifyUsed = runtimeStoreService.hasRunEventType(runId, "notify")
                    || runtimeStoreService.hasRunEventType(runId, "notify_progress");
            boolean delivered = tryDeliverFinalReply(runId, request, visibleResponse, notifyUsed);

            run.setStatus(RunStatus.SUCCEEDED);
            run.setFinishedAt(System.currentTimeMillis());
            run.setFinalResponse(visibleResponse);
            runtimeStoreService.saveRun(run);
            runtimeStoreService.appendRunEvent(runId, "reply", visibleResponse);
            runtimeStoreService.appendRunEvent(runId, "status", "succeeded");

            if (delivered) {
                log.info("Isolated agent run {} delivered via {}", runId, request.getDeliveryMode());
            }
        } catch (Throwable throwable) {
            run.setStatus(RunStatus.FAILED);
            run.setFinishedAt(System.currentTimeMillis());
            run.setErrorMessage(throwable.getMessage());
            runtimeStoreService.saveRun(run);
            runtimeStoreService.appendRunEvent(runId, "error", throwable.getMessage());
            runtimeStoreService.appendRunEvent(runId, "status", "failed");
            log.warn("Isolated agent run {} failed: {}", runId, throwable.getMessage(), throwable);
        }
    }

    private boolean tryDeliverFinalReply(
            String runId,
            AgentTurnRequest request,
            String visibleResponse,
            boolean notifyUsed
    ) {
        if (notifyUsed || StrUtil.isBlank(visibleResponse) || RuntimeDeliveryHelper.isNoReply(visibleResponse)) {
            return false;
        }

        ReplyTarget replyTarget = resolveReplyTarget(
                request.getDeliveryMode(),
                request.getBoundSessionKey(),
                request.getBoundReplyTarget()
        );
        if (replyTarget == null) {
            runtimeStoreService.appendRunEvent(runId, "delivery_suppressed", visibleResponse);
            return false;
        }

        OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
        outboundEnvelope.setRunId(runId);
        outboundEnvelope.setReplyTarget(replyTarget);
        outboundEnvelope.setContent(visibleResponse);
        DeliveryResult deliveryResult = channelRegistry.send(outboundEnvelope);
        RuntimeDeliveryHelper.recordDeliveryResult(runtimeStoreService, runId, deliveryResult);
        runtimeStoreService.appendRunEvent(runId, "delivery_fallback_sent", visibleResponse);
        return deliveryResult.isDelivered();
    }

    private NotificationSupport buildNotificationSupport(AgentTurnRequest request, String runId) {
        if (request.getDeliveryMode() == JobDeliveryMode.NONE) {
            return null;
        }

        return (message, progress) -> {
            NotificationResult result = new NotificationResult();
            result.setSessionKey(request.getBoundSessionKey());

            if (StrUtil.isBlank(message)) {
                result.setDelivered(false);
                result.setMessage("message 不能为空");
                return result;
            }

            ReplyTarget replyTarget = resolveReplyTarget(
                request.getDeliveryMode(),
                request.getBoundSessionKey(),
                request.getBoundReplyTarget()
        );
            if (replyTarget == null) {
                result.setDelivered(false);
                result.setMessage("当前 agentTurn 没有可用的 ReplyTarget");
                return result;
            }

            OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
            outboundEnvelope.setRunId(runId);
            outboundEnvelope.setReplyTarget(replyTarget);
            outboundEnvelope.setContent(message);
            outboundEnvelope.setProgress(progress);
            DeliveryResult deliveryResult = channelRegistry.send(outboundEnvelope);
            RuntimeDeliveryHelper.recordDeliveryResult(runtimeStoreService, runId, deliveryResult);
            RuntimeDeliveryHelper.applyDeliveryResult(result, deliveryResult);
            runtimeStoreService.appendRunEvent(runId, progress ? "notify_progress" : "notify", message);

            result.setDelivered(deliveryResult.isDelivered());
            if (StrUtil.isBlank(result.getMessage())) {
                result.setMessage("sent to " + replyTarget.getChannelType() + ":" + replyTarget.getConversationId());
            }
            return result;
        };
    }

    private ReplyTarget resolveReplyTarget(
            JobDeliveryMode deliveryMode,
            String boundSessionKey,
            ReplyTarget boundReplyTarget
    ) {
        if (deliveryMode == JobDeliveryMode.NONE) {
            return null;
        }
        if (deliveryMode == JobDeliveryMode.BOUND_REPLY_TARGET) {
            return boundReplyTarget;
        }
        if (StrUtil.isBlank(boundSessionKey)) {
            return null;
        }
        return runtimeStoreService.getReplyTarget(boundSessionKey);
    }

    private String buildIsolatedSessionKey(AgentTurnRequest request, String runId) {
        String base = StrUtil.blankToDefault(StrUtil.trim(request.getJobName()), "agent-turn-job");
        return "job-agent:" + base + ":run:" + runId;
    }

    private void validate(AgentTurnRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        if (request.getSourceKind() != RuntimeSourceKind.JOB_AGENT_TURN) {
            throw new IllegalArgumentException("IsolatedAgentRunService 仅支持 JOB_AGENT_TURN");
        }
        AgentTurnSpec agentTurn = request.getAgentTurn();
        if (agentTurn == null || StrUtil.isBlank(agentTurn.getMessage())) {
            throw new IllegalArgumentException("agentTurn.message 不能为空");
        }
        if (request.getDeliveryMode() == JobDeliveryMode.BOUND_REPLY_TARGET && request.getBoundReplyTarget() == null) {
            throw new IllegalArgumentException("BOUND_REPLY_TARGET 模式必须提供 boundReplyTarget");
        }
    }

    private AgentTurnRequest copyRequest(AgentTurnRequest request) {
        AgentTurnRequest copy = new AgentTurnRequest();
        copy.setSourceKind(request.getSourceKind());
        copy.setJobName(request.getJobName());
        copy.setBoundSessionKey(request.getBoundSessionKey());
        copy.setBoundReplyTarget(request.getBoundReplyTarget());
        copy.setDeliveryMode(request.getDeliveryMode());

        AgentTurnSpec spec = new AgentTurnSpec();
        if (request.getAgentTurn() != null) {
            spec.setMessage(request.getAgentTurn().getMessage());
            spec.setModel(request.getAgentTurn().getModel());
            spec.setThinking(request.getAgentTurn().getThinking());
            spec.setTimeoutSeconds(request.getAgentTurn().getTimeoutSeconds());
            spec.setLightContext(request.getAgentTurn().isLightContext());
        }
        copy.setAgentTurn(spec);
        return copy;
    }

    private void handleEmptyAgentTurnResponse(AgentRun run, String runId, AgentTurnRequest request) {
        String fallback = "这次后台任务执行时没有拿到有效结果，可能是模型响应超时或解析异常。";
        run.setStatus(RunStatus.FAILED);
        run.setFinishedAt(System.currentTimeMillis());
        run.setErrorMessage("模型未返回有效结果");
        run.setFinalResponse(fallback);
        runtimeStoreService.saveRun(run);
        runtimeStoreService.appendRunEvent(runId, "llm_empty_response", fallback);
        runtimeStoreService.appendRunEvent(runId, "reply", fallback);
        runtimeStoreService.appendRunEvent(runId, "status", "failed");

        if (request.getDeliveryMode() != JobDeliveryMode.NONE) {
            ReplyTarget replyTarget = resolveReplyTarget(
                request.getDeliveryMode(),
                request.getBoundSessionKey(),
                request.getBoundReplyTarget()
        );
            if (replyTarget != null) {
                OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
                outboundEnvelope.setRunId(runId);
                outboundEnvelope.setReplyTarget(replyTarget);
                outboundEnvelope.setContent(fallback);
                DeliveryResult deliveryResult = channelRegistry.send(outboundEnvelope);
                RuntimeDeliveryHelper.recordDeliveryResult(runtimeStoreService, runId, deliveryResult);
                runtimeStoreService.appendRunEvent(runId, "delivery_fallback_sent", fallback);
            }
        }
    }
}
