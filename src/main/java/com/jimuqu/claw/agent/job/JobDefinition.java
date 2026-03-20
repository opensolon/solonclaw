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
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 任务唯一名称，用于查询、替换、启动和停止。 */
    private String name;
    /** 调度模式：fixed_rate、fixed_delay、once_delay 或 cron。 */
    private String mode;
    /** 调度值；固定频率/延迟时为毫秒，cron 模式时为 cron 表达式。 */
    private String scheduleValue;
    /** 首次执行前的延迟毫秒数。 */
    private long initialDelay;
    /** 可选时区；cron 任务通常需要结合该字段解释触发时间。 */
    private String zone;
    /** 任务当前是否启用。 */
    private boolean enabled = true;
    /** 任务载荷类型，决定这次触发走 system event 还是 agent turn。 */
    private JobPayloadKind payloadKind;
    /** 任务绑定到主会话还是隔离会话。 */
    private JobSessionTarget sessionTarget;
    /** system event 任务的唤醒策略。 */
    private JobWakeMode wakeMode = JobWakeMode.NOW;
    /** agent turn 任务的回传策略。 */
    private JobDeliveryMode deliveryMode = JobDeliveryMode.NONE;
    /** 任务绑定的主会话 sessionKey。 */
    private String boundSessionKey;
    /** 任务绑定的回复路由，用于需要对外发送结果的场景。 */
    private ReplyTarget boundReplyTarget;
    /** system event 任务触发时注入主会话的内部事件文本。 */
    private String systemEventText;
    /** agent turn 任务触发时实际执行的指令参数。 */
    private AgentTurnSpec agentTurn = new AgentTurnSpec();
    /** 任务创建时间戳（毫秒）。 */
    private long createdAt;
    /** 任务最近更新时间戳（毫秒）。 */
    private long updatedAt;
}
