package com.jimuqu.claw.agent.runtime;

/**
 * 描述一次子任务派生的结果。
 */
public class SpawnTaskResult {
    /** 新建子运行标识。 */
    private String runId;
    /** 子会话键。 */
    private String sessionKey;
    /** 任务描述。 */
    private String taskDescription;
    /** 子任务批次键。 */
    private String batchKey;

    /**
     * 返回子运行标识。
     *
     * @return 子运行标识
     */
    public String getRunId() {
        return runId;
    }

    /**
     * 设置子运行标识。
     *
     * @param runId 子运行标识
     */
    public void setRunId(String runId) {
        this.runId = runId;
    }

    /**
     * 返回子会话键。
     *
     * @return 子会话键
     */
    public String getSessionKey() {
        return sessionKey;
    }

    /**
     * 设置子会话键。
     *
     * @param sessionKey 子会话键
     */
    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    /**
     * 返回任务描述。
     *
     * @return 任务描述
     */
    public String getTaskDescription() {
        return taskDescription;
    }

    /**
     * 设置任务描述。
     *
     * @param taskDescription 任务描述
     */
    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    /**
     * 返回子任务批次键。
     *
     * @return 子任务批次键
     */
    public String getBatchKey() {
        return batchKey;
    }

    /**
     * 设置子任务批次键。
     *
     * @param batchKey 子任务批次键
     */
    public void setBatchKey(String batchKey) {
        this.batchKey = batchKey;
    }
}
