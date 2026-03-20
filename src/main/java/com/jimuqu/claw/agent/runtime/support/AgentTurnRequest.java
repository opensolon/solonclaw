package com.jimuqu.claw.agent.runtime.support;

import com.jimuqu.claw.agent.job.AgentTurnSpec;
import com.jimuqu.claw.agent.job.JobDeliveryMode;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述一次隔离 agent turn 执行请求。
 */
@Data
@NoArgsConstructor
public class AgentTurnRequest implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 请求来源类型，固定为 JOB_AGENT_TURN。 */
    private RuntimeSourceKind sourceKind = RuntimeSourceKind.JOB_AGENT_TURN;
    /** 触发此次执行的任务名称。 */
    private String jobName;
    /** 任务创建时绑定的主会话 sessionKey。 */
    private String boundSessionKey;
    /** 任务创建时绑定的回复路由。 */
    private ReplyTarget boundReplyTarget;
    /** 本次执行完成后如何把结果投递回外部会话。 */
    private JobDeliveryMode deliveryMode = JobDeliveryMode.NONE;
    /** 本次隔离 agent turn 的执行参数。 */
    private AgentTurnSpec agentTurn = new AgentTurnSpec();
}
