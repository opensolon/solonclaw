package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 同一 source/session busy 时的调度决策。 */
@Getter
@Setter
@NoArgsConstructor
public class RunBusyDecision {
    private String policy;
    private String status;
    private String message;
    private String runId;
    private String queueId;
    private boolean shouldRunNow;
    private boolean queued;
    private boolean rejected;

    public static RunBusyDecision runNow(String policy) {
        RunBusyDecision decision = new RunBusyDecision();
        decision.setPolicy(policy);
        decision.setStatus("run_now");
        decision.setShouldRunNow(true);
        return decision;
    }
}
