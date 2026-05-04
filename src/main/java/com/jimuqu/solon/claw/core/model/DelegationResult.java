package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 子代理执行结果。 */
@Getter
@Setter
@NoArgsConstructor
public class DelegationResult {
    /** 子代理 ID。 */
    private String subagentId;

    /** 任务名称。 */
    private String name;

    /** 子 run ID。 */
    private String runId;

    /** 子会话 ID。 */
    private String sessionId;

    /** 子 source key。 */
    private String sourceKey;

    /** 执行摘要。 */
    private String content;

    /** 是否失败。 */
    private boolean error;
}
