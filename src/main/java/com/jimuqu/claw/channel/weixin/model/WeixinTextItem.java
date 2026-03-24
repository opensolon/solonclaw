package com.jimuqu.claw.channel.weixin.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述微信文本消息片段。
 */
@Data
@NoArgsConstructor
public class WeixinTextItem implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 文本内容。 */
    private String text;
}
