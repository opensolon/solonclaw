package com.jimuqu.claw.agent.model;

/**
 * 描述一条回复应投递到的目标位置。
 */
public class ReplyTarget {
    /** 目标所属渠道。 */
    private ChannelType channelType;
    /** 目标所属会话类型。 */
    private ConversationType conversationType;
    /** 目标会话标识，例如群会话 ID。 */
    private String conversationId;
    /** 目标用户标识，例如私聊用户 staffId。 */
    private String userId;
    /** 历史兼容保留的 sessionWebhook。 */
    private String sessionWebhook;
    /** 历史兼容保留的 sessionWebhook 过期时间。 */
    private Long sessionWebhookExpiredAt;

    /**
     * 创建一个空的回复目标。
     */
    public ReplyTarget() {
    }

    /**
     * 按关键字段创建回复目标。
     *
     * @param channelType 渠道类型
     * @param conversationType 会话类型
     * @param conversationId 会话标识
     * @param userId 用户标识
     */
    public ReplyTarget(ChannelType channelType, ConversationType conversationType, String conversationId, String userId) {
        this.channelType = channelType;
        this.conversationType = conversationType;
        this.conversationId = conversationId;
        this.userId = userId;
    }

    /**
     * 返回渠道类型。
     *
     * @return 渠道类型
     */
    public ChannelType getChannelType() {
        return channelType;
    }

    /**
     * 设置渠道类型。
     *
     * @param channelType 渠道类型
     */
    public void setChannelType(ChannelType channelType) {
        this.channelType = channelType;
    }

    /**
     * 返回会话类型。
     *
     * @return 会话类型
     */
    public ConversationType getConversationType() {
        return conversationType;
    }

    /**
     * 设置会话类型。
     *
     * @param conversationType 会话类型
     */
    public void setConversationType(ConversationType conversationType) {
        this.conversationType = conversationType;
    }

    /**
     * 返回会话标识。
     *
     * @return 会话标识
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * 设置会话标识。
     *
     * @param conversationId 会话标识
     */
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    /**
     * 返回用户标识。
     *
     * @return 用户标识
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 设置用户标识。
     *
     * @param userId 用户标识
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * 返回兼容字段 sessionWebhook。
     *
     * @return sessionWebhook
     */
    public String getSessionWebhook() {
        return sessionWebhook;
    }

    /**
     * 设置兼容字段 sessionWebhook。
     *
     * @param sessionWebhook sessionWebhook
     */
    public void setSessionWebhook(String sessionWebhook) {
        this.sessionWebhook = sessionWebhook;
    }

    /**
     * 返回 sessionWebhook 过期时间。
     *
     * @return 过期时间戳
     */
    public Long getSessionWebhookExpiredAt() {
        return sessionWebhookExpiredAt;
    }

    /**
     * 设置 sessionWebhook 过期时间。
     *
     * @param sessionWebhookExpiredAt 过期时间戳
     */
    public void setSessionWebhookExpiredAt(Long sessionWebhookExpiredAt) {
        this.sessionWebhookExpiredAt = sessionWebhookExpiredAt;
    }

    /**
     * 判断当前目标是否属于调试页渠道。
     *
     * @return 若为调试页则返回 true
     */
    public boolean isDebugWeb() {
        return channelType == ChannelType.DEBUG_WEB;
    }
}
