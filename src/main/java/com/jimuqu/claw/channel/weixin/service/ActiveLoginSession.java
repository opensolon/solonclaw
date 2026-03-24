package com.jimuqu.claw.channel.weixin.service;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述一次活跃的微信扫码登录会话。
 */
@Data
@NoArgsConstructor
public class ActiveLoginSession implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 登录会话标识。 */
    private String sessionKey;
    /** 二维码标识。 */
    private String qrCode;
    /** 二维码图片地址。 */
    private String qrCodeUrl;
    /** 会话启动时间。 */
    private long startedAt;
}
