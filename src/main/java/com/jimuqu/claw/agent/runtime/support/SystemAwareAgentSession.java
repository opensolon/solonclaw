package com.jimuqu.claw.agent.runtime.support;

import org.noear.solon.ai.agent.Agent;
import org.noear.solon.ai.agent.AgentSession;
import org.noear.solon.ai.chat.session.InMemoryChatSession;
import org.noear.solon.flow.FlowContext;

/**
 * 保留 system 消息的轻量级 AgentSession。
 * Solon 自带的 InMemoryAgentSession 会过滤 system 消息，导致运行时重建出的系统事件无法真正进入模型短期上下文。
 */
public class SystemAwareAgentSession extends InMemoryChatSession implements AgentSession {
    /** 当前执行快照。 */
    private volatile FlowContext snapshot;

    /**
     * 创建带默认窗口大小的会话。
     *
     * @param sessionId 会话标识
     * @return 会话对象
     */
    public static SystemAwareAgentSession of(String sessionId) {
        return new SystemAwareAgentSession(sessionId);
    }

    /**
     * 创建带默认窗口大小的会话。
     *
     * @param sessionId 会话标识
     */
    public SystemAwareAgentSession(String sessionId) {
        super(sessionId == null ? "tmp" : sessionId);
        this.snapshot = FlowContext.of(getSessionId());
        this.snapshot.put(Agent.KEY_SESSION, this);
    }

    /**
     * 创建带指定最大消息数的会话。
     *
     * @param sessionId 会话标识
     * @param maxMessages 最大消息数
     */
    public SystemAwareAgentSession(String sessionId, int maxMessages) {
        super(sessionId == null ? "tmp" : sessionId, maxMessages);
        this.snapshot = FlowContext.of(getSessionId());
        this.snapshot.put(Agent.KEY_SESSION, this);
    }

    @Override
    public void updateSnapshot() {
        // 当前实现为纯内存 session，无需额外持久化快照。
    }

    @Override
    public FlowContext getSnapshot() {
        return snapshot;
    }
}

