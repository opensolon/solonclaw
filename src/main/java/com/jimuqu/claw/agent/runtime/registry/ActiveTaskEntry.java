package com.jimuqu.claw.agent.runtime.registry;

import com.jimuqu.claw.agent.model.enums.RunStatus;
import lombok.Data;

/**
 * 活跃任务条目，用于跟踪正在执行的子任务状态和运行时控制。
 */
@Data
public class ActiveTaskEntry {
    /** 子任务运行标识。 */
    private String runId;
    /** 父运行标识。 */
    private String parentRunId;
    /** 父会话键。 */
    private String parentSessionKey;
    /** 子任务会话键。 */
    private String childSessionKey;
    /** 任务标题。 */
    private String taskTitle;
    /** 任务描述。 */
    private String taskDescription;
    /** 批次键。 */
    private String batchKey;
    /** 当前运行状态。 */
    private RunStatus status;
    /** 创建时间戳。 */
    private long createdAt;
    /** 最新进度阶段标签。 */
    private String latestPhase;
    /** 最新进度描述。 */
    private String latestProgressDetail;
    /** 最新进度更新时间戳。 */
    private long latestProgressAt;
    /** 执行当前任务的线程（运行时控制，不持久化）。 */
    private volatile Thread executionThread;
    /** 合作取消标志（运行时控制，不持久化）。 */
    private volatile boolean cancelRequested;
}
