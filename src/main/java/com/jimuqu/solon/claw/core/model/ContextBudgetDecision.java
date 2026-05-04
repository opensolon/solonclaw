package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 上下文预算检查结果。 */
@Getter
@Setter
@NoArgsConstructor
public class ContextBudgetDecision {
    private int estimatedTokens;
    private int thresholdTokens;
    private int contextWindowTokens;
    private boolean shouldCompress;
    private String reason;
}
