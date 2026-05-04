package com.jimuqu.solon.claw.core.model;

import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.support.IdSupport;
import com.jimuqu.solon.claw.support.SecretRedactor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.noear.snack4.ONode;

/** 当前 Agent run 的追踪上下文。 */
public class AgentRunContext {
    private static final ThreadLocal<AgentRunContext> CURRENT = new ThreadLocal<AgentRunContext>();

    private final AgentRunRepository repository;
    private final String runId;
    private final String sessionId;
    private final String sourceKey;
    private String workspaceDir;
    private String phase;
    private int attemptNo;
    private String provider;
    private String model;

    public AgentRunContext(
            AgentRunRepository repository, String runId, String sessionId, String sourceKey) {
        this.repository = repository;
        this.runId = runId;
        this.sessionId = sessionId;
        this.sourceKey = sourceKey;
    }

    public static AgentRunContext current() {
        return CURRENT.get();
    }

    public static void setCurrent(AgentRunContext context) {
        if (context == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
        }
    }

    public String getRunId() {
        return runId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public void setAttempt(int attemptNo, String provider, String model) {
        this.attemptNo = attemptNo;
        this.provider = provider;
        this.model = model;
    }

    public String getWorkspaceDir() {
        return workspaceDir;
    }

    public void setWorkspaceDir(String workspaceDir) {
        this.workspaceDir = workspaceDir;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public void event(String eventType, String summary) {
        event(eventType, summary, null);
    }

    public void event(String eventType, String summary, Map<String, Object> metadata) {
        if (repository == null) {
            return;
        }
        try {
            AgentRunEventRecord event = new AgentRunEventRecord();
            event.setEventId(IdSupport.newId());
            event.setRunId(runId);
            event.setSessionId(sessionId);
            event.setSourceKey(sourceKey);
            event.setEventType(eventType);
            event.setPhase(phase);
            event.setSeverity(resolveSeverity(eventType));
            event.setAttemptNo(attemptNo);
            event.setProvider(provider);
            event.setModel(model);
            event.setSummary(safe(summary, 1000));
            event.setMetadataJson(metadata == null ? null : safe(ONode.serialize(metadata), 4000));
            event.setCreatedAt(System.currentTimeMillis());
            repository.appendEvent(event);
        } catch (Exception ignored) {
        }
    }

    public Map<String, Object> metadata(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put(key, value);
        return map;
    }

    public Map<String, Object> metadata(String key, Object value, String key2, Object value2) {
        Map<String, Object> map = metadata(key, value);
        map.put(key2, value2);
        return map;
    }

    public void saveToolCall(ToolCallRecord record) {
        if (repository == null || record == null) {
            return;
        }
        try {
            repository.saveToolCall(record);
        } catch (Exception ignored) {
        }
    }

    public static String safe(String text, int limit) {
        String redacted = SecretRedactor.redact(text, limit);
        if (redacted == null) {
            return null;
        }
        return redacted.length() <= limit ? redacted : redacted.substring(0, limit) + "...";
    }

    private String resolveSeverity(String eventType) {
        String value = eventType == null ? "" : eventType.toLowerCase(java.util.Locale.ROOT);
        if (value.contains("failed") || value.contains("error")) {
            return "error";
        }
        if (value.contains("fallback")
                || value.contains("recovery")
                || value.contains("compression.failed")
                || value.contains("cancel")) {
            return "warn";
        }
        return "info";
    }
}
