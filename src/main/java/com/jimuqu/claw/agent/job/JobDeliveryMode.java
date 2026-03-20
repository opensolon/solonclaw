package com.jimuqu.claw.agent.job;

/**
 * 描述 agentTurn 定时任务的投递策略。
 */
public enum JobDeliveryMode {
    /** 不对外投递。 */
    NONE,
    /** 投递到任务绑定的回复目标。 */
    BOUND_REPLY_TARGET,
    /** 投递到当前最近一次外部可回复路由。 */
    LAST_ROUTE
}
