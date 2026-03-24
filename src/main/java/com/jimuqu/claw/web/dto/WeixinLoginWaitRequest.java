package com.jimuqu.claw.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述等待微信扫码登录完成的请求。
 */
@Data
@NoArgsConstructor
public class WeixinLoginWaitRequest implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 登录会话标识。 */
    private String sessionKey;
}
