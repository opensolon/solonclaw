package com.jimuqu.claw.agent.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一描述外部或内部入站消息的标准信封对象。
 */
public class InboundEnvelope {
    /** 上游消息唯一标识。 */
    private String messageId;
    /** 入站消息来源渠道。 */
    private ChannelType channelType;
    /** 渠道实例标识，用于多实例扩展。 */
    private String channelInstanceId;
    /** 发送者标识。 */
    private String senderId;
    /** 会话标识。 */
    private String conversationId;
    /** 会话类型。 */
    private ConversationType conversationType;
    /** 文本内容。 */
    private String content;
    /** 附件引用列表。 */
    private List<AttachmentRef> attachments = new ArrayList<>();
    /** 原路回复目标。 */
    private ReplyTarget replyTarget;
    /** 接收时间戳。 */
    private long receivedAt;
    /** 运行时内部会话键。 */
    private String sessionKey;
    /** 会话事件版本号。 */
    private long sessionVersion;
    /** 是否允许将最终回复回发到外部渠道。 */
    private boolean externalReplyEnabled = true;
    /** 是否将当前入站消息写入会话历史。 */
    private boolean persistInboundConversationEvent = true;
    /** 是否将本次运行产生的助手回复写入会话历史。 */
    private boolean persistAssistantConversationEvent = true;

    /**
     * 返回消息唯一标识。
     *
     * @return 消息唯一标识
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * 设置消息唯一标识。
     *
     * @param messageId 消息唯一标识
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
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
     * 返回渠道实例标识。
     *
     * @return 渠道实例标识
     */
    public String getChannelInstanceId() {
        return channelInstanceId;
    }

    /**
     * 设置渠道实例标识。
     *
     * @param channelInstanceId 渠道实例标识
     */
    public void setChannelInstanceId(String channelInstanceId) {
        this.channelInstanceId = channelInstanceId;
    }

    /**
     * 返回发送者标识。
     *
     * @return 发送者标识
     */
    public String getSenderId() {
        return senderId;
    }

    /**
     * 设置发送者标识。
     *
     * @param senderId 发送者标识
     */
    public void setSenderId(String senderId) {
        this.senderId = senderId;
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
     * 返回消息文本内容。
     *
     * @return 文本内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置消息文本内容。
     *
     * @param content 文本内容
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 返回附件引用列表。
     *
     * @return 附件引用列表
     */
    public List<AttachmentRef> getAttachments() {
        return attachments;
    }

    /**
     * 设置附件引用列表。
     *
     * @param attachments 附件引用列表
     */
    public void setAttachments(List<AttachmentRef> attachments) {
        this.attachments = attachments;
    }

    /**
     * 返回原路回复目标。
     *
     * @return 原路回复目标
     */
    public ReplyTarget getReplyTarget() {
        return replyTarget;
    }

    /**
     * 设置原路回复目标。
     *
     * @param replyTarget 原路回复目标
     */
    public void setReplyTarget(ReplyTarget replyTarget) {
        this.replyTarget = replyTarget;
    }

    /**
     * 返回接收时间戳。
     *
     * @return 接收时间戳
     */
    public long getReceivedAt() {
        return receivedAt;
    }

    /**
     * 设置接收时间戳。
     *
     * @param receivedAt 接收时间戳
     */
    public void setReceivedAt(long receivedAt) {
        this.receivedAt = receivedAt;
    }

    /**
     * 返回内部会话键。
     *
     * @return 内部会话键
     */
    public String getSessionKey() {
        return sessionKey;
    }

    /**
     * 设置内部会话键。
     *
     * @param sessionKey 内部会话键
     */
    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    /**
     * 返回会话版本号。
     *
     * @return 会话版本号
     */
    public long getSessionVersion() {
        return sessionVersion;
    }

    /**
     * 设置会话版本号。
     *
     * @param sessionVersion 会话版本号
     */
    public void setSessionVersion(long sessionVersion) {
        this.sessionVersion = sessionVersion;
    }

    /**
     * 返回是否允许外部回发。
     *
     * @return 若允许外部回发则返回 true
     */
    public boolean isExternalReplyEnabled() {
        return externalReplyEnabled;
    }

    /**
     * 设置是否允许外部回发。
     *
     * @param externalReplyEnabled 外部回发标记
     */
    public void setExternalReplyEnabled(boolean externalReplyEnabled) {
        this.externalReplyEnabled = externalReplyEnabled;
    }

    /**
     * 返回是否持久化当前入站事件。
     *
     * @return 若持久化则返回 true
     */
    public boolean isPersistInboundConversationEvent() {
        return persistInboundConversationEvent;
    }

    /**
     * 设置是否持久化当前入站事件。
     *
     * @param persistInboundConversationEvent 入站事件持久化标记
     */
    public void setPersistInboundConversationEvent(boolean persistInboundConversationEvent) {
        this.persistInboundConversationEvent = persistInboundConversationEvent;
    }

    /**
     * 返回是否持久化助手回复事件。
     *
     * @return 若持久化则返回 true
     */
    public boolean isPersistAssistantConversationEvent() {
        return persistAssistantConversationEvent;
    }

    /**
     * 设置是否持久化助手回复事件。
     *
     * @param persistAssistantConversationEvent 助手回复事件持久化标记
     */
    public void setPersistAssistantConversationEvent(boolean persistAssistantConversationEvent) {
        this.persistAssistantConversationEvent = persistAssistantConversationEvent;
    }
}
