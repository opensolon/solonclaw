package com.jimuqu.claw.channel.weixin.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述微信语音消息片段。
 */
@Data
@NoArgsConstructor
public class WeixinVoiceItem implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 语音转文字结果。 */
    private String text;
}
