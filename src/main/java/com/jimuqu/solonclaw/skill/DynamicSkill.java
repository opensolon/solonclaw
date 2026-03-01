package com.jimuqu.solonclaw.skill;

import org.noear.solon.ai.chat.prompt.Prompt;
import org.noear.solon.ai.chat.skill.Skill;
import org.noear.solon.ai.chat.skill.SkillMetadata;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.chat.tool.MethodToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Function;

/**
 * 动态构建的 Skill
 * <p>
 * 基于 JSON 配置文件动态构建的技能
 *
 * @author SolonClaw
 */
public class DynamicSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(DynamicSkill.class);

    private final SkillConfig config;
    private final SkillMetadata metadata;
    private final List<FunctionTool> tools;
    private final Predicate<Prompt> supportPredicate;
    private final Function<Prompt, String> instructionProvider;

    public DynamicSkill(SkillConfig config, List<FunctionTool> tools) {
        this.config = config;
        this.tools = tools != null ? tools : Collections.emptyList();
        this.metadata = new SkillMetadata(config.name(), config.description());

        // 构建准入检查谓词
        this.supportPredicate = buildSupportPredicate(config.condition());

        // 构建指令提供器
        this.instructionProvider = buildInstructionProvider(config.instruction());
    }

    @Override
    public SkillMetadata metadata() {
        return metadata;
    }

    @Override
    public boolean isSupported(Prompt prompt) {
        if (supportPredicate == null) {
            return true;
        }
        try {
            return supportPredicate.test(prompt);
        } catch (Exception e) {
            log.warn("技能准入检查失败: {}", config.name(), e);
            return false;
        }
    }

    @Override
    public String getInstruction(Prompt prompt) {
        if (instructionProvider == null) {
            return config.instruction();
        }
        try {
            return instructionProvider.apply(prompt);
        } catch (Exception e) {
            log.warn("获取指令失败: {}", config.name(), e);
            return config.instruction();
        }
    }

    @Override
    public Collection<FunctionTool> getTools(Prompt prompt) {
        return tools;
    }

    /**
     * 构建准入检查谓词
     */
    private Predicate<Prompt> buildSupportPredicate(String condition) {
        if (condition == null || condition.isEmpty()) {
            return null;
        }

        return prompt -> {
            String content = prompt.getUserContent();
            if (content == null) {
                return false;
            }

            // 解析条件表达式
            return evaluateCondition(condition, content);
        };
    }

    /**
     * 构建指令提供器
     */
    private Function<Prompt, String> buildInstructionProvider(String instruction) {
        if (instruction == null || instruction.isEmpty()) {
            return null;
        }

        // 检查是否包含模板变量
        if (!instruction.contains("${")) {
            return null; // 静态指令，返回 null 使用默认
        }

        return prompt -> replaceVariables(instruction, prompt);
    }

    /**
     * 评估条件表达式
     */
    private boolean evaluateCondition(String condition, String content) {
        // 支持 prompt.contains('关键词') 格式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("contains\\('([^']+)'\\)");
        java.util.regex.Matcher matcher = pattern.matcher(condition);

        boolean isOrCondition = condition.contains("||");
        boolean isAndCondition = condition.contains("&&");
        List<Boolean> results = new ArrayList<>();

        while (matcher.find()) {
            String keyword = matcher.group(1);
            results.add(content.contains(keyword));
        }

        if (results.isEmpty()) {
            return true;
        }

        if (isAndCondition) {
            return results.stream().allMatch(r -> r);
        } else {
            return results.stream().anyMatch(r -> r);
        }
    }

    /**
     * 替换模板变量
     */
    private String replaceVariables(String template, Prompt prompt) {
        if (template == null) return "";

        String result = template;

        // 替换 ${attr.xxx} 格式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{attr\\.([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(result);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String attrName = matcher.group(1);
            Object value = prompt.attr(attrName);
            matcher.appendReplacement(sb, value != null ? value.toString() : "");
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 技能配置
     */
    public record SkillConfig(
            String name,
            String description,
            String instruction,
            String condition,
            List<String> tools,
            boolean enabled
    ) {
        public SkillConfig {
            enabled = enabled != false; // 默认 true
        }
    }
}