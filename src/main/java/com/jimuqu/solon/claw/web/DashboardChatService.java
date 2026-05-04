package com.jimuqu.solon.claw.web;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.core.model.MessageAttachment;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.CommandService;
import com.jimuqu.solon.claw.core.service.ConversationEventSink;
import com.jimuqu.solon.claw.core.service.ConversationOrchestrator;
import com.jimuqu.solon.claw.gateway.feedback.ToolPreviewSupport;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import com.jimuqu.solon.claw.support.BoundedExecutorFactory;
import com.jimuqu.solon.claw.support.IdSupport;
import com.jimuqu.solon.claw.support.MessageSupport;
import com.jimuqu.solon.claw.support.constants.CompressionConstants;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.annotation.Component;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.UploadedFile;

/** Dashboard chat 运行服务。 */
@Component
public class DashboardChatService {
    private static final long SSE_KEEPALIVE_MILLIS = 15_000L;
    private static final long RUN_TTL_MILLIS = 5L * 60L * 1000L;

    private final SessionRepository sessionRepository;
    private final ConversationOrchestrator conversationOrchestrator;
    private final CommandService commandService;
    private final AttachmentCacheService attachmentCacheService;
    private final ExecutorService executor;
    private final ConcurrentMap<String, ChatRunState> runs =
            new ConcurrentHashMap<String, ChatRunState>();

    public DashboardChatService(
            SessionRepository sessionRepository,
            ConversationOrchestrator conversationOrchestrator,
            CommandService commandService,
            AttachmentCacheService attachmentCacheService) {
        this.sessionRepository = sessionRepository;
        this.conversationOrchestrator = conversationOrchestrator;
        this.commandService = commandService;
        this.attachmentCacheService = attachmentCacheService;
        this.executor = BoundedExecutorFactory.fixed("dashboard-chat-run", 4, 128);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public Map<String, Object> uploads(UploadedFile[] files) throws Exception {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("至少上传一个文件。");
        }

        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (UploadedFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            try {
                MessageAttachment attachment =
                        attachmentCacheService.cacheBytes(
                                PlatformType.MEMORY,
                                null,
                                file.getName(),
                                file.getContentType(),
                                false,
                                null,
                                file.getContentAsBytes());
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("name", attachment.getOriginalName());
                item.put("path", attachment.getLocalPath());
                item.put("local_path", attachment.getLocalPath());
                item.put("kind", attachment.getKind());
                item.put("mime_type", attachment.getMimeType());
                item.put("size", file.getContentSize());
                results.add(item);
            } finally {
                file.delete();
            }
        }

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("files", results);
        return response;
    }

    public Map<String, Object> startRun(ONode body) throws Exception {
        cleanupExpiredRuns();

        final ChatRunRequest request = ChatRunRequest.from(body);
        if (StrUtil.isBlank(request.input)
                && (request.attachments == null || request.attachments.isEmpty())) {
            throw new IllegalArgumentException("input 与 attachments 不能同时为空。");
        }
        if (StrUtil.isBlank(request.sessionId)) {
            request.sessionId = IdSupport.newId();
        }

        final String runId = IdSupport.newId();
        final ChatRunState state = new ChatRunState(runId, request.sessionId);
        runs.put(runId, state);

        Future<?> future =
                executor.submit(
                        new Runnable() {
                            @Override
                            public void run() {
                                executeRun(state, request);
                            }
                        });
        state.future = future;

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("run_id", runId);
        response.put("status", "running");
        response.put("session_id", request.sessionId);
        return response;
    }

    public void streamEvents(String runId, Context context) throws Exception {
        ChatRunState state = runs.get(runId);
        if (state == null) {
            context.status(404);
            context.contentType("application/json;charset=UTF-8");
            context.output(ONode.serialize(Collections.singletonMap("error", "Run not found")));
            return;
        }

        context.contentType("text/event-stream;charset=UTF-8");
        context.headerSet("Cache-Control", "no-cache");
        context.headerSet("Connection", "keep-alive");
        context.headerSet("X-Accel-Buffering", "no");

        OutputStream outputStream = context.outputStream();
        long lastWriteAt = 0L;
        try {
            while (true) {
                ChatRunEvent event = state.events.poll(500, TimeUnit.MILLISECONDS);
                long now = System.currentTimeMillis();
                if (event != null) {
                    writeEvent(outputStream, event);
                    lastWriteAt = now;
                } else if (lastWriteAt == 0L || now - lastWriteAt >= SSE_KEEPALIVE_MILLIS) {
                    outputStream.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    lastWriteAt = now;
                }

                if (state.completed && state.events.isEmpty()) {
                    break;
                }
            }
        } finally {
            if (state.completed && state.events.isEmpty()) {
                runs.remove(runId, state);
            }
            IoUtil.close(outputStream);
        }
    }

    public Map<String, Object> cancelRun(String runId) {
        ChatRunState state = runs.get(runId);
        if (state == null) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }

        state.canceled = true;
        state.completed = true;
        state.status = "canceled";
        enqueue(
                state,
                "run.failed",
                new LinkedHashMap<String, Object>() {
                    {
                        put("error", "Run canceled");
                    }
                });

        Future<?> future = state.future;
        if (future != null) {
            future.cancel(true);
        }

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("ok", true);
        response.put("run_id", runId);
        response.put("status", "canceled");
        return response;
    }

    private void executeRun(ChatRunState state, ChatRunRequest request) {
        DashboardRunEventSink eventSink = new DashboardRunEventSink(state);
        try {
            SessionRecord session = prepareSession(request);
            state.sessionId = session.getSessionId();
            state.status = "running";
            eventSink.onRunStarted(state.sessionId);

            GatewayMessage message = buildMessage(request, state.sessionId);
            GatewayReply reply;
            if (request.input.trim().startsWith("/")) {
                reply = commandService.handle(message, request.input.trim(), eventSink);
            } else {
                reply = conversationOrchestrator.handleIncoming(message, eventSink);
            }

            if (!state.completed) {
                if (reply != null && reply.isError()) {
                    eventSink.onRunFailed(
                            reply.getSessionId(), new IllegalStateException(reply.getContent()));
                } else {
                    eventSink.onRunCompleted(
                            reply == null ? state.sessionId : reply.getSessionId(),
                            reply == null ? "" : StrUtil.nullToEmpty(reply.getContent()),
                            null);
                }
            }
        } catch (Throwable e) {
            if (!state.completed) {
                eventSink.onRunFailed(state.sessionId, e);
            }
        }
    }

    private SessionRecord prepareSession(ChatRunRequest request) throws Exception {
        String sourceKey = sourceKey(request.sessionId);
        SessionRecord session = sessionRepository.findById(request.sessionId);
        if (session == null) {
            session = new SessionRecord();
            session.setSessionId(request.sessionId);
            session.setSourceKey(sourceKey);
            session.setBranchName("main");
            session.setNdjson(historyToNdjson(request.conversationHistory));
            session.setTitle(extractTitle(request));
            session.setCreatedAt(System.currentTimeMillis());
            session.setUpdatedAt(System.currentTimeMillis());
            if (StrUtil.isNotBlank(request.model)) {
                session.setModelOverride(request.model);
            }
            sessionRepository.save(session);
        } else if (StrUtil.isBlank(session.getSourceKey())) {
            session.setSourceKey(sourceKey);
            session.setUpdatedAt(System.currentTimeMillis());
            sessionRepository.save(session);
        }

        if (StrUtil.isNotBlank(request.model)) {
            sessionRepository.setModelOverride(session.getSessionId(), request.model);
            session.setModelOverride(request.model);
        }
        sessionRepository.bindSource(sourceKey, session.getSessionId());
        return sessionRepository.findById(session.getSessionId());
    }

    private GatewayMessage buildMessage(ChatRunRequest request, String sessionId) {
        GatewayMessage message =
                new GatewayMessage(PlatformType.MEMORY, "dashboard", sessionId, request.input);
        message.setChatType("dm");
        message.setChatName("dashboard");
        message.setUserName("dashboard");
        message.setSourceKeyOverride(sourceKey(sessionId));
        if (request.attachments != null && !request.attachments.isEmpty()) {
            List<MessageAttachment> attachments = new ArrayList<MessageAttachment>();
            for (AttachmentInput item : request.attachments) {
                MessageAttachment attachment =
                        attachmentCacheService.fromMediaCacheFile(
                                PlatformType.MEMORY,
                                new java.io.File(item.localPath),
                                item.kind,
                                false,
                                null);
                attachments.add(attachment);
            }
            message.setAttachments(attachments);
        }
        return message;
    }

    private String historyToNdjson(List<HistoryItem> history) throws IOException {
        if (history == null || history.isEmpty()) {
            return "";
        }

        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        for (HistoryItem item : history) {
            if (item == null || StrUtil.isBlank(item.content)) {
                continue;
            }
            String role = StrUtil.blankToDefault(item.role, "user").trim().toLowerCase(Locale.ROOT);
            if ("assistant".equals(role)) {
                messages.add(ChatMessage.ofAssistant(item.content));
            } else if ("system".equals(role)) {
                messages.add(ChatMessage.ofSystem(item.content));
            } else {
                messages.add(ChatMessage.ofUser(item.content));
            }
        }
        return MessageSupport.toNdjson(messages);
    }

    private String extractTitle(ChatRunRequest request) {
        String base = "";
        if (request.conversationHistory != null) {
            for (HistoryItem item : request.conversationHistory) {
                if (item != null
                        && "user".equalsIgnoreCase(item.role)
                        && StrUtil.isNotBlank(item.content)) {
                    base = item.content;
                    break;
                }
            }
        }
        if (StrUtil.isBlank(base)) {
            base = request.input;
        }
        String normalized = StrUtil.nullToEmpty(base).replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= CompressionConstants.MAX_TITLE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, CompressionConstants.MAX_TITLE_LENGTH) + "...";
    }

    private void enqueue(ChatRunState state, String eventName, Map<String, Object> data) {
        if (state.canceled && !"run.failed".equals(eventName)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("run_id", state.runId);
        if (StrUtil.isNotBlank(state.sessionId)) {
            payload.put("session_id", state.sessionId);
        }
        if (data != null) {
            payload.putAll(data);
        }
        state.events.offer(new ChatRunEvent(eventName, payload));
    }

    private String sourceKey(String sessionId) {
        return "MEMORY:dashboard:" + StrUtil.blankToDefault(sessionId, "");
    }

    private void cleanupExpiredRuns() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ChatRunState> entry : runs.entrySet()) {
            ChatRunState state = entry.getValue();
            if (!state.completed) {
                continue;
            }
            if (now - state.updatedAt > RUN_TTL_MILLIS) {
                runs.remove(entry.getKey(), state);
            }
        }
    }

    private void writeEvent(OutputStream outputStream, ChatRunEvent event) throws IOException {
        outputStream.write(("event: " + event.name + "\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(
                ("data: " + ONode.serialize(event.data) + "\n\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private final class DashboardRunEventSink implements ConversationEventSink {
        private final ChatRunState state;

        private DashboardRunEventSink(ChatRunState state) {
            this.state = state;
        }

        @Override
        public void onRunStarted(String sessionId) {
            state.sessionId = sessionId;
            enqueue(state, "run.started", Collections.<String, Object>emptyMap());
        }

        @Override
        public void onAssistantDelta(String delta) {
            if (StrUtil.isBlank(delta) || state.completed || state.canceled) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("delta", delta);
            enqueue(state, "message.delta", payload);
        }

        @Override
        public void onReasoningDelta(String delta) {
            if (StrUtil.isBlank(delta) || state.completed || state.canceled) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("delta", delta);
            enqueue(state, "reasoning.delta", payload);
        }

        @Override
        public void onToolStarted(String toolName, Map<String, Object> args) {
            if (state.completed || state.canceled) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("tool", toolName);
            payload.put("preview", ToolPreviewSupport.buildPreview(toolName, args, 60, false));
            enqueue(state, "tool.started", payload);
        }

        @Override
        public void onToolCompleted(String toolName, String result, long durationMs) {
            if (state.completed || state.canceled) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("tool", toolName);
            payload.put("duration_ms", durationMs);
            if (StrUtil.isNotBlank(result)) {
                payload.put("preview", truncateInline(result, 80));
            }
            enqueue(state, "tool.completed", payload);
        }

        @Override
        public void onAttemptStarted(String runId, int attemptNo, String provider, String model) {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("agent_run_id", runId);
            payload.put("attempt_no", attemptNo);
            payload.put("provider", provider);
            payload.put("model", model);
            enqueue(state, "attempt.started", payload);
        }

        @Override
        public void onAttemptCompleted(String runId, int attemptNo, String status, String reason) {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("agent_run_id", runId);
            payload.put("attempt_no", attemptNo);
            payload.put("status", status);
            payload.put("reason", reason);
            enqueue(state, "attempt.completed", payload);
        }

        @Override
        public void onCompressionDecision(
                String runId,
                boolean compressed,
                String reason,
                int estimatedTokens,
                int thresholdTokens) {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("agent_run_id", runId);
            payload.put("compressed", compressed);
            payload.put("reason", reason);
            payload.put("estimated_tokens", estimatedTokens);
            payload.put("threshold_tokens", thresholdTokens);
            enqueue(state, "compression.decision", payload);
        }

        @Override
        public void onRecoveryStarted(String runId, String recoveryType) {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("agent_run_id", runId);
            payload.put("recovery_type", recoveryType);
            enqueue(state, "recovery.started", payload);
        }

        @Override
        public void onFallback(
                String runId, String fromProvider, String toProvider, String reason) {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("agent_run_id", runId);
            payload.put("from_provider", fromProvider);
            payload.put("to_provider", toProvider);
            payload.put("reason", reason);
            enqueue(state, "fallback", payload);
        }

        @Override
        public void onRunCompleted(String sessionId, String finalReply, LlmResult result) {
            state.sessionId = StrUtil.blankToDefault(sessionId, state.sessionId);
            state.status = "completed";
            state.completed = true;
            state.updatedAt = System.currentTimeMillis();

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            if (result != null) {
                Map<String, Object> usage = new LinkedHashMap<String, Object>();
                usage.put("input_tokens", result.getInputTokens());
                usage.put("output_tokens", result.getOutputTokens());
                usage.put("reasoning_tokens", result.getReasoningTokens());
                usage.put("total_tokens", result.getTotalTokens());
                payload.put("usage", usage);
                if (StrUtil.isNotBlank(result.getReasoningText())) {
                    payload.put("reasoning", result.getReasoningText());
                }
            }
            enqueue(state, "run.completed", payload);
        }

        @Override
        public void onRunFailed(String sessionId, Throwable error) {
            state.sessionId = StrUtil.blankToDefault(sessionId, state.sessionId);
            state.status = "failed";
            state.completed = true;
            state.updatedAt = System.currentTimeMillis();

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put(
                    "error",
                    error == null
                            ? "Run failed"
                            : StrUtil.blankToDefault(
                                    error.getMessage(), error.getClass().getSimpleName()));
            enqueue(state, "run.failed", payload);
        }

        private String truncateInline(String text, int limit) {
            String normalized =
                    StrUtil.nullToEmpty(text).replace('\r', ' ').replace('\n', ' ').trim();
            if (normalized.length() <= limit) {
                return normalized;
            }
            return normalized.substring(0, Math.max(0, limit - 3)) + "...";
        }
    }

    private static class ChatRunState {
        private final String runId;
        private final BlockingQueue<ChatRunEvent> events = new LinkedBlockingQueue<ChatRunEvent>();
        private final long createdAt = System.currentTimeMillis();
        private volatile long updatedAt = createdAt;
        private volatile String sessionId;
        private volatile String status = "queued";
        private volatile boolean completed;
        private volatile boolean canceled;
        private volatile Future<?> future;

        private ChatRunState(String runId, String sessionId) {
            this.runId = runId;
            this.sessionId = sessionId;
        }
    }

    private static class ChatRunEvent {
        private final String name;
        private final Map<String, Object> data;

        private ChatRunEvent(String name, Map<String, Object> data) {
            this.name = name;
            this.data = data;
        }
    }

    private static class ChatRunRequest {
        private String input;
        private String sessionId;
        private String model;
        private List<HistoryItem> conversationHistory;
        private List<AttachmentInput> attachments;

        private static ChatRunRequest from(ONode body) {
            ChatRunRequest request = new ChatRunRequest();
            request.input = body.get("input").getString();
            request.sessionId = body.get("session_id").getString();
            request.model = body.get("model").getString();

            List<HistoryItem> history = new ArrayList<HistoryItem>();
            ONode historyNode = body.get("conversation_history");
            if (historyNode != null && historyNode.isArray()) {
                for (int i = 0; i < historyNode.size(); i++) {
                    ONode item = historyNode.get(i);
                    HistoryItem historyItem = new HistoryItem();
                    historyItem.role = item.get("role").getString();
                    historyItem.content = item.get("content").getString();
                    history.add(historyItem);
                }
            }
            request.conversationHistory = history;

            List<AttachmentInput> attachments = new ArrayList<AttachmentInput>();
            ONode attachmentsNode = body.get("attachments");
            if (attachmentsNode != null && attachmentsNode.isArray()) {
                for (int i = 0; i < attachmentsNode.size(); i++) {
                    ONode item = attachmentsNode.get(i);
                    AttachmentInput attachment = new AttachmentInput();
                    attachment.name = item.get("name").getString();
                    attachment.localPath = item.get("local_path").getString();
                    attachment.kind = item.get("kind").getString();
                    attachment.mimeType = item.get("mime_type").getString();
                    attachments.add(attachment);
                }
            }
            request.attachments = attachments;
            return request;
        }
    }

    private static class HistoryItem {
        private String role;
        private String content;
    }

    private static class AttachmentInput {
        private String name;
        private String localPath;
        private String kind;
        private String mimeType;
    }
}
