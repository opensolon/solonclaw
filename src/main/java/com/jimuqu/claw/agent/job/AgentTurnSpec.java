package com.jimuqu.claw.agent.job;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述一次隔离 agent turn 的执行参数。
 */
@Data
@NoArgsConstructor
public class AgentTurnSpec implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 任务描述或执行指令。 */
    private String message;
    /** 可选模型覆盖。 */
    private String model;
    /** 可选思考强度。 */
    private String thinking;
    /** 可选超时时间，单位秒。 */
    private Integer timeoutSeconds;
    /** 是否使用轻量上下文。 */
    private boolean lightContext;
}
