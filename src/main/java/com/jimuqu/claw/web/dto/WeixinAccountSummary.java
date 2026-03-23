package com.jimuqu.claw.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述微信账号的对外摘要信息。
 */
@Data
@NoArgsConstructor
public class WeixinAccountSummary implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 平台内部账号标识。 */
    private String accountId;
    /** 微信侧原始 Bot 账号标识。 */
    private String remoteAccountId;
    /** 扫码登录用户标识。 */
    private String userId;
    /** 实际基础地址。 */
    private String baseUrl;
    /** 最近保存时间。 */
    private long savedAt;
}
