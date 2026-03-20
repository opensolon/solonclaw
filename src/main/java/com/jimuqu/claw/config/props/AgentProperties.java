package com.jimuqu.claw.config.props;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述 Agent 行为配置。
 */
@Data
@NoArgsConstructor
public class AgentProperties implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 基础系统提示词。 */
    private String systemPrompt;
    /** 调度器配置。 */
    private SchedulerProperties scheduler = new SchedulerProperties();
    /** 工具配置。 */
    private ToolsProperties tools = new ToolsProperties();
    /** 子任务治理配置。 */
    private SubtasksProperties subtasks = new SubtasksProperties();
    /** 心跳配置。 */
    private HeartbeatProperties heartbeat = new HeartbeatProperties();
    /** 系统事件执行配置。 */
    private SystemEventsProperties systemEvents = new SystemEventsProperties();
    /** 定时任务执行配置。 */
    private JobsProperties jobs = new JobsProperties();
    /** 隔离 agent turn 配置。 */
    private AgentTurnProperties agentTurn = new AgentTurnProperties();
    /** 黑名单配置。 */
    private BlacklistProperties blacklist = new BlacklistProperties();
}
