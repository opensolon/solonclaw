package com.jimuqu.solonclaw.skill;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solonclaw.config.WorkspaceConfig;
import com.jimuqu.solonclaw.tool.ToolRegistry;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.chat.tool.MethodToolProvider;
import org.noear.solon.ai.chat.skill.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skills 管理器
 * <p>
 * 管理基于 JSON 配置的动态 Skills
 * 支持热加载、启用/禁用、REST API 管理
 *
 * @author SolonClaw
 */
@Component
public class SkillsManager {

    private static final Logger log = LoggerFactory.getLogger(SkillsManager.class);

    @Inject
    private WorkspaceConfig.WorkspaceInfo workspaceInfo;

    @Inject
    private ToolRegistry toolRegistry;

    /**
     * 已注册的技能：名称 -> Skill
     */
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    /**
     * 技能配置：名称 -> DynamicSkill.SkillConfig
     */
    private final Map<String, DynamicSkill.SkillConfig> skillConfigs = new ConcurrentHashMap<>();

    /**
     * 初始化时加载技能
     */
    @Init
    public void init() {
        loadSkills();
        log.info("SkillsManager 初始化完成，已加载 {} 个技能", skills.size());
    }

    /**
     * 加载 workspace/skills/ 目录下的所有技能
     */
    public void loadSkills() {
        try {
            Path skillsDir = workspaceInfo.skillsDir();
            if (!Files.exists(skillsDir)) {
                Files.createDirectories(skillsDir);
                log.info("创建技能目录: {}", skillsDir);
                return;
            }

            // 清空现有技能
            skills.clear();
            skillConfigs.clear();

            // 扫描所有 JSON 文件
            try (var stream = Files.list(skillsDir)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                      .forEach(this::loadSkillFile);
            }

            log.info("从目录加载了 {} 个技能: {}", skills.size(), skillsDir);
        } catch (Exception e) {
            log.error("加载技能失败", e);
        }
    }

    /**
     * 加载单个技能文件
     */
    private void loadSkillFile(Path skillFile) {
        try {
            String content = Files.readString(skillFile);
            DynamicSkill.SkillConfig config = parseSkillConfig(content);

            if (ObjUtil.isNull(config) || StrUtil.isBlank(config.name())) {
                log.warn("无效的技能配置文件: {}", skillFile);
                return;
            }

            // 构建技能
            Skill skill = buildSkill(config);
            if (skill != null) {
                skills.put(config.name(), skill);
                skillConfigs.put(config.name(), config);
                log.debug("加载技能: {} - {}", config.name(), config.description());
            }
        } catch (Exception e) {
            log.error("加载技能文件失败: {}", skillFile, e);
        }
    }

    /**
     * 构建技能
     */
    private Skill buildSkill(DynamicSkill.SkillConfig config) {
        if (!config.enabled()) {
            log.debug("技能已禁用: {}", config.name());
            return null;
        }

        // 解析工具
        List<FunctionTool> toolList = resolveTools(config.tools());

        // 创建动态技能
        return new DynamicSkill(config, toolList);
    }

    /**
     * 解析工具列表
     */
    private List<FunctionTool> resolveTools(List<String> toolNames) {
        List<FunctionTool> result = new ArrayList<>();

        if (CollUtil.isEmpty(toolNames)) {
            return result;
        }

        for (String toolName : toolNames) {
            try {
                // 尝试从 ToolRegistry 查找工具
                var toolInfo = toolRegistry.getTool(toolName);
                if (toolInfo != null) {
                    // 使用 MethodToolProvider 从方法创建工具
                    var provider = new MethodToolProvider(toolInfo.bean());
                    var tools = provider.getTools();
                    for (FunctionTool tool : tools) {
                        if (tool.name().equals(toolInfo.name())) {
                            result.add(tool);
                            break;
                        }
                    }
                } else {
                    log.warn("未找到工具: {}", toolName);
                }
            } catch (Exception e) {
                log.warn("解析工具失败: {}", toolName, e);
            }
        }

        return result;
    }

    /**
     * 解析技能配置 JSON
     */
    private DynamicSkill.SkillConfig parseSkillConfig(String json) {
        try {
            String name = extractStringValue(json, "name");
            String description = extractStringValue(json, "description");
            String instruction = extractStringValue(json, "instruction");
            String condition = extractStringValue(json, "condition");
            boolean enabled = extractBooleanValue(json, "enabled", true);
            List<String> tools = extractArrayValue(json, "tools");

            return new DynamicSkill.SkillConfig(name, description, instruction, condition, tools, enabled);
        } catch (Exception e) {
            log.warn("解析技能配置失败", e);
            return null;
        }
    }

    /**
     * 添加技能
     */
    public boolean addSkill(DynamicSkill.SkillConfig config) {
        if (ObjUtil.isNull(config) || StrUtil.isBlank(config.name())) {
            return false;
        }

        if (skills.containsKey(config.name())) {
            log.warn("技能已存在: {}", config.name());
            return false;
        }

        Skill skill = buildSkill(config);
        if (skill != null) {
            skills.put(config.name(), skill);
            skillConfigs.put(config.name(), config);
            saveSkillConfig(config);
            log.info("添加技能: {}", config.name());
            triggerAgentReload();
            return true;
        }
        return false;
    }

    /**
     * 更新技能
     */
    public boolean updateSkill(String name, DynamicSkill.SkillConfig config) {
        if (!skills.containsKey(name)) {
            log.warn("技能不存在: {}", name);
            return false;
        }

        // 删除旧的
        skills.remove(name);
        skillConfigs.remove(name);

        // 添加新的
        Skill skill = buildSkill(config);
        if (skill != null) {
            skills.put(config.name(), skill);
            skillConfigs.put(config.name(), config);
            saveSkillConfig(config);

            // 如果名称变了，删除旧文件
            if (!StrUtil.equals(name, config.name())) {
                deleteSkillFile(name);
            }

            log.info("更新技能: {} -> {}", name, config.name());
            triggerAgentReload();
            return true;
        }
        return false;
    }

    /**
     * 删除技能
     */
    public boolean removeSkill(String name) {
        if (!skills.containsKey(name)) {
            log.warn("技能不存在: {}", name);
            return false;
        }

        skills.remove(name);
        skillConfigs.remove(name);
        deleteSkillFile(name);
        log.info("删除技能: {}", name);
        triggerAgentReload();
        return true;
    }

    /**
     * 启用/禁用技能
     */
    public boolean setSkillEnabled(String name, boolean enabled) {
        DynamicSkill.SkillConfig config = skillConfigs.get(name);
        if (config == null) {
            log.warn("技能不存在: {}", name);
            return false;
        }

        // 创建新配置
        DynamicSkill.SkillConfig newConfig = new DynamicSkill.SkillConfig(
                config.name(),
                config.description(),
                config.instruction(),
                config.condition(),
                config.tools(),
                enabled
        );

        // 更新
        skillConfigs.put(name, newConfig);

        // 重新构建技能
        if (enabled) {
            Skill skill = buildSkill(newConfig);
            if (skill != null) {
                skills.put(name, skill);
            }
        } else {
            skills.remove(name);
        }

        saveSkillConfig(newConfig);
        log.info("{} 技能: {}", enabled ? "启用" : "禁用", name);
        triggerAgentReload();
        return true;
    }

    /**
     * 触发 Agent 重载
     * <p>
     * 使用延迟加载方式获取 AgentService，避免循环依赖
     */
    private void triggerAgentReload() {
        try {
            // 通过 Solon 上下文延迟获取 AgentService，避免循环依赖
            var context = org.noear.solon.Solon.context();
            var agentService = context.getBean(com.jimuqu.solonclaw.agent.AgentService.class);
            if (agentService != null) {
                agentService.reloadAgent();
                log.debug("已触发 Agent 重载");
            }
        } catch (Exception e) {
            log.debug("触发 Agent 重载失败（可能 AgentService 尚未初始化或不存在）");
        }
    }

    /**
     * 获取所有技能
     */
    public List<Skill> getSkills() {
        return new ArrayList<>(skills.values());
    }

    /**
     * 获取所有技能配置
     */
    public List<DynamicSkill.SkillConfig> getSkillConfigs() {
        return new ArrayList<>(skillConfigs.values());
    }

    /**
     * 获取技能
     */
    public Skill getSkill(String name) {
        return skills.get(name);
    }

    /**
     * 获取技能配置
     */
    public DynamicSkill.SkillConfig getSkillConfig(String name) {
        return skillConfigs.get(name);
    }

    /**
     * 检查技能是否存在
     */
    public boolean hasSkill(String name) {
        return skills.containsKey(name);
    }

    /**
     * 保存技能配置到文件
     */
    private void saveSkillConfig(DynamicSkill.SkillConfig config) {
        try {
            Path skillsDir = workspaceInfo.skillsDir();
            Files.createDirectories(skillsDir);

            Path skillFile = skillsDir.resolve(config.name() + ".json");

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"name\": \"").append(escapeJson(config.name())).append("\",\n");
            sb.append("  \"description\": \"").append(escapeJson(config.description())).append("\",\n");
            sb.append("  \"instruction\": \"").append(escapeJson(config.instruction())).append("\",\n");
            sb.append("  \"condition\": \"").append(escapeJson(config.condition())).append("\",\n");
            sb.append("  \"enabled\": ").append(config.enabled()).append(",\n");
            sb.append("  \"tools\": [");
            if (CollUtil.isNotEmpty(config.tools())) {
                for (int i = 0; i < config.tools().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append("\"").append(escapeJson(config.tools().get(i))).append("\"");
                }
            }
            sb.append("]\n");
            sb.append("}");

            Files.writeString(skillFile, sb.toString());
            log.debug("技能配置已保存: {}", skillFile);
        } catch (Exception e) {
            log.error("保存技能配置失败", e);
        }
    }

    /**
     * 删除技能文件
     */
    private void deleteSkillFile(String name) {
        try {
            Path skillsDir = workspaceInfo.skillsDir();
            Path skillFile = skillsDir.resolve(name + ".json");
            if (Files.exists(skillFile)) {
                Files.delete(skillFile);
                log.debug("删除技能文件: {}", skillFile);
            }
        } catch (Exception e) {
            log.error("删除技能文件失败: {}", name, e);
        }
    }

    // JSON 辅助方法
    private String escapeJson(String s) {
        if (StrUtil.isBlank(s)) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String extractStringValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) start++;
        if (start >= json.length() || json.charAt(start) != '"') return null;
        start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != '"') {
            if (json.charAt(end) == '\\') end++;
            end++;
        }
        return json.substring(start, end).replace("\\\"", "\"").replace("\\n", "\n");
    }

    private boolean extractBooleanValue(String json, String key, boolean defaultValue) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return defaultValue;
        start += pattern.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) start++;
        if (json.substring(start).startsWith("true")) return true;
        if (json.substring(start).startsWith("false")) return false;
        return defaultValue;
    }

    private List<String> extractArrayValue(String json, String key) {
        List<String> result = new ArrayList<>();
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return result;
        start += pattern.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) start++;
        if (start >= json.length() || json.charAt(start) != '[') return result;
        start++;

        int end = json.indexOf("]", start);
        if (end < 0) return result;

        String arrayContent = json.substring(start, end);
        for (String item : arrayContent.split(",")) {
            item = item.trim();
            if (item.startsWith("\"") && item.endsWith("\"")) {
                result.add(item.substring(1, item.length() - 1));
            }
        }
        return result;
    }
}