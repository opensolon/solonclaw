package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 长任务 Todo 一等状态记录。 */
@Getter
@Setter
@NoArgsConstructor
public class TodoRecord {
    private String todoId;
    private String runId;
    private String sessionId;
    private String sourceKey;
    private String content;
    private String status;
    private int sortOrder;
    private long createdAt;
    private long updatedAt;
}
