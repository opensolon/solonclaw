package com.jimuqu.claw.agent.model.event;

/**
 * 表示一次运行任务在执行过程中的事件。
 */
public class RunEvent {
    /** 事件序号。 */
    private long seq;
    /** 所属运行任务标识。 */
    private String runId;
    /** 事件类型。 */
    private String eventType;
    /** 事件消息文本。 */
    private String message;
    /** 事件创建时间戳。 */
    private long createdAt;

    /**
     * 返回事件序号。
     *
     * @return 事件序号
     */
    public long getSeq() {
        return seq;
    }

    /**
     * 设置事件序号。
     *
     * @param seq 事件序号
     */
    public void setSeq(long seq) {
        this.seq = seq;
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
     * 返回事件类型。
     *
     * @return 事件类型
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * 设置事件类型。
     *
     * @param eventType 事件类型
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * 返回事件消息文本。
     *
     * @return 事件消息文本
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置事件消息文本。
     *
     * @param message 事件消息文本
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 返回事件创建时间。
     *
     * @return 事件创建时间
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置事件创建时间。
     *
     * @param createdAt 事件创建时间
     */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}

