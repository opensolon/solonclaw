package com.jimuqu.solon.claw.llm;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.config.RuntimeConfigResolver;
import com.jimuqu.solon.claw.core.model.AgentRunContext;
import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.model.ToolCallRecord;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.ConversationEventSink;
import com.jimuqu.solon.claw.core.service.LlmGateway;
import com.jimuqu.solon.claw.gateway.feedback.ConversationFeedbackSink;
import com.jimuqu.solon.claw.gateway.feedback.ToolPreviewSupport;
import com.jimuqu.solon.claw.llm.dialect.RawResponseLoggingChatDialect;
import com.jimuqu.solon.claw.storage.session.SqliteAgentSession;
import com.jimuqu.solon.claw.support.LlmProviderService;
import com.jimuqu.solon.claw.support.IdSupport;
import com.jimuqu.solon.claw.support.constants.LlmConstants;
import com.jimuqu.solon.claw.tool.runtime.DangerousCommandApprovalService;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.noear.solon.ai.AiUsage;
import org.noear.solon.ai.agent.AgentSession;
import org.noear.solon.ai.agent.AgentSessionProvider;
import org.noear.solon.ai.agent.react.ReActAgent;
import org.noear.solon.ai.agent.react.ReActInterceptor;
import org.noear.solon.ai.agent.react.ReActResponse;
import org.noear.solon.ai.agent.react.ReActTrace;
import org.noear.solon.ai.agent.react.intercept.SummarizationInterceptor;
import org.noear.solon.ai.agent.react.intercept.SummarizationStrategy;
import org.noear.solon.ai.agent.react.intercept.ToolRetryInterceptor;
import org.noear.solon.ai.agent.react.intercept.ToolSanitizerInterceptor;
import org.noear.solon.ai.agent.react.intercept.summarize.CompositeSummarizationStrategy;
import org.noear.solon.ai.agent.react.intercept.summarize.HierarchicalSummarizationStrategy;
import org.noear.solon.ai.agent.react.intercept.summarize.KeyInfoExtractionStrategy;
import org.noear.solon.ai.agent.trace.Metrics;
import org.noear.solon.ai.chat.ChatConfig;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.ChatRequestDesc;
import org.noear.solon.ai.chat.ChatResponse;
import org.noear.solon.ai.chat.dialect.ChatDialectManager;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.chat.prompt.Prompt;
import org.noear.solon.ai.harness.HarnessEngine;
import org.noear.solon.ai.harness.HarnessProperties;
import org.noear.solon.ai.harness.agent.AgentDefinition;
import org.noear.solon.ai.llm.dialect.anthropic.AnthropicChatDialect;
import org.noear.solon.ai.llm.dialect.gemini.GeminiChatDialect;
import org.noear.solon.ai.llm.dialect.ollama.OllamaChatDialect;
import org.noear.solon.ai.llm.dialect.openai.OpenaiChatDialect;
import org.noear.solon.ai.llm.dialect.openai.OpenaiResponsesDialect;
import org.noear.solon.ai.skills.pdf.PdfSkill;
import org.noear.snack4.ONode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SolonAiLlmGateway 实现。 */
public class SolonAiLlmGateway implements LlmGateway {
    /** LLM 网关日志器。 */
    private static final Logger log = LoggerFactory.getLogger(SolonAiLlmGateway.class);

    private static final AtomicBoolean CUSTOM_DIALECTS_REGISTERED = new AtomicBoolean(false);

    private final AppConfig appConfig;
    private final SessionRepository sessionRepository;
    private final DangerousCommandApprovalService dangerousCommandApprovalService;
    private final LlmProviderService llmProviderService;
    private volatile PdfSkill pdfSkill;

    public SolonAiLlmGateway(AppConfig appConfig) {
        this(appConfig, null, null, null);
    }

    public SolonAiLlmGateway(AppConfig appConfig, SessionRepository sessionRepository) {
        this(appConfig, sessionRepository, null, null);
    }

    public SolonAiLlmGateway(
            AppConfig appConfig,
            SessionRepository sessionRepository,
            DangerousCommandApprovalService dangerousCommandApprovalService) {
        this(appConfig, sessionRepository, dangerousCommandApprovalService, null);
    }

    public SolonAiLlmGateway(
            AppConfig appConfig,
            SessionRepository sessionRepository,
            DangerousCommandApprovalService dangerousCommandApprovalService,
            LlmProviderService llmProviderService) {
        this.appConfig = appConfig;
        this.sessionRepository = sessionRepository;
        this.dangerousCommandApprovalService = dangerousCommandApprovalService;
        this.llmProviderService =
                llmProviderService == null ? new LlmProviderService(appConfig) : llmProviderService;
    }

    @Override
    public LlmResult chat(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> toolObjects)
            throws Exception {
        return chat(
                session, systemPrompt, userMessage, toolObjects, ConversationFeedbackSink.noop());
    }

    @Override
    public LlmResult chat(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> toolObjects,
            ConversationFeedbackSink feedbackSink)
            throws Exception {
        return chat(
                session,
                systemPrompt,
                userMessage,
                toolObjects,
                feedbackSink,
                ConversationEventSink.noop());
    }

    @Override
    public LlmResult chat(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> toolObjects,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink)
            throws Exception {
        return executeWithFailover(
                session, systemPrompt, userMessage, toolObjects, feedbackSink, eventSink, false);
    }

    @Override
    public LlmResult resume(SessionRecord session, String systemPrompt, List<Object> toolObjects)
            throws Exception {
        return resume(session, systemPrompt, toolObjects, ConversationFeedbackSink.noop());
    }

    @Override
    public LlmResult resume(
            SessionRecord session,
            String systemPrompt,
            List<Object> toolObjects,
            ConversationFeedbackSink feedbackSink)
            throws Exception {
        return resume(
                session, systemPrompt, toolObjects, feedbackSink, ConversationEventSink.noop());
    }

    @Override
    public LlmResult resume(
            SessionRecord session,
            String systemPrompt,
            List<Object> toolObjects,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink)
            throws Exception {
        return executeWithFailover(
                session, systemPrompt, null, toolObjects, feedbackSink, eventSink, true);
    }

    @Override
    public LlmResult executeOnce(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> toolObjects,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink,
            boolean resume,
            AppConfig.LlmConfig resolved,
            AgentRunContext runContext)
            throws Exception {
        return executeSingle(
                session,
                systemPrompt,
                userMessage,
                toolObjects,
                feedbackSink,
                eventSink,
                resume,
                resolved,
                runContext);
    }

    private LlmResult executeWithFailover(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> toolObjects,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink,
            boolean resume)
            throws Exception {
        List<AppConfig.LlmConfig> candidates = buildCandidateConfigs(session);
        Throwable lastError = null;
        boolean primary = true;

        for (AppConfig.LlmConfig resolved : candidates) {
            int maxAttempts = 2;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    LlmResult result =
                            executeSingle(
                                    session,
                                    systemPrompt,
                                    userMessage,
                                    toolObjects,
                                    feedbackSink,
                                    eventSink,
                                    resume,
                                    resolved);
                    if (hasVisibleContent(result.getAssistantMessage(), result.getRawResponse())) {
                        return result;
                    }
                    if (attempt < maxAttempts) {
                        log.warn(
                                "LLM empty response, retrying same provider: provider={}, dialect={}, model={}, attempt={}",
                                resolved.getProvider(),
                                resolved.getDialect(),
                                resolved.getModel(),
                                attempt);
                        continue;
                    }
                    lastError = new IllegalStateException("LLM returned empty assistant content");
                } catch (Exception e) {
                    lastError = e;
                    LlmErrorClassifier.ClassifiedError classified = LlmErrorClassifier.classify(e);
                    if (classified.isRetryable() && attempt < maxAttempts) {
                        log.warn(
                                "LLM request failed, retrying same provider: provider={}, dialect={}, model={}, attempt={}, message={}",
                                resolved.getProvider(),
                                resolved.getDialect(),
                                resolved.getModel(),
                                attempt,
                                e.getMessage());
                        continue;
                    }
                }

                if (!primary) {
                    log.warn(
                            "Fallback provider failed, trying next candidate: provider={}, dialect={}, model={}, message={}",
                            resolved.getProvider(),
                            resolved.getDialect(),
                            resolved.getModel(),
                            lastError == null ? "" : lastError.getMessage());
                } else {
                    log.warn(
                            "Primary provider failed, switching to fallback candidate: provider={}, dialect={}, model={}, message={}",
                            resolved.getProvider(),
                            resolved.getDialect(),
                            resolved.getModel(),
                            lastError == null ? "" : lastError.getMessage());
                }
                break;
            }
            primary = false;
        }

        if (lastError instanceof Exception) {
            throw (Exception) lastError;
        }
        throw new IllegalStateException(
                lastError == null ? "LLM execution failed" : lastError.getMessage(), lastError);
    }

    protected LlmResult executeSingle(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> toolObjects,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink,
            boolean resume,
            AppConfig.LlmConfig resolved)
            throws Exception {
        return executeSingle(
                session,
                systemPrompt,
                userMessage,
                toolObjects,
                feedbackSink,
                eventSink,
                resume,
                resolved,
                null);
    }

    protected LlmResult executeSingle(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> toolObjects,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink,
            boolean resume,
            AppConfig.LlmConfig resolved,
            AgentRunContext runContext)
            throws Exception {
        validate(resolved);
        log.info(
                "LLM {}: provider={}, dialect={}, model={}, sessionId={}, stream={}, sessionOverride={}",
                resume ? "resume" : "request",
                resolved.getProvider(),
                resolved.getDialect(),
                resolved.getModel(),
                session == null ? "" : StrUtil.nullToEmpty(session.getSessionId()),
                resolved.isStream(),
                session != null && StrUtil.isNotBlank(session.getModelOverride()));
        SqliteAgentSession agentSession = new SqliteAgentSession(session, sessionRepository);
        ChatConfig chatConfig = buildChatConfig(resolved);
        UsageCollector usageCollector = new UsageCollector();
        ReActAgent agent =
                buildHarnessReActAgent(
                        chatConfig,
                        resolved,
                        systemPrompt,
                        toolObjects,
                        agentSession,
                        feedbackSink,
                        runContext,
                        usageCollector);
        if (eventSink != null && eventSink != ConversationEventSink.noop()) {
            return callAgentStream(
                    agent,
                    agentSession,
                    session,
                    userMessage,
                    resume,
                    resolved,
                    feedbackSink,
                    eventSink,
                    usageCollector);
        }
        ReActResponse response = callAgent(agent, agentSession, userMessage, resume);

        AssistantMessage assistantMessage = response.getMessage();
        LlmResult result = new LlmResult();
        result.setAssistantMessage(assistantMessage);
        result.setNdjson(ChatMessage.toNdjson(agentSession.getMessages()));
        result.setStreamed(resolved.isStream());
        result.setRawResponse(response.getContent());
        result.setReasoningText(extractReasoning(assistantMessage));
        result.setProvider(resolved.getProvider());
        result.setModel(StrUtil.blankToDefault(resolved.getModel(), ""));
        applyMetrics(result, response.getMetrics());
        usageCollector.applyTo(result);
        logUsage(session, resolved, result);
        return result;
    }

    private ReActResponse callAgent(
            ReActAgent agent, SqliteAgentSession agentSession, String userMessage, boolean resume)
            throws Exception {
        try {
            if (resume) {
                return agent.prompt().session(agentSession).call();
            }
            return agent.prompt(Prompt.of(userMessage))
                    .session(agentSession)
                    .options(options -> options.toolContextPut("user_message", userMessage))
                    .call();
        } catch (Exception e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("ReActAgent call failed", e);
        }
    }

    private LlmResult callAgentStream(
            ReActAgent agent,
            SqliteAgentSession agentSession,
            SessionRecord session,
            String userMessage,
            boolean resume,
            AppConfig.LlmConfig resolved,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink,
            UsageCollector usageCollector)
            throws Exception {
        final StringBuilder emittedText = new StringBuilder();
        final ReActResponse[] finalResponse = new ReActResponse[1];
        final ThinkingStreamSplitter thinkingSplitter = new ThinkingStreamSplitter();

        try {
            if (resume) {
                agent.prompt().session(agentSession).stream()
                        .doOnNext(
                                chunk ->
                                        handleStreamChunk(
                                                chunk,
                                                emittedText,
                                                thinkingSplitter,
                                                eventSink,
                                                feedbackSink,
                                                finalResponse))
                        .blockLast();
            } else {
                agent
                        .prompt(Prompt.of(userMessage))
                        .session(agentSession)
                        .options(options -> options.toolContextPut("user_message", userMessage))
                        .stream()
                        .doOnNext(
                                chunk ->
                                        handleStreamChunk(
                                                chunk,
                                                emittedText,
                                                thinkingSplitter,
                                                eventSink,
                                                feedbackSink,
                                                finalResponse))
                        .blockLast();
            }
        } catch (Throwable e) {
            if (e instanceof Exception) {
                throw (Exception) e;
            }
            throw new IllegalStateException("ReActAgent stream failed", e);
        }
        emitThinking(thinkingSplitter.flushPending(), emittedText, eventSink, feedbackSink);

        AssistantMessage assistantMessage =
                finalResponse[0] == null
                        ? ChatMessage.ofAssistant(emittedText.toString())
                        : finalResponse[0].getMessage();
        String finalText = extractText(assistantMessage);
        String emitted = emittedText.toString();
        if (StrUtil.isNotBlank(finalText) && finalText.startsWith(emitted)) {
            String tail = finalText.substring(emitted.length());
            if (StrUtil.isNotBlank(tail)) {
                eventSink.onAssistantDelta(tail);
            }
        } else if (StrUtil.isNotBlank(finalText) && !StrUtil.equals(finalText, emitted)) {
            eventSink.onAssistantDelta(finalText);
        }

        LlmResult result = new LlmResult();
        result.setAssistantMessage(assistantMessage);
        result.setNdjson(ChatMessage.toNdjson(agentSession.getMessages()));
        result.setStreamed(true);
        result.setRawResponse(finalText);
        result.setReasoningText(
                StrUtil.blankToDefault(
                        thinkingSplitter.reasoningText(), extractReasoning(assistantMessage)));
        result.setProvider(resolved.getProvider());
        result.setModel(StrUtil.blankToDefault(resolved.getModel(), ""));
        if (finalResponse[0] != null) {
            applyMetrics(result, finalResponse[0].getMetrics());
        }
        if (usageCollector != null) {
            usageCollector.applyTo(result);
        }
        logUsage(session, resolved, result);
        return result;
    }

    private void handleStreamChunk(
            org.noear.solon.ai.agent.AgentChunk chunk,
            StringBuilder emittedText,
            ThinkingStreamSplitter thinkingSplitter,
            ConversationEventSink eventSink,
            ConversationFeedbackSink feedbackSink,
            ReActResponse[] finalResponse) {
        if (chunk instanceof org.noear.solon.ai.agent.react.task.ReasonChunk) {
            org.noear.solon.ai.agent.react.task.ReasonChunk reasonChunk =
                    (org.noear.solon.ai.agent.react.task.ReasonChunk) chunk;
            if (reasonChunk.isToolCalls()) {
                return;
            }

            ChatMessage message = reasonChunk.getMessage();
            String delta = message == null ? null : message.getContent();
            if (StrUtil.isBlank(delta)) {
                return;
            }

            emitThinking(
                    thinkingSplitter.accept(delta, message.isThinking()),
                    emittedText,
                    eventSink,
                    feedbackSink);
            return;
        }

        if (chunk instanceof org.noear.solon.ai.agent.react.task.ActionStartChunk) {
            org.noear.solon.ai.agent.react.task.ActionStartChunk actionChunk =
                    (org.noear.solon.ai.agent.react.task.ActionStartChunk) chunk;
            eventSink.onToolStarted(actionChunk.getToolName(), actionChunk.getArgs());
            return;
        }

        if (chunk instanceof org.noear.solon.ai.agent.react.task.ActionEndChunk) {
            org.noear.solon.ai.agent.react.task.ActionEndChunk actionChunk =
                    (org.noear.solon.ai.agent.react.task.ActionEndChunk) chunk;
            String preview =
                    ToolPreviewSupport.buildPreview(
                            actionChunk.getToolName(), actionChunk.getArgs(), 80, false);
            eventSink.onToolCompleted(actionChunk.getToolName(), preview, 0L);
            return;
        }

        if (chunk instanceof org.noear.solon.ai.agent.react.ReActChunk) {
            finalResponse[0] = ((org.noear.solon.ai.agent.react.ReActChunk) chunk).getResponse();
        }
    }

    private void emitThinking(
            ThinkingStreamSplitter.Delta delta,
            StringBuilder emittedText,
            ConversationEventSink eventSink,
            ConversationFeedbackSink feedbackSink) {
        if (delta == null) {
            return;
        }
        if (StrUtil.isNotBlank(delta.reasoning)) {
            eventSink.onReasoningDelta(delta.reasoning);
            if (feedbackSink != null) {
                feedbackSink.onReasoning(delta.reasoning);
            }
        }
        if (StrUtil.isNotBlank(delta.visible)) {
            emittedText.append(delta.visible);
            eventSink.onAssistantDelta(delta.visible);
        }
    }

    private List<AppConfig.LlmConfig> buildCandidateConfigs(SessionRecord session) {
        List<AppConfig.LlmConfig> candidates = new java.util.ArrayList<AppConfig.LlmConfig>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<String>();

        if (appConfig.getProviders() == null
                || appConfig.getProviders().isEmpty()
                || StrUtil.isBlank(appConfig.getModel().getProviderKey())) {
            candidates.add(copyLlmConfig(appConfig.getLlm()));
            return candidates;
        }

        AppConfig.LlmConfig primary =
                toLlmConfig(llmProviderService.resolveEffectiveProvider(session));
        candidates.add(primary);
        seen.add(providerSignature(primary));

        for (LlmProviderService.ResolvedProvider fallback :
                llmProviderService.resolveFallbackProviders()) {
            AppConfig.LlmConfig candidate = toLlmConfig(fallback);
            String signature = providerSignature(candidate);
            if (seen.add(signature)) {
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

    private boolean hasVisibleContent(AssistantMessage assistantMessage, String rawResponse) {
        return StrUtil.isNotBlank(extractText(assistantMessage)) || StrUtil.isNotBlank(rawResponse);
    }

    private String extractText(AssistantMessage assistantMessage) {
        if (assistantMessage == null) {
            return "";
        }
        if (StrUtil.isNotBlank(assistantMessage.getResultContent())) {
            return assistantMessage.getResultContent().trim();
        }
        if (StrUtil.isNotBlank(assistantMessage.getContent())) {
            return assistantMessage.getContent().trim();
        }
        log.warn("Assistant message has no visible content; suppressing message object fallback: role={}, contentRawType={}, toolCalls={}",
                assistantMessage.getRole(),
                assistantMessage.getContentRaw() == null
                        ? ""
                        : assistantMessage.getContentRaw().getClass().getName(),
                assistantMessage.getToolCalls() == null ? 0 : assistantMessage.getToolCalls().size());
        return "";
    }

    private String extractReasoning(AssistantMessage assistantMessage) {
        if (assistantMessage == null) {
            return "";
        }
        return StrUtil.nullToEmpty(assistantMessage.getReasoning()).trim();
    }

    /** 将流式 <think>...</think> 内容拆成 reasoning 和可见答复。 */
    private static class ThinkingStreamSplitter {
        private static final String THINK_OPEN = "<think>";
        private static final String THINK_CLOSE = "</think>";

        private final StringBuilder visible = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final StringBuilder pendingTag = new StringBuilder();
        private boolean thinking;

        Delta accept(String text, boolean thinkingOnly) {
            if (StrUtil.isBlank(text)) {
                return Delta.empty();
            }
            if (thinkingOnly) {
                reasoning.append(text);
                return new Delta("", text);
            }

            StringBuilder visibleDelta = new StringBuilder();
            StringBuilder reasoningDelta = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (pendingTag.length() > 0 || ch == '<') {
                    pendingTag.append(ch);
                    String pending = pendingTag.toString();
                    if (THINK_OPEN.equals(pending)) {
                        thinking = true;
                        pendingTag.setLength(0);
                        continue;
                    }
                    if (THINK_CLOSE.equals(pending)) {
                        thinking = false;
                        pendingTag.setLength(0);
                        continue;
                    }
                    if (THINK_OPEN.startsWith(pending) || THINK_CLOSE.startsWith(pending)) {
                        continue;
                    }
                    appendCurrent(pending, visibleDelta, reasoningDelta);
                    pendingTag.setLength(0);
                    continue;
                }
                appendCurrent(String.valueOf(ch), visibleDelta, reasoningDelta);
            }
            return buildDelta(visibleDelta, reasoningDelta);
        }

        Delta flushPending() {
            if (pendingTag.length() == 0) {
                return Delta.empty();
            }
            StringBuilder visibleDelta = new StringBuilder();
            StringBuilder reasoningDelta = new StringBuilder();
            appendCurrent(pendingTag.toString(), visibleDelta, reasoningDelta);
            pendingTag.setLength(0);
            return buildDelta(visibleDelta, reasoningDelta);
        }

        String reasoningText() {
            return reasoning.toString().trim();
        }

        private Delta buildDelta(StringBuilder visibleDelta, StringBuilder reasoningDelta) {
            if (visibleDelta.length() > 0) {
                visible.append(visibleDelta);
            }
            if (reasoningDelta.length() > 0) {
                reasoning.append(reasoningDelta);
            }
            return new Delta(visibleDelta.toString(), reasoningDelta.toString());
        }

        private void appendCurrent(
                String value, StringBuilder visibleDelta, StringBuilder reasoningDelta) {
            if (thinking) {
                reasoningDelta.append(value);
            } else {
                visibleDelta.append(value);
            }
        }

        private static class Delta {
            private static final Delta EMPTY = new Delta("", "");
            private final String visible;
            private final String reasoning;

            private Delta(String visible, String reasoning) {
                this.visible = visible;
                this.reasoning = reasoning;
            }

            private static Delta empty() {
                return EMPTY;
            }
        }
    }

    /** 校验 provider 与 URL 配置，避免隐式降级到错误协议。 */
    private void validate(AppConfig.LlmConfig resolved) {
        if (StrUtil.isBlank(resolved.getProvider())) {
            throw new IllegalStateException("LLM provider 不能为空。");
        }
        String dialect =
                LlmProviderSupport.normalizeDialect(
                        StrUtil.isNotBlank(resolved.getDialect())
                                ? resolved.getDialect()
                                : resolved.getProvider());
        if (!LlmConstants.SUPPORTED_PROVIDERS.contains(dialect)) {
            throw new IllegalStateException("不支持的 provider dialect：" + dialect);
        }
        if (StrUtil.isBlank(resolved.getApiUrl())) {
            throw new IllegalStateException("LLM apiUrl 不能为空。");
        }
        if (StrUtil.isBlank(resolved.getModel())) {
            throw new IllegalStateException("LLM model 不能为空。");
        }
        if (LlmConstants.PROVIDER_OPENAI_RESPONSES.equals(dialect)
                && !StrUtil.containsIgnoreCase(resolved.getApiUrl(), "/responses")) {
            throw new IllegalStateException("openai-responses 的 apiUrl 必须直接指向 /responses 接口。");
        }
    }

    private ChatConfig buildChatConfig(AppConfig.LlmConfig resolved) {
        ensureCustomDialectsRegistered();
        String dialect =
                LlmProviderSupport.normalizeDialect(
                        StrUtil.isNotBlank(resolved.getDialect())
                                ? resolved.getDialect()
                                : resolved.getProvider());

        ChatConfig chatConfig = new ChatConfig();
        chatConfig.setApiUrl(resolved.getApiUrl());
        chatConfig.setProvider(dialect);
        chatConfig.setModel(resolved.getModel());
        chatConfig.setTimeout(Duration.ofMinutes(5));

        if (StrUtil.isNotBlank(resolved.getApiKey())) {
            chatConfig.setApiKey(resolved.getApiKey());
        }

        chatConfig.getModelOptions().temperature(resolved.getTemperature());
        chatConfig.getModelOptions().max_tokens(resolved.getMaxTokens());
        if (LlmConstants.PROVIDER_OPENAI_RESPONSES.equals(dialect)
                && StrUtil.isNotBlank(resolved.getReasoningEffort())) {
            chatConfig
                    .getModelOptions()
                    .optionSet(
                            "reasoning",
                            Collections.<String, Object>singletonMap(
                                    "effort", resolved.getReasoningEffort()));
        }

        return chatConfig;
    }

    private ChatModel buildChatModel(AppConfig.LlmConfig resolved) {
        return buildChatConfig(resolved).toChatModel();
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

    private void ensureCustomDialectsRegistered() {
        if (CUSTOM_DIALECTS_REGISTERED.compareAndSet(false, true)) {
            ChatDialectManager.register(
                    new RawResponseLoggingChatDialect(
                            OpenaiResponsesDialect.getInstance(),
                            LlmConstants.PROVIDER_OPENAI_RESPONSES,
                            true),
                    -100);
            ChatDialectManager.register(
                    new RawResponseLoggingChatDialect(
                            OpenaiChatDialect.getInstance(), LlmConstants.PROVIDER_OPENAI, false),
                    -99);
            ChatDialectManager.register(
                    new RawResponseLoggingChatDialect(
                            OllamaChatDialect.getInstance(), LlmConstants.PROVIDER_OLLAMA, false),
                    -98);
            ChatDialectManager.register(
                    new RawResponseLoggingChatDialect(
                            GeminiChatDialect.getInstance(), LlmConstants.PROVIDER_GEMINI, false),
                    -97);
            ChatDialectManager.register(
                    new RawResponseLoggingChatDialect(
                            AnthropicChatDialect.getInstance(),
                            LlmConstants.PROVIDER_ANTHROPIC,
                            false),
                    -96);
        }
    }

    private ReActAgent buildHarnessReActAgent(
            ChatConfig chatConfig,
            AppConfig.LlmConfig resolved,
            String systemPrompt,
            List<Object> toolObjects,
            SqliteAgentSession agentSession,
            ConversationFeedbackSink feedbackSink,
            AgentRunContext runContext,
            UsageCollector usageCollector) {
        boolean delegateSession = isDelegateSession(agentSession);
        int maxSteps =
                delegateSession
                        ? appConfig.getReact().getDelegateMaxSteps()
                        : appConfig.getReact().getMaxSteps();
        int retryMax =
                delegateSession
                        ? appConfig.getReact().getDelegateRetryMax()
                        : appConfig.getReact().getRetryMax();
        long retryDelayMs =
                delegateSession
                        ? appConfig.getReact().getDelegateRetryDelayMs()
                        : appConfig.getReact().getRetryDelayMs();

        HarnessProperties harnessProperties = new HarnessProperties(".jimuqu-harness");
        harnessProperties.setWorkspace(resolveWorkspace(runContext));
        harnessProperties.addModel(chatConfig);
        harnessProperties.setMaxSteps(maxSteps);
        harnessProperties.setMaxStepsAutoExtensible(false);
        harnessProperties.setSessionWindowSize(Math.max(8, agentSession.getMessages().size() + 8));
        harnessProperties.setSummaryWindowSize(
                Math.max(10, appConfig.getReact().getSummarizationMaxMessages()));
        harnessProperties.setSummaryWindowToken(
                Math.max(8000, appConfig.getReact().getSummarizationMaxTokens()));
        harnessProperties.setSandboxMode(true);
        harnessProperties.setSubagentEnabled(false);
        harnessProperties.setHitlEnabled(false);

        HarnessEngine harnessEngine =
                HarnessEngine.of(harnessProperties)
                        .sessionProvider(new FixedAgentSessionProvider(agentSession))
                        .summarizationInterceptor(
                                buildHarnessSummarizationInterceptor(resolved, chatConfig))
                        .build();

        AgentDefinition definition = new AgentDefinition();
        definition.setSystemPrompt(systemPrompt);
        definition.getMetadata().setName("solonclaw_react");
        definition.getMetadata().setDescription("SolonClaw");
        definition.getMetadata().setMaxSteps(maxSteps);
        definition.getMetadata().setMaxStepsAutoExtensible(Boolean.FALSE);
        definition
                .getMetadata()
                .setSessionWindowSize(Math.max(8, agentSession.getMessages().size() + 8));
        definition.getMetadata().setTools(Collections.<String>emptyList());

        ReActAgent.Builder builder =
                harnessEngine
                        .createSubagent(definition)
                        .role("SolonClaw")
                        .retryConfig(retryMax, retryDelayMs)
                        .defaultInterceptorAdd(new ToolRetryInterceptor())
                        .defaultInterceptorAdd(new ToolSanitizerInterceptor());
        if (dangerousCommandApprovalService != null) {
            builder.defaultInterceptorAdd(dangerousCommandApprovalService.buildInterceptor());
        }
        if (feedbackSink != null && feedbackSink != ConversationFeedbackSink.noop()) {
            builder.defaultInterceptorAdd(
                    new FeedbackInterceptor(feedbackSink, dangerousCommandApprovalService));
        }
        if (runContext != null) {
            builder.defaultInterceptorAdd(
                    new TracingReActInterceptor(
                            runContext,
                            appConfig.getTrace().getToolPreviewLength(),
                            appConfig.getTask().getToolOutputInlineLimit(),
                            appConfig.getRuntime().getCacheDir()));
        }
        if (usageCollector != null) {
            builder.defaultInterceptorAdd(new UsageCollectingInterceptor(usageCollector));
        }

        for (Object toolObject : toolObjects) {
            builder.defaultToolAdd(toolObject);
        }
        builder.defaultSkillAdd(pdfSkill());
        return builder.build();
    }

    private SummarizationInterceptor buildHarnessSummarizationInterceptor(
            AppConfig.LlmConfig resolved, ChatConfig chatConfig) {
        if (!appConfig.getReact().isSummarizationEnabled()) {
            return new NoopSummarizationInterceptor();
        }

        ChatModel primaryChatModel = chatConfig.toChatModel();
        ChatModel summaryChatModel = buildSummaryChatModel(resolved, chatConfig);
        String summaryModel =
                StrUtil.nullToEmpty(appConfig.getCompression().getSummaryModel()).trim();
        boolean auxSummaryModel =
                StrUtil.isNotBlank(summaryModel)
                        && !StrUtil.equals(summaryModel, resolved.getModel());
        CompositeSummarizationStrategy strategy = new CompositeSummarizationStrategy();
        if (auxSummaryModel) {
            strategy.addStrategy(
                    new SummaryFallbackStrategy(
                            "key_info",
                            new KeyInfoExtractionStrategy(summaryChatModel),
                            new KeyInfoExtractionStrategy(primaryChatModel),
                            summaryModel,
                            resolved.getModel()));
            strategy.addStrategy(
                    new SummaryFallbackStrategy(
                            "hierarchical",
                            new HierarchicalSummarizationStrategy(summaryChatModel),
                            new HierarchicalSummarizationStrategy(primaryChatModel),
                            summaryModel,
                            resolved.getModel()));
        } else {
            strategy.addStrategy(new KeyInfoExtractionStrategy(summaryChatModel))
                    .addStrategy(new HierarchicalSummarizationStrategy(summaryChatModel));
        }

        return new SummarizationInterceptor(
                Math.max(10, appConfig.getReact().getSummarizationMaxMessages()),
                Math.max(8000, appConfig.getReact().getSummarizationMaxTokens()),
                strategy);
    }

    private String resolveWorkspace(AgentRunContext runContext) {
        if (runContext != null && StrUtil.isNotBlank(runContext.getWorkspaceDir())) {
            return runContext.getWorkspaceDir();
        }
        return appConfig.getRuntime().getHome();
    }

    private ChatModel buildSummaryChatModel(AppConfig.LlmConfig resolved, ChatConfig chatConfig) {
        String summaryModel =
                StrUtil.nullToEmpty(appConfig.getCompression().getSummaryModel()).trim();
        if (StrUtil.isBlank(summaryModel) || StrUtil.equals(summaryModel, resolved.getModel())) {
            return chatConfig.toChatModel();
        }

        AppConfig.LlmConfig summaryConfig = copyLlmConfig(resolved);
        summaryConfig.setModel(summaryModel);
        return buildChatConfig(summaryConfig).toChatModel();
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

    private void applyMetrics(LlmResult result, Metrics metrics) {
        if (metrics == null) {
            return;
        }
        result.setInputTokens(metrics.getPromptTokens());
        result.setOutputTokens(metrics.getCompletionTokens());
        result.setTotalTokens(metrics.getTotalTokens());
    }

    private void logUsage(SessionRecord session, AppConfig.LlmConfig resolved, LlmResult result) {
        if (result.getTotalTokens() <= 0
                && result.getInputTokens() <= 0
                && result.getOutputTokens() <= 0
                && result.getCacheReadTokens() <= 0
                && result.getCacheWriteTokens() <= 0) {
            log.info(
                    "LLM usage unavailable: provider={}, dialect={}, model={}, sessionId={}",
                    resolved.getProvider(),
                    resolved.getDialect(),
                    resolved.getModel(),
                    session == null ? "" : StrUtil.nullToEmpty(session.getSessionId()));
            return;
        }

        log.info(
                "LLM usage: provider={}, dialect={}, model={}, sessionId={}, inputTokens={}, outputTokens={}, cacheReadTokens={}, cacheWriteTokens={}, totalTokens={}",
                resolved.getProvider(),
                resolved.getDialect(),
                resolved.getModel(),
                session == null ? "" : StrUtil.nullToEmpty(session.getSessionId()),
                result.getInputTokens(),
                result.getOutputTokens(),
                result.getCacheReadTokens(),
                result.getCacheWriteTokens(),
                result.getTotalTokens());
    }

    private boolean isDelegateSession(SqliteAgentSession agentSession) {
        Object sourceKey = agentSession.getContext().get("source_key");
        if (sourceKey != null && String.valueOf(sourceKey).contains(":delegate:")) {
            return true;
        }
        Object parentSessionId = agentSession.getContext().get("parent_session_id");
        if (parentSessionId != null && StrUtil.isNotBlank(String.valueOf(parentSessionId))) {
            return true;
        }
        return false;
    }

    /** Harness 需要一个会话提供器；Jimuqu 的会话生命周期仍由外层仓储控制。 */
    private static class FixedAgentSessionProvider implements AgentSessionProvider {
        private final AgentSession session;

        private FixedAgentSessionProvider(AgentSession session) {
            this.session = session;
        }

        @Override
        public AgentSession getSession(String instanceId) {
            return session;
        }
    }

    /** 关闭 Jimuqu 配置里的 ReAct 摘要时，避免 Harness 默认摘要拦截器改变现有行为。 */
    private static class NoopSummarizationInterceptor extends SummarizationInterceptor {
        @Override
        public void onObservation(
                ReActTrace trace, String toolName, String result, long durationMs) {
            // no-op
        }
    }

    /** 摘要 aux 模型无结果或异常时回退当前主模型，避免摘要失败中断主推理。 */
    private static class SummaryFallbackStrategy implements SummarizationStrategy {
        private final String name;
        private final SummarizationStrategy aux;
        private final SummarizationStrategy primary;
        private final String auxModel;
        private final String primaryModel;

        private SummaryFallbackStrategy(
                String name,
                SummarizationStrategy aux,
                SummarizationStrategy primary,
                String auxModel,
                String primaryModel) {
            this.name = name;
            this.aux = aux;
            this.primary = primary;
            this.auxModel = auxModel;
            this.primaryModel = primaryModel;
        }

        @Override
        public ChatMessage summarize(ReActTrace trace, List<ChatMessage> messagesToSummarize) {
            try {
                ChatMessage result = aux.summarize(trace, messagesToSummarize);
                if (result != null && StrUtil.isNotBlank(result.getContent())) {
                    return result;
                }
                log.warn(
                        "Aux summary model returned empty result, fallback to primary model: strategy={}, auxModel={}, primaryModel={}",
                        name,
                        auxModel,
                        primaryModel);
            } catch (Throwable e) {
                log.warn(
                        "Aux summary model failed, fallback to primary model: strategy={}, auxModel={}, primaryModel={}",
                        name,
                        auxModel,
                        primaryModel,
                        e);
            }
            return primary.summarize(trace, messagesToSummarize);
        }
    }

    /** 懒加载 PDF 技能，统一复用 runtime/cache/pdf 目录。 */
    PdfSkill pdfSkill() {
        if (pdfSkill == null) {
            synchronized (this) {
                if (pdfSkill == null) {
                    pdfSkill = buildPdfSkill();
                }
            }
        }
        return pdfSkill;
    }

    private PdfSkill buildPdfSkill() {
        File pdfWorkDir = new File(appConfig.getRuntime().getCacheDir(), "pdf");
        if (!pdfWorkDir.exists() && !pdfWorkDir.mkdirs()) {
            log.warn("Failed to create pdf work directory: {}", pdfWorkDir.getAbsolutePath());
        }

        final File fontFile = resolvePdfFontFile();
        if (fontFile != null) {
            log.info("PDF skill font detected: {}", fontFile.getAbsolutePath());
            return new PdfSkill(
                    pdfWorkDir.getAbsolutePath(),
                    new Supplier<InputStream>() {
                        @Override
                        public InputStream get() {
                            try {
                                return new FileInputStream(fontFile);
                            } catch (Exception e) {
                                log.warn(
                                        "Failed to open PDF font file: {}",
                                        fontFile.getAbsolutePath(),
                                        e);
                                return null;
                            }
                        }
                    });
        }

        log.warn("No PDF font detected, PDF generation will use default font fallback");
        return new PdfSkill(pdfWorkDir.getAbsolutePath());
    }

    private File resolvePdfFontFile() {
        String override = RuntimeConfigResolver.getValue("solonclaw.pdf.fontPath");
        if (StrUtil.isNotBlank(override)) {
            File file = new File(override.trim());
            if (file.isFile()) {
                return file;
            }
            log.warn("Configured PDF font path not found: {}", file.getAbsolutePath());
        }

        List<String> candidates =
                Arrays.asList(
                        "C:\\Windows\\Fonts\\msyh.ttf",
                        "C:\\Windows\\Fonts\\simhei.ttf",
                        "/Library/Fonts/Arial Unicode.ttf",
                        "/usr/share/fonts/truetype/arphic-gbsn00lp/gbsn00lp.ttf",
                        "/usr/share/fonts/truetype/arphic/gbsn00lp.ttf",
                        "/usr/share/fonts/truetype/gbsn00lp.ttf",
                        "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttf",
                        "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttf");

        for (String path : candidates) {
            File file = new File(path);
            if (file.isFile()) {
                return file;
            }
        }

        return null;
    }

    /** 将 ReAct 生命周期事件桥接到网关反馈 sink。 */
    private static class FeedbackInterceptor implements ReActInterceptor {
        private final ConversationFeedbackSink feedbackSink;
        private final DangerousCommandApprovalService dangerousCommandApprovalService;

        private FeedbackInterceptor(
                ConversationFeedbackSink feedbackSink,
                DangerousCommandApprovalService dangerousCommandApprovalService) {
            this.feedbackSink = feedbackSink;
            this.dangerousCommandApprovalService = dangerousCommandApprovalService;
        }

        @Override
        public void onThought(ReActTrace trace, String thought) {
            feedbackSink.onReasoning(thought);
        }

        @Override
        public void onAction(ReActTrace trace, String toolName, Map<String, Object> args) {
            if (trace != null && trace.getSession() != null && trace.getSession().isPending()) {
                return;
            }
            if (dangerousCommandApprovalService != null
                    && trace != null
                    && trace.getSession() != null
                    && dangerousCommandApprovalService.getPendingApproval(trace.getSession())
                            != null) {
                return;
            }
            feedbackSink.onToolStarted(toolName, args);
        }

        @Override
        public void onObservation(
                ReActTrace trace, String toolName, String result, long durationMs) {
            feedbackSink.onToolFinished(toolName, result, durationMs);
        }
    }

    private static class UsageCollectingInterceptor implements ReActInterceptor {
        private final UsageCollector usageCollector;

        private UsageCollectingInterceptor(UsageCollector usageCollector) {
            this.usageCollector = usageCollector;
        }

        @Override
        public void onModelEnd(ReActTrace trace, ChatResponse resp) {
            if (resp != null) {
                usageCollector.add(resp.getUsage());
            }
        }
    }

    private static class UsageCollector {
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private long reasoningTokens;
        private long cacheReadTokens;
        private long cacheWriteTokens;

        private synchronized void add(AiUsage usage) {
            if (usage == null) {
                return;
            }
            UsageSnapshot snapshot = normalize(usage);
            promptTokens += snapshot.inputTokens;
            completionTokens += snapshot.outputTokens;
            totalTokens += snapshot.totalTokens;
            reasoningTokens += snapshot.reasoningTokens;
            cacheReadTokens += snapshot.cacheReadTokens;
            cacheWriteTokens += snapshot.cacheWriteTokens;
        }

        private synchronized void applyTo(LlmResult result) {
            if (result == null) {
                return;
            }
            result.setCacheReadTokens(cacheReadTokens);
            result.setCacheWriteTokens(cacheWriteTokens);
            result.setReasoningTokens(reasoningTokens);
            if (promptTokens > 0) {
                result.setInputTokens(promptTokens);
            }
            if (completionTokens > 0) {
                result.setOutputTokens(completionTokens);
            }
            if (totalTokens > 0) {
                result.setTotalTokens(totalTokens);
            }
        }

        private UsageSnapshot normalize(AiUsage usage) {
            ONode source = usage.getSource();
            long rawPromptTokens = Math.max(0L, usage.promptTokens());
            long outputTokens = Math.max(0L, usage.completionTokens());
            long cacheReadTokens = cacheReadTokens(usage, source);
            long cacheWriteTokens = cacheWriteTokens(usage, source);
            long inputTokens = rawPromptTokens;
            if (promptTotalIncludesCache(source)) {
                inputTokens = Math.max(0L, rawPromptTokens - cacheReadTokens - cacheWriteTokens);
            }
            long canonicalTotal = inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens;
            long apiTotal = Math.max(0L, usage.totalTokens());
            return new UsageSnapshot(
                    inputTokens,
                    outputTokens,
                    Math.max(apiTotal, canonicalTotal),
                    reasoningTokens(usage, source),
                    cacheReadTokens,
                    cacheWriteTokens);
        }

        private long cacheReadTokens(AiUsage usage, ONode source) {
            return max(
                    Math.max(0L, usage.cacheReadInputTokens()),
                    detailLong(source, "prompt_tokens_details", "cached_tokens"),
                    detailLong(source, "input_tokens_details", "cached_tokens"),
                    nodeLong(source, "cache_read_input_tokens"));
        }

        private long cacheWriteTokens(AiUsage usage, ONode source) {
            return max(
                    Math.max(0L, usage.cacheCreationInputTokens()),
                    detailLong(source, "prompt_tokens_details", "cache_write_tokens"),
                    detailLong(source, "input_tokens_details", "cache_creation_tokens"),
                    detailLong(source, "input_tokens_details", "cache_write_tokens"),
                    nodeLong(source, "cache_creation_input_tokens"));
        }

        private long reasoningTokens(AiUsage usage, ONode source) {
            return max(
                    Math.max(0L, usage.thinkTokens()),
                    detailLong(source, "completion_tokens_details", "reasoning_tokens"),
                    detailLong(source, "output_tokens_details", "reasoning_tokens"));
        }

        private boolean promptTotalIncludesCache(ONode source) {
            return objectNode(source, "prompt_tokens_details") != null
                    || objectNode(source, "input_tokens_details") != null
                    || nodeLong(source, "prompt_tokens") > 0L;
        }

        private ONode objectNode(ONode source, String key) {
            if (source == null || !source.isObject()) {
                return null;
            }
            ONode node = source.getOrNull(key);
            return node == null || !node.isObject() ? null : node;
        }

        private long detailLong(ONode source, String detailsKey, String key) {
            return nodeLong(objectNode(source, detailsKey), key);
        }

        private long nodeLong(ONode node, String key) {
            if (node == null || !node.isObject() || !node.hasKey(key)) {
                return 0L;
            }
            Long value = node.get(key).getLong(0L);
            return value == null ? 0L : Math.max(0L, value);
        }

        private long max(long... values) {
            long result = 0L;
            if (values == null) {
                return result;
            }
            for (long value : values) {
                result = Math.max(result, value);
            }
            return result;
        }

        private static class UsageSnapshot {
            private final long inputTokens;
            private final long outputTokens;
            private final long totalTokens;
            private final long reasoningTokens;
            private final long cacheReadTokens;
            private final long cacheWriteTokens;

            private UsageSnapshot(
                    long inputTokens,
                    long outputTokens,
                    long totalTokens,
                    long reasoningTokens,
                    long cacheReadTokens,
                    long cacheWriteTokens) {
                this.inputTokens = inputTokens;
                this.outputTokens = outputTokens;
                this.totalTokens = totalTokens;
                this.reasoningTokens = reasoningTokens;
                this.cacheReadTokens = cacheReadTokens;
                this.cacheWriteTokens = cacheWriteTokens;
            }
        }
    }

    /** 将 ReAct 生命周期写入持久化 run 轨迹。 */
    private static class TracingReActInterceptor implements ReActInterceptor {
        private final AgentRunContext runContext;
        private final int previewLength;
        private final int inlineLimitBytes;
        private final String cacheDir;
        private final ConcurrentMap<String, ToolCallRecord> activeToolCalls =
                new ConcurrentHashMap<String, ToolCallRecord>();

        private TracingReActInterceptor(
                AgentRunContext runContext,
                int previewLength,
                int inlineLimitBytes,
                String cacheDir) {
            this.runContext = runContext;
            this.previewLength = Math.max(200, previewLength);
            this.inlineLimitBytes = Math.max(256, inlineLimitBytes);
            this.cacheDir = cacheDir;
        }

        @Override
        public void onModelStart(ReActTrace trace, ChatRequestDesc req) {
            runContext.setPhase("model");
            runContext.event("model.start", "开始请求模型");
        }

        @Override
        public void onModelEnd(ReActTrace trace, ChatResponse resp) {
            runContext.setPhase("model");
            runContext.event("model.end", "模型响应完成");
        }

        @Override
        public void onAction(ReActTrace trace, String toolName, Map<String, Object> args) {
            runContext.setPhase("tool");
            Map<String, Object> metadata = new java.util.LinkedHashMap<String, Object>();
            metadata.put("tool", toolName);
            metadata.put("args", args);
            runContext.event("tool.start", "调用工具：" + toolName, metadata);
            ToolCallRecord record = new ToolCallRecord();
            record.setToolCallId(IdSupport.newId());
            record.setRunId(runContext.getRunId());
            record.setSessionId(runContext.getSessionId());
            record.setSourceKey(runContext.getSourceKey());
            record.setToolName(toolName);
            record.setStatus("running");
            record.setArgsPreview(AgentRunContext.safe(String.valueOf(args), previewLength));
            record.setInterruptible(true);
            record.setSideEffecting(isSideEffectingTool(toolName));
            record.setReadOnly(!record.isSideEffecting());
            record.setResultIndexable(true);
            record.setOutputLimitBytes(inlineLimitBytes);
            record.setExecutionPolicy(record.isSideEffecting() ? "serial" : "parallel_readonly");
            record.setStartedAt(System.currentTimeMillis());
            activeToolCalls.put(toolName, record);
            runContext.saveToolCall(record);
        }

        @Override
        public void onObservation(
                ReActTrace trace, String toolName, String result, long durationMs) {
            runContext.setPhase("tool");
            Map<String, Object> metadata = new java.util.LinkedHashMap<String, Object>();
            metadata.put("tool", toolName);
            metadata.put("durationMs", durationMs);
            ToolOutput output = storeOutputIfNeeded(toolName, result, null);
            metadata.put("preview", output.preview);
            metadata.put("result_ref", output.ref);
            runContext.event("tool.end", "工具完成：" + toolName + "（" + durationMs + "ms）", metadata);
            ToolCallRecord record = activeToolCalls.remove(toolName);
            if (record == null) {
                record = new ToolCallRecord();
                output = storeOutputIfNeeded(toolName, result, record);
                record.setToolCallId(IdSupport.newId());
                record.setRunId(runContext.getRunId());
                record.setSessionId(runContext.getSessionId());
                record.setSourceKey(runContext.getSourceKey());
                record.setToolName(toolName);
                record.setStartedAt(System.currentTimeMillis());
                record.setInterruptible(true);
                record.setSideEffecting(isSideEffectingTool(toolName));
                record.setReadOnly(!record.isSideEffecting());
                record.setResultIndexable(true);
                record.setOutputLimitBytes(inlineLimitBytes);
                record.setExecutionPolicy(record.isSideEffecting() ? "serial" : "parallel_readonly");
            } else {
                output = storeOutputIfNeeded(toolName, result, record);
            }
            record.setStatus("completed");
            record.setResultPreview(output.preview);
            record.setResultRef(output.ref);
            record.setResultSizeBytes(output.sizeBytes);
            record.setFinishedAt(System.currentTimeMillis());
            record.setDurationMs(durationMs);
            runContext.saveToolCall(record);
        }

        private ToolOutput storeOutputIfNeeded(
                String toolName, String result, ToolCallRecord record) {
            ToolOutput output = new ToolOutput();
            byte[] bytes = StrUtil.nullToEmpty(result).getBytes(StandardCharsets.UTF_8);
            output.sizeBytes = bytes.length;
            output.preview = AgentRunContext.safe(result, previewLength);
            if (bytes.length <= inlineLimitBytes || StrUtil.isBlank(cacheDir)) {
                return output;
            }
            try {
                String callId =
                        record != null && StrUtil.isNotBlank(record.getToolCallId())
                                ? record.getToolCallId()
                                : IdSupport.newId();
                if (record != null && StrUtil.isBlank(record.getToolCallId())) {
                    record.setToolCallId(callId);
                }
                File dir = new File(new File(cacheDir, "tool-results"), runContext.getRunId());
                cn.hutool.core.io.FileUtil.mkdir(dir);
                File file = new File(dir, callId + ".txt");
                Files.write(file.toPath(), bytes);
                output.ref = file.getAbsolutePath();
                output.preview =
                        AgentRunContext.safe(result, Math.min(previewLength, 600))
                                + "\n[result_ref: "
                                + output.ref
                                + "]";
            } catch (Exception ignored) {
            }
            return output;
        }

        private boolean isSideEffectingTool(String toolName) {
            if (toolName == null) {
                return false;
            }
            String value = toolName.toLowerCase(java.util.Locale.ROOT);
            return value.contains("write")
                    || value.contains("delete")
                    || value.contains("shell")
                    || value.contains("python")
                    || value.contains("js")
                    || value.contains("send")
                    || value.contains("cron")
                    || value.contains("skill_manage")
                    || value.contains("delegate");
        }

        private static class ToolOutput {
            private String preview;
            private String ref;
            private long sizeBytes;
        }
    }
}
