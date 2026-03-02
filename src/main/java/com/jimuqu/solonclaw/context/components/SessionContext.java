package com.jimuqu.solonclaw.context.components;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Init;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会话上下文组件
 * <p>
 * 负责从历史对话记录中构建会话上下文摘要
 *
 * @author SolonClaw
 */
@Component
public class SessionContext {

    private static final Logger log = LoggerFactory.getLogger(SessionContext.class);

    @Inject
    private com.jimuqu.solonclaw.memory.MemoryService memoryService;

    @Inject
    private com.jimuqu.solonclaw.context.config.ContextBuilderConfig config;

    @Init
    public void init() {
        log.info("会话上下文组件初始化完成");
    }

    /**
     * 构建会话上下文
     *
     * @param sessionId   会话ID
     * @param userMessage 用户消息（暂未使用，保留用于未来扩展）
     * @param options     构建选项
     * @return 会话上下文文本，如果没有历史记录返回空字符串
     */
    public String build(String sessionId, String userMessage, Map<String, Object> options) {
        // 从配置获取启用状态
        boolean enabled = config != null && config.isSessionEnabled();
        if (!enabled) {
            log.debug("会话上下文已禁用，跳过构建");
            return "";
        }

        if (memoryService == null) {
            log.debug("记忆服务未初始化，跳过会话上下文构建");
            return "";
        }

        try {
            // 从配置获取参数或使用选项中的覆盖配置
            int maxHistoryMessages = getMaxHistoryMessages(options);
            int maxSummaryLength = getMaxSummaryLength(options);

            // 获取历史记录
            List<Map<String, String>> history = memoryService.getSessionHistory(sessionId);

            if (history == null || history.isEmpty()) {
                log.debug("会话无历史记录: sessionId={}", sessionId);
                return "";
            }

            // 限制历史消息数量
            List<Map<String, String>> limitedHistory = limitHistory(history, maxHistoryMessages);

            // 构建会话摘要
            return buildSessionSummary(limitedHistory, maxSummaryLength);

        } catch (Exception e) {
            log.warn("构建会话上下文失败: sessionId={}", sessionId, e);
            return "";
        }
    }

    /**
     * 获取最大历史消息数
     */
    private int getMaxHistoryMessages(Map<String, Object> options) {
        if (options != null && options.containsKey("maxHistoryMessages")) {
            return (Integer) options.get("maxHistoryMessages");
        }
        return config != null ? config.getMaxHistoryMessages() : 10;
    }

    /**
     * 获取最大摘要长度
     */
    private int getMaxSummaryLength(Map<String, Object> options) {
        if (options != null && options.containsKey("maxSummaryLength")) {
            return (Integer) options.get("maxSummaryLength");
        }
        return config != null ? config.getMaxSummaryLength() : 500;
    }

    /**
     * 限制历史消息数量
     */
    private List<Map<String, String>> limitHistory(List<Map<String, String>> history, int maxHistoryMessages) {
        if (history.size() <= maxHistoryMessages) {
            return history;
        }

        // 保留最近的 N 条消息
        return new ArrayList<>(
            history.subList(history.size() - maxHistoryMessages, history.size())
        );
    }

    /**
     * 构建会话摘要
     */
    private String buildSessionSummary(List<Map<String, String>> history, int maxSummaryLength) {
        StringBuilder summary = new StringBuilder();
        summary.append("最近的对话摘要：\n\n");

        int messageCount = 0;
        int totalLength = 0;

        for (Map<String, String> msg : history) {
            String role = msg.get("role");
            String content = msg.get("content");

            if (content == null || content.isEmpty()) {
                continue;
            }

            // 简化内容：只显示前50个字符
            String truncatedContent = truncate(content, 50);

            String roleLabel = "user".equals(role) ? "用户" : "助手";
            summary.append(String.format("%s: %s\n", roleLabel, truncatedContent));

            totalLength += truncatedContent.length();
            messageCount++;

            // 如果摘要超过长度限制，提前终止
            if (totalLength >= maxSummaryLength) {
                summary.append(String.format("\n... (还有 %d 条消息未显示)\n",
                    history.size() - messageCount));
                break;
            }
        }

        log.debug("构建会话摘要: 消息数={}, 总长度={}", messageCount, totalLength);
        return summary.toString();
    }

    /**
     * 截断文本
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}