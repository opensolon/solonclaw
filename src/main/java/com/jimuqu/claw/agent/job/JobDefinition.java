package com.jimuqu.claw.agent.job;

import com.jimuqu.claw.agent.model.route.ReplyTarget;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 定义一个可持久化的定时任务。
 */
@Data
@NoArgsConstructor
public class JobDefinition implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String mode;
    private String scheduleValue;
    private long initialDelay;
    private String zone;
    private boolean enabled = true;
    private JobPayloadKind payloadKind;
    private JobSessionTarget sessionTarget;
    private JobWakeMode wakeMode = JobWakeMode.NOW;
    private JobDeliveryMode deliveryMode = JobDeliveryMode.NONE;
    private String boundSessionKey;
    private ReplyTarget boundReplyTarget;
    private String systemEventText;
    private AgentTurnSpec agentTurn = new AgentTurnSpec();
    /** 兼容旧版 add_job 持久化字段。 */
    @Deprecated
    private String prompt;
    /** 兼容旧版 add_job 持久化字段。 */
    @Deprecated
    private String sessionKey;
    /** 兼容旧版 add_job 持久化字段。 */
    @Deprecated
    private ReplyTarget replyTarget;
    private long createdAt;
    private long updatedAt;
}
