package com.jimuqu.claw.agent.model.enums;

/**
 * 定义单次 Agent 运行任务的状态枚举。
 */
public enum RunStatus {
    /** 任务已创建但尚未开始执行。 */
    QUEUED,
    /** 任务正在执行中。 */
    RUNNING,
    /** 当前运行已派生子任务，等待子任务结果回流后继续。 */
    WAITING_CHILDREN,
    /** 任务执行成功。 */
    SUCCEEDED,
    /** 任务执行失败。 */
    FAILED,
    /** 任务被主动取消。 */
    CANCELLED,
    /** 任务因进程中断等原因被异常终止。 */
    ABORTED
}

