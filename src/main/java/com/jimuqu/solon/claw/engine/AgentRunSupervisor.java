package com.jimuqu.solon.claw.engine;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.agent.AgentRuntimeScope;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.AgentRunContext;
import com.jimuqu.solon.claw.core.model.AgentRunEventRecord;
import com.jimuqu.solon.claw.core.model.AgentRunOutcome;
import com.jimuqu.solon.claw.core.model.AgentRunRecord;
import com.jimuqu.solon.claw.core.model.AgentRunStopResult;
import com.jimuqu.solon.claw.core.model.CompressionOutcome;
import com.jimuqu.solon.claw.core.model.ContextBudgetDecision;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.core.model.QueuedRunMessage;
import com.jimuqu.solon.claw.core.model.RunBusyDecision;
import com.jimuqu.solon.claw.core.model.RunControlCommand;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.AgentRunCancelledException;
import com.jimuqu.solon.claw.core.service.AgentRunControlService;
import com.jimuqu.solon.claw.core.service.ContextBudgetService;
import com.jimuqu.solon.claw.core.service.ContextCompressionService;
import com.jimuqu.solon.claw.core.service.ConversationEventSink;
import com.jimuqu.solon.claw.core.service.LlmGateway;
import com.jimuqu.solon.claw.gateway.feedback.ConversationFeedbackSink;
import com.jimuqu.solon.claw.llm.LlmErrorClassifier;
import com.jimuqu.solon.claw.support.IdSupport;
import com.jimuqu.solon.claw.support.LlmProviderService;
import com.jimuqu.solon.claw.support.MessageSupport;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.noear.solon.ai.chat.ChatRole;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** OpenClaw 风格的外层 Agent run 状态机。 */
@RequiredArgsConstructor
public class AgentRunSupervisor implements AgentRunControlService {
    private static final Logger log = LoggerFactory.getLogger(AgentRunSupervisor.class);
    private static final String EMPTY_REPLY_RECOVERY_PROMPT =
            "你刚刚已经完成了工具调用，但没有输出最终答复。请基于当前会话中的最新工具结果，直接用中文给出简洁最终答复，不要再次调用工具。";
    private static final String EMPTY_REPLY_FALLBACK =
            "本轮已完成工具调用，但模型没有返回可读结论。请使用 /retry 重试，或继续给出下一步指令。";
    private static final String MAX_STEPS_RECOVERY_PROMPT =
            "你刚刚因为最大推理步数限制而停止。不要再次调用工具。请基于当前会话中已经完成的分析、工具结果、文件修改和观察，直接输出中文收敛答复：优先给出已经完成的结果；若任务仍未彻底完成，明确说明还差什么、最推荐的下一步是什么。";
    private static final String MAX_STEPS_RECOVERY_FALLBACK =
            "本轮执行已达到最大步骤限制，已保留当前进展。请继续给出更聚焦的下一步，或使用 /retry 继续。";
    private static final String QUEUED_RUN_ID_KEY = "__queuedRunId";
    private static final String QUEUE_ID_KEY = "__queueId";

    private final AppConfig appConfig;
    private final SessionRepository sessionRepository;
    private final AgentRunRepository agentRunRepository;
    private final ContextCompressionService contextCompressionService;
    private final ContextBudgetService contextBudgetService;
    private final LlmGateway llmGateway;
    private final LlmProviderService llmProviderService;
    private final ConcurrentMap<String, RunHandle> runningRuns =
            new ConcurrentHashMap<String, RunHandle>();
    private final ConcurrentMap<String, AtomicBoolean> drainingQueues =
            new ConcurrentHashMap<String, AtomicBoolean>();
    private volatile long lastRunFinishedAt;

    @Override
    public AgentRunStopResult stop(String sourceKey) {
        RunHandle handle = runningRuns.get(normalizeSourceKey(sourceKey));
        if (handle == null) {
            return AgentRunStopResult.none();
        }
        handle.cancelled.set(true);
        Thread thread = handle.thread;
        boolean interruptSent = false;
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            interruptSent = true;
        }
        return AgentRunStopResult.stopped(
                handle.runId, handle.sessionId, interruptSent, handle.startedAt);
    }

    @Override
    public RunBusyDecision coordinateIncoming(
            String sourceKey, String sessionId, GatewayMessage message) throws Exception {
        String key = normalizeSourceKey(sourceKey);
        String policy = normalizeBusyPolicy(appConfig.getTask().getBusyPolicy());
        RunHandle handle = runningRuns.get(key);
        if (handle == null || handle.cancelled.get()) {
            return RunBusyDecision.runNow(policy);
        }
        AgentRunRecord runningRecord = agentRunRepository.findRun(handle.runId);
        if (runningRecord != null && runningRecord.isBackgrounded()) {
            return RunBusyDecision.runNow(policy);
        }
        if ("interrupt".equals(policy)) {
            AgentRunRecord active = runningRecord;
            if (active != null) {
                active.setStatus("interrupting");
                active.setPhase("interrupting");
                active.setLastActivityAt(System.currentTimeMillis());
                active.setExitReason("busy_interrupt");
                agentRunRepository.saveRun(active);
                appendRunEvent(active, "run.interrupting", "收到新消息，按 interrupt 策略打断当前 run", null);
            }
            recordCommand(handle.runId, key, "interrupt", "{\"reason\":\"busy_policy\"}", "handled");
            stop(key);
            return RunBusyDecision.runNow(policy);
        }
        if ("steer".equals(policy)) {
            String text = message == null ? "" : message.getText();
            recordCommand(
                    handle.runId,
                    key,
                    "steer",
                    "{\"instruction\":\"" + escapeJson(AgentRunContext.safe(text, 2000)) + "\"}",
                    "pending");
            AgentRunRecord active = agentRunRepository.findRun(handle.runId);
            if (active != null) {
                appendRunEvent(active, "run.steer", "收到运行中 steer 指令，下一轮模型调用前注入", null);
            }
            RunBusyDecision decision = new RunBusyDecision();
            decision.setPolicy(policy);
            decision.setStatus("steered");
            decision.setRunId(handle.runId);
            decision.setMessage("已将新消息注入当前长任务。");
            return decision;
        }
        if ("reject".equals(policy)) {
            AgentRunRecord active = agentRunRepository.findRun(handle.runId);
            if (active != null) {
                appendRunEvent(active, "run.rejected", "同一会话已有运行中任务，按 reject 策略拒绝新消息", null);
            }
            RunBusyDecision decision = new RunBusyDecision();
            decision.setPolicy(policy);
            decision.setStatus("rejected");
            decision.setRunId(handle.runId);
            decision.setRejected(true);
            decision.setMessage("当前会话已有任务在运行，请稍后再试，或先停止当前任务。");
            return decision;
        }
        QueuedRunMessage queued = queueMessage(key, sessionId, message, policy);
        RunBusyDecision decision = new RunBusyDecision();
        decision.setPolicy(policy);
        decision.setStatus("queued");
        decision.setRunId(queued.getRunId());
        decision.setQueueId(queued.getQueueId());
        decision.setQueued(true);
        decision.setMessage("当前会话已有任务在运行，新消息已排队。");
        return decision;
    }

    @Override
    public Map<String, Object> controlRun(String runId, String command, Map<String, Object> payload)
            throws Exception {
        AgentRunRecord record = agentRunRepository.findRun(runId);
        if (record == null) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }
        String normalized =
                StrUtil.blankToDefault(command, "").trim().toLowerCase(Locale.ROOT);
        String payloadJson = payload == null ? null : org.noear.snack4.ONode.serialize(payload);
        recordCommand(runId, record.getSourceKey(), normalized, payloadJson, "handled");
        Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();
        result.put("run_id", runId);
        result.put("command", normalized);
        if ("cancel".equals(normalized) || "interrupt".equals(normalized) || "stop".equals(normalized)) {
            record.setStatus("interrupting");
            record.setPhase("interrupting");
            record.setLastActivityAt(System.currentTimeMillis());
            agentRunRepository.saveRun(record);
            appendRunEvent(record, "run.control." + normalized, "收到控制命令：" + normalized, payloadJson);
            result.put("result", stop(record.getSourceKey()));
            result.put("ok", true);
            result.put("status", "interrupting");
            return result;
        }
        if ("background".equals(normalized)) {
            record.setStatus("backgrounded");
            record.setPhase("backgrounded");
            record.setBackgrounded(true);
            record.setLastActivityAt(System.currentTimeMillis());
            agentRunRepository.saveRun(record);
            appendRunEvent(record, "run.backgrounded", "run 已转入后台继续执行", payloadJson);
            result.put("ok", true);
            result.put("status", "backgrounded");
            return result;
        }
        if ("resume".equals(normalized)) {
            record.setStatus("running");
            record.setPhase("recovery");
            record.setRecoverable(false);
            record.setLastActivityAt(System.currentTimeMillis());
            agentRunRepository.saveRun(record);
            appendRunEvent(record, "run.resume", "Dashboard 请求恢复观察 run", payloadJson);
            result.put("ok", true);
            result.put("status", record.getStatus());
            return result;
        }
        if ("steer".equals(normalized)) {
            recordCommand(runId, record.getSourceKey(), "steer", payloadJson, "pending");
            appendRunEvent(record, "run.steer", "Dashboard 注入 steer 指令", payloadJson);
            result.put("ok", true);
            result.put("status", "steered");
            return result;
        }
        result.put("ok", false);
        result.put("status", "unsupported_command");
        return result;
    }

    @Override
    public String consumeSteerInstruction(String runId) {
        try {
            RunControlCommand command = agentRunRepository.findLatestPendingCommand(runId, "steer");
            if (command == null) {
                return null;
            }
            agentRunRepository.markRunControlCommandHandled(
                    command.getCommandId(), "handled", System.currentTimeMillis());
            String payload = command.getPayloadJson();
            if (StrUtil.isBlank(payload)) {
                return null;
            }
            Object parsed = org.noear.snack4.ONode.deserialize(payload, Object.class);
            if (parsed instanceof Map) {
                Object instruction = ((Map<?, ?>) parsed).get("instruction");
                return instruction == null ? payload : String.valueOf(instruction);
            }
            return payload;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public void onRunFinished(
            String sourceKey, String sessionId, Function<GatewayMessage, GatewayReply> runner) {
        String key = normalizeSourceKey(sourceKey);
        if (isRunning(key)) {
            return;
        }
        AtomicBoolean draining =
                drainingQueues.computeIfAbsent(key, ignored -> new AtomicBoolean(false));
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        Thread thread =
                new Thread(
                        () -> {
                            try {
                                drainQueue(key, sessionId, runner);
                            } finally {
                                draining.set(false);
                            }
                        },
                        "jimuqu-run-queue-" + Math.abs(key.hashCode()));
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public boolean isRunning(String sourceKey) {
        RunHandle handle = runningRuns.get(normalizeSourceKey(sourceKey));
        return handle != null && !handle.cancelled.get();
    }

    @Override
    public boolean hasRunningRuns() {
        return !runningRuns.isEmpty();
    }

    @Override
    public long lastRunFinishedAt() {
        return lastRunFinishedAt;
    }

    public AgentRunOutcome run(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> tools,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink,
            boolean resume)
            throws Exception {
        return run(
                session, systemPrompt, userMessage, tools, feedbackSink, eventSink, resume, null);
    }

    public AgentRunOutcome run(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> tools,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink,
            boolean resume,
            AgentRuntimeScope agentScope)
            throws Exception {
        if (agentScope == null) {
            agentScope = new AgentRuntimeScope();
            agentScope.setAgentName(
                    AgentRuntimeScope.normalizeName(
                            session == null ? null : session.getActiveAgentName()));
            agentScope.setWorkspaceDir(appConfig.getRuntime().getHome());
            agentScope.setSkillsDir(appConfig.getRuntime().getSkillsDir());
            agentScope.setCacheDir(appConfig.getRuntime().getCacheDir());
        }
        long now = System.currentTimeMillis();
        String queuedRunId = extractQueuedMarker(userMessage, QUEUED_RUN_ID_KEY);
        userMessage = stripQueuedMarkers(userMessage);
        AgentRunRecord runRecord =
                StrUtil.isBlank(queuedRunId) ? null : agentRunRepository.findRun(queuedRunId);
        if (runRecord == null) {
            runRecord = new AgentRunRecord();
            runRecord.setRunId(IdSupport.newId());
            runRecord.setSessionId(session.getSessionId());
            runRecord.setSourceKey(session.getSourceKey());
        }
        AgentRunContext parentContext = AgentRunContext.current();
        boolean subagentRun = parentContext != null && !StrUtil.equals(parentContext.getSourceKey(), session.getSourceKey());
        runRecord.setRunKind(subagentRun ? "subagent" : (resume ? "resume" : "conversation"));
        runRecord.setParentRunId(subagentRun ? parentContext.getRunId() : null);
        runRecord.setAgentName(agentScope.getEffectiveName());
        runRecord.setAgentSnapshotJson(agentScope.getSnapshotJson());
        runRecord.setStatus("running");
        runRecord.setPhase("queued");
        runRecord.setBusyPolicy("queue");
        runRecord.setInputPreview(AgentRunContext.safe(userMessage, 1000));
        if (runRecord.getQueuedAt() <= 0) {
            runRecord.setQueuedAt(now);
        }
        runRecord.setStartedAt(now);
        runRecord.setHeartbeatAt(now);
        runRecord.setLastActivityAt(now);
        agentRunRepository.saveRun(runRecord);

        AgentRunContext runContext =
                new AgentRunContext(
                        agentRunRepository,
                        runRecord.getRunId(),
                        session.getSessionId(),
                        session.getSourceKey());
        runContext.setWorkspaceDir(agentScope.getWorkspaceDir());
        RunHandle runHandle =
                registerRun(
                        session.getSourceKey(), runRecord.getRunId(), session.getSessionId(), now);
        AgentRunContext previousContext = AgentRunContext.current();
        AgentRunContext.setCurrent(runContext);
        try {
            pruneOldRuns();

            updateRunPhase(runRecord, "running");
            runContext.setPhase("running");
            runContext.event("run.start", resume ? "恢复挂起会话" : "开始执行用户请求");
            eventSink.onRunStarted(session.getSessionId());

            List<AppConfig.LlmConfig> candidates = buildCandidateConfigs(session, agentScope);
            Throwable lastError = null;
            LlmResult finalResult = null;
            String replyText = "";
            String previousProvider = null;
            int attemptNo = 0;
            String compressionWarning = "";
            int contextEstimateTokens = 0;
            int contextWindowTokens = 0;

            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                checkCancellation(session.getSourceKey());
                AppConfig.LlmConfig resolved = candidates.get(candidateIndex);
                int maxAttempts = Math.max(1, appConfig.getTrace().getMaxAttempts());
                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    checkCancellation(session.getSourceKey());
                    attemptNo++;
                    runContext.setAttempt(attemptNo, resolved.getProvider(), resolved.getModel());
                    updateRunPhase(runRecord, "model");
                    runRecord.setAttempts(attemptNo);
                    runRecord.setProvider(resolved.getProvider());
                    runRecord.setModel(resolved.getModel());
                    heartbeat(runRecord);
                    agentRunRepository.saveRun(runRecord);
                    eventSink.onAttemptStarted(
                            runRecord.getRunId(),
                            attemptNo,
                            resolved.getProvider(),
                            resolved.getModel());
                    runContext.event(
                            "attempt.start",
                            "开始第 "
                                    + attemptNo
                                    + " 次尝试："
                                    + resolved.getProvider()
                                    + "/"
                                    + resolved.getModel(),
                            attemptMetadata(resolved, attemptNo, candidateIndex));

                    try {
                        String steer = consumeSteerInstruction(runRecord.getRunId());
                        String effectiveUserMessage = userMessage;
                        if (StrUtil.isNotBlank(steer)) {
                            effectiveUserMessage =
                                    StrUtil.blankToDefault(userMessage, "")
                                            + "\n\n[运行中追加指令]\n"
                                            + steer;
                            runContext.event("run.steer.injected", "已将 steer 指令注入本轮模型调用");
                        }
                        CompressionOutcome compression =
                                compressBeforeAttempt(
                                        session,
                                        systemPrompt,
                                        effectiveUserMessage,
                                        resolved,
                                        runContext,
                                        eventSink,
                                        runRecord.getRunId());
                        session = compression.getSession();
                        if (StrUtil.isBlank(compressionWarning)
                                && StrUtil.isNotBlank(compression.getWarning())) {
                            compressionWarning = compression.getWarning();
                        }
                        if (compression.getEstimatedTokens() > 0) {
                            contextEstimateTokens = compression.getEstimatedTokens();
                        }
                        runRecord.setContextEstimateTokens(contextEstimateTokens);
                        contextWindowTokens = Math.max(1024, resolved.getContextWindowTokens());
                        runRecord.setContextWindowTokens(contextWindowTokens);
                        heartbeat(runRecord);
                        agentRunRepository.saveRun(runRecord);
                        checkCancellation(session.getSourceKey());
                        String previousNdjson = session.getNdjson();
                        LlmResult result =
                                llmGateway.executeOnce(
                                        session,
                                        systemPrompt,
                                        effectiveUserMessage,
                                        tools,
                                        feedbackSink,
                                        eventSink,
                                        resume,
                                        resolved,
                                        runContext);
                        checkCancellation(session.getSourceKey());
                        String currentReply = extractText(result.getAssistantMessage());
                        if (StrUtil.isBlank(currentReply)
                                && hasRecentToolActivity(previousNdjson, result.getNdjson())) {
                            session.setNdjson(result.getNdjson());
                            checkCancellation(session.getSourceKey());
                            updateRunPhase(runRecord, "recovery");
                            runContext.event("recovery.start", "工具调用后空回复，发起无工具恢复");
                            eventSink.onRecoveryStarted(runRecord.getRunId(), "empty_reply");
                            LlmResult recovered =
                                    recover(
                                            session,
                                            systemPrompt,
                                            EMPTY_REPLY_RECOVERY_PROMPT,
                                            resolved,
                                            feedbackSink,
                                            eventSink,
                                            runContext);
                            checkCancellation(session.getSourceKey());
                            if (recovered != null) {
                                mergeUsage(result, recovered);
                                result = recovered;
                                currentReply = extractText(recovered.getAssistantMessage());
                            }
                        }

                        if (isMaxStepsReply(currentReply)) {
                            session.setNdjson(result.getNdjson());
                            checkCancellation(session.getSourceKey());
                            updateRunPhase(runRecord, "recovery");
                            runContext.event("recovery.start", "达到最大步骤上限，发起收敛总结");
                            eventSink.onRecoveryStarted(runRecord.getRunId(), "max_steps");
                            LlmResult recovered =
                                    recover(
                                            session,
                                            systemPrompt,
                                            MAX_STEPS_RECOVERY_PROMPT,
                                            resolved,
                                            feedbackSink,
                                            eventSink,
                                            runContext);
                            checkCancellation(session.getSourceKey());
                            if (hasUsableRecoveryReply(recovered)) {
                                mergeUsage(result, recovered);
                                result = recovered;
                                currentReply = extractText(recovered.getAssistantMessage());
                            } else {
                                currentReply = MAX_STEPS_RECOVERY_FALLBACK;
                            }
                        }

                        if (StrUtil.isNotBlank(currentReply) || hasVisibleContent(result)) {
                            finalResult = result;
                            replyText = StrUtil.blankToDefault(currentReply, EMPTY_REPLY_FALLBACK);
                            eventSink.onAttemptCompleted(
                                    runRecord.getRunId(), attemptNo, "success", "");
                            runContext.event("attempt.success", "第 " + attemptNo + " 次尝试成功");
                            break;
                        }

                        lastError =
                                new IllegalStateException("LLM returned empty assistant content");
                        eventSink.onAttemptCompleted(
                                runRecord.getRunId(),
                                attemptNo,
                                "empty",
                                "LLM returned empty assistant content");
                        runContext.event(
                                "attempt.empty",
                                "模型返回空内容",
                                errorMetadata(lastError, resolved, attemptNo, candidateIndex));
                    } catch (AgentRunCancelledException e) {
                        throw e;
                    } catch (Exception e) {
                        if (isCancellationRequested(session.getSourceKey())) {
                            throw new AgentRunCancelledException();
                        }
                        updateRunPhase(runRecord, "retry");
                        lastError = e;
                        eventSink.onAttemptCompleted(
                                runRecord.getRunId(), attemptNo, "error", e.getMessage());
                        runContext.event(
                                "attempt.error",
                                "第 " + attemptNo + " 次尝试失败：" + e.getMessage(),
                                errorMetadata(e, resolved, attemptNo, candidateIndex));
                        if (classifyRetryable(e) && attempt < maxAttempts) {
                            continue;
                        }
                    }
                }

                if (finalResult != null) {
                    break;
                }

                previousProvider = resolved.getProvider();
                if (candidateIndex + 1 < candidates.size()) {
                    AppConfig.LlmConfig next = candidates.get(candidateIndex + 1);
                    runRecord.setFallbackCount(runRecord.getFallbackCount() + 1);
                    updateRunPhase(runRecord, "fallback");
                    eventSink.onFallback(
                            runRecord.getRunId(),
                            previousProvider,
                            next.getProvider(),
                            lastError == null ? "empty response" : lastError.getMessage());
                    runContext.event(
                            "fallback",
                            "切换 fallback provider："
                                    + previousProvider
                                    + " -> "
                                    + next.getProvider(),
                            fallbackMetadata(previousProvider, next, lastError));
                }
            }

            if (finalResult == null) {
                runRecord.setStatus("failed");
                runRecord.setPhase("failed");
                runRecord.setExitReason("failed");
                runRecord.setFinishedAt(System.currentTimeMillis());
                runRecord.setError(
                        lastError == null ? "LLM execution failed" : lastError.getMessage());
                agentRunRepository.saveRun(runRecord);
                runContext.event("run.failed", runRecord.getError());
                if (lastError instanceof Exception) {
                    throw (Exception) lastError;
                }
                throw new IllegalStateException(runRecord.getError(), lastError);
            }

            checkCancellation(session.getSourceKey());
            session.setNdjson(finalResult.getNdjson());
            applyUsage(session, finalResult);
            session.setUpdatedAt(System.currentTimeMillis());
            sessionRepository.save(session);

            runRecord.setStatus("success");
            runRecord.setPhase("completed");
            runRecord.setFinalReplyPreview(AgentRunContext.safe(replyText, 1000));
            runRecord.setInputTokens(finalResult.getInputTokens());
            runRecord.setOutputTokens(finalResult.getOutputTokens());
            runRecord.setTotalTokens(finalResult.getTotalTokens());
            runRecord.setProvider(finalResult.getProvider());
            runRecord.setModel(finalResult.getModel());
            runRecord.setFinishedAt(System.currentTimeMillis());
            runRecord.setExitReason("success");
            heartbeat(runRecord);
            agentRunRepository.saveRun(runRecord);
            runContext.event("run.success", "运行完成");

            AgentRunOutcome outcome = new AgentRunOutcome();
            outcome.setFinalReply(replyText);
            outcome.setResult(finalResult);
            outcome.setRunRecord(runRecord);
            outcome.setCompressionWarning(compressionWarning);
            outcome.setModel(finalResult.getModel());
            outcome.setProvider(finalResult.getProvider());
            outcome.setContextEstimateTokens(contextEstimateTokens);
            outcome.setContextWindowTokens(contextWindowTokens);
            outcome.setCwd(
                    StrUtil.blankToDefault(
                            agentScope.getWorkspaceDir(), System.getProperty("user.dir")));
            return outcome;
        } catch (AgentRunCancelledException e) {
            runRecord.setStatus("cancelled");
            runRecord.setPhase("cancelled");
            runRecord.setExitReason("cancelled");
            runRecord.setFinishedAt(System.currentTimeMillis());
            runRecord.setError(e.getMessage());
            heartbeat(runRecord);
            agentRunRepository.saveRun(runRecord);
            runContext.event("run.cancelled", e.getMessage());
            throw e;
        } finally {
            unregisterRun(session.getSourceKey(), runHandle);
            AgentRunContext.setCurrent(previousContext);
            if (runHandle.cancelled.get()) {
                Thread.interrupted();
            }
            lastRunFinishedAt = System.currentTimeMillis();
        }
    }

    private CompressionOutcome compressBeforeAttempt(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            AppConfig.LlmConfig resolved,
            AgentRunContext runContext,
            ConversationEventSink eventSink,
            String runId)
            throws Exception {
        ContextBudgetDecision decision =
                contextBudgetService.decide(session, systemPrompt, userMessage, resolved);
        if (!decision.isShouldCompress()) {
            runContext.setPhase("compression");
            eventSink.onCompressionDecision(
                    runId,
                    false,
                    decision.getReason(),
                    decision.getEstimatedTokens(),
                    decision.getThresholdTokens());
            runContext.event(
                    "compression.skip",
                    decision.getReason(),
                    runContext.metadata("estimatedTokens", decision.getEstimatedTokens()));
            CompressionOutcome skipped = CompressionOutcome.skipped(session);
            skipped.setEstimatedTokens(decision.getEstimatedTokens());
            skipped.setThresholdTokens(decision.getThresholdTokens());
            return skipped;
        }

        SessionRecord before = cloneSessionState(session);
        runContext.setPhase("compression");
        CompressionOutcome outcome =
                contextCompressionService.compressNowWithOutcome(
                        session, systemPrompt, userMessage);
        SessionRecord compressed = outcome.getSession();
        boolean changed = !StrUtil.equals(before.getNdjson(), compressed.getNdjson());
        eventSink.onCompressionDecision(
                runId,
                changed,
                decision.getReason(),
                decision.getEstimatedTokens(),
                decision.getThresholdTokens());
        String eventType =
                outcome.isFailed()
                        ? "compression.failed"
                        : (changed ? "compression.done" : "compression.unchanged");
        runContext.event(
                eventType,
                outcome.isFailed() ? outcome.getErrorMessage() : decision.getReason(),
                runContext.metadata("estimatedTokens", decision.getEstimatedTokens()));
        if (changed) {
            sessionRepository.save(compressed);
        }
        AgentRunRecord record = agentRunRepository.findRun(runId);
        if (record != null) {
            record.setCompressionCount(record.getCompressionCount() + 1);
            record.setContextEstimateTokens(decision.getEstimatedTokens());
            record.setContextWindowTokens(decision.getThresholdTokens());
            heartbeat(record);
            agentRunRepository.saveRun(record);
        }
        outcome.setEstimatedTokens(decision.getEstimatedTokens());
        outcome.setThresholdTokens(decision.getThresholdTokens());
        return outcome;
    }

    private SessionRecord cloneSessionState(SessionRecord source) {
        SessionRecord clone = new SessionRecord();
        clone.setNdjson(source.getNdjson());
        return clone;
    }

    private LlmResult recover(
            SessionRecord session,
            String systemPrompt,
            String prompt,
            AppConfig.LlmConfig resolved,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink,
            AgentRunContext runContext) {
        try {
            return llmGateway.executeOnce(
                    session,
                    systemPrompt,
                    prompt,
                    Collections.emptyList(),
                    feedbackSink,
                    eventSink,
                    false,
                    resolved,
                    runContext);
        } catch (Exception e) {
            runContext.event("recovery.error", e.getMessage());
            log.warn("Agent recovery failed: sessionId={}", session.getSessionId(), e);
            return null;
        }
    }

    private List<AppConfig.LlmConfig> buildCandidateConfigs(
            SessionRecord session, AgentRuntimeScope agentScope) {
        List<AppConfig.LlmConfig> candidates = new java.util.ArrayList<AppConfig.LlmConfig>();
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        AppConfig.LlmConfig primary =
                toLlmConfig(
                        llmProviderService.resolveEffectiveProvider(
                                session, agentScope == null ? null : agentScope.getDefaultModel()));
        candidates.add(primary);
        seen.add(providerSignature(primary));
        for (LlmProviderService.ResolvedProvider fallback :
                llmProviderService.resolveFallbackProviders()) {
            AppConfig.LlmConfig candidate = toLlmConfig(fallback);
            if (seen.add(providerSignature(candidate))) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private AppConfig.LlmConfig toLlmConfig(LlmProviderService.ResolvedProvider resolved) {
        AppConfig.LlmConfig config = copyLlmConfig(appConfig.getLlm());
        config.setProvider(StrUtil.nullToEmpty(resolved.getProviderKey()).trim());
        config.setDialect(StrUtil.nullToEmpty(resolved.getDialect()).trim());
        config.setApiUrl(StrUtil.nullToEmpty(resolved.getApiUrl()).trim());
        config.setApiKey(resolved.getApiKey());
        config.setModel(StrUtil.nullToEmpty(resolved.getModel()).trim());
        return config;
    }

    private AppConfig.LlmConfig copyLlmConfig(AppConfig.LlmConfig source) {
        AppConfig.LlmConfig copy = new AppConfig.LlmConfig();
        copy.setProvider(source.getProvider());
        copy.setDialect(source.getDialect());
        copy.setApiUrl(source.getApiUrl());
        copy.setApiKey(source.getApiKey());
        copy.setModel(source.getModel());
        copy.setStream(source.isStream());
        copy.setReasoningEffort(source.getReasoningEffort());
        copy.setTemperature(source.getTemperature());
        copy.setMaxTokens(source.getMaxTokens());
        copy.setContextWindowTokens(source.getContextWindowTokens());
        return copy;
    }

    private String providerSignature(AppConfig.LlmConfig config) {
        return StrUtil.nullToEmpty(config.getProvider())
                + "|"
                + StrUtil.nullToEmpty(config.getDialect())
                + "|"
                + StrUtil.nullToEmpty(config.getApiUrl())
                + "|"
                + StrUtil.nullToEmpty(config.getModel())
                + "|"
                + (StrUtil.isBlank(config.getApiKey()) ? "no-key" : "has-key");
    }

    private String extractText(AssistantMessage assistantMessage) {
        if (assistantMessage == null) {
            return "";
        }
        if (StrUtil.isNotBlank(assistantMessage.getResultContent())) {
            return assistantMessage.getResultContent();
        }
        if (StrUtil.isNotBlank(assistantMessage.getContent())) {
            return assistantMessage.getContent();
        }
        log.warn(
                "Assistant message has no visible content in agent run; suppressing message object fallback: role={}, contentRawType={}, toolCalls={}",
                assistantMessage.getRole(),
                assistantMessage.getContentRaw() == null
                        ? ""
                        : assistantMessage.getContentRaw().getClass().getName(),
                assistantMessage.getToolCalls() == null ? 0 : assistantMessage.getToolCalls().size());
        return "";
    }

    private boolean hasVisibleContent(LlmResult result) {
        return result != null
                && (StrUtil.isNotBlank(extractText(result.getAssistantMessage()))
                        || StrUtil.isNotBlank(result.getRawResponse()));
    }

    private boolean hasRecentToolActivity(String previousNdjson, String currentNdjson) {
        try {
            List<ChatMessage> previous = MessageSupport.loadMessages(previousNdjson);
            List<ChatMessage> current = MessageSupport.loadMessages(currentNdjson);
            if (countTools(current) > countTools(previous)) {
                return true;
            }
            for (int i = current.size() - 1; i >= 0; i--) {
                ChatMessage message = current.get(i);
                if (message.getRole() == ChatRole.TOOL) {
                    return true;
                }
                if (message.getRole() == ChatRole.ASSISTANT
                        && StrUtil.isNotBlank(message.getContent())) {
                    return false;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private int countTools(List<ChatMessage> messages) {
        int count = 0;
        for (ChatMessage message : messages) {
            if (message.getRole() == ChatRole.TOOL) {
                count++;
            }
        }
        return count;
    }

    private boolean hasUsableRecoveryReply(LlmResult recovered) {
        String text = recovered == null ? "" : extractText(recovered.getAssistantMessage());
        return StrUtil.isNotBlank(text) && !isMaxStepsReply(text);
    }

    private boolean isMaxStepsReply(String replyText) {
        if (StrUtil.isBlank(replyText)) {
            return false;
        }
        String normalized = replyText.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("agent error: maximum steps reached")
                || normalized.contains("maximum steps reached")
                || replyText.contains("已达到硬性步数上限");
    }

    private boolean classifyRetryable(Throwable error) {
        return LlmErrorClassifier.classify(error).isRetryable();
    }

    private Map<String, Object> attemptMetadata(
            AppConfig.LlmConfig resolved, int attemptNo, int candidateIndex) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("attempt", Integer.valueOf(attemptNo));
        metadata.put("candidate_index", Integer.valueOf(candidateIndex));
        metadata.put("provider", resolved.getProvider());
        metadata.put("model", resolved.getModel());
        metadata.put("dialect", resolved.getDialect());
        metadata.put("stream", Boolean.valueOf(resolved.isStream()));
        return metadata;
    }

    private Map<String, Object> errorMetadata(
            Throwable error, AppConfig.LlmConfig resolved, int attemptNo, int candidateIndex) {
        LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(error);
        Map<String, Object> metadata = attemptMetadata(resolved, attemptNo, candidateIndex);
        metadata.put("reason", classified.getReason().name());
        metadata.put("status_code", Integer.valueOf(classified.getStatusCode()));
        metadata.put("retryable", Boolean.valueOf(classified.isRetryable()));
        metadata.put("should_fallback", Boolean.valueOf(classified.isShouldFallback()));
        metadata.put("should_compress", Boolean.valueOf(classified.isShouldCompress()));
        metadata.put("error", error == null ? "" : error.getMessage());
        return metadata;
    }

    private Map<String, Object> fallbackMetadata(
            String previousProvider, AppConfig.LlmConfig next, Throwable lastError) {
        LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(lastError);
        Map<String, Object> metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("from_provider", previousProvider);
        metadata.put("to_provider", next.getProvider());
        metadata.put("to_model", next.getModel());
        metadata.put("to_dialect", next.getDialect());
        metadata.put("reason", classified.getReason().name());
        metadata.put("status_code", Integer.valueOf(classified.getStatusCode()));
        metadata.put("retryable", Boolean.valueOf(classified.isRetryable()));
        metadata.put("should_compress", Boolean.valueOf(classified.isShouldCompress()));
        metadata.put("error", lastError == null ? "empty response" : lastError.getMessage());
        return metadata;
    }

    private void applyUsage(SessionRecord session, LlmResult result) {
        if (session == null || result == null) {
            return;
        }
        session.setLastInputTokens(result.getInputTokens());
        session.setLastOutputTokens(result.getOutputTokens());
        session.setLastReasoningTokens(result.getReasoningTokens());
        session.setLastCacheReadTokens(result.getCacheReadTokens());
        session.setLastCacheWriteTokens(result.getCacheWriteTokens());
        session.setLastTotalTokens(result.getTotalTokens());
        session.setCumulativeInputTokens(
                session.getCumulativeInputTokens() + Math.max(0L, result.getInputTokens()));
        session.setCumulativeOutputTokens(
                session.getCumulativeOutputTokens() + Math.max(0L, result.getOutputTokens()));
        session.setCumulativeReasoningTokens(
                session.getCumulativeReasoningTokens() + Math.max(0L, result.getReasoningTokens()));
        session.setCumulativeCacheReadTokens(
                session.getCumulativeCacheReadTokens() + Math.max(0L, result.getCacheReadTokens()));
        session.setCumulativeCacheWriteTokens(
                session.getCumulativeCacheWriteTokens()
                        + Math.max(0L, result.getCacheWriteTokens()));
        session.setCumulativeTotalTokens(
                session.getCumulativeTotalTokens() + Math.max(0L, result.getTotalTokens()));
        if (result.getTotalTokens() > 0
                || result.getInputTokens() > 0
                || result.getOutputTokens() > 0
                || result.getCacheReadTokens() > 0
                || result.getCacheWriteTokens() > 0) {
            session.setLastUsageAt(System.currentTimeMillis());
        }
        if (StrUtil.isNotBlank(result.getProvider())) {
            session.setLastResolvedProvider(result.getProvider());
        }
        if (StrUtil.isNotBlank(result.getModel())) {
            session.setLastResolvedModel(result.getModel());
        }
    }

    private void mergeUsage(LlmResult base, LlmResult extra) {
        if (base == null || extra == null) {
            return;
        }
        extra.setInputTokens(
                Math.max(0L, extra.getInputTokens()) + Math.max(0L, base.getInputTokens()));
        extra.setOutputTokens(
                Math.max(0L, extra.getOutputTokens()) + Math.max(0L, base.getOutputTokens()));
        extra.setReasoningTokens(
                Math.max(0L, extra.getReasoningTokens()) + Math.max(0L, base.getReasoningTokens()));
        extra.setCacheReadTokens(
                Math.max(0L, extra.getCacheReadTokens()) + Math.max(0L, base.getCacheReadTokens()));
        extra.setCacheWriteTokens(
                Math.max(0L, extra.getCacheWriteTokens())
                        + Math.max(0L, base.getCacheWriteTokens()));
        extra.setTotalTokens(
                Math.max(0L, extra.getTotalTokens()) + Math.max(0L, base.getTotalTokens()));
    }

    private RunHandle registerRun(
            String sourceKey, String runId, String sessionId, long startedAt) {
        RunHandle handle = new RunHandle(runId, sessionId, Thread.currentThread(), startedAt);
        runningRuns.put(normalizeSourceKey(sourceKey), handle);
        return handle;
    }

    private void unregisterRun(String sourceKey, RunHandle handle) {
        if (handle == null) {
            return;
        }
        runningRuns.remove(normalizeSourceKey(sourceKey), handle);
        lastRunFinishedAt = System.currentTimeMillis();
    }

    public void recoverStaleRuns(long staleAfterMillis) {
        if (agentRunRepository == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long before = now - Math.max(60_000L, staleAfterMillis);
        try {
            agentRunRepository.markStaleRuns(before, now);
        } catch (Exception e) {
            log.warn("recoverStaleRuns failed", e);
        }
    }

    private void updateRunPhase(AgentRunRecord runRecord, String phase) throws Exception {
        runRecord.setPhase(phase);
        heartbeat(runRecord);
        agentRunRepository.saveRun(runRecord);
    }

    private void heartbeat(AgentRunRecord runRecord) {
        long now = System.currentTimeMillis();
        runRecord.setHeartbeatAt(now);
        runRecord.setLastActivityAt(now);
    }

    private void checkCancellation(String sourceKey) {
        if (isCancellationRequested(sourceKey)) {
            throw new AgentRunCancelledException();
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new AgentRunCancelledException();
        }
    }

    private boolean isCancellationRequested(String sourceKey) {
        RunHandle handle = runningRuns.get(normalizeSourceKey(sourceKey));
        return handle != null && handle.cancelled.get();
    }

    private QueuedRunMessage queueMessage(
            String sourceKey, String sessionId, GatewayMessage message, String policy)
            throws Exception {
        long now = System.currentTimeMillis();
        QueuedRunMessage queued = new QueuedRunMessage();
        queued.setQueueId(IdSupport.newId());
        queued.setRunId(IdSupport.newId());
        queued.setSessionId(sessionId);
        queued.setSourceKey(sourceKey);
        queued.setMessageText(AgentRunContext.safe(message == null ? "" : message.getText(), 4000));
        queued.setMessageJson(serializeMessage(message));
        queued.setStatus("queued");
        queued.setBusyPolicy(policy);
        queued.setCreatedAt(now);
        agentRunRepository.saveQueuedMessage(queued);

        AgentRunRecord runRecord = new AgentRunRecord();
        runRecord.setRunId(queued.getRunId());
        runRecord.setSessionId(sessionId);
        runRecord.setSourceKey(sourceKey);
        runRecord.setRunKind("conversation");
        runRecord.setStatus("queued");
        runRecord.setPhase("queued");
        runRecord.setBusyPolicy(policy);
        runRecord.setInputPreview(queued.getMessageText());
        runRecord.setQueuedAt(now);
        runRecord.setStartedAt(now);
        runRecord.setHeartbeatAt(now);
        runRecord.setLastActivityAt(now);
        agentRunRepository.saveRun(runRecord);
        appendRunEvent(runRecord, "run.queued", "busy 策略为 queue，新消息已进入队列", null);
        return queued;
    }

    private String serializeMessage(GatewayMessage message) {
        if (message == null) {
            return "{}";
        }
        Map<String, Object> map = new java.util.LinkedHashMap<String, Object>();
        map.put("platform", message.getPlatform() == null ? null : message.getPlatform().name());
        map.put("chatId", message.getChatId());
        map.put("userId", message.getUserId());
        map.put("chatType", message.getChatType());
        map.put("chatName", message.getChatName());
        map.put("userName", message.getUserName());
        map.put("text", message.getText());
        map.put("threadId", message.getThreadId());
        map.put("sourceKeyOverride", message.getSourceKeyOverride());
        map.put("heartbeat", message.isHeartbeat());
        map.put("timestamp", message.getTimestamp());
        return org.noear.snack4.ONode.serialize(map);
    }

    private GatewayMessage deserializeMessage(QueuedRunMessage queued) {
        GatewayMessage message = new GatewayMessage();
        try {
            Object parsed = org.noear.snack4.ONode.deserialize(queued.getMessageJson(), Object.class);
            if (parsed instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) parsed;
                message.setPlatform(
                        com.jimuqu.solon.claw.core.enums.PlatformType.fromName(
                                stringValue(map.get("platform"))));
                message.setChatId(stringValue(map.get("chatId")));
                message.setUserId(stringValue(map.get("userId")));
                message.setChatType(stringValue(map.get("chatType")));
                message.setChatName(stringValue(map.get("chatName")));
                message.setUserName(stringValue(map.get("userName")));
                message.setText(stringValue(map.get("text")));
                message.setThreadId(stringValue(map.get("threadId")));
                message.setSourceKeyOverride(stringValue(map.get("sourceKeyOverride")));
                message.setHeartbeat(Boolean.parseBoolean(stringValue(map.get("heartbeat"))));
                Object timestamp = map.get("timestamp");
                if (timestamp instanceof Number) {
                    message.setTimestamp(((Number) timestamp).longValue());
                }
            }
        } catch (Exception ignored) {
            message.setText(queued.getMessageText());
            message.setSourceKeyOverride(queued.getSourceKey());
        }
        if (StrUtil.isBlank(message.getSourceKeyOverride())) {
            message.setSourceKeyOverride(queued.getSourceKey());
        }
        String text = StrUtil.nullToEmpty(message.getText());
        message.setText(
                text
                        + "\n\n[queue-metadata:"
                        + QUEUED_RUN_ID_KEY
                        + "="
                        + queued.getRunId()
                        + ";"
                        + QUEUE_ID_KEY
                        + "="
                        + queued.getQueueId()
                        + "]");
        return message;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void drainQueue(
            String sourceKey, String sessionId, Function<GatewayMessage, GatewayReply> runner) {
        if (runner == null) {
            return;
        }
        while (!isRunning(sourceKey)) {
            QueuedRunMessage queued;
            try {
                queued = agentRunRepository.findNextQueuedMessage(sourceKey, sessionId);
            } catch (Exception e) {
                log.warn("find queued run failed: sourceKey={}", sourceKey, e);
                return;
            }
            if (queued == null) {
                return;
            }
            try {
                agentRunRepository.markQueuedMessage(
                        queued.getQueueId(), "running", System.currentTimeMillis(), null);
                runner.apply(deserializeMessage(queued));
                agentRunRepository.markQueuedMessage(
                        queued.getQueueId(), "success", System.currentTimeMillis(), null);
            } catch (Exception e) {
                try {
                    agentRunRepository.markQueuedMessage(
                            queued.getQueueId(), "failed", System.currentTimeMillis(), e.getMessage());
                } catch (Exception ignored) {
                }
                log.warn("queued run failed: queueId={}", queued.getQueueId(), e);
            }
        }
    }

    private String normalizeBusyPolicy(String policy) {
        String normalized = StrUtil.blankToDefault(policy, "queue").trim().toLowerCase(Locale.ROOT);
        if ("interrupt".equals(normalized)
                || "steer".equals(normalized)
                || "reject".equals(normalized)
                || "queue".equals(normalized)) {
            return normalized;
        }
        return "queue";
    }

    private void recordCommand(
            String runId, String sourceKey, String command, String payloadJson, String status)
            throws Exception {
        RunControlCommand record = new RunControlCommand();
        record.setCommandId(IdSupport.newId());
        record.setRunId(runId);
        record.setSourceKey(sourceKey);
        record.setCommand(command);
        record.setPayloadJson(payloadJson);
        record.setStatus(StrUtil.blankToDefault(status, "pending"));
        record.setCreatedAt(System.currentTimeMillis());
        if (!"pending".equals(record.getStatus())) {
            record.setHandledAt(record.getCreatedAt());
        }
        agentRunRepository.saveRunControlCommand(record);
    }

    private void appendRunEvent(
            AgentRunRecord record, String eventType, String summary, String metadataJson) {
        if (record == null) {
            return;
        }
        try {
            AgentRunEventRecord event = new AgentRunEventRecord();
            event.setEventId(IdSupport.newId());
            event.setRunId(record.getRunId());
            event.setSessionId(record.getSessionId());
            event.setSourceKey(record.getSourceKey());
            event.setEventType(eventType);
            event.setPhase(record.getPhase());
            event.setSeverity(eventType != null && eventType.contains("reject") ? "warn" : "info");
            event.setSummary(AgentRunContext.safe(summary, 1000));
            event.setMetadataJson(metadataJson);
            event.setCreatedAt(System.currentTimeMillis());
            agentRunRepository.appendEvent(event);
        } catch (Exception ignored) {
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractQueuedMarker(String text, String key) {
        if (StrUtil.isBlank(text) || StrUtil.isBlank(key)) {
            return null;
        }
        String markerStart = "[queue-metadata:";
        int start = text.lastIndexOf(markerStart);
        if (start < 0) {
            return null;
        }
        int end = text.indexOf(']', start);
        if (end < 0) {
            return null;
        }
        String body = text.substring(start + markerStart.length(), end);
        String[] parts = body.split(";");
        for (String part : parts) {
            int equals = part.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String name = part.substring(0, equals).trim();
            if (key.equals(name)) {
                return part.substring(equals + 1).trim();
            }
        }
        return null;
    }

    private String stripQueuedMarkers(String text) {
        if (StrUtil.isBlank(text)) {
            return text;
        }
        String markerStart = "\n\n[queue-metadata:";
        int start = text.lastIndexOf(markerStart);
        if (start < 0) {
            return text;
        }
        int end = text.indexOf(']', start);
        if (end < 0 || end != text.length() - 1) {
            return text;
        }
        return text.substring(0, start);
    }

    private String normalizeSourceKey(String sourceKey) {
        return StrUtil.blankToDefault(sourceKey, "__default__");
    }

    private static class RunHandle {
        private final String runId;
        private final String sessionId;
        private final Thread thread;
        private final long startedAt;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private RunHandle(String runId, String sessionId, Thread thread, long startedAt) {
            this.runId = runId;
            this.sessionId = sessionId;
            this.thread = thread;
            this.startedAt = startedAt;
        }
    }

    private void pruneOldRuns() {
        int days = appConfig.getTrace().getRetentionDays();
        if (days <= 0) {
            return;
        }
        try {
            agentRunRepository.pruneBefore(
                    System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L);
        } catch (Exception ignored) {
        }
    }
}
