package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Dashboard/渠道对长任务发出的控制命令。 */
@Getter
@Setter
@NoArgsConstructor
public class RunControlCommand {
    private String commandId;
    private String runId;
    private String sourceKey;
    private String command;
    private String payloadJson;
    private String status;
    private long createdAt;
    private long handledAt;
}
