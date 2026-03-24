package com.jimuqu.claw.channel.weixin.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述启动微信扫码登录后的结果。
 */
@Data
@NoArgsConstructor
public class WeixinQrStartResult implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 登录会话标识。 */
    private String sessionKey;
    /** 二维码图片地址。 */
    private String qrCodeUrl;
    /** 结果说明。 */
    private String message;
}
