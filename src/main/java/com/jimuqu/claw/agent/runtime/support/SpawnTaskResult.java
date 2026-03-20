package com.jimuqu.claw.agent.runtime.support;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述一次子任务派生的结果。
 */
@Data
@NoArgsConstructor
public class SpawnTaskResult implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 新建子运行标识。 */
    private String runId;
    /** 子会话键。 */
    private String sessionKey;
    /** 任务描述。 */
    private String taskDescription;
    /** 子任务批次键。 */
    private String batchKey;
}
