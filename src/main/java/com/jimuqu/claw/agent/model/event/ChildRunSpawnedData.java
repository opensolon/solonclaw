package com.jimuqu.claw.agent.model.event;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述父会话中的子任务创建事件数据。
 */
@Data
@NoArgsConstructor
public class ChildRunSpawnedData implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 父运行标识。 */
    private String parentRunId;
    /** 子运行标识。 */
    private String childRunId;
    /** 子会话键。 */
    private String childSessionKey;
    /** 任务描述。 */
    private String taskDescription;
    /** 子任务批次键。 */
    private String batchKey;
}
