package com.jimuqu.claw.agent.job;

/**
 * 描述定时任务的执行负载类型。
 */
public enum JobPayloadKind {
    /** 向主会话注入一条 system event。 */
    SYSTEM_EVENT,
    /** 启动一次独立 agent turn。 */
    AGENT_TURN
}
