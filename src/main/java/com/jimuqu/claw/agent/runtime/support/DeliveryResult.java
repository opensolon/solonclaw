package com.jimuqu.claw.agent.runtime.support;

import com.jimuqu.claw.agent.model.enums.ChannelType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述一次出站发送结果。
 */
@Data
@NoArgsConstructor
public class DeliveryResult implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 是否成功发送。 */
    private boolean delivered;
    /** 是否发生截断。 */
    private boolean truncated;
    /** 是否按分隔符拆成多段发送。 */
    private boolean segmented;
    /** 实际发送段数。 */
    private int segmentCount;
    /** 原始文本长度。 */
    private int originalLength;
    /** 实际发出的总文本长度。 */
    private int finalLength;
    /** 渠道类型。 */
    private ChannelType channelType;
    /** 结果说明。 */
    private String message;
}
