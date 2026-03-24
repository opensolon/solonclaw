package com.jimuqu.claw.agent.model.enums;

/**
 * 描述一次运行请求的来源类型。
 */
public enum RuntimeSourceKind {
    /** 用户主动发送的消息。 */
    USER_MESSAGE,
    /** 定时任务的 systemEvent 触发。 */
    JOB_SYSTEM_EVENT,
    /** 定时任务的 agentTurn 触发。 */
    JOB_AGENT_TURN,
    /** 心跳内部检查触发。 */
    HEARTBEAT_EVENT,
    /** 子任务完成后的父会话 continuation。 */
    CHILD_CONTINUATION
}
