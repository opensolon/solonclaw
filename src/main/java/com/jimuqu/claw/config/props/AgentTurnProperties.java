package com.jimuqu.claw.config.props;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述隔离 agent turn 的默认配置。
 */
@Data
@NoArgsConstructor
public class AgentTurnProperties implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 默认超时时间，单位秒。 */
    private int defaultTimeoutSeconds = 300;
}
