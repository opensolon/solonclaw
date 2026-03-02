package com.jimuqu.solonclaw.memory;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆服务
 * <p>
 * 处理会话记忆的业务逻辑，提供简化的接口
 *
 * @author SolonClaw
 */
@Component
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    @Inject
    private SessionStore sessionStore;

    @Inject("${solonclaw.memory.session.maxHistory}")
    private int maxHistory;

    /**
     * 保存用户消息
     */
    public void saveUserMessage(String sessionId, String message) {
        sessionStore.createOrGetSession(sessionId);
        sessionStore.saveMessage(sessionId, "user", message);
        log.debug("保存用户消息: sessionId={}, length={}", sessionId, message.length());
    }

    /**
     * 保存 AI 响应
     */
    public void saveAssistantMessage(String sessionId, String response) {
        sessionStore.createOrGetSession(sessionId);
        sessionStore.saveMessage(sessionId, "assistant", response);
        log.debug("保存 AI 响应: sessionId={}, length={}", sessionId, response.length());
    }

    /**
     * 保存工具调用结果
     */
    public void saveToolResult(String sessionId, String toolName, String result) {
        sessionStore.createOrGetSession(sessionId);
        String content = String.format("[工具调用 %s]: %s", toolName, result);
        sessionStore.saveMessage(sessionId, "tool", content);
        log.debug("保存工具调用: sessionId={}, tool={}", sessionId, toolName);
    }

    /**
     * 获取会话历史（用于 AI 上下文）
     */
    public List<Map<String, String>> getSessionHistory(String sessionId) {
        List<SessionStore.Message> messages =
            sessionStore.getSessionMessages(sessionId, maxHistory);

        // 转换为 OpenAI 格式
        List<Map<String, String>> history = new java.util.ArrayList<>();
        for (SessionStore.Message msg : messages) {
            Map<String, String> message = new HashMap<>();
            message.put("role", msg.role());
            message.put("content", msg.content());
            history.add(message);
        }

        log.debug("获取会话历史: sessionId={}, count={}", sessionId, history.size());
        return history;
    }

    /**
     * 获取所有会话列表
     */
    public List<SessionStore.SessionInfo> listSessions() {
        return sessionStore.listSessions(100);
    }

    /**
     * 搜索历史消息
     */
    public List<SessionStore.Message> searchMessages(String keyword) {
        return sessionStore.searchMessages(keyword, 50);
    }

    /**
     * 删除会话
     */
    public void deleteSession(String sessionId) {
        sessionStore.deleteSession(sessionId);
        log.info("删除会话: {}", sessionId);
    }

    /**
     * 清理旧会话（可选功能）
     */
    public void cleanupOldSessions(int days) {
        // TODO: 实现旧会话清理逻辑
        log.debug("清理旧会话: days={}", days);
    }
}