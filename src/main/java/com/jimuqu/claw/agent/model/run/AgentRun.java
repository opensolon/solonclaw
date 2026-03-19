package com.jimuqu.claw.agent.model.run;

import com.jimuqu.claw.agent.model.enums.RunStatus;
import com.jimuqu.claw.agent.model.route.ReplyTarget;

/**
 * 描述单条入站消息触发的一次 Agent 执行任务。
 */
public class AgentRun {
    /** 运行任务唯一标识。 */
    private String runId;
    /** 所属会话键。 */
    private String sessionKey;
    /** 来源消息标识。 */
    private String sourceMessageId;
    /** 来源用户消息版本号。 */
    private long sourceUserVersion;
    /** 父运行任务标识；为空表示根运行。 */
    private String parentRunId;
    /** 父运行所属会话键。 */
    private String parentSessionKey;
    /** 父运行原路回复目标。 */
    private ReplyTarget parentReplyTarget;
    /** 当前运行承载的任务描述。 */
    private String taskDescription;
    /** 当前运行所属的子任务批次键。 */
    private String batchKey;
    /** 原路回复目标。 */
    private ReplyTarget replyTarget;
    /** 当前运行状态。 */
    private RunStatus status;
    /** 创建时间戳。 */
    private long createdAt;
    /** 开始执行时间戳。 */
    private long startedAt;
    /** 完成时间戳。 */
    private long finishedAt;
    /** 最终回复文本。 */
    private String finalResponse;
    /** 错误信息。 */
    private String errorMessage;

    /**
     * 返回运行任务标识。
     *
     * @return 运行任务标识
     */
    public String getRunId() {
        return runId;
    }

    /**
     * 设置运行任务标识。
     *
     * @param runId 运行任务标识
     */
    public void setRunId(String runId) {
        this.runId = runId;
    }

    /**
     * 返回会话键。
     *
     * @return 会话键
     */
    public String getSessionKey() {
        return sessionKey;
    }

    /**
     * 设置会话键。
     *
     * @param sessionKey 会话键
     */
    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    /**
     * 返回来源消息标识。
     *
     * @return 来源消息标识
     */
    public String getSourceMessageId() {
        return sourceMessageId;
    }

    /**
     * 设置来源消息标识。
     *
     * @param sourceMessageId 来源消息标识
     */
    public void setSourceMessageId(String sourceMessageId) {
        this.sourceMessageId = sourceMessageId;
    }

    /**
     * 返回来源用户消息版本号。
     *
     * @return 来源用户消息版本号
     */
    public long getSourceUserVersion() {
        return sourceUserVersion;
    }

    /**
     * 设置来源用户消息版本号。
     *
     * @param sourceUserVersion 来源用户消息版本号
     */
    public void setSourceUserVersion(long sourceUserVersion) {
        this.sourceUserVersion = sourceUserVersion;
    }

    /**
     * 返回父运行任务标识。
     *
     * @return 父运行任务标识
     */
    public String getParentRunId() {
        return parentRunId;
    }

    /**
     * 设置父运行任务标识。
     *
     * @param parentRunId 父运行任务标识
     */
    public void setParentRunId(String parentRunId) {
        this.parentRunId = parentRunId;
    }

    /**
     * 返回父运行所属会话键。
     *
     * @return 父运行所属会话键
     */
    public String getParentSessionKey() {
        return parentSessionKey;
    }

    /**
     * 设置父运行所属会话键。
     *
     * @param parentSessionKey 父运行所属会话键
     */
    public void setParentSessionKey(String parentSessionKey) {
        this.parentSessionKey = parentSessionKey;
    }

    /**
     * 返回父运行原路回复目标。
     *
     * @return 父运行原路回复目标
     */
    public ReplyTarget getParentReplyTarget() {
        return parentReplyTarget;
    }

    /**
     * 设置父运行原路回复目标。
     *
     * @param parentReplyTarget 父运行原路回复目标
     */
    public void setParentReplyTarget(ReplyTarget parentReplyTarget) {
        this.parentReplyTarget = parentReplyTarget;
    }

    /**
     * 返回当前运行承载的任务描述。
     *
     * @return 当前运行任务描述
     */
    public String getTaskDescription() {
        return taskDescription;
    }

    /**
     * 设置当前运行承载的任务描述。
     *
     * @param taskDescription 当前运行任务描述
     */
    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    /**
     * 返回当前运行所属的子任务批次键。
     *
     * @return 子任务批次键
     */
    public String getBatchKey() {
        return batchKey;
    }

    /**
     * 设置当前运行所属的子任务批次键。
     *
     * @param batchKey 子任务批次键
     */
    public void setBatchKey(String batchKey) {
        this.batchKey = batchKey;
    }

    /**
     * 返回回复目标。
     *
     * @return 回复目标
     */
    public ReplyTarget getReplyTarget() {
        return replyTarget;
    }

    /**
     * 设置回复目标。
     *
     * @param replyTarget 回复目标
     */
    public void setReplyTarget(ReplyTarget replyTarget) {
        this.replyTarget = replyTarget;
    }

    /**
     * 返回当前状态。
     *
     * @return 当前状态
     */
    public RunStatus getStatus() {
        return status;
    }

    /**
     * 设置当前状态。
     *
     * @param status 当前状态
     */
    public void setStatus(RunStatus status) {
        this.status = status;
    }

    /**
     * 返回创建时间。
     *
     * @return 创建时间
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间。
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 返回开始时间。
     *
     * @return 开始时间
     */
    public long getStartedAt() {
        return startedAt;
    }

    /**
     * 设置开始时间。
     *
     * @param startedAt 开始时间
     */
    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    /**
     * 返回完成时间。
     *
     * @return 完成时间
     */
    public long getFinishedAt() {
        return finishedAt;
    }

    /**
     * 设置完成时间。
     *
     * @param finishedAt 完成时间
     */
    public void setFinishedAt(long finishedAt) {
        this.finishedAt = finishedAt;
    }

    /**
     * 返回最终回复文本。
     *
     * @return 最终回复文本
     */
    public String getFinalResponse() {
        return finalResponse;
    }

    /**
     * 设置最终回复文本。
     *
     * @param finalResponse 最终回复文本
     */
    public void setFinalResponse(String finalResponse) {
        this.finalResponse = finalResponse;
    }

    /**
     * 返回错误信息。
     *
     * @return 错误信息
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 设置错误信息。
     *
     * @param errorMessage 错误信息
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

