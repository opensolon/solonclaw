package com.jimuqu.claw.agent.runtime.support;

import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.enums.SystemEventPolicy;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述一次系统事件执行请求。
 */
@Data
@NoArgsConstructor
public class SystemEventRequest implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 请求来源类型，如定时任务、心跳或子任务 continuation。 */
    private RuntimeSourceKind sourceKind;
    /** 系统事件的对外可见策略。 */
    private SystemEventPolicy policy = SystemEventPolicy.INTERNAL_ONLY;
    /** 要投递到的目标会话 sessionKey。 */
    private String sessionKey;
    /** 当前事件可使用的回复路由。 */
    private ReplyTarget replyTarget;
    /** 本次系统事件注入给 Agent 的文本内容。 */
    private String content;
    /** 关联的用户消息版本号，用于历史重建和 continuation 对齐。 */
    private long sourceUserVersion;
    /** 关联运行 ID，常用于父任务 continuation 聚合。 */
    private String relatedRunId;
    /** 是否允许本次系统事件调用 notify_user 主动通知外部会话。 */
    private boolean allowNotifyUser;
    /** 是否在提交时立即唤醒执行；否则进入待处理队列。 */
    private boolean wakeImmediately = true;
}
