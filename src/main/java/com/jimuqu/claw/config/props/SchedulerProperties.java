package com.jimuqu.claw.config.props;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述并发调度配置。
 */
@Data
@NoArgsConstructor
public class SchedulerProperties implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 单会话最大并发数。 */
    private int maxConcurrentPerConversation = 4;
    /** 忙时是否立即回执确认消息。 */
    private boolean ackWhenBusy = true;
}
