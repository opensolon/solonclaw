package com.jimuqu.claw.agent.model.run;

import com.jimuqu.claw.agent.model.enums.RunStatus;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述单条入站消息触发的一次 Agent 执行任务。
 */
@Data
@NoArgsConstructor
public class AgentRun implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 运行任务唯一标识。 */
    private String runId;
    /** 所属会话键。 */
    private String sessionKey;
    /** 来源消息标识。 */
    private String sourceMessageId;
    /** 运行来源类型。 */
    private RuntimeSourceKind sourceKind = RuntimeSourceKind.USER_MESSAGE;
    /** 来源用户消息版本号。 */
    private long sourceUserVersion;
    /** 父运行任务标识；为空表示根运行。 */
    private String parentRunId;
    /** 父运行所属会话键。 */
    private String parentSessionKey;
    /** 父运行原路回复目标。 */
    private ReplyTarget parentReplyTarget;
    /** 当前运行承载的任务描述。 */
    private String taskDescription;
    /** 当前运行承载的任务标题。 */
    private String taskTitle;
    /** 当前运行所属的子任务批次键。 */
    private String batchKey;
    /** 当前运行关联的业务运行标识，例如 continuation 对应的父运行。 */
    private String relatedRunId;
    /** 原路回复目标。 */
    private ReplyTarget replyTarget;
    /** 当前运行状态。 */
    private RunStatus status;
    /** 创建时间戳。 */
    private long createdAt;
    /** 开始执行时间戳。 */
    private long startedAt;
    /** 完成时间戳。 */
    private long finishedAt;
    /** 最终回复文本。 */
    private String finalResponse;
    /** 错误信息。 */
    private String errorMessage;
    /** 最新进度阶段标签。 */
    private String latestPhase;
    /** 最新进度描述。 */
    private String latestProgressDetail;
    /** 最新进度更新时间戳。 */
    private long latestProgressAt;
}
