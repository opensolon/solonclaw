package com.jimuqu.claw.agent.runtime.api;

import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.runtime.support.ParentRunChildrenSummary;

import java.util.List;

/**
 * 为当前运行提供子任务状态查询能力。
 */
public interface RunQuerySupport {
    /**
     * 返回当前会话最近的子任务列表。
     *
     * @param limit 最大返回条数
     * @return 子任务列表
     */
    List<AgentRun> listChildRuns(int limit);

    /**
     * 查询指定运行任务。
     *
     * @param runId 运行任务标识
     * @return 运行任务；不存在则返回 null
     */
    AgentRun getRun(String runId);

    /**
     * 返回最近一次子任务。
     *
     * @return 最近一次子任务；不存在则返回 null
     */
    AgentRun getLatestChildRun();

    /**
     * 聚合某个父运行下的所有子任务状态。
     *
     * @param parentRunId 父运行标识；为空时默认取最近一个有子任务的父运行
     * @param batchKey 子任务批次键；为空时聚合该父运行下全部子任务
     * @return 聚合结果；不存在则返回 null
     */
    ParentRunChildrenSummary getChildSummary(String parentRunId, String batchKey);
}


