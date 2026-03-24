package com.jimuqu.claw.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述启动微信扫码登录的请求。
 */
@Data
@NoArgsConstructor
public class WeixinLoginStartRequest implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 可选的登录会话标识；为空时自动生成。 */
    private String sessionKey;
}
