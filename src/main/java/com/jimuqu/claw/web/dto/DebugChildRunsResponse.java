package com.jimuqu.claw.web.dto;

import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.runtime.support.ParentRunChildrenSummary;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 描述调试页查询父子任务关系时返回的数据。
 */
@Data
@NoArgsConstructor
public class DebugChildRunsResponse implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 父运行下的子任务列表。 */
    private List<AgentRun> children = new ArrayList<AgentRun>();
    /** 子任务聚合摘要。 */
    private ParentRunChildrenSummary summary;
}
