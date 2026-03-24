package com.jimuqu.claw.channel.weixin.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述一个已登录的微信 Bot 账号。
 */
@Data
@NoArgsConstructor
public class WeixinAccount implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 平台内部使用的规范化账号标识。 */
    private String accountId;
    /** 微信侧返回的原始 bot 账号标识。 */
    private String remoteAccountId;
    /** Bot token。 */
    private String token;
    /** 对应基础地址。 */
    private String baseUrl;
    /** 扫码登录用户标识。 */
    private String userId;
    /** 最近保存时间。 */
    private long savedAt;
}
