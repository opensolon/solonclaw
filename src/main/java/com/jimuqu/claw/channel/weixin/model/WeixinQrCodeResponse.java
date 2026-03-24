package com.jimuqu.claw.channel.weixin.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述微信二维码登录启动响应。
 */
@Data
@NoArgsConstructor
public class WeixinQrCodeResponse implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 二维码会话标识。 */
    private String qrcode;
    /** 二维码图片内容地址。 */
    private String qrcode_img_content;
}
