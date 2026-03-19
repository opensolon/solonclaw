package com.jimuqu.claw.agent.runtime.support;

import com.jimuqu.claw.agent.model.run.AgentRun;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合某个父运行下的所有子任务状态。
 */
public class ParentRunChildrenSummary {
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
    private List<AgentRun> children = new ArrayList<>();

    public String getParentRunId() {
        return parentRunId;
    }

    public void setParentRunId(String parentRunId) {
        this.parentRunId = parentRunId;
    }

    public String getBatchKey() {
        return batchKey;
    }

    public void setBatchKey(String batchKey) {
        this.batchKey = batchKey;
    }

    public int getTotalChildren() {
        return totalChildren;
    }

    public void setTotalChildren(int totalChildren) {
        this.totalChildren = totalChildren;
    }

    public int getSucceededChildren() {
        return succeededChildren;
    }

    public void setSucceededChildren(int succeededChildren) {
        this.succeededChildren = succeededChildren;
    }

    public int getFailedChildren() {
        return failedChildren;
    }

    public void setFailedChildren(int failedChildren) {
        this.failedChildren = failedChildren;
    }

    public int getPendingChildren() {
        return pendingChildren;
    }

    public void setPendingChildren(int pendingChildren) {
        this.pendingChildren = pendingChildren;
    }

    public boolean isAllCompleted() {
        return allCompleted;
    }

    public void setAllCompleted(boolean allCompleted) {
        this.allCompleted = allCompleted;
    }

    public List<AgentRun> getChildren() {
        return children;
    }

    public void setChildren(List<AgentRun> children) {
        this.children = children;
    }
}


