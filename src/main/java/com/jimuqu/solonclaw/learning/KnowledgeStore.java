package com.jimuqu.solonclaw.learning;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 知识存储服务
 * <p>
 * 负责管理 AI Agent 学习到的知识、经验和模式
 *
 * @author SolonClaw
 */
@Component
public class KnowledgeStore {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeStore.class);

    @Inject
    private com.jimuqu.solonclaw.memory.SessionStore sessionStore;

    // ==================== 反省管理 ====================

    /**
     * 保存反省记录
     */
    public long saveReflection(String sessionId, String reflectionType, String content,
                             String context, String actionItems, Double effectivenessScore) {
        log.debug("保存反省：sessionId={}, type={}", sessionId, reflectionType);
        return sessionStore.saveReflection(sessionId, reflectionType, content,
            context, actionItems, effectivenessScore);
    }

    /**
     * 获取反省记录
     */
    public List<com.jimuqu.solonclaw.memory.SessionStore.Reflection> getReflections(
            String sessionId, String reflectionType, int limit) {
        log.debug("获取反省记录：sessionId={}, type={}, limit={}", sessionId, reflectionType, limit);
        return sessionStore.getReflections(sessionId, reflectionType, limit);
    }

    /**
     * 获取最近的反省记录
     */
    public List<com.jimuqu.solonclaw.memory.SessionStore.Reflection> getRecentReflections(int limit) {
        return getReflections(null, null, limit);
    }

    /**
     * 获取特定类型的反省记录
     */
    public List<com.jimuqu.solonclaw.memory.SessionStore.Reflection> getReflectionsByType(
            String reflectionType, int limit) {
        return getReflections(null, reflectionType, limit);
    }

    // ==================== 经验管理 ====================

    /**
     * 保存经验条目
     */
    public long saveExperience(String experienceType, String title, String content,
                             String sourceType, String sourceId, Boolean success,
                             Double confidence) {
        log.debug("保存经验：type={}, title={}", experienceType, title);
        return sessionStore.saveExperience(experienceType, title, content,
            sourceType, sourceId, success, confidence);
    }

    /**
     * 搜索经验
     */
    public List<com.jimuqu.solonclaw.memory.SessionStore.Experience> searchExperiences(
            String experienceType, String keyword, int limit) {
        log.debug("搜索经验：type={}, keyword={}", experienceType, keyword);
        return sessionStore.searchExperiences(experienceType, keyword, limit);
    }

    /**
     * 根据类型获取经验
     */
    public List<com.jimuqu.solonclaw.memory.SessionStore.Experience> getExperiencesByType(
            String experienceType, int limit) {
        return searchExperiences(experienceType, null, limit);
    }

    /**
     * 根据关键词搜索所有类型的经验
     */
    public List<com.jimuqu.solonclaw.memory.SessionStore.Experience> searchAllExperiences(
            String keyword, int limit) {
        return searchExperiences(null, keyword, limit);
    }

    /**
     * 更新经验使用统计
     */
    public void updateExperienceUsage(long experienceId, double effectivenessScore) {
        log.debug("更新经验使用：experienceId={}, score={}", experienceId, effectivenessScore);
        sessionStore.updateExperienceUsage(experienceId, effectivenessScore);
    }

    // ==================== 技能需求管理 ====================

    /**
     * 保存技能需求
     */
    public long saveSkillRequest(Long reflectionId, String skillName, String skillDescription,
                                Integer priority, String status, String metadata) {
        log.debug("保存技能需求：skillName={}", skillName);
        return sessionStore.saveSkillRequest(reflectionId, skillName, skillDescription,
            priority, status, metadata);
    }

    /**
     * 保存技能需求（简化版本）
     */
    public long requestSkill(Long reflectionId, String skillName, String skillDescription, Integer priority) {
        log.debug("创建技能需求：skillName={}", skillName);
        return sessionStore.saveSkillRequest(reflectionId, skillName, skillDescription,
            priority, "pending", null);
    }

    /**
     * 获取技能需求列表（按状态）
     */
    public List<com.jimuqu.solonclaw.memory.SessionStore.SkillRequest> getSkillRequests(
            String status, int limit) {
        log.debug("获取技能需求：status={}, limit={}", status, limit);
        return sessionStore.getSkillRequests(status, limit);
    }

    /**
     * 获取指定反思的技能需求列表
     */
    public List<com.jimuqu.solonclaw.memory.SessionStore.SkillRequest> getSkillRequests(
            Long reflectionId, int limit) {
        log.debug("获取技能需求：reflectionId={}, limit={}", reflectionId, limit);
        return sessionStore.getSkillRequestsByReflectionId(reflectionId, limit);
    }

    /**
     * 获取待处理的技能需求
     */
    public List<com.jimuqu.solonclaw.memory.SessionStore.SkillRequest> getPendingSkillRequests(int limit) {
        return getSkillRequests("pending", limit);
    }

    /**
     * 更新技能需求状态
     */
    public void updateSkillRequestStatus(long requestId, String status) {
        log.debug("更新技能需求状态：requestId={}, status={}", requestId, status);
        sessionStore.updateSkillRequestStatus(requestId, status);
    }

    // ==================== 便捷方法 ====================

    /**
     * 从任务执行中学习
     */
    public void learnFromTask(String sessionId, String title, Boolean success, String summary) {
        log.debug("从任务中学习：sessionId={}, success={}", sessionId, success);
        saveExperience("task_learning", title, summary, "session", sessionId, success,
            success ? 0.7 : 0.3);
    }
}
