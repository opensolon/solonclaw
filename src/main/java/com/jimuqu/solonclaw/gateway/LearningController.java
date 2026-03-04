package com.jimuqu.solonclaw.gateway;

import com.jimuqu.solonclaw.learning.*;
import com.jimuqu.solonclaw.common.Result;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Param;
import org.noear.solon.annotation.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 学习系统控制器
 * <p>
 * 提供 SolonClaw 学习系统的管理和查询接口
 * 技能创建采用事件驱动模式，实时响应反思产生的技能需求
 *
 * @author SolonClaw
 */
@Controller
@Mapping("/api/learning")
public class LearningController {

    private static final Logger log = LoggerFactory.getLogger(LearningController.class);

    @Inject
    private LearningOrchestrator learningOrchestrator;

    @Inject
    private KnowledgeStore knowledgeStore;

    @Inject
    private ReflectionService reflectionService;

    @Inject
    private LearningConfig learningConfig;

    /**
     * 获取学习系统状态
     */
    @Get
    @Mapping("/status")
    public Result getStatus() {
        try {
            LearningOrchestrator.LearningStats stats = learningOrchestrator.getStats();
            return Result.success("学习系统状态", stats);
        } catch (Exception e) {
            log.error("获取学习系统状态失败", e);
            return Result.failure("获取状态失败：" + e.getMessage());
        }
    }

    /**
     * 手动触发反思
     * <p>
     * 反思完成后会自动处理产生的技能需求
     */
    @Post
    @Mapping("/reflection/trigger")
    public Result triggerReflection() {
        try {
            long reflectionId = learningOrchestrator.triggerReflection();
            if (reflectionId > 0) {
                return Result.success("反思任务已触发，技能需求将实时处理", new ReflectionTriggerResult(reflectionId, true));
            } else {
                return Result.success("没有需要反思的内容", new ReflectionTriggerResult(-1, false));
            }
        } catch (Exception e) {
            log.error("触发反思失败", e);
            return Result.failure("触发反思失败：" + e.getMessage());
        }
    }

    /**
     * 获取反思记录列表
     */
    @Get
    @Mapping("/reflections")
    public Result getReflections(
            @Param(value = "sessionId", required = false) String sessionId,
            @Param(value = "type", required = false) String type,
            @Param(value = "limit", defaultValue = "20") int limit) {
        try {
            List<com.jimuqu.solonclaw.memory.SessionStore.Reflection> reflections =
                knowledgeStore.getReflections(sessionId, type, Math.min(limit, 100));
            return Result.success("获取反思记录成功", reflections);
        } catch (Exception e) {
            log.error("获取反思记录失败", e);
            return Result.failure("获取反思记录失败：" + e.getMessage());
        }
    }

    /**
     * 获取最近的反思记录
     */
    @Get
    @Mapping("/reflections/recent")
    public Result getRecentReflections(@Param(value = "limit", defaultValue = "10") int limit) {
        try {
            List<com.jimuqu.solonclaw.memory.SessionStore.Reflection> reflections =
                knowledgeStore.getRecentReflections(Math.min(limit, 100));
            return Result.success("获取最近反思记录成功", reflections);
        } catch (Exception e) {
            log.error("获取最近反思记录失败", e);
            return Result.failure("获取最近反思记录失败：" + e.getMessage());
        }
    }

    /**
     * 按类型获取反思记录
     */
    @Get
    @Mapping("/reflections/type/{type}")
    public Result getReflectionsByType(
            String type,
            @Param(value = "limit", defaultValue = "20") int limit) {
        try {
            List<com.jimuqu.solonclaw.memory.SessionStore.Reflection> reflections =
                knowledgeStore.getReflectionsByType(type, Math.min(limit, 100));
            return Result.success("获取反思记录成功", reflections);
        } catch (Exception e) {
            log.error("获取反思记录失败", e);
            return Result.failure("获取反思记录失败：" + e.getMessage());
        }
    }

    /**
     * 搜索经验
     */
    @Get
    @Mapping("/experiences/search")
    public Result searchExperiences(
            @Param(value = "keyword", required = false) String keyword,
            @Param(value = "type", required = false) String type,
            @Param(value = "limit", defaultValue = "10") int limit) {
        try {
            List<com.jimuqu.solonclaw.memory.SessionStore.Experience> experiences;

            if (type != null && !type.isEmpty()) {
                experiences = knowledgeStore.getExperiencesByType(type, Math.min(limit, 100));
            } else {
                experiences = knowledgeStore.searchAllExperiences(
                    keyword != null ? keyword : "",
                    Math.min(limit, 100)
                );
            }

            return Result.success("搜索经验成功", experiences);
        } catch (Exception e) {
            log.error("搜索经验失败", e);
            return Result.failure("搜索经验失败：" + e.getMessage());
        }
    }

    /**
     * 获取技能请求列表
     */
    @Get
    @Mapping("/skills/requests")
    public Result getSkillRequests(
            @Param(value = "reflectionId", required = false) Long reflectionId,
            @Param(value = "status", required = false) String status,
            @Param(value = "limit", defaultValue = "20") int limit) {
        try {
            List<com.jimuqu.solonclaw.memory.SessionStore.SkillRequest> requests;

            // 如果指定了 reflectionId，使用新方法查询
            if (reflectionId != null && reflectionId > 0) {
                requests = knowledgeStore.getSkillRequests(reflectionId, Math.min(limit, 100));
            } else {
                // 否则按状态查询
                requests = knowledgeStore.getSkillRequests(status, Math.min(limit, 100));
            }

            return Result.success("获取技能请求成功", requests);
        } catch (Exception e) {
            log.error("获取技能请求失败", e);
            return Result.failure("获取技能请求失败：" + e.getMessage());
        }
    }

    /**
     * 获取学习系统配置
     */
    @Get
    @Mapping("/config")
    public Result getConfig() {
        try {
            return Result.success("获取配置成功", Map.of(
                "enabled", learningConfig.isEnabled(),
                "reflection", learningConfig.getReflectionConfig(),
                "autoSkill", learningConfig.getAutoSkillConfig(),
                "knowledge", learningConfig.getKnowledgeConfig()
            ));
        } catch (Exception e) {
            log.error("获取配置失败", e);
            return Result.failure("获取配置失败：" + e.getMessage());
        }
    }

    /**
     * 启用学习系统
     */
    @Post
    @Mapping("/enable")
    public Result enable() {
        try {
            learningConfig.enable();
            return Result.success("学习系统已启用");
        } catch (Exception e) {
            log.error("启用学习系统失败", e);
            return Result.failure("启用学习系统失败：" + e.getMessage());
        }
    }

    /**
     * 禁用学习系统
     */
    @Post
    @Mapping("/disable")
    public Result disable() {
        try {
            learningConfig.disable();
            return Result.success("学习系统已禁用");
        } catch (Exception e) {
            log.error("禁用学习系统失败", e);
            return Result.failure("禁用学习系统失败：" + e.getMessage());
        }
    }

    /**
     * 反思触发结果
     */
    private record ReflectionTriggerResult(
            long reflectionId,
            boolean hasContent
    ) {
    }
}
