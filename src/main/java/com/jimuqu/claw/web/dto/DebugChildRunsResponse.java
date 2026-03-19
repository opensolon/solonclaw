package com.jimuqu.claw.web.dto;

import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.runtime.support.ParentRunChildrenSummary;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述调试页查询父子任务关系时返回的数据。
 */
public class DebugChildRunsResponse {
    /** 父运行下的子任务列表。 */
    private List<AgentRun> children = new ArrayList<>();
    /** 子任务聚合摘要。 */
    private ParentRunChildrenSummary summary;

    public List<AgentRun> getChildren() {
        return children;
    }

    public void setChildren(List<AgentRun> children) {
        this.children = children;
    }

    public ParentRunChildrenSummary getSummary() {
        return summary;
    }

    public void setSummary(ParentRunChildrenSummary summary) {
        this.summary = summary;
    }
}



