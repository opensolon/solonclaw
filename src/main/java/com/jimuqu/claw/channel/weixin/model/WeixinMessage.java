package com.jimuqu.claw.channel.weixin.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 描述 getUpdates 返回的一条微信消息。
 */
@Data
@NoArgsConstructor
public class WeixinMessage implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 消息唯一标识。 */
    private Long message_id;
    /** 发送者标识。 */
    private String from_user_id;
    /** 接收者标识。 */
    private String to_user_id;
    /** 创建时间戳（毫秒）。 */
    private Long create_time_ms;
    /** 消息类型：1 USER，2 BOT。 */
    private Integer message_type;
    /** 消息内容项。 */
    private List<WeixinMessageItem> item_list = new ArrayList<WeixinMessageItem>();
    /** 回复时必须回传的上下文令牌。 */
    private String context_token;
}
