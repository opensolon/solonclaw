package com.jimuqu.solonclaw.agent.event;

/**
 * 内部事件监听器
 * <p>
 * 用于接收和处理 Agent 系统内部的异步事件
 * 参考 OpenClaw 的事件监听器设计
 *
 * @author SolonClaw
 */
@FunctionalInterface
public interface InternalEventListener {

    /**
     * 处理内部事件
     *
     * @param event 内部事件
     */
    void onInternalEvent(AgentInternalEvent event);
}
