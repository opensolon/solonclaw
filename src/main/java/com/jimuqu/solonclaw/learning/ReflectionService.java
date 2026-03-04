package com.jimuqu.solonclaw.learning;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.ChatResponse;
import org.noear.snack4.ONode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 反省服务
 * <p>
 * 负责定期执行自我反省和错误触发的反省分析
 * 从日志和经验中学习，提升 Agent 能力
 *
 * @author SolonClaw
 */
@Component
public class ReflectionService {

    private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);

    @Inject
    private KnowledgeStore knowledgeStore;

    @Inject(required = false)
    private com.jimuqu.solonclaw.logging.LogStore logStore;

    @Inject(required = false)
    private ChatModel chatModel;

    /**
     * 定时反省提示词模板
     */
    private static final String SCHEDULED_REFLECTION_PROMPT = """
        你是一个 AI Agent 的自我反省系统。请分析以下最近的日志记录，总结经验教训。

        ## 最近日志
        %s

        ## 分析要求
        1. 识别成功的模式和最佳实践
        2. 识别失败的原因和改进方向
        3. 发现可能需要的新技能
        4. 提出具体的改进建议

        ## 输出格式（JSON）
        {
            "summary": "总体总结",
            "successes": ["成功点1", "成功点2"],
            "failures": ["失败点1", "失败点2"],
            "improvements": ["改进建议1", "改进建议2"],
            "neededSkills": [{"name": "技能名", "description": "技能描述", "priority": 1-10}]
        }
        """;

    /**
     * 错误反省提示词模板
     */
    private static final String ERROR_REFLECTION_PROMPT = """
        你是一个 AI Agent 的错误分析专家。请分析以下错误，提出解决方案。

        ## 错误信息
        类型: %s
        消息: %s
        上下文: %s

        ## 分析要求
        1. 分析错误的根本原因
        2. 提出解决方案
        3. 识别需要预防的类似错误
        4. 判断是否需要新技能来处理此类错误

        ## 输出格式（JSON）
        {
            "rootCause": "根本原因分析",
            "solution": "解决方案",
            "prevention": "预防措施",
            "neededSkill": {
                "name": "技能名称",
                "description": "技能描述",
                "priority": 1-10
            }
        }
        """;

    /**
     * 执行定时反省
     * <p>
     * 分析最近的日志和经验，生成反省记录
     *
     * @param sessionId 会话ID（可选，用于特定会话的反省）
     * @return 反省记录ID
     */
    public long performScheduledReflection(String sessionId) {
        log.info("开始执行定时反省: sessionId={}", sessionId);

        try {
            // 1. 获取最近的日志
            // TODO: 实现 getRecentLogs 方法
            // List<com.jimuqu.solonclaw.logging.LogEntry> recentLogs =
            //     logStore.getRecentLogs(sessionId, 50);
            List<com.jimuqu.solonclaw.logging.LogEntry> recentLogs = List.of();

            if (CollUtil.isEmpty(recentLogs)) {
                log.debug("没有找到最近的日志，跳过反省");
                return -1;
            }

            // 2. 构建日志摘要
            String logsSummary = buildLogsSummary(recentLogs);

            // 3. 使用 AI 分析
            String fullPrompt = SCHEDULED_REFLECTION_PROMPT.replace("%s", logsSummary);
            fullPrompt = "你是 SolonClaw AI Agent 的自我反省系统。\n\n" + fullPrompt;

            ChatResponse response = chatModel.prompt(fullPrompt).call();

            String analysis = response.getContent();

            // 4. 解析 AI 响应
            Map<String, Object> reflectionData = parseJsonResponse(analysis);

            // 5. 保存反省记录
            String summary = (String) reflectionData.getOrDefault("summary", "定期反省总结");
            List<String> successes = (List<String>) reflectionData.get("successes");
            List<String> improvements = (List<String>) reflectionData.get("improvements");

            String content = buildReflectionContent(summary, successes, improvements);

            long reflectionId = knowledgeStore.saveReflection(
                sessionId,
                "scheduled_reflection",
                content,
                "基于最近 " + recentLogs.size() + " 条日志的定期反省",
                null,
                null
            );

            // 6. 处理需要的技能
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> neededSkills =
                (List<Map<String, Object>>) reflectionData.get("neededSkills");

            if (CollUtil.isNotEmpty(neededSkills)) {
                for (Map<String, Object> skill : neededSkills) {
                    String name = (String) skill.get("name");
                    String description = (String) skill.get("description");
                    int priority = (int) skill.getOrDefault("priority", 5);

                    knowledgeStore.requestSkill(reflectionId, name, description, priority);
                }
            }

            log.info("定时反省完成: reflectionId={}, 发现 {} 个需要学习的技能",
                reflectionId, CollUtil.isNotEmpty(neededSkills) ? neededSkills.size() : 0);

            return reflectionId;

        } catch (Exception e) {
            log.error("定时反省失败", e);
            return -1;
        }
    }

    /**
     * 触发错误反省
     * <p>
     * 当发生错误时，分析错误原因并提出解决方案
     *
     * @param sessionId   会话ID
     * @param errorType   错误类型
     * @param errorMessage 错误消息
     * @param context     错误上下文
     * @return 反省记录ID
     */
    public long triggerErrorReflection(String sessionId, String errorType,
                                      String errorMessage, String context) {
        log.warn("触发错误反省: sessionId={}, errorType={}", sessionId, errorType);

        try {
            // 1. 构建反省提示词
            String prompt = String.format(ERROR_REFLECTION_PROMPT,
                errorType, errorMessage, StrUtil.blankToDefault(context, "无上下文"));

            // 2. 使用 AI 分析
            String fullPrompt = "你是 SolonClaw AI Agent 的错误分析专家。\n\n" + prompt;
            ChatResponse response = chatModel.prompt(fullPrompt).call();

            String analysis = response.getContent();

            // 3. 解析 AI 响应
            Map<String, Object> reflectionData = parseJsonResponse(analysis);

            // 4. 保存反省记录
            String rootCause = (String) reflectionData.getOrDefault("rootCause", "待分析");
            String solution = (String) reflectionData.getOrDefault("solution", "待确定");
            String prevention = (String) reflectionData.getOrDefault("prevention", "待制定");

            String content = String.format("""
                ## 错误分析

                **错误类型**: %s
                **错误消息**: %s

                ### 根本原因
                %s

                ### 解决方案
                %s

                ### 预防措施
                %s
                """, errorType, errorMessage, rootCause, solution, prevention);

            long reflectionId = knowledgeStore.saveReflection(
                sessionId,
                "error_recovery",
                content,
                "错误: " + errorType + " - " + errorMessage,
                null,
                null
            );

            // 5. 保存错误处理经验
            knowledgeStore.saveExperience(
                "error_handling",
                errorType + " 处理方案",
                content,
                "session",
                sessionId,
                true,
                0.8
            );

            // 6. 处理需要的新技能
            @SuppressWarnings("unchecked")
            Map<String, Object> neededSkill =
                (Map<String, Object>) reflectionData.get("neededSkill");

            if (cn.hutool.core.util.ObjUtil.isNotNull(neededSkill)) {
                String name = (String) neededSkill.get("name");
                String description = (String) neededSkill.get("description");
                int priority = (int) neededSkill.getOrDefault("priority", 5);

                knowledgeStore.requestSkill(reflectionId, name, description, priority);
            }

            log.info("错误反省完成: reflectionId={}", reflectionId);

            return reflectionId;

        } catch (Exception e) {
            log.error("错误反省失败", e);
            // 即使 AI 分析失败，也要记录基本的错误信息
            return knowledgeStore.saveReflection(
                sessionId,
                "error_recovery",
                "错误: " + errorMessage,
                "错误类型: " + errorType,
                null,
                null
            );
        }
    }

    /**
     * 执行深度反省
     * <p>
     * 分析指定时间段内的所有活动，生成全面的反省报告
     *
     * @param sessionId 会话ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 反省记录ID
     */
    public long performDeepReflection(String sessionId, LocalDateTime startTime,
                                     LocalDateTime endTime) {
        log.info("开始执行深度反省: sessionId={}, start={}, end={}",
            sessionId, startTime, endTime);

        try {
            // 1. 获取指定时间范围内的日志
            // TODO: 实现 getLogsByTimeRange 方法
            // List<com.jimuqu.solonclaw.logging.LogEntry> logs =
            //     logStore.getLogsByTimeRange(sessionId, startTime, endTime);
            List<com.jimuqu.solonclaw.logging.LogEntry> logs = List.of();

            // 2. 获取该时间段内的反省记录
            List<com.jimuqu.solonclaw.memory.SessionStore.Reflection> reflections =
                knowledgeStore.getReflections(sessionId, null, 20);

            // 3. 获取该时间段内的经验
            List<com.jimuqu.solonclaw.memory.SessionStore.Experience> experiences =
                knowledgeStore.searchExperiences(null, null, 20);

            // 4. 构建深度分析提示词
            String deepReflectionPrompt = buildDeepReflectionPrompt(logs, reflections, experiences);

            // 5. 使用 AI 分析
            String fullPrompt = "你是 SolonClaw AI Agent 的深度学习分析专家。\n\n" + deepReflectionPrompt;
            ChatResponse response = chatModel.prompt(fullPrompt).call();

            String analysis = response.getContent();

            // 6. 保存深度反省记录
            long reflectionId = knowledgeStore.saveReflection(
                sessionId,
                "deep_reflection",
                analysis,
                String.format("深度反省: %s 到 %s", startTime, endTime),
                null,
                0.9
            );

            log.info("深度反省完成: reflectionId={}", reflectionId);
            return reflectionId;

        } catch (Exception e) {
            log.error("深度反省失败", e);
            return -1;
        }
    }

    /**
     * 构建日志摘要
     */
    private String buildLogsSummary(List<com.jimuqu.solonclaw.logging.LogEntry> logs) {
        StringBuilder sb = new StringBuilder();
        for (com.jimuqu.solonclaw.logging.LogEntry entry : logs) {
            sb.append(String.format("[%s] %s: %s\n",
                entry.getTimestamp(),
                entry.getLevel().getCode(),
                entry.getMessage()));
        }
        return sb.toString();
    }

    /**
     * 构建反省内容
     */
    private String buildReflectionContent(String summary, List<String> successes,
                                        List<String> improvements) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 总体总结\n").append(summary).append("\n\n");

        if (CollUtil.isNotEmpty(successes)) {
            sb.append("## 成功经验\n");
            for (String success : successes) {
                sb.append("- ").append(success).append("\n");
            }
            sb.append("\n");
        }

        if (CollUtil.isNotEmpty(improvements)) {
            sb.append("## 改进建议\n");
            for (String improvement : improvements) {
                sb.append("- ").append(improvement).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 构建深度反省提示词
     */
    private String buildDeepReflectionPrompt(
            List<com.jimuqu.solonclaw.logging.LogEntry> logs,
            List<com.jimuqu.solonclaw.memory.SessionStore.Reflection> reflections,
            List<com.jimuqu.solonclaw.memory.SessionStore.Experience> experiences) {

        return String.format("""
            你是一个 AI Agent 的深度学习分析专家。请对以下数据进行全面分析。

            ## 时间段内的日志 (%d 条)
            %s

            ## 之前的反省记录 (%d 条)
            %s

            ## 已有经验 (%d 条)
            %s

            ## 分析要求
            1. 评估能力成长趋势
            2. 识别反复出现的问题
            3. 评估已学习经验的有效性
            4. 提出下一步学习重点

            请提供全面的分析报告。
            """,
            logs.size(), buildLogsSummary(logs),
            reflections.size(), summarizeReflections(reflections),
            experiences.size(), summarizeExperiences(experiences)
        );
    }

    /**
     * 总结反省记录
     */
    private String summarizeReflections(List<com.jimuqu.solonclaw.memory.SessionStore.Reflection> reflections) {
        StringBuilder sb = new StringBuilder();
        for (com.jimuqu.solonclaw.memory.SessionStore.Reflection r : reflections) {
            sb.append(String.format("- [%s] %s\n", r.reflectionType(), r.content()));
        }
        return sb.toString();
    }

    /**
     * 总结经验记录
     */
    private String summarizeExperiences(List<com.jimuqu.solonclaw.memory.SessionStore.Experience> experiences) {
        StringBuilder sb = new StringBuilder();
        for (com.jimuqu.solonclaw.memory.SessionStore.Experience e : experiences) {
            sb.append(String.format("- [%s] %s (使用%d次, 评分%.2f)\n",
                e.experienceType(), e.title(), e.usageCount(), e.effectivenessScore()));
        }
        return sb.toString();
    }

    /**
     * 解析 JSON 响应
     * <p>
     * 使用 Solon 内置的 ONode 工具解析 JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonResponse(String jsonResponse) {
        try {
            if (StrUtil.isBlank(jsonResponse)) {
                log.warn("JSON 响应为空");
                return new HashMap<>();
            }

            log.debug("解析 JSON 响应: {}", jsonResponse);


            // 使用 ONode 解析 JSON
            ONode node = ONode.ofJson(jsonResponse);

            // 手动提取各个字段
            Map<String, Object> result = new HashMap<>();

            // 尝试提取各个字段，如果不存在则忽略
            try {
                result.put("summary", node.get("summary").getString());
            } catch (Exception ignored) {}

            try {
                result.put("successes", extractStringList(node.get("successes")));
            } catch (Exception ignored) {}

            try {
                result.put("failures", extractStringList(node.get("failures")));
            } catch (Exception ignored) {}

            try {
                result.put("improvements", extractStringList(node.get("improvements")));
            } catch (Exception ignored) {}

            try {
                result.put("neededSkills", extractObjectList(node.get("neededSkills")));
            } catch (Exception ignored) {}

            try {
                result.put("rootCause", node.get("rootCause").getString());
            } catch (Exception ignored) {}

            try {
                result.put("solution", node.get("solution").getString());
            } catch (Exception ignored) {}

            try {
                result.put("prevention", node.get("prevention").getString());
            } catch (Exception ignored) {}

            try {
                result.put("neededSkill", extractMap(node.get("neededSkill")));
            } catch (Exception ignored) {}

            return result;

        } catch (Exception e) {
            log.warn("解析 AI 响应失败，返回空结果: {}", e.getMessage());
            log.debug("失败的 JSON 内容: {}", jsonResponse, e);

            // 返回空的 Map 而不是硬编码默认值
            return new HashMap<>();
        }
    }

    /**
     * 提取字符串列表
     */
    private List<String> extractStringList(ONode node) {
        List<String> result = new ArrayList<>();
        if (cn.hutool.core.util.ObjUtil.isNull(node) || !node.isArray()) {
            return result;
        }

        for (int i = 0; i < node.size(); i++) {
            try {
                result.add(node.get(i).getString());
            } catch (Exception ignored) {}
        }
        return result;
    }

    /**
     * 提取对象列表
     */
    private List<Map<String, Object>> extractObjectList(ONode node) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (cn.hutool.core.util.ObjUtil.isNull(node) || !node.isArray()) {
            return result;
        }

        for (int i = 0; i < node.size(); i++) {
            result.add(extractMap(node.get(i)));
        }
        return result;
    }

    /**
     * 提取 Map（简化实现，只处理字符串和整数）
     */
    private Map<String, Object> extractMap(ONode node) {
        Map<String, Object> result = new HashMap<>();
        if (cn.hutool.core.util.ObjUtil.isNull(node) || !node.isObject()) {
            return result;
        }

        // 遍历对象的所有属性
        try {
            // 将对象转换为字符串然后解析
            String jsonStr = node.toString();
            ONode objNode = ONode.ofJson(jsonStr);

            // 手动提取已知字段
            try {
                result.put("name", objNode.get("name").getString());
            } catch (Exception ignored) {}

            try {
                result.put("description", objNode.get("description").getString());
            } catch (Exception ignored) {}

            try {
                result.put("priority", objNode.get("priority").getInt());
            } catch (Exception ignored) {}

            try {
                result.put("instruction", objNode.get("instruction").getString());
            } catch (Exception ignored) {}

            try {
                result.put("condition", objNode.get("condition").getString());
            } catch (Exception ignored) {}

            try {
                result.put("enabled", objNode.get("enabled").getBoolean());
            } catch (Exception ignored) {}

            try {
                result.put("tools", extractStringList(objNode.get("tools")));
            } catch (Exception ignored) {}

        } catch (Exception e) {
            log.debug("提取 Map 失败: {}", e.getMessage());
        }

        return result;
    }
}
