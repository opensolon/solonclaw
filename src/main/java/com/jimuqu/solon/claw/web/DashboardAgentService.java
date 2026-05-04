package com.jimuqu.solon.claw.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.agent.AgentProfile;
import com.jimuqu.solon.claw.agent.AgentProfileService;
import com.jimuqu.solon.claw.agent.AgentRuntimeScope;
import com.jimuqu.solon.claw.agent.AgentRuntimeService;
import com.jimuqu.solon.claw.core.model.AgentRunRecord;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.noear.snack4.ONode;

/** Dashboard Agent 管理服务。 */
public class DashboardAgentService {
    private final AgentProfileService agentProfileService;
    private final AgentRuntimeService agentRuntimeService;
    private final SessionRepository sessionRepository;
    private final AgentRunRepository agentRunRepository;

    public DashboardAgentService(
            AgentProfileService agentProfileService,
            AgentRuntimeService agentRuntimeService,
            SessionRepository sessionRepository,
            AgentRunRepository agentRunRepository) {
        this.agentProfileService = agentProfileService;
        this.agentRuntimeService = agentRuntimeService;
        this.sessionRepository = sessionRepository;
        this.agentRunRepository = agentRunRepository;
    }

    public Map<String, Object> list(String sessionId) throws Exception {
        List<Map<String, Object>> agents = new ArrayList<Map<String, Object>>();
        SessionRecord session =
                StrUtil.isBlank(sessionId) ? null : sessionRepository.findById(sessionId);
        String active =
                AgentRuntimeScope.normalizeName(
                        session == null ? null : session.getActiveAgentName());
        for (AgentProfile profile : agentProfileService.listAll()) {
            agents.add(toSummary(profile, active));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("agents", agents);
        result.put("active_agent_name", active);
        return result;
    }

    public Map<String, Object> get(String name, String sessionId) throws Exception {
        String normalized = agentRuntimeService.normalizeName(name);
        SessionRecord session =
                StrUtil.isBlank(sessionId) ? null : sessionRepository.findById(sessionId);
        String active =
                AgentRuntimeScope.normalizeName(
                        session == null ? null : session.getActiveAgentName());
        if (AgentRuntimeScope.DEFAULT_AGENT.equals(normalized)) {
            return defaultAgent(active);
        }
        AgentProfile profile = agentProfileService.findByName(normalized);
        if (profile == null) {
            throw new IllegalArgumentException("未找到 Agent：" + normalized);
        }
        return toDetail(profile, active);
    }

    public Map<String, Object> create(Map<String, Object> body) throws Exception {
        String name = string(body, "name");
        String role = string(body, "role_prompt");
        if (StrUtil.isBlank(role)) {
            role = string(body, "role");
        }
        AgentProfile profile = agentProfileService.createAgent(name, role);
        applyMutableFields(profile, body);
        profile = agentProfileService.save(profile);
        return toDetail(profile, "");
    }

    public Map<String, Object> update(String name, Map<String, Object> body) throws Exception {
        agentRuntimeService.rejectDefault(name);
        AgentProfile profile = agentProfileService.findByName(name);
        if (profile == null) {
            throw new IllegalArgumentException("未找到 Agent：" + name);
        }
        applyMutableFields(profile, body);
        profile = agentProfileService.save(profile);
        return toDetail(profile, "");
    }

    public Map<String, Object> delete(String name) throws Exception {
        agentRuntimeService.rejectDefault(name);
        String normalized = agentRuntimeService.normalizeName(name);
        sessionRepository.clearActiveAgentName(normalized);
        agentProfileService.deleteByName(normalized);
        return Collections.singletonMap("ok", Boolean.TRUE);
    }

    public Map<String, Object> activate(String name, Map<String, Object> body) throws Exception {
        String sessionId = string(body, "session_id");
        if (StrUtil.isBlank(sessionId)) {
            throw new IllegalArgumentException("session_id 不能为空。");
        }
        String normalized = agentRuntimeService.normalizeName(name);
        if (!AgentRuntimeScope.DEFAULT_AGENT.equals(normalized)) {
            AgentProfile profile = agentProfileService.findByName(normalized);
            if (profile == null) {
                throw new IllegalArgumentException("未找到 Agent：" + normalized);
            }
            if (!profile.isEnabled()) {
                throw new IllegalArgumentException("Agent 已停用：" + normalized);
            }
        }
        SessionRecord session = sessionRepository.findById(sessionId);
        if (session == null) {
            session = new SessionRecord();
            session.setSessionId(sessionId);
            session.setSourceKey("MEMORY:dashboard:" + sessionId);
            session.setBranchName("main");
            session.setNdjson("");
            session.setTitle("");
            session.setCreatedAt(System.currentTimeMillis());
            session.setUpdatedAt(System.currentTimeMillis());
            sessionRepository.save(session);
        }
        sessionRepository.setActiveAgentName(
                sessionId, AgentRuntimeScope.DEFAULT_AGENT.equals(normalized) ? null : normalized);
        agentRuntimeService.markUsed(normalized);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("session_id", sessionId);
        result.put("active_agent_name", normalized);
        return result;
    }

    private Map<String, Object> defaultAgent(String active) {
        AgentRuntimeScope scope = agentRuntimeService.defaultScope();
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("name", AgentRuntimeScope.DEFAULT_AGENT);
        item.put("display_name", "默认 Agent");
        item.put("description", "映射 runtime 根目录的默认行为");
        item.put("default_agent", true);
        item.put("readonly", true);
        item.put("enabled", true);
        item.put("active", AgentRuntimeScope.DEFAULT_AGENT.equals(active));
        item.put("default_model", "");
        item.put("role_prompt", "");
        item.put("allowed_tools_json", "[]");
        item.put("skills_json", "[]");
        item.put("memory", "");
        item.put("workspace_path", scope.getWorkspaceDir());
        item.put("skills_path", scope.getSkillsDir());
        item.put("cache_path", scope.getCacheDir());
        item.put("running_runs", 0);
        item.put("recent_runs", Collections.emptyList());
        return item;
    }

    private Map<String, Object> toSummary(AgentProfile profile, String active) {
        Map<String, Object> item = baseProfile(profile, active);
        item.put("role_prompt", profile.getRolePrompt());
        item.put("memory", profile.getMemory());
        return item;
    }

    private Map<String, Object> toDetail(AgentProfile profile, String active) throws Exception {
        Map<String, Object> item = baseProfile(profile, active);
        item.put("role_prompt", profile.getRolePrompt());
        item.put("allowed_tools_json", StrUtil.blankToDefault(profile.getAllowedToolsJson(), "[]"));
        item.put("skills_json", StrUtil.blankToDefault(profile.getSkillsJson(), "[]"));
        item.put("memory", profile.getMemory());
        List<Map<String, Object>> runs = recentRuns(profile.getAgentName());
        item.put("recent_runs", runs);
        item.put("running_runs", countRunning(runs));
        return item;
    }

    private Map<String, Object> baseProfile(AgentProfile profile, String active) {
        File root = agentRuntimeService.agentRoot(profile.getAgentName());
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("name", profile.getAgentName());
        item.put(
                "display_name",
                StrUtil.blankToDefault(profile.getDisplayName(), profile.getAgentName()));
        item.put("description", StrUtil.nullToEmpty(profile.getDescription()));
        item.put("default_agent", false);
        item.put("readonly", false);
        item.put("enabled", profile.isEnabled());
        item.put("active", StrUtil.equals(profile.getAgentName(), active));
        item.put("default_model", StrUtil.nullToEmpty(profile.getDefaultModel()));
        item.put("workspace_path", FileUtil.file(root, "workspace").getAbsolutePath());
        item.put("skills_path", FileUtil.file(root, "skills").getAbsolutePath());
        item.put("cache_path", FileUtil.file(root, "cache").getAbsolutePath());
        item.put("last_used_at", profile.getLastUsedAt());
        item.put("updated_at", profile.getUpdatedAt());
        return item;
    }

    private List<Map<String, Object>> recentRuns(String agentName) throws Exception {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        int total = Math.max(sessionRepository.countAll(), 1);
        List<SessionRecord> sessions =
                sessionRepository.listRecent(Math.min(Math.max(total, 100), 1000), 0);
        for (SessionRecord session : sessions) {
            for (AgentRunRecord run :
                    agentRunRepository.listBySession(session.getSessionId(), 10)) {
                if (!StrUtil.equals(agentName, run.getAgentName())) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("run_id", run.getRunId());
                item.put("session_id", run.getSessionId());
                item.put("status", run.getStatus());
                item.put("model", run.getModel());
                item.put("started_at", run.getStartedAt());
                item.put("finished_at", run.getFinishedAt());
                result.add(item);
                if (result.size() >= 5) {
                    return result;
                }
            }
        }
        return result;
    }

    private int countRunning(List<Map<String, Object>> runs) {
        int count = 0;
        for (Map<String, Object> run : runs) {
            if ("running".equals(run.get("status"))) {
                count++;
            }
        }
        return count;
    }

    private void applyMutableFields(AgentProfile profile, Map<String, Object> body) {
        if (body == null) {
            return;
        }
        if (body.containsKey("display_name")) profile.setDisplayName(string(body, "display_name"));
        if (body.containsKey("description")) profile.setDescription(string(body, "description"));
        if (body.containsKey("role_prompt")) profile.setRolePrompt(string(body, "role_prompt"));
        if (body.containsKey("default_model"))
            profile.setDefaultModel(string(body, "default_model"));
        if (body.containsKey("allowed_tools_json"))
            profile.setAllowedToolsJson(jsonString(body.get("allowed_tools_json")));
        if (body.containsKey("skills_json"))
            profile.setSkillsJson(jsonString(body.get("skills_json")));
        if (body.containsKey("memory")) profile.setMemory(string(body, "memory"));
        if (body.containsKey("enabled"))
            profile.setEnabled(
                    Boolean.TRUE.equals(body.get("enabled"))
                            || "true".equalsIgnoreCase(String.valueOf(body.get("enabled"))));
    }

    private String jsonString(Object value) {
        if (value == null) {
            return "[]";
        }
        if (value instanceof String) {
            return String.valueOf(value);
        }
        return ONode.serialize(value);
    }

    private String string(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
