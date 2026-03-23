package com.jimuqu.claw.agent.runtime.support;

import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.runtime.api.NotificationSupport;
import com.jimuqu.claw.agent.runtime.api.ProgressReportSupport;
import com.jimuqu.claw.agent.runtime.api.RunQuerySupport;
import com.jimuqu.claw.agent.runtime.api.SpawnTaskSupport;
import com.jimuqu.claw.agent.runtime.api.TaskControlSupport;
import com.jimuqu.claw.agent.runtime.registry.ActiveTaskEntry;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.noear.solon.ai.chat.message.ChatMessage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 描述一次会话执行所需的上下文输入。
 */
@Data
@NoArgsConstructor
public class ConversationExecutionRequest implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 当前会话对应的内部键。 */
    private String sessionKey;
    /** 当前待处理的用户消息。 */
    private String currentMessage;
    /** 当前消息的来源类型。 */
    private RuntimeSourceKind currentSourceKind = RuntimeSourceKind.USER_MESSAGE;
    /** 当前运行是否启用轻量上下文。 */
    private boolean lightContext;
    /** 当前运行是否为父任务派生出的子任务。 */
    private boolean childRun;
    /** 当前子任务对应的父运行标识。 */
    private String parentRunId;
    /** 当前运行的任务标题。 */
    private String taskTitle;
    /** 历史消息列表。 */
    private List<ChatMessage> history = new ArrayList<ChatMessage>();
    /** 当前运行可用的子任务派生能力。 */
    private SpawnTaskSupport spawnTaskSupport;
    /** 当前运行可用的任务状态查询能力。 */
    private RunQuerySupport runQuerySupport;
    /** 当前运行可用的主动通知能力。 */
    private NotificationSupport notificationSupport;
    /** 当前运行可用的进度报告能力。 */
    private ProgressReportSupport progressReportSupport;
    /** 当前运行可用的子任务控制能力。 */
    private TaskControlSupport taskControlSupport;
    /** 当前运行标识。 */
    private String runId;
    /** 当前会话的活跃子任务快照，用于注入系统提示词。 */
    private List<ActiveTaskEntry> activeTasks;
}
