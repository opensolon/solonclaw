package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Agent 单轮运行记录。 */
@Getter
@Setter
@NoArgsConstructor
public class AgentRunRecord {
    private String runId;
    private String sessionId;
    private String sourceKey;
    private String runKind;
    private String parentRunId;
    private String agentName;
    private String agentSnapshotJson;
    private String status;
    private String phase;
    private String busyPolicy;
    private boolean backgrounded;
    private String inputPreview;
    private String finalReplyPreview;
    private String provider;
    private String model;
    private int attempts;
    private int contextEstimateTokens;
    private int contextWindowTokens;
    private int compressionCount;
    private int fallbackCount;
    private int toolCallCount;
    private int subtaskCount;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    private long queuedAt;
    private long startedAt;
    private long heartbeatAt;
    private long lastActivityAt;
    private long finishedAt;
    private String exitReason;
    private boolean recoverable;
    private String recoveryHint;
    private String error;
}
