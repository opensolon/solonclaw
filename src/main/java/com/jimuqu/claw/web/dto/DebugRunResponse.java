package com.jimuqu.claw.web.dto;

import com.jimuqu.claw.agent.model.run.AgentRun;

/**
 * 描述调试页查询运行任务详情时返回的数据。
 */
public class DebugRunResponse {
    /** 当前运行任务详情。 */
    private AgentRun run;

    /**
     * 创建一个空响应对象。
     */
    public DebugRunResponse() {
    }

    /**
     * 使用运行任务创建响应对象。
     *
     * @param run 运行任务
     */
    public DebugRunResponse(AgentRun run) {
        this.run = run;
    }

    /**
     * 返回运行任务。
     *
     * @return 运行任务
     */
    public AgentRun getRun() {
        return run;
    }

    /**
     * 设置运行任务。
     *
     * @param run 运行任务
     */
    public void setRun(AgentRun run) {
        this.run = run;
    }
}


