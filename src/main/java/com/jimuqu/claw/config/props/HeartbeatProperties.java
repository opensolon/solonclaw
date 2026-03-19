package com.jimuqu.claw.config.props;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述心跳任务配置。
 */
@Data
@NoArgsConstructor
public class HeartbeatProperties implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否启用心跳。 */
    private boolean enabled = true;
    /** 心跳触发间隔，单位秒。 */
    private int intervalSeconds = 1800;
}
