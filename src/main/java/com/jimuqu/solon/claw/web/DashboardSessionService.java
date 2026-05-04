package com.jimuqu.solon.claw.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.CheckpointRecord;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.CheckpointService;
import com.jimuqu.solon.claw.support.MessageSupport;
import com.jimuqu.solon.claw.support.SecretRedactor;
import com.jimuqu.solon.claw.support.SourceKeySupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.chat.message.ToolMessage;
import org.noear.solon.ai.chat.tool.ToolCall;

/** Dashboard 会话查询服务。 */
public class DashboardSessionService {
    private final SessionRepository sessionRepository;
    private final CheckpointService checkpointService;

    public DashboardSessionService(SessionRepository sessionRepository) {
        this(sessionRepository, null);
    }

    public DashboardSessionService(
            SessionRepository sessionRepository, CheckpointService checkpointService) {
        this.sessionRepository = sessionRepository;
        this.checkpointService = checkpointService;
    }

    public Map<String, Object> getSessions(int limit, int offset) throws Exception {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        int safeOffset = Math.max(0, offset);
        List<SessionRecord> records = sessionRepository.listRecent(safeLimit, safeOffset);
        List<Map<String, Object>> sessions = new ArrayList<Map<String, Object>>();
        for (SessionRecord record : records) {
            sessions.add(toSessionInfo(record));
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("sessions", sessions);
        result.put("total", sessionRepository.countAll());
        result.put("limit", safeLimit);
        result.put("offset", safeOffset);
        return result;
    }

    public Map<String, Object> getSessionMessages(String sessionId) throws Exception {
        SessionRecord record = sessionRepository.findById(sessionId);
        if (record == null) {
            return Collections.singletonMap("messages", Collections.emptyList());
        }

        List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
        for (ChatMessage message : MessageSupport.loadMessages(record.getNdjson())) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("role", message.getRole().name().toLowerCase(Locale.ROOT));
            String content = message.getContent();
            if (message instanceof AssistantMessage) {
                content = ((AssistantMessage) message).getResultContent();
            }
            item.put("content", SecretRedactor.redact(content, 8000));
            item.put("timestamp", null);

            if (message instanceof AssistantMessage) {
                AssistantMessage assistant = (AssistantMessage) message;
                item.put("reasoning", SecretRedactor.redact(assistant.getReasoning(), 8000));
                if (assistant.getToolCalls() != null && !assistant.getToolCalls().isEmpty()) {
                    List<Map<String, Object>> toolCalls = new ArrayList<Map<String, Object>>();
                    for (ToolCall call : assistant.getToolCalls()) {
                        Map<String, Object> function = new LinkedHashMap<String, Object>();
                        function.put("name", call.getName());
                        function.put(
                                "arguments",
                                SecretRedactor.redact(
                                        StrUtil.blankToDefault(
                                                call.getArgumentsStr(),
                                                ONode.serialize(call.getArguments())),
                                        4000));

                        Map<String, Object> toolCall = new LinkedHashMap<String, Object>();
                        toolCall.put("id", call.getId());
                        toolCall.put("function", function);
                        toolCalls.add(toolCall);
                    }
                    item.put("tool_calls", toolCalls);
                }
            }

            if (message instanceof ToolMessage) {
                ToolMessage toolMessage = (ToolMessage) message;
                item.put("tool_name", toolMessage.getName());
                item.put("tool_call_id", toolMessage.getToolCallId());
            }

            messages.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("session_id", sessionId);
        result.put(
                "model",
                StrUtil.blankToDefault(
                        record.getLastResolvedModel(),
                        StrUtil.blankToDefault(record.getModelOverride(), null)));
        result.put("provider", StrUtil.blankToDefault(record.getLastResolvedProvider(), null));
        result.put(
                "active_agent_name",
                StrUtil.blankToDefault(record.getActiveAgentName(), "default"));
        result.put("input_tokens", record.getCumulativeInputTokens());
        result.put("output_tokens", record.getCumulativeOutputTokens());
        result.put("reasoning_tokens", record.getCumulativeReasoningTokens());
        result.put("cache_read_tokens", record.getCumulativeCacheReadTokens());
        result.put("cache_write_tokens", record.getCumulativeCacheWriteTokens());
        result.put("total_tokens", record.getCumulativeTotalTokens());
        result.put("last_input_tokens", record.getLastInputTokens());
        result.put("last_output_tokens", record.getLastOutputTokens());
        result.put("last_reasoning_tokens", record.getLastReasoningTokens());
        result.put("last_cache_read_tokens", record.getLastCacheReadTokens());
        result.put("last_cache_write_tokens", record.getLastCacheWriteTokens());
        result.put("last_total_tokens", record.getLastTotalTokens());
        result.put("last_usage_at", record.getLastUsageAt());
        result.put(
                "compressed_summary", SecretRedactor.redact(record.getCompressedSummary(), 8000));
        result.put("last_compression_at", record.getLastCompressionAt());
        result.put("last_compression_input_tokens", record.getLastCompressionInputTokens());
        result.put("compression_failure_count", record.getCompressionFailureCount());
        result.put("parent_session_id", record.getParentSessionId());
        result.put("branch_name", record.getBranchName());
        result.put("messages", messages);
        return result;
    }

    public Map<String, Object> searchSessions(String query) throws Exception {
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        if (StrUtil.isBlank(query)) {
            return Collections.singletonMap("results", results);
        }

        for (SessionRecord record : sessionRepository.search(query.trim(), 50)) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("session_id", record.getSessionId());
            item.put("snippet", buildSnippet(record, query));
            item.put("role", null);
            item.put("source", parseSource(record.getSourceKey()));
            item.put(
                    "model",
                    StrUtil.blankToDefault(
                            record.getLastResolvedModel(), record.getModelOverride()));
            item.put("session_started", record.getCreatedAt());
            results.add(item);
        }

        return Collections.singletonMap("results", results);
    }

    public Map<String, Object> deleteSession(String sessionId) throws Exception {
        sessionRepository.delete(sessionId);
        return Collections.singletonMap("ok", true);
    }

    public Map<String, Object> sessionTree(String sessionId) throws Exception {
        SessionRecord root = sessionRepository.findById(sessionId);
        if (root == null) {
            return Collections.singletonMap("nodes", Collections.emptyList());
        }
        List<SessionRecord> lineage = lineageRecords(root);
        List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
        for (SessionRecord record : lineage) {
            Map<String, Object> node = toSessionInfo(record);
            node.put("parent_session_id", record.getParentSessionId());
            node.put("branch_name", record.getBranchName());
            nodes.add(node);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("root_session_id", sessionId);
        result.put("nodes", nodes);
        return result;
    }

    private List<SessionRecord> lineageRecords(SessionRecord root) throws Exception {
        int total = Math.max(sessionRepository.countAll(), 1);
        List<SessionRecord> records =
                sessionRepository.listRecent(Math.min(Math.max(total, 200), 5000), 0);
        Map<String, SessionRecord> byId = new LinkedHashMap<String, SessionRecord>();
        for (SessionRecord record : records) {
            byId.put(record.getSessionId(), record);
        }
        if (!byId.containsKey(root.getSessionId())) {
            byId.put(root.getSessionId(), root);
        }

        java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<String>();
        String cursor = root.getSessionId();
        while (StrUtil.isNotBlank(cursor) && selected.add(cursor)) {
            SessionRecord current = byId.get(cursor);
            if (current == null) {
                break;
            }
            cursor = current.getParentSessionId();
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (SessionRecord record : byId.values()) {
                String parent = record.getParentSessionId();
                if (StrUtil.isNotBlank(parent)
                        && selected.contains(parent)
                        && selected.add(record.getSessionId())) {
                    changed = true;
                }
            }
        }

        List<SessionRecord> result = new ArrayList<SessionRecord>();
        for (SessionRecord record : byId.values()) {
            if (selected.contains(record.getSessionId())) {
                result.add(record);
            }
        }
        return result;
    }

    public Map<String, Object> checkpoints(String sessionId) throws Exception {
        if (checkpointService == null) {
            return Collections.singletonMap("checkpoints", Collections.emptyList());
        }
        SessionRecord record = sessionRepository.findById(sessionId);
        if (record == null) {
            return Collections.singletonMap("checkpoints", Collections.emptyList());
        }
        List<Map<String, Object>> checkpoints = new ArrayList<Map<String, Object>>();
        for (CheckpointRecord checkpoint :
                checkpointService.listRecent(record.getSourceKey(), 50)) {
            checkpoints.add(toCheckpoint(checkpoint));
        }
        return Collections.singletonMap("checkpoints", checkpoints);
    }

    public Map<String, Object> checkpointPreview(String checkpointId) throws Exception {
        if (checkpointService == null) {
            return Collections.emptyMap();
        }
        return checkpointService.preview(checkpointId);
    }

    public Map<String, Object> rollbackCheckpoint(String checkpointId) throws Exception {
        if (checkpointService == null) {
            throw new IllegalStateException("checkpoint service is not configured");
        }
        CheckpointRecord record = checkpointService.rollback(checkpointId);
        return toCheckpoint(record);
    }

    private Map<String, Object> toSessionInfo(SessionRecord record) throws Exception {
        List<ChatMessage> messages = MessageSupport.loadMessages(record.getNdjson());
        int toolCallCount = 0;
        for (ChatMessage message : messages) {
            if (message instanceof AssistantMessage) {
                AssistantMessage assistant = (AssistantMessage) message;
                if (assistant.getToolCalls() != null) {
                    toolCallCount += assistant.getToolCalls().size();
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", record.getSessionId());
        result.put("source", parseSource(record.getSourceKey()));
        result.put(
                "model",
                StrUtil.blankToDefault(
                        record.getLastResolvedModel(),
                        StrUtil.blankToDefault(record.getModelOverride(), null)));
        result.put("provider", StrUtil.blankToDefault(record.getLastResolvedProvider(), null));
        result.put(
                "active_agent_name",
                StrUtil.blankToDefault(record.getActiveAgentName(), "default"));
        result.put("title", record.getTitle());
        result.put("started_at", record.getCreatedAt());
        result.put("ended_at", null);
        result.put("last_active", record.getUpdatedAt());
        result.put(
                "is_active",
                record.getUpdatedAt() >= System.currentTimeMillis() - 5L * 60L * 1000L);
        result.put("message_count", messages.size());
        result.put("tool_call_count", toolCallCount);
        result.put("input_tokens", record.getCumulativeInputTokens());
        result.put("output_tokens", record.getCumulativeOutputTokens());
        result.put("reasoning_tokens", record.getCumulativeReasoningTokens());
        result.put("cache_read_tokens", record.getCumulativeCacheReadTokens());
        result.put("cache_write_tokens", record.getCumulativeCacheWriteTokens());
        result.put("total_tokens", record.getCumulativeTotalTokens());
        result.put("last_total_tokens", record.getLastTotalTokens());
        result.put("last_usage_at", record.getLastUsageAt());
        result.put("parent_session_id", record.getParentSessionId());
        result.put("branch_name", record.getBranchName());
        result.put(
                "compressed_summary", SecretRedactor.redact(record.getCompressedSummary(), 8000));
        result.put("last_compression_at", record.getLastCompressionAt());
        result.put("last_compression_input_tokens", record.getLastCompressionInputTokens());
        result.put("compression_failure_count", record.getCompressionFailureCount());
        result.put(
                "preview",
                trim(
                        StrUtil.blankToDefault(
                                MessageSupport.getLastUserMessage(record.getNdjson()),
                                record.getCompressedSummary()),
                        160));
        return result;
    }

    private Map<String, Object> toCheckpoint(CheckpointRecord checkpoint) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("checkpoint_id", checkpoint.getCheckpointId());
        item.put("source_key", checkpoint.getSourceKey());
        item.put("session_id", checkpoint.getSessionId());
        item.put("created_at", checkpoint.getCreatedAt());
        item.put("restored_at", checkpoint.getRestoredAt());
        return item;
    }

    private String buildSnippet(SessionRecord record, String query) throws Exception {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (ChatMessage message : MessageSupport.loadMessages(record.getNdjson())) {
            String content = StrUtil.nullToEmpty(message.getContent()).replace('\n', ' ').trim();
            if (StrUtil.isBlank(content)) {
                continue;
            }
            int index = content.toLowerCase(Locale.ROOT).indexOf(lowerQuery);
            if (index >= 0) {
                int start = Math.max(0, index - 60);
                int end = Math.min(content.length(), index + lowerQuery.length() + 60);
                String prefix = start > 0 ? "..." : "";
                String suffix = end < content.length() ? "..." : "";
                return prefix
                        + content.substring(start, index)
                        + ">>>"
                        + content.substring(index, index + lowerQuery.length())
                        + "<<<"
                        + content.substring(index + lowerQuery.length(), end)
                        + suffix;
            }
        }
        return trim(StrUtil.blankToDefault(record.getCompressedSummary(), record.getTitle()), 160);
    }

    private String parseSource(String sourceKey) {
        String[] parts = SourceKeySupport.split(sourceKey);
        if ("MEMORY".equalsIgnoreCase(parts[0])) {
            return "local";
        }
        return parts[0].toLowerCase(Locale.ROOT);
    }

    private String trim(String text, int limit) {
        String normalized = StrUtil.nullToEmpty(text).replace('\n', ' ').trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }
}
