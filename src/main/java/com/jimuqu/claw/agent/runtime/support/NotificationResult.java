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
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 是否成功发送。 */
    private boolean delivered;
    /** 实际投递的会话键。 */
    private String sessionKey;
    /** 结果说明。 */
    private String message;
    /** 是否发生截断。 */
    private boolean truncated;
    /** 是否按多段发送。 */
    private boolean segmented;
    /** 发送段数。 */
    private int segmentCount;
    /** 原始文本长度。 */
    private int originalLength;
    /** 实际发出文本总长度。 */
    private int finalLength;
    /** 外发渠道类型。 */
    private String channelType;
}
