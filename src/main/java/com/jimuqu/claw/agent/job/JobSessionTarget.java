package com.jimuqu.claw.agent.job;

/**
 * 描述定时任务执行时使用主会话还是隔离会话。
 */
public enum JobSessionTarget {
    /** 主会话 system event。 */
    MAIN,
    /** 隔离的独立 agent turn。 */
    ISOLATED
}
