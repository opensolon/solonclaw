package com.jimuqu.claw.agent.model.enums;

/**
 * 描述系统事件的执行与外发策略。
 */
public enum SystemEventPolicy {
    /** 仅允许内部处理，不允许最终普通回复外发。 */
    INTERNAL_ONLY,
    /** 允许用户可见动作；可使用 notify_user，必要时可兜底外发一次提醒文案。 */
    USER_VISIBLE_OPTIONAL,
    /** 仅允许 continuation 聚合型回复；必须通过 FINAL_REPLY_ONCE: 明确声明。 */
    AGGREGATE_ONLY
}
