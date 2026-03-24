package com.jimuqu.claw.channel.weixin.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述微信二维码登录状态响应。
 */
@Data
@NoArgsConstructor
public class WeixinQrStatusResponse implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 当前状态：wait/scaned/confirmed/expired。 */
    private String status;
    /** 登录成功后的 Bot token。 */
    private String bot_token;
    /** 微信侧返回的 Bot 账号标识。 */
    private String ilink_bot_id;
    /** 实际基础地址。 */
    private String baseurl;
    /** 扫码用户标识。 */
    private String ilink_user_id;
}
