package com.jimuqu.claw.agent.model.event;

/**
 * 表示会话历史中的单条事件记录。
 */
public class ConversationEvent {
    /** 事件版本号。 */
    private long version;
    /** 所属会话键。 */
    private String sessionKey;
    /** 事件类型。 */
    private String eventType;
    /** 关联运行任务标识。 */
    private String runId;
    /** 来源消息标识。 */
    private String sourceMessageId;
    /** 来源用户消息对应的版本号。 */
    private long sourceUserVersion;
    /** 事件角色，例如 user、assistant、system。 */
    private String role;
    /** 事件文本内容。 */
    private String content;
    /** 事件结构化数据的 JSON 表示。 */
    private String eventDataJson;
    /** 事件创建时间戳。 */
    private long createdAt;

    /**
     * 返回事件版本号。
     *
     * @return 事件版本号
     */
    public long getVersion() {
        return version;
    }

    /**
     * 设置事件版本号。
     *
     * @param version 事件版本号
     */
    public void setVersion(long version) {
        this.version = version;
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
     * 返回事件角色。
     *
     * @return 事件角色
     */
    public String getRole() {
        return role;
    }

    /**
     * 设置事件角色。
     *
     * @param role 事件角色
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * 返回事件内容。
     *
     * @return 事件内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置事件内容。
     *
     * @param content 事件内容
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 返回事件结构化数据 JSON。
     *
     * @return 事件结构化数据 JSON
     */
    public String getEventDataJson() {
        return eventDataJson;
    }

    /**
     * 设置事件结构化数据 JSON。
     *
     * @param eventDataJson 事件结构化数据 JSON
     */
    public void setEventDataJson(String eventDataJson) {
        this.eventDataJson = eventDataJson;
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

