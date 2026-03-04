package com.jimuqu.solonclaw.learning;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Init;
import org.noear.solon.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习编排服务
 * <p>
 * 负责协调和编排学习系统的各个组件
 * 采用事件驱动机制，实时响应技能需求
 *
 * @author SolonClaw
 */
@Component
public class LearningOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LearningOrchestrator.class);

    @Inject
    private ReflectionService reflectionService;

    @Inject
    private AutoSkillService autoSkillService;

    @Inject
    private LearningConfig learningConfig;

    @Inject
    private KnowledgeStore knowledgeStore;

    /**
     * 初始化学习系统
     */
    @Init
    public void init() {
        if (!learningConfig.isEnabled()) {
            log.info("学习系统已禁用，跳过初始化");
            return;
        }

        log.info("初始化 SolonClaw 学习系统（事件驱动模式）");

        // 打印配置信息
        LearningConfig.ReflectionConfig reflectionConfig = learningConfig.getReflectionConfig();
        LearningConfig.AutoSkillConfig autoSkillConfig = learningConfig.getAutoSkillConfig();
        LearningConfig.KnowledgeConfig knowledgeConfig = learningConfig.getKnowledgeConfig();

        log.info("反思配置：cron={}, 时间窗口={}小时，最大消息数={}",
            reflectionConfig.cron(), reflectionConfig.timeWindowHours(), reflectionConfig.maxMessagesPerReflection());

        log.info("自动技能配置：置信度阈值={}, 实时分析={}",
            autoSkillConfig.minConfidenceThreshold(), autoSkillConfig.realtimeAnalysisEnabled());

        log.info("知识库配置：最大搜索结果={}, 最小置信度={}",
            knowledgeConfig.maxSearchResults(), knowledgeConfig.minConfidenceThreshold());

        log.info("学习系统初始化完成 - 技能创建采用事件驱动模式，不再使用定时任务");
    }

    /**
     * 定时执行反思任务
     * <p>
     * 每小时执行一次，分析最近的日志和经验
     * 反思完成后会立即处理产生的技能需求
     */
    @Scheduled(cron = "${solonclaw.learning.reflection.cron:0 0 * * * ?}")
    public void scheduledReflectionTask() {
        if (!learningConfig.isEnabled()) {
            return;
        }

        log.info("开始执行定时反思任务");

        try {
            long reflectionId = reflectionService.performScheduledReflection(null);

            if (reflectionId > 0) {
                log.info("定时反思任务完成：reflectionId={}", reflectionId);
                // 反思完成后，立即处理可能产生的技能需求
                processSkillRequestsForReflection(reflectionId);
            } else {
                log.debug("定时反思任务完成：没有需要反思的内容");
            }

        } catch (Exception e) {
            log.error("定时反思任务执行失败", e);
        }
    }

    /**
     * 对话完成后的回调
     * <p>
     * 在每次对话完成后触发，用于实时分析和学习
     *
     * @param sessionId 会话 ID
     * @param response  Agent 响应
     * @param error     发生的错误（如果有）
     */
    public void onChatComplete(String sessionId, String response, Throwable error) {
        if (!learningConfig.isEnabled()) {
            return;
        }

        log.debug("对话完成回调：sessionId={}, hasError={}", sessionId, error != null);

        try {
            // 如果发生了错误，触发错误反思并实时处理技能需求
            if (error != null) {
                log.warn("检测到错误，触发错误反省：sessionId={}, error={}",
                    sessionId, error.getMessage());

                long reflectionId = reflectionService.triggerErrorReflection(
                    sessionId,
                    error.getClass().getSimpleName(),
                    error.getMessage(),
                    "对话执行过程中发生错误"
                );

                // 错误反思完成后，立即处理可能产生的技能需求
                if (reflectionId > 0) {
                    processSkillRequestsForReflection(reflectionId);
                }
            }

            // 记录学习经验（如果有重要发现）
            recordLearningFromSession(sessionId, response, error);

        } catch (Exception e) {
            log.error("对话完成回调处理失败", e);
        }
    }

    /**
     * 从会话中学习
     * <p>
     * 自动提取会话中的关键信息并记录为经验
     */
    private void recordLearningFromSession(String sessionId, String response, Throwable error) {
        try {
            if (error == null && response != null && response.length() > 100) {
                // 成功的对话，记录为正面经验
                knowledgeStore.learnFromTask(
                    sessionId,
                    "完成对话任务",
                    true,
                    "成功完成用户请求：" + response.substring(0, Math.min(100, response.length()))
                );
            }

        } catch (Exception e) {
            log.debug("记录会话学习经验失败", e);
        }
    }

    /**
     * 处理指定反思产生的技能需求
     * <p>
     * 事件驱动的核心方法，在反思完成后立即调用
     *
     * @param reflectionId 反思记录 ID
     */
    private void processSkillRequestsForReflection(long reflectionId) {
        try {
            log.info("开始处理反思产生的技能需求：reflectionId={}", reflectionId);

            // 获取该反思产生的所有待处理技能请求
            var requests = knowledgeStore.getSkillRequests(reflectionId, 10);

            if (requests.isEmpty()) {
                log.debug("反思 {} 没有产生技能需求", reflectionId);
                return;
            }

            int processedCount = 0;
            int createdCount = 0;

            for (var request : requests) {
                if ("pending".equals(request.status())) {
                    processedCount++;
                    boolean success = autoSkillService.analyzeAndCreateSkill(request.id());
                    if (success) {
                        createdCount++;
                        log.info("技能创建成功：reflectionId={}, skillName={}",
                            reflectionId, request.skillName());
                    } else {
                        log.warn("技能创建失败：reflectionId={}, skillName={}, status={}",
                            reflectionId, request.skillName(), request.status());
                    }
                }
            }

            log.info("反思 {} 的技能需求处理完成：总计={}, 成功创建={}",
                reflectionId, processedCount, createdCount);

        } catch (Exception e) {
            log.error("处理反思技能需求失败：reflectionId={}", reflectionId, e);
        }
    }

    /**
     * 获取学习系统统计信息
     */
    public LearningStats getStats() {
        try {
            // 获取反省记录数
            int reflectionCount = knowledgeStore.getRecentReflections(1000).size();

            // 获取经验条目数
            int experienceCount = knowledgeStore.searchAllExperiences("", 1000).size();

            // 获取待处理技能请求数
            int pendingSkillRequests = knowledgeStore.getPendingSkillRequests(1000).size();

            return new LearningStats(
                learningConfig.isEnabled(),
                reflectionCount,
                experienceCount,
                pendingSkillRequests
            );

        } catch (Exception e) {
            log.error("获取学习系统统计信息失败", e);
            return new LearningStats(learningConfig.isEnabled(), 0, 0, 0);
        }
    }

    /**
     * 手动触发反思（用于 API 接口）
     */
    public long triggerReflection() {
        log.info("手动触发反思");
        long reflectionId = reflectionService.performScheduledReflection(null);
        if (reflectionId > 0) {
            processSkillRequestsForReflection(reflectionId);
        }
        return reflectionId;
    }

    /**
     * 学习系统统计信息
     */
    public record LearningStats(
            boolean enabled,
            int reflectionCount,
            int experienceCount,
            int pendingSkillRequests
    ) {
    }
}
