package com.jimuqu.claw.agent.job;

/**
 * 描述 system event 定时任务的唤醒模式。
 */
public enum JobWakeMode {
    /** 触发后立即处理。 */
    NOW,
    /** 触发后仅记录事件，等待下一次内部轮询。 */
    NEXT_TICK
}
