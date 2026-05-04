package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Agent run 内的一次工具调用轨迹。 */
@Getter
@Setter
@NoArgsConstructor
public class ToolCallRecord {
    private String toolCallId;
    private String runId;
    private String sessionId;
    private String sourceKey;
    private String toolName;
    private String status;
    private String argsPreview;
    private String resultPreview;
    private String resultRef;
    private String error;
    private boolean readOnly;
    private boolean interruptible;
    private boolean sideEffecting;
    private boolean resultIndexable;
    private int outputLimitBytes;
    private long resultSizeBytes;
    private String executionPolicy;
    private long startedAt;
    private long finishedAt;
    private long durationMs;
}
