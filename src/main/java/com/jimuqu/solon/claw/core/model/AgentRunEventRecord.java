package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Agent 运行时间线事件。 */
@Getter
@Setter
@NoArgsConstructor
public class AgentRunEventRecord {
    private String eventId;
    private String runId;
    private String sessionId;
    private String sourceKey;
    private String eventType;
    private String phase;
    private String severity;
    private int attemptNo;
    private String provider;
    private String model;
    private String summary;
    private String metadataJson;
    private long createdAt;
}
