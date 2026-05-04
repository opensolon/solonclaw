package com.jimuqu.solon.claw.engine;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.AgentRunRecord;
import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.model.SessionSearchEntry;
import com.jimuqu.solon.claw.core.model.SessionSearchQuery;
import com.jimuqu.solon.claw.core.model.ToolCallRecord;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.LlmGateway;
import com.jimuqu.solon.claw.core.service.SessionSearchService;
import com.jimuqu.solon.claw.support.IdSupport;
import com.jimuqu.solon.claw.support.MessageSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.noear.solon.ai.chat.ChatRole;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;

/** 默认会话搜索服务。 */
public class DefaultSessionSearchService implements SessionSearchService {
    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 5;
    private static final String SUMMARY_SYSTEM_PROMPT =
            "你正在回顾历史会话，目标是帮助当前任务快速回忆相关内容。"
                    + "\n请围绕搜索主题总结：用户目标、采取的动作、关键结论/决策、重要命令或文件、未解决事项。"
                    + "\n只输出基于对话记录可确认的事实。";

    private final SessionRepository sessionRepository;
    private final LlmGateway llmGateway;
    private final AgentRunRepository agentRunRepository;

    public DefaultSessionSearchService(SessionRepository sessionRepository, LlmGateway llmGateway) {
        this(sessionRepository, llmGateway, null);
    }

    public DefaultSessionSearchService(
            SessionRepository sessionRepository,
            LlmGateway llmGateway,
            AgentRunRepository agentRunRepository) {
        this.sessionRepository = sessionRepository;
        this.llmGateway = llmGateway;
        this.agentRunRepository = agentRunRepository;
    }

    @Override
    public List<SessionSearchEntry> search(String sourceKey, String query, int limit)
            throws Exception {
        int resolvedLimit = Math.max(1, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT));
        SessionRecord currentSession =
                StrUtil.isBlank(sourceKey) ? null : sessionRepository.getBoundSession(sourceKey);
        String currentRootId = resolveRootId(currentSession);

        List<SessionRecord> raw =
                StrUtil.isBlank(query)
                        ? sessionRepository.listRecent(Math.max(10, resolvedLimit * 5))
                        : sessionRepository.search(query.trim(), Math.max(10, resolvedLimit * 5));

        Map<String, SearchCandidate> grouped = new LinkedHashMap<String, SearchCandidate>();
        for (SessionRecord candidate : raw) {
            if (candidate == null) {
                continue;
            }
            String rootId = resolveRootId(candidate);
            if (StrUtil.isNotBlank(currentRootId) && currentRootId.equals(rootId)) {
                continue;
            }
            if (!grouped.containsKey(rootId)) {
                SessionRecord display = candidate;
                if (StrUtil.isNotBlank(rootId) && !rootId.equals(candidate.getSessionId())) {
                    SessionRecord resolved = sessionRepository.findById(rootId);
                    if (resolved != null) {
                        display = resolved;
                    }
                }
                grouped.put(rootId, new SearchCandidate(display, candidate));
            }
            if (grouped.size() >= resolvedLimit) {
                break;
            }
        }

        List<SessionSearchEntry> results = new ArrayList<SessionSearchEntry>();
        for (SearchCandidate candidate : grouped.values()) {
            SessionSearchEntry entry = new SessionSearchEntry();
            SessionRecord display = candidate.display;
            SessionRecord representative = candidate.representative;
            entry.setSessionId(display.getSessionId());
            entry.setBranchName(
                    StrUtil.blankToDefault(
                            display.getBranchName(), representative.getBranchName()));
            entry.setTitle(resolveTitle(display, representative));
            entry.setUpdatedAt(Math.max(display.getUpdatedAt(), representative.getUpdatedAt()));
            entry.setMatchPreview(buildPreview(representative, query));
            if (StrUtil.isBlank(query)) {
                entry.setSummary(entry.getMatchPreview());
            } else {
                entry.setSummary(
                        buildSummary(
                                currentSession, representative, query, entry.getMatchPreview()));
            }
            results.add(entry);
        }
        return results;
    }

    @Override
    public List<SessionSearchEntry> search(SessionSearchQuery query) throws Exception {
        if (query == null) {
            return search(null, null, DEFAULT_LIMIT);
        }
        if (shouldSearchRuns(query)) {
            return searchRunScope(query);
        }
        List<SessionSearchEntry> entries =
                search(query.getSourceKey(), query.getQuery(), query.getLimit());
        List<SessionSearchEntry> filtered = new ArrayList<SessionSearchEntry>();
        for (SessionSearchEntry entry : entries) {
            if (StrUtil.isNotBlank(query.getSessionId())
                    && !query.getSessionId().equals(entry.getSessionId())) {
                continue;
            }
            if (query.getTimeFrom() > 0 && entry.getUpdatedAt() < query.getTimeFrom()) {
                continue;
            }
            if (query.getTimeTo() > 0 && entry.getUpdatedAt() > query.getTimeTo()) {
                continue;
            }
            filtered.add(entry);
        }
        return filtered;
    }

    private boolean shouldSearchRuns(SessionSearchQuery query) {
        return agentRunRepository != null
                && (StrUtil.isNotBlank(query.getRunId())
                        || StrUtil.isNotBlank(query.getToolName())
                        || StrUtil.isNotBlank(query.getChannel()));
    }

    private List<SessionSearchEntry> searchRunScope(SessionSearchQuery query) throws Exception {
        int limit = Math.max(1, Math.min(query.getLimit() <= 0 ? DEFAULT_LIMIT : query.getLimit(), 50));
        Map<String, SessionSearchEntry> results = new LinkedHashMap<String, SessionSearchEntry>();
        if (StrUtil.isNotBlank(query.getToolName())) {
            for (ToolCallRecord toolCall :
                    agentRunRepository.searchToolCalls(
                            firstNonBlankValue(query.getChannel(), query.getSourceKey()),
                            query.getSessionId(),
                            query.getRunId(),
                            query.getToolName(),
                            query.getQuery(),
                            query.getTimeFrom(),
                            query.getTimeTo(),
                            limit)) {
                SessionSearchEntry entry = entryFromToolCall(toolCall);
                results.put(entryKey(entry), entry);
                if (results.size() >= limit) {
                    break;
                }
            }
            return new ArrayList<SessionSearchEntry>(results.values());
        }
        for (AgentRunRecord run :
                agentRunRepository.searchRuns(
                        firstNonBlankValue(query.getChannel(), query.getSourceKey()),
                        query.getSessionId(),
                        query.getRunId(),
                        query.getQuery(),
                        query.getTimeFrom(),
                        query.getTimeTo(),
                        limit)) {
            SessionSearchEntry entry = entryFromRun(run);
            results.put(entryKey(entry), entry);
            if (results.size() >= limit) {
                break;
            }
        }
        return new ArrayList<SessionSearchEntry>(results.values());
    }

    private SessionSearchEntry entryFromRun(AgentRunRecord run) throws Exception {
        SessionRecord session =
                StrUtil.isBlank(run.getSessionId()) ? null : sessionRepository.findById(run.getSessionId());
        SessionSearchEntry entry = new SessionSearchEntry();
        entry.setSessionId(run.getSessionId());
        entry.setBranchName(session == null ? null : session.getBranchName());
        entry.setTitle(
                StrUtil.blankToDefault(
                        session == null ? null : session.getTitle(), "run-" + run.getRunId()));
        entry.setUpdatedAt(Math.max(run.getLastActivityAt(), run.getStartedAt()));
        entry.setMatchPreview(
                firstNonBlank(
                        run.getFinalReplyPreview(),
                        run.getInputPreview(),
                        run.getError(),
                        run.getStatus()));
        entry.setSummary(entry.getMatchPreview());
        entry.setRunId(run.getRunId());
        entry.setChannel(run.getSourceKey());
        return entry;
    }

    private SessionSearchEntry entryFromToolCall(ToolCallRecord record) throws Exception {
        SessionRecord session =
                StrUtil.isBlank(record.getSessionId())
                        ? null
                        : sessionRepository.findById(record.getSessionId());
        SessionSearchEntry entry = new SessionSearchEntry();
        entry.setSessionId(record.getSessionId());
        entry.setBranchName(session == null ? null : session.getBranchName());
        entry.setTitle(
                StrUtil.blankToDefault(
                        session == null ? null : session.getTitle(), "run-" + record.getRunId()));
        entry.setUpdatedAt(Math.max(record.getFinishedAt(), record.getStartedAt()));
        entry.setMatchPreview(
                firstNonBlank(
                        record.getResultPreview(),
                        record.getArgsPreview(),
                        record.getError(),
                        record.getToolName()));
        entry.setSummary(entry.getMatchPreview());
        entry.setRunId(record.getRunId());
        entry.setToolName(record.getToolName());
        entry.setChannel(record.getSourceKey());
        return entry;
    }

    private String entryKey(SessionSearchEntry entry) {
        return StrUtil.blankToDefault(entry.getRunId(), "")
                + ":"
                + StrUtil.blankToDefault(entry.getToolName(), "")
                + ":"
                + StrUtil.blankToDefault(entry.getSessionId(), "");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return trim(value, 220);
            }
        }
        return "";
    }

    private String firstNonBlankValue(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String buildSummary(
            SessionRecord currentSession,
            SessionRecord representative,
            String query,
            String fallback) {
        try {
            String transcript = formatConversation(representative);
            if (StrUtil.isBlank(transcript)) {
                return StrUtil.blankToDefault(fallback, "No summary available");
            }

            SessionRecord synthetic = new SessionRecord();
            synthetic.setSessionId("session-search-" + IdSupport.newId());
            synthetic.setNdjson("");
            if (currentSession != null) {
                synthetic.setModelOverride(currentSession.getModelOverride());
            }

            String userPrompt =
                    "Search topic: "
                            + query.trim()
                            + "\nSession title: "
                            + resolveTitle(representative, representative)
                            + "\n\nConversation:\n"
                            + transcript
                            + "\n\n请围绕搜索主题给出简洁事实回顾。";
            LlmResult result =
                    llmGateway.chat(
                            synthetic, SUMMARY_SYSTEM_PROMPT, userPrompt, Collections.emptyList());
            String summary = extractText(result == null ? null : result.getAssistantMessage());
            return StrUtil.blankToDefault(
                    summary, StrUtil.blankToDefault(fallback, "No summary available"));
        } catch (Exception e) {
            return StrUtil.blankToDefault(fallback, "No summary available");
        }
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
        return "";
    }

    private String formatConversation(SessionRecord session) throws Exception {
        List<ChatMessage> messages = MessageSupport.loadMessages(session.getNdjson());
        StringBuilder buffer = new StringBuilder();
        for (ChatMessage message : messages) {
            String content = StrUtil.nullToEmpty(message.getContent()).trim();
            if (content.length() == 0 || message.getRole() == ChatRole.SYSTEM) {
                continue;
            }
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(roleLabel(message.getRole())).append(": ").append(trim(content, 400));
        }
        return trim(buffer.toString(), 4000);
    }

    private String buildPreview(SessionRecord session, String query) throws Exception {
        List<ChatMessage> messages = MessageSupport.loadMessages(session.getNdjson());
        String normalizedQuery = StrUtil.nullToEmpty(query).trim().toLowerCase(Locale.ROOT);
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            String content = StrUtil.nullToEmpty(message.getContent()).trim();
            if (content.length() == 0 || message.getRole() == ChatRole.SYSTEM) {
                continue;
            }
            if (normalizedQuery.length() == 0
                    || content.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                return trimAroundMatch(content, normalizedQuery);
            }
        }
        return "";
    }

    private String trimAroundMatch(String content, String query) {
        String normalized = content.replace('\r', ' ').replace('\n', ' ').trim();
        if (query.length() == 0) {
            return trim(normalized, 220);
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(query);
        if (index < 0) {
            return trim(normalized, 220);
        }
        int start = Math.max(0, index - 80);
        int end = Math.min(normalized.length(), index + query.length() + 80);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < normalized.length() ? "..." : "";
        return prefix + normalized.substring(start, end) + suffix;
    }

    private String resolveRootId(SessionRecord session) throws Exception {
        if (session == null || StrUtil.isBlank(session.getSessionId())) {
            return null;
        }
        SessionRecord current = session;
        String currentId = current.getSessionId();
        LinkedHashSet<String> visited = new LinkedHashSet<String>();
        while (StrUtil.isNotBlank(current.getParentSessionId()) && visited.add(currentId)) {
            SessionRecord parent = sessionRepository.findById(current.getParentSessionId());
            if (parent == null) {
                break;
            }
            current = parent;
            currentId = current.getSessionId();
        }
        return currentId;
    }

    private String resolveTitle(SessionRecord display, SessionRecord representative) {
        String title = display == null ? "" : display.getTitle();
        if (StrUtil.isNotBlank(title)) {
            return title;
        }
        if (representative != null && StrUtil.isNotBlank(representative.getTitle())) {
            return representative.getTitle();
        }
        return "session-"
                + (display != null ? display.getSessionId() : representative.getSessionId());
    }

    private String roleLabel(ChatRole role) {
        if (role == ChatRole.USER) {
            return "User";
        }
        if (role == ChatRole.ASSISTANT) {
            return "Assistant";
        }
        if (role == ChatRole.TOOL) {
            return "Tool";
        }
        return String.valueOf(role);
    }

    private String trim(String content, int maxLength) {
        String normalized =
                StrUtil.nullToEmpty(content).replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private static class SearchCandidate {
        private final SessionRecord display;
        private final SessionRecord representative;

        private SearchCandidate(SessionRecord display, SessionRecord representative) {
            this.display = display;
            this.representative = representative;
        }
    }
}
