package com.jimuqu.solon.claw.agent;

import cn.hutool.core.util.StrUtil;

/** 单轮运行开始时冻结的 Agent 运行范围。 */
public class AgentRuntimeScope {
    public static final String DEFAULT_AGENT = "default";

    private String agentName = DEFAULT_AGENT;
    private String displayName = "默认 Agent";
    private String description;
    private boolean defaultAgent = true;
    private String rolePrompt;
    private String defaultModel;
    private String allowedToolsJson = "[]";
    private String skillsJson = "[]";
    private String memory;
    private String agentHomeDir;
    private String workspaceDir;
    private String skillsDir;
    private String cacheDir;
    private String agentFilePath;
    private String memoryFilePath;
    private String snapshotJson;

    public static String normalizeName(String name) {
        String normalized = StrUtil.nullToEmpty(name).trim();
        if (StrUtil.isBlank(normalized) || DEFAULT_AGENT.equalsIgnoreCase(normalized)) {
            return DEFAULT_AGENT;
        }
        return normalized;
    }

    public boolean isDefaultAgentName() {
        return defaultAgent || DEFAULT_AGENT.equalsIgnoreCase(agentName);
    }

    public String getEffectiveName() {
        return normalizeName(agentName);
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = normalizeName(agentName);
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isDefaultAgent() {
        return defaultAgent;
    }

    public void setDefaultAgent(boolean defaultAgent) {
        this.defaultAgent = defaultAgent;
    }

    public String getRolePrompt() {
        return rolePrompt;
    }

    public void setRolePrompt(String rolePrompt) {
        this.rolePrompt = rolePrompt;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public String getAllowedToolsJson() {
        return allowedToolsJson;
    }

    public void setAllowedToolsJson(String allowedToolsJson) {
        this.allowedToolsJson = allowedToolsJson;
    }

    public String getSkillsJson() {
        return skillsJson;
    }

    public void setSkillsJson(String skillsJson) {
        this.skillsJson = skillsJson;
    }

    public String getMemory() {
        return memory;
    }

    public void setMemory(String memory) {
        this.memory = memory;
    }

    public String getAgentHomeDir() {
        return agentHomeDir;
    }

    public void setAgentHomeDir(String agentHomeDir) {
        this.agentHomeDir = agentHomeDir;
    }

    public String getWorkspaceDir() {
        return workspaceDir;
    }

    public void setWorkspaceDir(String workspaceDir) {
        this.workspaceDir = workspaceDir;
    }

    public String getSkillsDir() {
        return skillsDir;
    }

    public void setSkillsDir(String skillsDir) {
        this.skillsDir = skillsDir;
    }

    public String getCacheDir() {
        return cacheDir;
    }

    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    public String getAgentFilePath() {
        return agentFilePath;
    }

    public void setAgentFilePath(String agentFilePath) {
        this.agentFilePath = agentFilePath;
    }

    public String getMemoryFilePath() {
        return memoryFilePath;
    }

    public void setMemoryFilePath(String memoryFilePath) {
        this.memoryFilePath = memoryFilePath;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }
}
