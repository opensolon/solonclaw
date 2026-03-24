package com.jimuqu.claw.config.props;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 描述微信 Bot 渠道配置。
 */
@Data
@NoArgsConstructor
public class WeixinProperties implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 是否启用微信渠道。 */
    private boolean enabled;
    /** 微信 iLink Bot 基础地址。 */
    private String baseUrl = "https://ilinkai.weixin.qq.com";
    /** 二维码登录使用的 botType。 */
    private String botType = "3";
    /** 长轮询超时时间（毫秒）。 */
    private int longPollTimeoutMs = 35000;
    /** 二维码登录等待超时时间（毫秒）。 */
    private int loginTimeoutMs = 480000;
    /** 私聊允许列表；为空时默认放行。 */
    private List<String> allowFrom = new ArrayList<String>();
}
