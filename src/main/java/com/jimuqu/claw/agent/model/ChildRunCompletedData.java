package com.jimuqu.claw.agent.model;

/**
 * 描述父会话中的子任务完成事件数据。
 */
public class ChildRunCompletedData {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
