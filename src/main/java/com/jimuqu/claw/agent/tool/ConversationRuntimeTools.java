package com.jimuqu.claw.agent.tool;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.runtime.support.NotificationResult;
import com.jimuqu.claw.agent.runtime.api.NotificationSupport;
import com.jimuqu.claw.agent.runtime.api.ProgressReportSupport;
import com.jimuqu.claw.agent.runtime.support.ParentRunChildrenSummary;
import com.jimuqu.claw.agent.runtime.support.RunTurnControl;
import com.jimuqu.claw.agent.runtime.support.SpawnTaskResult;
import com.jimuqu.claw.agent.runtime.api.SpawnTaskSupport;
import com.jimuqu.claw.agent.runtime.api.RunQuerySupport;
import com.jimuqu.claw.agent.runtime.api.TaskControlSupport;
import com.jimuqu.claw.agent.runtime.support.TaskControlResult;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Param;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 聚合基础工作区工具与运行时编排工具。
 */
public class ConversationRuntimeTools {
    private static final String DIRECT_REPLY_HINT = "本轮子任务已经安排完成，请直接面向用户回复，不要继续调用工具。";
    /** 基础工作区工具。 */
    private final WorkspaceAgentTools workspaceAgentTools;
    /** 子任务派生能力。 */
    private final SpawnTaskSupport spawnTaskSupport;
    /** 任务状态查询能力。 */
    private final RunQuerySupport runQuerySupport;
    /** 主动通知能力。 */
    private final NotificationSupport notificationSupport;
    /** 进度报告能力。 */
    private final ProgressReportSupport progressReportSupport;
    /** 子任务控制能力。 */
    private final TaskControlSupport taskControlSupport;

    /**
     * 创建运行时工具集。
     *
     * @param workspaceAgentTools   基础工作区工具
     * @param spawnTaskSupport      子任务派生能力
     * @param runQuerySupport       任务状态查询能力
     * @param notificationSupport   主动通知能力
     * @param progressReportSupport 进度报告能力
     * @param taskControlSupport    子任务控制能力
     */
    public ConversationRuntimeTools(
            WorkspaceAgentTools workspaceAgentTools,
            SpawnTaskSupport spawnTaskSupport,
            RunQuerySupport runQuerySupport,
            NotificationSupport notificationSupport,
            ProgressReportSupport progressReportSupport,
            TaskControlSupport taskControlSupport
    ) {
        this.workspaceAgentTools = workspaceAgentTools;
        this.spawnTaskSupport = spawnTaskSupport;
        this.runQuerySupport = runQuerySupport;
        this.notificationSupport = notificationSupport;
        this.progressReportSupport = progressReportSupport;
        this.taskControlSupport = taskControlSupport;
    }

    /**
     * 创建运行时工具集。
     *
     * @param workspaceAgentTools   基础工作区工具
     * @param spawnTaskSupport      子任务派生能力
     * @param runQuerySupport       任务状态查询能力
     * @param notificationSupport   主动通知能力
     * @param progressReportSupport 进度报告能力
     */
    public ConversationRuntimeTools(
            WorkspaceAgentTools workspaceAgentTools,
            SpawnTaskSupport spawnTaskSupport,
            RunQuerySupport runQuerySupport,
            NotificationSupport notificationSupport,
            ProgressReportSupport progressReportSupport
    ) {
        this(workspaceAgentTools, spawnTaskSupport, runQuerySupport, notificationSupport, progressReportSupport, null);
    }

    /**
     * 创建运行时工具集（兼容旧构造器）。
     */
    public ConversationRuntimeTools(
            WorkspaceAgentTools workspaceAgentTools,
            SpawnTaskSupport spawnTaskSupport,
            RunQuerySupport runQuerySupport,
            NotificationSupport notificationSupport
    ) {
        this(workspaceAgentTools, spawnTaskSupport, runQuerySupport, notificationSupport, null, null);
    }

    @ToolMapping(name = "read_file", description = "读取工作区内指定文件的文本内容")
    public String readFile(@Param(description = "工作区内的相对路径，或工作区内的绝对路径") String filePath) throws Exception {
        if (shouldReplyInsteadOfCallingTools()) {
            return DIRECT_REPLY_HINT;
        }
        return workspaceAgentTools.readFile(filePath);
    }

    @ToolMapping(name = "write_file", description = "写入工作区内文件；如目录不存在则自动创建；会覆盖原文件")
    public String writeFile(
            @Param(description = "工作区内的相对路径，或工作区内的绝对路径") String filePath,
            @Param(description = "要写入的完整文本内容") String content
    ) throws Exception {
        if (shouldReplyInsteadOfCallingTools()) {
            return DIRECT_REPLY_HINT;
        }
        return workspaceAgentTools.writeFile(filePath, content);
    }

    @ToolMapping(name = "edit_file", description = "修改工作区内文件中的指定文本片段；仅当旧文本存在时才会替换")
    public String editFile(
            @Param(description = "工作区内的相对路径，或工作区内的绝对路径") String filePath,
            @Param(description = "需要被替换的原始文本") String oldText,
            @Param(description = "替换后的新文本") String newText
    ) throws Exception {
        if (shouldReplyInsteadOfCallingTools()) {
            return DIRECT_REPLY_HINT;
        }
        return workspaceAgentTools.editFile(filePath, oldText, newText);
    }

    @ToolMapping(name = "notify_user", description = "向当前会话已绑定的用户主动发送通知；")
    public String notifyUser(
            @Param(description = "通知内容") String message,
            @Param(description = "是否标记为进度通知，可填 true/false", required = false) Boolean progress
    ) {
        if (notificationSupport == null) {
            return "当前运行不支持 notify_user";
        }
        if (StrUtil.isBlank(message)) {
            return "通知失败: message 不能为空";
        }

        NotificationResult result = notificationSupport.notifyUser(message.trim(), progress != null && progress);
        return result.isDelivered()
                ? "通知已发送。sessionKey=" + result.getSessionKey() + ", detail=" + result.getMessage()
                : "通知失败: " + result.getMessage();
    }

    @ToolMapping(name = "report_progress", description = "向父任务上报当前子任务的执行阶段和进展详情，父任务可据此向用户同步状态")
    public String reportProgress(
            @Param(description = "当前阶段标签，如：信息收集、代码分析、总结中") String phase,
            @Param(description = "详细进展说明") String detail
    ) {
        if (progressReportSupport == null) {
            return "当前运行不支持 report_progress";
        }
        if (StrUtil.isBlank(phase)) {
            return "进度上报失败: phase 不能为空";
        }

        try {
            progressReportSupport.reportProgress(phase.trim(), StrUtil.blankToDefault(detail, "").trim());
            return "进度已上报: phase=" + phase.trim();
        } catch (Exception e) {
            return "进度上报失败: " + e.getMessage();
        }
    }


    @ToolMapping(name = "cancel_task", description = "取消一个正在运行或排队中的子任务。适合用户要求停止后台调研、分析或执行时使用")
    public String cancelTask(
            @Param(description = "要取消的子任务 runId") String runId
    ) {
        if (taskControlSupport == null) {
            return "当前运行不支持 cancel_task";
        }
        if (StrUtil.isBlank(runId)) {
            return "取消失败: runId 不能为空";
        }
        TaskControlResult result = taskControlSupport.cancelTask(runId.trim());
        return result.isSuccess() ? result.getMessage() : "取消失败: " + result.getMessage();
    }

    @ToolMapping(name = "append_instruction", description = "向正在运行的子任务追加修正信息、补充要求或新的关注重点。适合用户要求修改正在执行任务的方向时使用")
    public String appendInstruction(
            @Param(description = "目标子任务 runId") String runId,
            @Param(description = "要追加给子任务的新指令") String instruction
    ) {
        if (taskControlSupport == null) {
            return "当前运行不支持 append_instruction";
        }
        if (StrUtil.isBlank(runId)) {
            return "追加指令失败: runId 不能为空";
        }
        if (StrUtil.isBlank(instruction)) {
            return "追加指令失败: instruction 不能为空";
        }
        TaskControlResult result = taskControlSupport.appendInstruction(runId.trim(), instruction.trim());
        return result.isSuccess() ? result.getMessage() : "追加指令失败: " + result.getMessage();
    }

    @ToolMapping(name = "spawn_task", description = "当你无法立刻直接回答时，优先用它创建子 Agent 去完成具体执行、检查、搜索或分析。适合长任务、复杂任务和可并行任务；子任务完成后会自动以内部事件回流父会话，便于继续汇总与追踪进度")
    public String spawnTask(
            @Param(description = "子任务标题，例如：分析 SolonCode 任务。标题应简短明确，用于日志、汇总和区分不同子任务") String taskTitle,
            @Param(description = "子任务描述，建议写成单一、清晰、可执行的目标；不要直接复述整段对话") String taskDescription,
            @Param(description = "可选的批次键/计划键；同一批长任务或同一阶段的子任务可复用同一个 batchKey，方便后续统一汇总", required = false) String batchKey
    ) {
        if (shouldReplyInsteadOfCallingTools()) {
            return DIRECT_REPLY_HINT;
        }
        if (spawnTaskSupport == null) {
            return "当前运行不支持 spawn_task";
        }
        if (StrUtil.isBlank(taskTitle)) {
            return "创建子任务失败: taskTitle 不能为空";
        }

        try {
            SpawnTaskResult result = spawnTaskSupport.spawnTask(taskTitle, taskDescription, batchKey);
            RunTurnControl turnControl = RunTurnControl.current();
            if (turnControl != null) {
                turnControl.onTaskSpawned(result);
            }
            return "已创建子任务。childRunId=" + result.getRunId()
                    + ", childSessionKey=" + result.getSessionKey()
                    + ", title=" + StrUtil.blankToDefault(result.getTaskTitle(), "(未记录任务标题)")
                    + ", task=" + result.getTaskDescription()
                    + (StrUtil.isBlank(result.getBatchKey()) ? "" : ", batchKey=" + result.getBatchKey());
        } catch (RuntimeException e) {
            return "创建子任务失败: " + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName());
        }
    }

    @ToolMapping(name = "list_child_runs", description = "查看当前会话最近的子任务列表。适合在长任务拆分后追踪后台进度，返回 runId、状态、任务描述和结果摘要")
    public String listChildRuns(
            @Param(description = "最大返回条数，默认 5", required = false) Integer limit,
            @Param(description = "可选批次键；传入后仅查看该批次的子任务", required = false) String batchKey
    ) {
        if (shouldReplyInsteadOfCallingTools()) {
            return DIRECT_REPLY_HINT;
        }
        if (runQuerySupport == null) {
            return "当前运行不支持 list_child_runs";
        }

        int resolvedLimit = limit == null ? 5 : Math.max(1, Math.min(limit, 20));
        List<AgentRun> runs = runQuerySupport.listChildRuns(resolvedLimit);
        if (StrUtil.isNotBlank(batchKey)) {
            runs = runs.stream()
                    .filter(run -> StrUtil.equals(batchKey.trim(), run.getBatchKey()))
                    .collect(Collectors.toList());
        }
        if (runs == null || runs.isEmpty()) {
            return "当前会话还没有子任务。";
        }

        StringBuilder builder = new StringBuilder("最近子任务如下:\n");
        int index = 1;
        for (AgentRun run : runs) {
            builder.append(index++)
                    .append(". runId=").append(run.getRunId())
                    .append(", status=").append(run.getStatus())
                    .append(", task=").append(StrUtil.blankToDefault(run.getTaskDescription(), "(未记录任务描述)"));
            if (StrUtil.isNotBlank(run.getBatchKey())) {
                builder.append(", batchKey=").append(run.getBatchKey());
            }
            if (StrUtil.isNotBlank(run.getFinalResponse())) {
                builder.append(", result=").append(truncate(run.getFinalResponse(), 120));
            } else if (StrUtil.isNotBlank(run.getErrorMessage())) {
                builder.append(", error=").append(truncate(run.getErrorMessage(), 120));
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    @ToolMapping(name = "get_run_status", description = "查看指定 runId 的状态。适合在长任务执行过程中追踪某个关键子任务；若不传 runId，则默认查看最近一个子任务")
    public String getRunStatus(
            @Param(description = "运行任务标识，可为空；为空时默认查看最近一个子任务", required = false) String runId
    ) {
        if (shouldReplyInsteadOfCallingTools()) {
            return DIRECT_REPLY_HINT;
        }
        if (runQuerySupport == null) {
            return "当前运行不支持 get_run_status";
        }

        AgentRun run = StrUtil.isBlank(runId)
                ? runQuerySupport.getLatestChildRun()
                : runQuerySupport.getRun(runId.trim());
        if (run == null) {
            return StrUtil.isBlank(runId) ? "当前会话还没有子任务。" : "未找到对应 runId: " + runId;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("runId=").append(run.getRunId()).append('\n');
        builder.append("status=").append(run.getStatus()).append('\n');
        builder.append("sessionKey=").append(run.getSessionKey()).append('\n');
        if (StrUtil.isNotBlank(run.getBatchKey())) {
            builder.append("batchKey=").append(run.getBatchKey()).append('\n');
        }
        builder.append("task=").append(StrUtil.blankToDefault(run.getTaskDescription(), "(未记录任务描述)")).append('\n');
        if (StrUtil.isNotBlank(run.getLatestPhase())) {
            builder.append("latestPhase=").append(run.getLatestPhase()).append('\n');
        }
        if (StrUtil.isNotBlank(run.getLatestProgressDetail())) {
            builder.append("latestProgressDetail=").append(run.getLatestProgressDetail()).append('\n');
        }
        if (run.getLatestProgressAt() > 0) {
            builder.append("latestProgressAt=").append(run.getLatestProgressAt()).append('\n');
        }
        if (StrUtil.isNotBlank(run.getFinalResponse())) {
            builder.append("result=").append(run.getFinalResponse()).append('\n');
        }
        if (StrUtil.isNotBlank(run.getErrorMessage())) {
            builder.append("error=").append(run.getErrorMessage()).append('\n');
        }
        return builder.toString().trim();
    }

    @ToolMapping(name = "get_child_summary", description = "聚合查看某个父运行下的全部子任务状态。适合在父任务需要统一汇总前，快速判断整批长任务是否都已完成；不传 parentRunId 时默认查看最近一个有子任务的父运行")
    public String getChildSummary(
            @Param(description = "父运行标识，可为空；为空时默认查看最近一个有子任务的父运行", required = false) String parentRunId,
            @Param(description = "可选批次键；传入后仅聚合该批次的子任务", required = false) String batchKey
    ) {
        if (shouldReplyInsteadOfCallingTools()) {
            return DIRECT_REPLY_HINT;
        }
        if (runQuerySupport == null) {
            return "当前运行不支持 get_child_summary";
        }

        ParentRunChildrenSummary summary = runQuerySupport.getChildSummary(
                StrUtil.blankToDefault(parentRunId, null),
                StrUtil.blankToDefault(batchKey, null)
        );
        if (summary == null || summary.getTotalChildren() == 0) {
            return "未找到对应父运行的子任务。";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("parentRunId=").append(summary.getParentRunId()).append('\n');
        if (StrUtil.isNotBlank(summary.getBatchKey())) {
            builder.append("batchKey=").append(summary.getBatchKey()).append('\n');
        }
        builder.append("totalChildren=").append(summary.getTotalChildren()).append('\n');
        builder.append("succeededChildren=").append(summary.getSucceededChildren()).append('\n');
        builder.append("failedChildren=").append(summary.getFailedChildren()).append('\n');
        builder.append("pendingChildren=").append(summary.getPendingChildren()).append('\n');
        builder.append("allCompleted=").append(summary.isAllCompleted()).append('\n');
        builder.append("children:\n");
        int index = 1;
        for (AgentRun child : summary.getChildren()) {
            builder.append(index++)
                    .append(". runId=").append(child.getRunId())
                    .append(", status=").append(child.getStatus())
                    .append(", task=").append(StrUtil.blankToDefault(child.getTaskDescription(), "(未记录任务描述)"));
            if (StrUtil.isNotBlank(child.getBatchKey())) {
                builder.append(", batchKey=").append(child.getBatchKey());
            }
            if (StrUtil.isNotBlank(child.getFinalResponse())) {
                builder.append(", result=").append(truncate(child.getFinalResponse(), 120));
            } else if (StrUtil.isNotBlank(child.getErrorMessage())) {
                builder.append(", error=").append(truncate(child.getErrorMessage(), 120));
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private boolean shouldReplyInsteadOfCallingTools() {
        RunTurnControl turnControl = RunTurnControl.current();
        return turnControl != null && turnControl.isFinalizeAfterSpawn();
    }
}


