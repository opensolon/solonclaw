package com.jimuqu.claw.agent.runtime.support;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述一次主动通知的结果。
 */
@Data
@NoArgsConstructor
public class NotificationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否成功发送。 */
    private boolean delivered;
    /** 实际投递的会话键。 */
    private String sessionKey;
    /** 结果说明。 */
    private String message;
}
