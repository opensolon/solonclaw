package com.jimuqu.solon.claw.core.model;

import com.jimuqu.solon.claw.core.enums.PlatformType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 统一消息投递请求。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryRequest {
    /** 目标平台。 */
    private PlatformType platform;

    /** 目标会话 ID。 */
    private String chatId;

    /** 目标用户 ID。 */
    private String userId;

    /** 会话类型。 */
    private String chatType;

    /** 线程或话题 ID。 */
    private String threadId;

    /** 要投递的文本内容。 */
    private String text;

    /** 要投递的附件列表。 */
    private List<MessageAttachment> attachments = new ArrayList<MessageAttachment>();

    /** 渠道定制扩展参数。 */
    private Map<String, Object> channelExtras = new LinkedHashMap<String, Object>();
}
