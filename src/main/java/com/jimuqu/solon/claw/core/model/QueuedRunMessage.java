package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Busy queue 中等待执行的用户输入。 */
@Getter
@Setter
@NoArgsConstructor
public class QueuedRunMessage {
    private String queueId;
    private String runId;
    private String sessionId;
    private String sourceKey;
    private String messageText;
    private String messageJson;
    private String status;
    private String busyPolicy;
    private long createdAt;
    private long startedAt;
    private long finishedAt;
    private String error;
}
