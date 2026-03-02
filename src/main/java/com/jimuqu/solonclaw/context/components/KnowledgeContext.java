package com.jimuqu.solonclaw.context.components;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Init;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 知识上下文组件
 * <p>
 * 负责从知识库检索相关经验并构建知识上下文
 *
 * @author SolonClaw
 */
@Component
public class KnowledgeContext {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeContext.class);

    @Inject(required = false)
    private com.jimuqu.solonclaw.learning.KnowledgeStore knowledgeStore;

    @Inject
    private com.jimuqu.solonclaw.context.config.ContextBuilderConfig config;

    @Init
    public void init() {
        log.info("知识上下文组件初始化完成");
    }

    /**
     * 构建知识上下文
     *
     * @param sessionId   会话ID
     * @param userMessage 用户消息
     * @param options     构建选项
     * @return 知识上下文文本，如果没有相关知识返回空字符串
     */
    public String build(String sessionId, String userMessage, Map<String, Object> options) {
        // 从配置获取启用状态
        boolean enabled = config != null && config.isKnowledgeEnabled();
        if (!enabled) {
            log.debug("知识上下文已禁用，跳过构建");
            return "";
        }

        if (knowledgeStore == null) {
            log.debug("知识库未初始化，跳过知识上下文构建");
            return "";
        }

        try {
            // 从配置获取参数或使用选项中的覆盖配置
            int maxSearchResults = getMaxSearchResults(options);
            double minConfidenceThreshold = getMinConfidenceThreshold(options);

            // 提取关键词
            String keyword = extractKeyword(userMessage);

            // 搜索相关经验
            List<com.jimuqu.solonclaw.memory.SessionStore.Experience> experiences =
                knowledgeStore.searchAllExperiences(keyword, maxSearchResults);

            if (experiences == null || experiences.isEmpty()) {
                log.debug("未找到相关知识: sessionId={}, keyword={}", sessionId, keyword);
                return "";
            }

            // 构建知识上下文
            return buildKnowledgeContextText(experiences, minConfidenceThreshold);

        } catch (Exception e) {
            log.warn("构建知识上下文失败: sessionId={}", sessionId, e);
            return "";
        }
    }

    /**
     * 获取最大搜索结果数
     */
    private int getMaxSearchResults(Map<String, Object> options) {
        if (options != null && options.containsKey("maxSearchResults")) {
            return (Integer) options.get("maxSearchResults");
        }
        return config != null ? config.getMaxSearchResults() : 5;
    }

    /**
     * 获取最小置信度阈值
     */
    private double getMinConfidenceThreshold(Map<String, Object> options) {
        if (options != null && options.containsKey("minConfidenceThreshold")) {
            return (Double) options.get("minConfidenceThreshold");
        }
        return config != null ? config.getMinConfidenceThreshold() : 0.6;
    }

    /**
     * 提取关键词
     */
    private String extractKeyword(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // 简化实现：使用前20个字符作为关键词
        // 实际项目中可以使用更复杂的 NLP 技术
        int maxLength = Math.min(20, message.length());
        return message.substring(0, maxLength).trim();
    }

    /**
     * 构建知识上下文文本
     */
    private String buildKnowledgeContextText(
            List<com.jimuqu.solonclaw.memory.SessionStore.Experience> experiences,
            double minConfidenceThreshold) {
        StringBuilder context = new StringBuilder();
        context.append("基于历史经验，以下信息可能对你有帮助：\n\n");

        for (com.jimuqu.solonclaw.memory.SessionStore.Experience exp : experiences) {
            if (exp.success() && exp.confidence() >= minConfidenceThreshold) {
                String content = exp.content();
                int contentLength = content != null ? content.length() : 0;
                String truncatedContent = content != null ?
                    content.substring(0, Math.min(100, contentLength)) : "";

                context.append(String.format("- **%s**: %s (置信度: %.1f%%)\n",
                    exp.title(),
                    truncatedContent,
                    exp.confidence() * 100
                ));
            }
        }

        log.debug("构建知识上下文: 经验数={}", experiences.size());
        return context.toString();
    }
}