package com.jimuqu.claw.config.props;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 描述飞书机器人配置。
 */
@Data
@NoArgsConstructor
public class FeishuProperties implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 是否启用飞书渠道。 */
    private boolean enabled;
    /** 飞书 appId。 */
    private String appId = "";
    /** 飞书 appSecret。 */
    private String appSecret = "";
    /** 飞书开放平台域名。 */
    private String baseDomain = "https://open.feishu.cn";
    /** 是否启用基于卡片 patch 的流式更新。 */
    private boolean streamingReply = true;
    /** 私聊允许列表。 */
    private List<String> allowFrom = new ArrayList<String>();
    /** 群聊允许列表。 */
    private List<String> groupAllowFrom = new ArrayList<String>();
}
