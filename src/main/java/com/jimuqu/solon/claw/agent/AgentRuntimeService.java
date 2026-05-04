package com.jimuqu.solon.claw.agent;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.noear.snack4.ONode;

/** 解析会话当前激活 Agent，并冻结运行路径与角色配置。 */
public class AgentRuntimeService {
    private static final String VALID_NAME_PATTERN = "^[a-zA-Z0-9][a-zA-Z0-9._-]*$";

    private final AppConfig appConfig;
    private final AgentProfileRepository repository;

    public AgentRuntimeService(AppConfig appConfig, AgentProfileRepository repository) {
        this.appConfig = appConfig;
        this.repository = repository;
    }

    public AgentRuntimeScope resolve(SessionRecord session) throws Exception {
        String active = session == null ? null : session.getActiveAgentName();
        return resolveByName(active);
    }

    public AgentRuntimeScope resolveByName(String rawName) throws Exception {
        String name = normalizeName(rawName);
        if (AgentRuntimeScope.DEFAULT_AGENT.equals(name)) {
            return defaultScope();
        }

        AgentProfile profile = repository.findByName(name);
        if (profile == null) {
            throw new IllegalStateException("未找到 Agent：" + name);
        }
        if (!profile.isEnabled()) {
            throw new IllegalStateException("Agent 已停用：" + name);
        }
        AgentRuntimeScope scope = namedScope(profile);
        scope.setSnapshotJson(toSnapshot(scope));
        return scope;
    }

    public AgentRuntimeScope defaultScope() {
        AgentRuntimeScope scope = new AgentRuntimeScope();
        scope.setAgentName(AgentRuntimeScope.DEFAULT_AGENT);
        scope.setDisplayName("默认 Agent");
        scope.setDescription("映射 runtime 根目录的默认行为");
        scope.setDefaultAgent(true);
        scope.setAgentHomeDir(appConfig.getRuntime().getHome());
        scope.setWorkspaceDir(appConfig.getRuntime().getHome());
        scope.setSkillsDir(appConfig.getRuntime().getSkillsDir());
        scope.setCacheDir(appConfig.getRuntime().getCacheDir());
        scope.setAllowedToolsJson("[]");
        scope.setSkillsJson("[]");
        scope.setSnapshotJson(toSnapshot(scope));
        return scope;
    }

    public AgentProfile create(String name, String rolePrompt) throws Exception {
        validateName(name);
        String normalized = normalizeName(name);
        rejectDefault(normalized);
        AgentProfile existing = repository.findByName(normalized);
        if (existing != null) {
            return existing;
        }

        long now = System.currentTimeMillis();
        AgentProfile profile = new AgentProfile();
        profile.setAgentName(normalized);
        profile.setDisplayName(normalized);
        profile.setDescription("");
        profile.setRolePrompt(StrUtil.blankToDefault(rolePrompt, "你是一个可复用的任务 Agent。"));
        profile.setDefaultModel("");
        profile.setAllowedToolsJson("[]");
        profile.setSkillsJson("[]");
        profile.setMemory("");
        profile.setEnabled(true);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        AgentProfile saved = repository.save(profile);
        ensureNamedDirs(normalized);
        return saved;
    }

    public AgentProfile save(AgentProfile profile) throws Exception {
        validateName(profile.getAgentName());
        rejectDefault(profile.getAgentName());
        long now = System.currentTimeMillis();
        if (profile.getCreatedAt() <= 0) {
            profile.setCreatedAt(now);
        }
        profile.setUpdatedAt(now);
        profile.setEnabled(profile.isEnabled());
        AgentProfile saved = repository.save(profile);
        ensureNamedDirs(saved.getAgentName());
        return saved;
    }

    public void delete(String name) throws Exception {
        String normalized = normalizeName(name);
        rejectDefault(normalized);
        repository.deleteByName(normalized);
    }

    public void markUsed(String name) throws Exception {
        String normalized = normalizeName(name);
        if (AgentRuntimeScope.DEFAULT_AGENT.equals(normalized)) {
            return;
        }
        AgentProfile profile = repository.findByName(normalized);
        if (profile == null) {
            return;
        }
        profile.setLastUsedAt(System.currentTimeMillis());
        profile.setUpdatedAt(System.currentTimeMillis());
        repository.save(profile);
    }

    public File agentRoot(String name) {
        return FileUtil.file(appConfig.getRuntime().getHome(), "agents", normalizeName(name));
    }

    public String normalizeName(String name) {
        return AgentRuntimeScope.normalizeName(name);
    }

    public void validateName(String name) {
        String normalized = normalizeName(name);
        if (AgentRuntimeScope.DEFAULT_AGENT.equals(normalized)) {
            return;
        }
        if (!normalized.matches(VALID_NAME_PATTERN)) {
            throw new IllegalArgumentException("Agent 名称只能包含字母、数字、点、下划线和短横线，且必须以字母或数字开头。");
        }
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            throw new IllegalArgumentException("Agent 名称不能包含路径片段。");
        }
    }

    public void rejectDefault(String name) {
        if (AgentRuntimeScope.DEFAULT_AGENT.equalsIgnoreCase(normalizeName(name))) {
            throw new IllegalArgumentException("default 是内置 Agent，映射 runtime 根目录，不允许创建、编辑、删除或克隆。");
        }
    }

    private AgentRuntimeScope namedScope(AgentProfile profile) {
        File root = agentRoot(profile.getAgentName());
        File workspace = FileUtil.file(root, "workspace");
        File skills = FileUtil.file(root, "skills");
        File cache = FileUtil.file(root, "cache");
        File agentFile = FileUtil.file(root, "AGENT.md");
        File memoryFile = FileUtil.file(root, "MEMORY.md");
        FileUtil.mkdir(workspace);
        FileUtil.mkdir(skills);
        FileUtil.mkdir(cache);

        AgentRuntimeScope scope = new AgentRuntimeScope();
        scope.setAgentName(profile.getAgentName());
        scope.setDisplayName(
                StrUtil.blankToDefault(profile.getDisplayName(), profile.getAgentName()));
        scope.setDescription(profile.getDescription());
        scope.setDefaultAgent(false);
        scope.setRolePrompt(profile.getRolePrompt());
        scope.setDefaultModel(profile.getDefaultModel());
        scope.setAllowedToolsJson(StrUtil.blankToDefault(profile.getAllowedToolsJson(), "[]"));
        scope.setSkillsJson(StrUtil.blankToDefault(profile.getSkillsJson(), "[]"));
        scope.setMemory(profile.getMemory());
        scope.setAgentHomeDir(root.getAbsolutePath());
        scope.setWorkspaceDir(workspace.getAbsolutePath());
        scope.setSkillsDir(skills.getAbsolutePath());
        scope.setCacheDir(cache.getAbsolutePath());
        scope.setAgentFilePath(agentFile.getAbsolutePath());
        scope.setMemoryFilePath(memoryFile.getAbsolutePath());
        return scope;
    }

    private void ensureNamedDirs(String name) {
        File root = agentRoot(name);
        FileUtil.mkdir(FileUtil.file(root, "workspace"));
        FileUtil.mkdir(FileUtil.file(root, "skills"));
        FileUtil.mkdir(FileUtil.file(root, "cache"));
    }

    private String toSnapshot(AgentRuntimeScope scope) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("agent_name", scope.getEffectiveName());
        map.put("display_name", scope.getDisplayName());
        map.put("description", scope.getDescription());
        map.put("default_agent", Boolean.valueOf(scope.isDefaultAgentName()));
        map.put("role_prompt", scope.getRolePrompt());
        map.put("default_model", scope.getDefaultModel());
        map.put("allowed_tools_json", scope.getAllowedToolsJson());
        map.put("skills_json", scope.getSkillsJson());
        map.put("memory", scope.getMemory());
        map.put("agent_home_dir", scope.getAgentHomeDir());
        map.put("workspace_dir", scope.getWorkspaceDir());
        map.put("skills_dir", scope.getSkillsDir());
        map.put("cache_dir", scope.getCacheDir());
        map.put("agent_file_path", scope.getAgentFilePath());
        map.put("memory_file_path", scope.getMemoryFilePath());
        map.put("created_at", Long.valueOf(System.currentTimeMillis()));
        return ONode.serialize(map);
    }
}
