package com.jimuqu.claw.agent.model.event;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述父会话中的子任务完成事件数据。
 */
@Data
@NoArgsConstructor
public class ChildRunCompletedData implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 父运行标识。 */
    private String parentRunId;
    /** 子运行标识。 */
    private String childRunId;
    /** 子会话键。 */
    private String childSessionKey;
    /** 子任务状态。 */
    private String status;
    /** 任务描述。 */
    private String taskDescription;
    /** 子任务批次键。 */
    private String batchKey;
    /** 子任务结果。 */
    private String result;
    /** 子任务错误。 */
    private String errorMessage;
}
