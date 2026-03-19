package com.jimuqu.claw.agent.model.event;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 表示一次运行任务在执行过程中的事件。
 */
@Data
@NoArgsConstructor
public class RunEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 事件序号。 */
    private long seq;
    /** 所属运行任务标识。 */
    private String runId;
    /** 事件类型。 */
    private String eventType;
    /** 事件消息文本。 */
    private String message;
    /** 事件创建时间戳。 */
    private long createdAt;
}
