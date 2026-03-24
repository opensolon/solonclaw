package com.jimuqu.claw.channel.weixin.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述等待微信扫码登录完成后的结果。
 */
@Data
@NoArgsConstructor
public class WeixinQrWaitResult implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 是否已完成登录。 */
    private boolean connected;
    /** 平台内部规范化账号标识。 */
    private String accountId;
    /** 微信侧原始 Bot 账号标识。 */
    private String remoteAccountId;
    /** 扫码用户标识。 */
    private String userId;
    /** 结果说明。 */
    private String message;
}
