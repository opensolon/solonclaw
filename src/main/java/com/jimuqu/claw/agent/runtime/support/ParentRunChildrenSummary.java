package com.jimuqu.claw.agent.runtime.support;

import com.jimuqu.claw.agent.model.run.AgentRun;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 聚合某个父运行下的所有子任务状态。
 */
@Data
@NoArgsConstructor
public class ParentRunChildrenSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 父运行标识。 */
    private String parentRunId;
    /** 聚合使用的批次键。 */
    private String batchKey;
    /** 子任务总数。 */
    private int totalChildren;
    /** 已成功子任务数。 */
    private int succeededChildren;
    /** 已失败子任务数。 */
    private int failedChildren;
    /** 仍未结束子任务数。 */
    private int pendingChildren;
    /** 是否全部结束。 */
    private boolean allCompleted;
    /** 聚合的子任务列表。 */
    private List<AgentRun> children = new ArrayList<AgentRun>();
}
