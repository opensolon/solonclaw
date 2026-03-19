package com.jimuqu.claw.agent.model.enums;

/**
 * 描述一次入站触发在运行时中的语义类型。
 */
public enum InboundTriggerType {
    /** 用户主动发起的普通消息。 */
    USER,
    /** 需要进入会话轨迹、但不应被视作用户发言的系统触发。 */
    SYSTEM_VISIBLE,
    /** 仅用于内部检查的静默系统触发。 */
    SYSTEM_SILENT
}

