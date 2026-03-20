package com.jimuqu.claw.agent.model.route;

import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述一条回复应投递到的目标位置。
 */
@Data
@NoArgsConstructor
public class ReplyTarget implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

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
}
