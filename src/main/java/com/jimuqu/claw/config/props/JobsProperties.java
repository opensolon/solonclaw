package com.jimuqu.claw.config.props;

import com.jimuqu.claw.agent.job.JobDeliveryMode;
import com.jimuqu.claw.agent.job.JobWakeMode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述定时任务默认行为配置。
 */
@Data
@NoArgsConstructor
public class JobsProperties implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** systemEvent 任务默认唤醒模式。 */
    private JobWakeMode defaultWakeMode = JobWakeMode.NOW;
    /** agentTurn 任务默认投递策略。 */
    private JobDeliveryMode defaultDeliveryMode = JobDeliveryMode.BOUND_REPLY_TARGET;
}
