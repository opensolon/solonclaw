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

    /** 单会话系统事件/子任务最大并发数。 */
    private int maxConcurrentPerConversation = 4;
    /** 单会话用户消息最大并发数（默认 1 = 严格串行）。 */
    private int maxConcurrentUserMessage = 1;
    /** 忙时是否立即回执确认消息。 */
    private boolean ackWhenBusy = true;
    /** 取消任务合作超时秒数，超时后强制线程中断。 */
    private int cancelCooperativeTimeoutSeconds = 30;
    /** 进度事件采样最小间隔毫秒数。 */
    private long progressSamplingIntervalMs = 500;
}
