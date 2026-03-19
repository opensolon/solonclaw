package com.jimuqu.claw.config.props;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述所有渠道配置的聚合对象。
 */
@Data
@NoArgsConstructor
public class ChannelsProperties implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 飞书渠道配置。 */
    private FeishuProperties feishu = new FeishuProperties();
    /** 钉钉渠道配置。 */
    private DingTalkProperties dingtalk = new DingTalkProperties();
}
