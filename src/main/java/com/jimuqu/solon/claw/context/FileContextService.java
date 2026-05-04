package com.jimuqu.solon.claw.context;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.agent.AgentRuntimeScope;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.repository.GlobalSettingRepository;
import com.jimuqu.solon.claw.core.service.ContextService;
import com.jimuqu.solon.claw.core.service.MemoryManager;
import com.jimuqu.solon.claw.support.constants.AgentSettingConstants;
import com.jimuqu.solon.claw.support.constants.ContextFileConstants;

/** 基于文件系统拼装系统提示词的上下文服务。 */
public class FileContextService implements ContextService {
    /** 应用配置。 */
    private final AppConfig appConfig;

    /** 本地技能服务。 */
    private final LocalSkillService localSkillService;

    /** 长期记忆服务。 */
    private final MemoryManager memoryManager;

    /** 全局设置仓储。 */
    private final GlobalSettingRepository globalSettingRepository;

    private final PersonaWorkspaceService personaWorkspaceService;

    /** 构造文件上下文服务。 */
    public FileContextService(
            AppConfig appConfig,
            LocalSkillService localSkillService,
            MemoryManager memoryManager,
            GlobalSettingRepository globalSettingRepository,
            PersonaWorkspaceService personaWorkspaceService) {
        this.appConfig = appConfig;
        this.localSkillService = localSkillService;
        this.memoryManager = memoryManager;
        this.globalSettingRepository = globalSettingRepository;
        this.personaWorkspaceService = personaWorkspaceService;
        FileUtil.mkdir(appConfig.getRuntime().getContextDir());
    }

    /**
     * 组合 AGENTS、MEMORY、USER 与已启用技能内容。
     *
     * @param sourceKey 来源键
     * @return 系统提示词
     */
    @Override
    public String buildSystemPrompt(String sourceKey) {
        return buildSystemPrompt(sourceKey, null);
    }

    @Override
    public String buildSystemPrompt(String sourceKey, AgentRuntimeScope agentScope) {
        StringBuilder buffer = new StringBuilder();
        appendWorkspaceFile(buffer, ContextFileConstants.KEY_AGENTS, "Workspace Rules");
        appendWorkspaceFile(buffer, ContextFileConstants.KEY_SOUL, "Soul");
        appendWorkspaceFile(buffer, ContextFileConstants.KEY_IDENTITY, "Identity");
        appendWorkspaceFile(buffer, ContextFileConstants.KEY_USER, "User");
        appendWorkspaceFile(buffer, ContextFileConstants.KEY_TOOLS, "Tools");
        appendWorkspaceFile(buffer, ContextFileConstants.KEY_HEARTBEAT, "Heartbeat");
        appendPersonality(buffer);
        appendMemoryBlock(buffer, sourceKey);
        appendAgentBlock(buffer, agentScope);

        try {
            String skillPrompt =
                    agentScope == null
                            ? localSkillService.renderSkillIndexPrompt(sourceKey)
                            : localSkillService.renderSkillIndexPrompt(sourceKey, agentScope);
            if (StrUtil.isNotBlank(skillPrompt)) {
                buffer.append("\n\n").append(skillPrompt);
            }
        } catch (Exception e) {
            buffer.append("\n\n[Enabled Skills]\nFailed to load local skills: ")
                    .append(e.getMessage());
        }

        return buffer.toString().trim();
    }

    private void appendAgentBlock(StringBuilder buffer, AgentRuntimeScope agentScope) {
        if (agentScope == null || agentScope.isDefaultAgentName()) {
            return;
        }
        appendBlock(
                buffer,
                "Agent",
                "name="
                        + agentScope.getEffectiveName()
                        + "\nworkspace="
                        + StrUtil.nullToEmpty(agentScope.getWorkspaceDir()));
        appendBlock(buffer, "Agent Role", agentScope.getRolePrompt());
        appendBlock(buffer, "Agent File", readIfExists(agentScope.getAgentFilePath()));
        appendBlock(
                buffer,
                "Agent Memory",
                joinNonBlank(agentScope.getMemory(), readIfExists(agentScope.getMemoryFilePath())));
    }

    private String readIfExists(String path) {
        if (StrUtil.isBlank(path)) {
            return "";
        }
        try {
            java.io.File file = FileUtil.file(path);
            return file.exists() && file.isFile() ? FileUtil.readUtf8String(file) : "";
        } catch (Exception e) {
            return "Failed to load file: " + e.getMessage();
        }
    }

    private String joinNonBlank(String left, String right) {
        if (StrUtil.isBlank(left)) {
            return StrUtil.nullToEmpty(right);
        }
        if (StrUtil.isBlank(right)) {
            return StrUtil.nullToEmpty(left);
        }
        return left.trim() + "\n\n" + right.trim();
    }

    /** 计算运行时上下文文件路径。 */
    private void appendPersonality(StringBuilder buffer) {
        try {
            String active =
                    globalSettingRepository == null
                            ? null
                            : globalSettingRepository.get(AgentSettingConstants.ACTIVE_PERSONALITY);
            if (StrUtil.isBlank(active)) {
                return;
            }
            AppConfig.PersonalityConfig personality =
                    appConfig.getAgent().getPersonalities().get(active);
            if (personality == null) {
                return;
            }
            String prompt = personality.toPrompt();
            if (StrUtil.isBlank(prompt)) {
                return;
            }
            appendBlock(buffer, "Personality: " + active, prompt);
        } catch (Exception e) {
            appendBlock(
                    buffer, "Personality", "Failed to load active personality: " + e.getMessage());
        }
    }

    /** 追加记忆管理器提供的系统提示块。 */
    private void appendMemoryBlock(StringBuilder buffer, String sourceKey) {
        try {
            appendBlock(
                    buffer,
                    "Memory Manager",
                    memoryManager == null ? "" : memoryManager.buildSystemPrompt(sourceKey));
        } catch (Exception e) {
            appendBlock(
                    buffer, "Memory Manager", "Failed to load memory context: " + e.getMessage());
        }
    }

    /** 按优先级追加上下文文件内容。 */
    private void appendWorkspaceFile(StringBuilder buffer, String key, String label) {
        String content = personaWorkspaceService.readPromptBody(key);
        if (StrUtil.isBlank(content)) {
            return;
        }
        appendBlock(buffer, label, content);
    }

    /** 追加指定文本块。 */
    private void appendBlock(StringBuilder buffer, String label, String content) {
        if (StrUtil.isBlank(content)) {
            return;
        }
        if (buffer.length() > 0) {
            buffer.append("\n\n");
        }
        buffer.append("[").append(label).append("]\n").append(content.trim());
    }
}
