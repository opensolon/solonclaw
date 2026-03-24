package com.jimuqu.claw.channel.weixin.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述一条微信消息中的内容项。
 */
@Data
@NoArgsConstructor
public class WeixinMessageItem implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 内容类型：1 文本，2 图片，3 语音，4 文件，5 视频。 */
    private Integer type;
    /** 文本内容。 */
    private WeixinTextItem text_item;
    /** 语音内容。 */
    private WeixinVoiceItem voice_item;
    /** 文件内容。 */
    private WeixinFileItem file_item;
}
