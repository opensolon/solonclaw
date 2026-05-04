package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Supervisor 执行结果。 */
@Getter
@Setter
@NoArgsConstructor
public class AgentRunOutcome {
    private String finalReply;
    private LlmResult result;
    private AgentRunRecord runRecord;
    private String compressionWarning;
    private String provider;
    private String model;
    private int contextEstimateTokens;
    private int contextWindowTokens;
    private String cwd;
}
