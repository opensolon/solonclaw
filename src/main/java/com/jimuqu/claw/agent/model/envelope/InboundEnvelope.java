package com.jimuqu.claw.agent.model.envelope;

import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一描述外部或内部入站消息的标准信封对象。
 */
@Data
@NoArgsConstructor
public class InboundEnvelope implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

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
    private List<AttachmentRef> attachments = new ArrayList<AttachmentRef>();
    /** 原路回复目标。 */
    private ReplyTarget replyTarget;
    /** 接收时间戳。 */
    private long receivedAt;
    /** 运行时内部会话键。 */
    private String sessionKey;
    /** 会话事件版本号。 */
    private long sessionVersion;
    /** 当前入站来源类型。 */
    private RuntimeSourceKind sourceKind = RuntimeSourceKind.USER_MESSAGE;
    /** 当前运行关联到的历史锚点版本。 */
    private long historyAnchorVersion;
}
