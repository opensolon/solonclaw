package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** stale/crash/max-step 等恢复线索。 */
@Getter
@Setter
@NoArgsConstructor
public class RunRecoveryRecord {
    private String recoveryId;
    private String runId;
    private String sessionId;
    private String sourceKey;
    private String recoveryType;
    private String status;
    private String summary;
    private String payloadJson;
    private long createdAt;
    private long resolvedAt;
}
