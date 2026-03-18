package com.jimuqu.claw.agent.model;

/**
 * 描述父会话中的子任务创建事件数据。
 */
public class ChildRunSpawnedData {
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

    public String getParentRunId() {
        return parentRunId;
    }

    public void setParentRunId(String parentRunId) {
        this.parentRunId = parentRunId;
    }

    public String getChildRunId() {
        return childRunId;
    }

    public void setChildRunId(String childRunId) {
        this.childRunId = childRunId;
    }

    public String getChildSessionKey() {
        return childSessionKey;
    }

    public void setChildSessionKey(String childSessionKey) {
        this.childSessionKey = childSessionKey;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    public String getBatchKey() {
        return batchKey;
    }

    public void setBatchKey(String batchKey) {
        this.batchKey = batchKey;
    }
}
