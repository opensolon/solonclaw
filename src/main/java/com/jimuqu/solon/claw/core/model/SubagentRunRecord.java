package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 父 run 派生出的子 Agent 运行记录。 */
@Getter
@Setter
@NoArgsConstructor
public class SubagentRunRecord {
    private String subagentId;
    private String parentRunId;
    private String childRunId;
    private String parentSourceKey;
    private String childSourceKey;
    private String sessionId;
    private String name;
    private String goalPreview;
    private String status;
    private boolean active;
    private boolean interruptRequested;
    private int depth;
    private int taskIndex;
    private String outputTailJson;
    private String error;
    private long startedAt;
    private long finishedAt;
    private long heartbeatAt;
}
