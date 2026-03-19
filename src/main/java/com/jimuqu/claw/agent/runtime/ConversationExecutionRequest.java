package com.jimuqu.claw.agent.runtime;

import com.jimuqu.claw.agent.model.InboundTriggerType;
import org.noear.solon.ai.chat.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述一次会话执行所需的上下文输入。
 */
public class ConversationExecutionRequest {
    /** 当前会话对应的内部键。 */
    private String sessionKey;
    /** 当前待处理的用户消息。 */
    private String currentMessage;
    /** 当前消息的触发类型。 */
    private InboundTriggerType currentMessageTriggerType = InboundTriggerType.USER;
    /** 当前运行是否为父任务派生出的子任务。 */
    private boolean childRun;
    /** 当前子任务对应的父运行标识。 */
    private String parentRunId;
    /** 历史消息列表。 */
    private List<ChatMessage> history = new ArrayList<>();
    /** 当前运行可用的子任务派生能力。 */
    private SpawnTaskSupport spawnTaskSupport;
    /** 当前运行可用的任务状态查询能力。 */
    private RunQuerySupport runQuerySupport;
    /** 当前运行可用的主动通知能力。 */
    private NotificationSupport notificationSupport;

    /**
     * 返回会话键。
     *
     * @return 会话键
     */
    public String getSessionKey() {
        return sessionKey;
    }

    /**
     * 设置会话键。
     *
     * @param sessionKey 会话键
     */
    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    /**
     * 返回当前消息。
     *
     * @return 当前消息
     */
    public String getCurrentMessage() {
        return currentMessage;
    }

    /**
     * 设置当前消息。
     *
     * @param currentMessage 当前消息
     */
    public void setCurrentMessage(String currentMessage) {
        this.currentMessage = currentMessage;
    }

    /**
     * 返回当前消息的触发类型。
     *
     * @return 触发类型
     */
    public InboundTriggerType getCurrentMessageTriggerType() {
        return currentMessageTriggerType;
    }

    /**
     * 设置当前消息的触发类型。
     *
     * @param currentMessageTriggerType 触发类型
     */
    public void setCurrentMessageTriggerType(InboundTriggerType currentMessageTriggerType) {
        this.currentMessageTriggerType = currentMessageTriggerType;
    }

    /**
     * 返回当前运行是否为子任务。
     *
     * @return 若当前运行为子任务则返回 true
     */
    public boolean isChildRun() {
        return childRun;
    }

    /**
     * 设置当前运行是否为子任务。
     *
     * @param childRun 子任务标记
     */
    public void setChildRun(boolean childRun) {
        this.childRun = childRun;
    }

    /**
     * 返回父运行标识。
     *
     * @return 父运行标识
     */
    public String getParentRunId() {
        return parentRunId;
    }

    /**
     * 设置父运行标识。
     *
     * @param parentRunId 父运行标识
     */
    public void setParentRunId(String parentRunId) {
        this.parentRunId = parentRunId;
    }

    /**
     * 返回历史消息列表。
     *
     * @return 历史消息列表
     */
    public List<ChatMessage> getHistory() {
        return history;
    }

    /**
     * 设置历史消息列表。
     *
     * @param history 历史消息列表
     */
    public void setHistory(List<ChatMessage> history) {
        this.history = history;
    }

    /**
     * 返回当前运行可用的子任务派生能力。
     *
     * @return 子任务派生能力
     */
    public SpawnTaskSupport getSpawnTaskSupport() {
        return spawnTaskSupport;
    }

    /**
     * 设置当前运行可用的子任务派生能力。
     *
     * @param spawnTaskSupport 子任务派生能力
     */
    public void setSpawnTaskSupport(SpawnTaskSupport spawnTaskSupport) {
        this.spawnTaskSupport = spawnTaskSupport;
    }

    /**
     * 返回当前运行可用的任务状态查询能力。
     *
     * @return 任务状态查询能力
     */
    public RunQuerySupport getRunQuerySupport() {
        return runQuerySupport;
    }

    /**
     * 设置当前运行可用的任务状态查询能力。
     *
     * @param runQuerySupport 任务状态查询能力
     */
    public void setRunQuerySupport(RunQuerySupport runQuerySupport) {
        this.runQuerySupport = runQuerySupport;
    }

    /**
     * 返回当前运行可用的主动通知能力。
     *
     * @return 主动通知能力
     */
    public NotificationSupport getNotificationSupport() {
        return notificationSupport;
    }

    /**
     * 设置当前运行可用的主动通知能力。
     *
     * @param notificationSupport 主动通知能力
     */
    public void setNotificationSupport(NotificationSupport notificationSupport) {
        this.notificationSupport = notificationSupport;
    }
}
