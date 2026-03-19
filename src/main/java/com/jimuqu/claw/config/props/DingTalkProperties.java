package com.jimuqu.claw.config.props;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 描述钉钉机器人配置。
 */
@Data
@NoArgsConstructor
public class DingTalkProperties implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否启用钉钉渠道。 */
    private boolean enabled;
    /** 钉钉 clientId。 */
    private String clientId = "";
    /** 钉钉 clientSecret。 */
    private String clientSecret = "";
    /** 钉钉 robotCode。 */
    private String robotCode = "";
    /** 私聊允许列表。 */
    private List<String> allowFrom = new ArrayList<String>();
    /** 群聊允许列表。 */
    private List<String> groupAllowFrom = new ArrayList<String>();
}
