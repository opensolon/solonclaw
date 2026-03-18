package com.jimuqu.claw.web;

/**
 * 描述调试页提交消息后的响应体。
 */
public class DebugChatResponse {
    /** 新创建的运行任务标识。 */
    private String runId;
    /** 所属内部会话键。 */
    private String sessionKey;
    /** 当前运行状态。 */
    private String status;

    /**
     * 创建一个空响应对象。
     */
    public DebugChatResponse() {
    }

    /**
     * 使用完整字段创建响应对象。
     *
     * @param runId 运行任务标识
     * @param sessionKey 会话键
     * @param status 当前状态
     */
    public DebugChatResponse(String runId, String sessionKey, String status) {
        this.runId = runId;
        this.sessionKey = sessionKey;
        this.status = status;
    }

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
     * 返回当前状态。
     *
     * @return 当前状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置当前状态。
     *
     * @param status 当前状态
     */
    public void setStatus(String status) {
        this.status = status;
    }
}
