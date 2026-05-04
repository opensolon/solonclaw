package com.jimuqu.solon.claw.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import java.util.Arrays;
import java.util.List;
import org.noear.snack4.ONode;

/** 用户侧 Agent 管理服务。底层仍复用 agent_profiles 表，不暴露 Profile 概念。 */
public class AgentProfileService {
    private final AgentProfileRepository repository;
    private final AgentRuntimeService runtimeService;

    public AgentProfileService(AgentProfileRepository repository) {
        this(repository, null);
    }

    public AgentProfileService(
            AgentProfileRepository repository, AgentRuntimeService runtimeService) {
        this.repository = repository;
        this.runtimeService = runtimeService;
    }

    public AgentProfile createAgent(String agentName, String rolePrompt) throws Exception {
        if (runtimeService != null) {
            return runtimeService.create(agentName, rolePrompt);
        }
        validateName(agentName);
        rejectDefault(agentName);
        AgentProfile existing = repository.findByName(agentName);
        if (existing != null) {
            return existing;
        }
        long now = System.currentTimeMillis();
        AgentProfile profile = new AgentProfile();
        profile.setAgentName(agentName);
        profile.setDisplayName(agentName);
        profile.setRolePrompt(StrUtil.blankToDefault(rolePrompt, "你是一个可复用的任务 Agent。"));
        profile.setDefaultModel("");
        profile.setAllowedToolsJson("[]");
        profile.setSkillsJson("[]");
        profile.setMemory("");
        profile.setEnabled(true);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        return repository.save(profile);
    }

    public AgentProfile ensureDefault(String agentName, String rolePrompt) throws Exception {
        return createAgent(agentName, rolePrompt);
    }

    public String handleCommand(String args) throws Exception {
        return handleCommand(args, null, null);
    }

    public String handleCommand(String args, SessionRepository sessionRepository, String sourceKey)
            throws Exception {
        String raw = StrUtil.nullToEmpty(args).trim();
        if (StrUtil.isBlank(raw)) {
            return formatList(currentSession(sessionRepository, sourceKey));
        }
        String[] parts = raw.split("\\s+", 3);
        String action = parts[0].toLowerCase();

        if ("list".equals(action)) return formatList(currentSession(sessionRepository, sourceKey));
        if ("use".equals(action))
            return switchAgent(parts.length > 1 ? parts[1] : "", sessionRepository, sourceKey);
        if ("create".equals(action)) return create(parts);
        if ("show".equals(action))
            return formatShow(resolveForShow(parts.length > 1 ? parts[1] : ""));
        if ("model".equals(action)) return updateModel(parts);
        if ("tools".equals(action)) return updateTools(parts);
        if ("skills".equals(action)) return updateSkills(parts);
        if ("memory".equals(action)) return appendMemory(parts);
        if ("delete".equals(action) || "remove".equals(action)) return delete(parts);
        if ("clone".equals(action)) return "default 是内置 Agent，命名 Agent 第一版不提供克隆。";

        if (parts.length == 1) {
            return switchAgent(parts[0], sessionRepository, sourceKey);
        }
        return "用法：/agent、/agent <name>、/agent use <name>、/agent create <name> [角色]、/agent show|model|tools|skills|memory <name> ...";
    }

    public AgentProfile findByName(String name) throws Exception {
        return repository.findByName(name);
    }

    public List<AgentProfile> listAll() throws Exception {
        return repository.listAll();
    }

    public AgentProfile save(AgentProfile profile) throws Exception {
        rejectDefault(profile.getAgentName());
        if (runtimeService != null) {
            return runtimeService.save(profile);
        }
        profile.setUpdatedAt(System.currentTimeMillis());
        return repository.save(profile);
    }

    public void deleteByName(String name) throws Exception {
        rejectDefault(name);
        repository.deleteByName(name);
    }

    private String switchAgent(String name, SessionRepository sessionRepository, String sourceKey)
            throws Exception {
        if (sessionRepository == null || StrUtil.isBlank(sourceKey)) {
            return "当前入口无法切换 Agent。";
        }
        String normalized = normalizeName(name);
        if (!AgentRuntimeScope.DEFAULT_AGENT.equals(normalized)) {
            AgentProfile profile = repository.findByName(normalized);
            if (profile == null) {
                return "未找到 Agent：" + normalized + "。可使用 /agent create " + normalized + " 创建。";
            }
            if (!profile.isEnabled()) {
                return "Agent 已停用：" + normalized;
            }
        }

        SessionRecord session = sessionRepository.getBoundSession(sourceKey);
        if (session == null) {
            session = sessionRepository.bindNewSession(sourceKey);
        }
        String stored = AgentRuntimeScope.DEFAULT_AGENT.equals(normalized) ? null : normalized;
        sessionRepository.setActiveAgentName(session.getSessionId(), stored);
        if (runtimeService != null) {
            runtimeService.markUsed(normalized);
        }
        return "已切换当前会话 Agent 为：" + normalized + "。正在运行的任务不会受影响。";
    }

    private String create(String[] parts) throws Exception {
        if (parts.length < 2 || StrUtil.isBlank(parts[1])) return "用法：/agent create <name> [角色说明]";
        String role = parts.length > 2 ? parts[2] : "你是一个可复用的任务 Agent。";
        AgentProfile profile = createAgent(parts[1], role);
        return "已创建 Agent：" + profile.getAgentName();
    }

    private String updateModel(String[] parts) throws Exception {
        if (parts.length < 3 || StrUtil.hasBlank(parts[1], parts[2]))
            return "用法：/agent model <name> <model|clear>";
        if (AgentRuntimeScope.DEFAULT_AGENT.equals(normalizeName(parts[1]))) {
            return defaultMutationRejected();
        }
        AgentProfile profile = requireNamedProfile(parts[1]);
        profile.setDefaultModel(isClear(parts[2]) ? "" : parts[2].trim());
        save(profile);
        return "已更新 Agent 默认模型："
                + profile.getAgentName()
                + " -> "
                + StrUtil.blankToDefault(profile.getDefaultModel(), "全局默认");
    }

    private String updateTools(String[] parts) throws Exception {
        if (parts.length < 3 || StrUtil.hasBlank(parts[1], parts[2]))
            return "用法：/agent tools <name> <tool1,tool2>";
        if (AgentRuntimeScope.DEFAULT_AGENT.equals(normalizeName(parts[1]))) {
            return defaultMutationRejected();
        }
        AgentProfile profile = requireNamedProfile(parts[1]);
        profile.setAllowedToolsJson(csvToJson(parts[2]));
        save(profile);
        return "已更新 Agent 工具：" + profile.getAgentName();
    }

    private String updateSkills(String[] parts) throws Exception {
        if (parts.length < 3 || StrUtil.hasBlank(parts[1], parts[2]))
            return "用法：/agent skills <name> <skill1,skill2>";
        if (AgentRuntimeScope.DEFAULT_AGENT.equals(normalizeName(parts[1]))) {
            return defaultMutationRejected();
        }
        AgentProfile profile = requireNamedProfile(parts[1]);
        profile.setSkillsJson(csvToJson(parts[2]));
        save(profile);
        return "已更新 Agent 技能：" + profile.getAgentName();
    }

    private String appendMemory(String[] parts) throws Exception {
        if (parts.length < 3 || StrUtil.hasBlank(parts[1], parts[2]))
            return "用法：/agent memory <name> <记忆内容>";
        if (AgentRuntimeScope.DEFAULT_AGENT.equals(normalizeName(parts[1]))) {
            return defaultMutationRejected();
        }
        AgentProfile profile = requireNamedProfile(parts[1]);
        profile.setMemory(
                StrUtil.nullToEmpty(profile.getMemory())
                        + (StrUtil.isBlank(profile.getMemory()) ? "" : "\n")
                        + parts[2].trim());
        save(profile);
        return "已追加 Agent 记忆：" + profile.getAgentName();
    }

    private String delete(String[] parts) throws Exception {
        if (parts.length < 2 || StrUtil.isBlank(parts[1])) return "用法：/agent delete <name>";
        if (AgentRuntimeScope.DEFAULT_AGENT.equals(normalizeName(parts[1]))) {
            return defaultMutationRejected();
        }
        rejectDefault(parts[1]);
        repository.deleteByName(parts[1]);
        return "已删除 Agent：" + parts[1];
    }

    private AgentProfile requireNamedProfile(String name) throws Exception {
        rejectDefault(name);
        AgentProfile profile = repository.findByName(name);
        if (profile == null) {
            throw new IllegalStateException("未找到 Agent：" + name);
        }
        return profile;
    }

    private Object resolveForShow(String name) throws Exception {
        String normalized = normalizeName(name);
        if (AgentRuntimeScope.DEFAULT_AGENT.equals(normalized)) {
            return normalized;
        }
        return requireNamedProfile(normalized);
    }

    private String formatList(SessionRecord session) throws Exception {
        List<AgentProfile> agents = repository.listAll();
        String active = normalizeName(session == null ? null : session.getActiveAgentName());
        StringBuilder builder = new StringBuilder("Agents：");
        builder.append("\n- default（内置，runtime 根目录）");
        if (AgentRuntimeScope.DEFAULT_AGENT.equals(active)) builder.append(" *当前*");
        if (CollUtil.isNotEmpty(agents)) {
            for (AgentProfile agent : agents) {
                builder.append("\n- ").append(agent.getAgentName());
                if (StrUtil.isNotBlank(agent.getDisplayName())
                        && !StrUtil.equals(agent.getDisplayName(), agent.getAgentName())) {
                    builder.append("（").append(agent.getDisplayName()).append("）");
                }
                if (StrUtil.isNotBlank(agent.getDefaultModel()))
                    builder.append(" model=").append(agent.getDefaultModel());
                if (!agent.isEnabled()) builder.append(" 已停用");
                if (StrUtil.equals(active, agent.getAgentName())) builder.append(" *当前*");
            }
        }
        builder.append("\n使用 /agent <name> 或 /agent default 切换当前会话。");
        return builder.toString();
    }

    private String formatShow(Object value) {
        if (value instanceof String && AgentRuntimeScope.DEFAULT_AGENT.equals(value)) {
            return "Agent: default\n类型：内置默认 Agent\n位置：runtime 根目录\n说明：default 不在 runtime/agents 下管理，不允许编辑、删除或克隆。";
        }
        AgentProfile profile = (AgentProfile) value;
        return "Agent: "
                + profile.getAgentName()
                + "\n显示名: "
                + StrUtil.blankToDefault(profile.getDisplayName(), profile.getAgentName())
                + "\n说明: "
                + StrUtil.blankToDefault(profile.getDescription(), "")
                + "\n角色: "
                + StrUtil.nullToDefault(profile.getRolePrompt(), "")
                + "\n默认模型: "
                + StrUtil.blankToDefault(profile.getDefaultModel(), "全局默认")
                + "\n工具: "
                + StrUtil.blankToDefault(profile.getAllowedToolsJson(), "[]")
                + "\n技能: "
                + StrUtil.blankToDefault(profile.getSkillsJson(), "[]")
                + "\n记忆: "
                + StrUtil.blankToDefault(profile.getMemory(), "无")
                + "\n启用: "
                + profile.isEnabled();
    }

    private SessionRecord currentSession(SessionRepository sessionRepository, String sourceKey)
            throws Exception {
        if (sessionRepository == null || StrUtil.isBlank(sourceKey)) {
            return null;
        }
        return sessionRepository.getBoundSession(sourceKey);
    }

    private boolean isClear(String value) {
        return "clear".equalsIgnoreCase(value)
                || "none".equalsIgnoreCase(value)
                || "default".equalsIgnoreCase(value);
    }

    private String csvToJson(String csv) {
        if (StrUtil.isBlank(csv)) {
            return "[]";
        }
        return toJson(Arrays.asList(csv.split("\\s*,\\s*")));
    }

    private String toJson(Object value) {
        return ONode.serialize(value);
    }

    private String defaultMutationRejected() {
        return "default 是内置 Agent，映射 runtime 根目录，不允许创建、编辑、删除或克隆。请在根目录上下文或全局设置中调整默认行为。";
    }

    private String normalizeName(String name) {
        return runtimeService == null
                ? AgentRuntimeScope.normalizeName(name)
                : runtimeService.normalizeName(name);
    }

    private void validateName(String name) {
        if (runtimeService != null) {
            runtimeService.validateName(name);
        }
    }

    private void rejectDefault(String name) {
        if (runtimeService != null) {
            runtimeService.rejectDefault(name);
            return;
        }
        if (AgentRuntimeScope.DEFAULT_AGENT.equalsIgnoreCase(
                AgentRuntimeScope.normalizeName(name))) {
            throw new IllegalArgumentException("default 是内置 Agent，映射 runtime 根目录，不允许创建、编辑、删除或克隆。");
        }
    }
}
