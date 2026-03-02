package com.jimuqu.solonclaw.context;

import java.util.Map;

/**
 * 上下文构建器接口
 * <p>
 * 定义构建 AI 对话上下文的契约
 *
 * @author SolonClaw
 */
public interface ContextBuilder {

    /**
     * 构建上下文
     *
     * @param sessionId   会话ID
     * @param userMessage 用户消息
     * @param options     构建选项（可选参数）
     * @return 构建的上下文
     */
    Context build(String sessionId, String userMessage, Map<String, Object> options);

    /**
     * 构建上下文（无额外选项）
     *
     * @param sessionId   会话ID
     * @param userMessage 用户消息
     * @return 构建的上下文
     */
    default Context build(String sessionId, String userMessage) {
        return build(sessionId, userMessage, null);
    }
}
