package com.jimuqu.claw.web.dto;

import com.jimuqu.claw.agent.model.run.AgentRun;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述调试页查询运行任务详情时返回的数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebugRunResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当前运行任务详情。 */
    private AgentRun run;
}
