package com.jimuqu.solon.claw.core.model;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.support.constants.GatewayBehaviorConstants;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 统一网关入站消息模型。 */
@Getter
@Setter
@NoArgsConstructor
public class GatewayMessage {
    /** 消息来源平台。 */
    private PlatformType platform;

    /** 会话或群 ID。 */
    private String chatId;

    /** 发送者用户 ID。 */
    private String userId;

    /** 会话类型，取值见 {@link GatewayBehaviorConstants}。 */
    private String chatType;

    /** 会话展示名称。 */
    private String chatName;

    /** 发送者展示名称。 */
    private String userName;

    /** 消息文本内容。 */
    private String text;

    /** 渠道线程 ID。 */
    private String threadId;

    /** 来源键覆盖值，供逻辑子会话等场景复用同一消息模型。 */
    private String sourceKeyOverride;

    /** 是否为 heartbeat 触发的合成消息。 */
    private boolean heartbeat;

    /** 入站附件列表。 */
    private List<MessageAttachment> attachments = new ArrayList<MessageAttachment>();

    /** 入站时间戳。 */
    private long timestamp;

    /** 便捷构造方法，用于构建最小可用入站消息。 */
    public GatewayMessage(PlatformType platform, String chatId, String userId, String text) {
        this.platform = platform;
        this.chatId = chatId;
        this.userId = userId;
        this.chatType = GatewayBehaviorConstants.CHAT_TYPE_DM;
        this.chatName = chatId;
        this.userName = userId;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 构造来源键，作为会话绑定与工具状态隔离的主键。
     *
     * @return 平台:会话:用户 组成的来源键
     */
    public String sourceKey() {
        if (StrUtil.isNotBlank(sourceKeyOverride)) {
            return sourceKeyOverride;
        }
        return String.valueOf(platform) + ":" + nullToEmpty(chatId) + ":" + nullToEmpty(userId);
    }

    /** 将空值转为空字符串，避免来源键出现字面量 null。 */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
