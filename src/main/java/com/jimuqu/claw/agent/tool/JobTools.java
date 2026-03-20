package com.jimuqu.claw.agent.tool;

import cn.hutool.json.JSONUtil;
import com.jimuqu.claw.agent.job.AgentTurnSpec;
import com.jimuqu.claw.agent.job.JobDeliveryMode;
import com.jimuqu.claw.agent.job.JobDefinition;
import com.jimuqu.claw.agent.job.JobWakeMode;
import com.jimuqu.claw.agent.job.WorkspaceJobService;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Param;
import org.noear.solon.scheduling.ScheduledException;

/**
 * 提供定时任务管理工具。
 */
public class JobTools {
    private final WorkspaceJobService workspaceJobService;

    public JobTools(WorkspaceJobService workspaceJobService) {
        this.workspaceJobService = workspaceJobService;
    }

    @ToolMapping(name = "list_jobs", description = "列出所有定时任务")
    public String listJobs() {
        return JSONUtil.toJsonPrettyStr(workspaceJobService.listJobs());
    }

    @ToolMapping(name = "get_job", description = "获取指定定时任务详情")
    public String getJob(@Param(description = "任务名称") String name) {
        JobDefinition definition = workspaceJobService.getJob(name);
        return definition == null ? "任务不存在: " + name : JSONUtil.toJsonPrettyStr(definition);
    }

    @ToolMapping(
            name = "add_system_job",
            description = "新增 systemEvent 定时任务。对时间触发型需求，只有实际成功调用本工具后，才能声称“已安排”或“已创建”。"
                    + "mode 仅支持 fixed_rate、fixed_delay、once_delay、cron；fixed_* 与 once_delay 的 scheduleValue 单位为毫秒。"
                    + "systemEventText 必须是未来触发时仍可独立理解的完整内部事件文本，"
                    + "不能只是对当前对话的省略复述，也不能保留依赖当前语境才能理解的指代。"
                    + "创建任务后，当前轮回复只应基于本工具的真实返回结果确认已安排的时间、频率和任务目标，"
                    + "不要把未来真正要执行的结果提前在当前轮发送。"
    )
    public String addSystemJob(
            @Param(description = "任务名称") String name,
            @Param(description = "调度模式：fixed_rate、fixed_delay、once_delay、cron") String mode,
            @Param(description = "调度值：cron 表达式或毫秒值") String scheduleValue,
            @Param(description = "触发时注入主会话的 system event 文本。必须自包含、可脱离当前对话单独理解") String systemEventText,
            @Param(description = "首次执行前延迟毫秒数，可填 0") long initialDelay,
            @Param(description = "时区，可为空") String zone,
            @Param(description = "唤醒模式：NOW 或 NEXT_TICK，可为空") String wakeMode
    ) {
        return JSONUtil.toJsonPrettyStr(
                workspaceJobService.addSystemJob(name, mode, scheduleValue, systemEventText, initialDelay, zone, parseWakeMode(wakeMode))
        );
    }

    @ToolMapping(
            name = "add_agent_job",
            description = "新增 agentTurn 定时任务。对时间触发型需求，只有实际成功调用本工具后，才能声称“已安排”或“已创建”。"
                    + "mode 仅支持 fixed_rate、fixed_delay、once_delay、cron；fixed_* 与 once_delay 的 scheduleValue 单位为毫秒。"
                    + "message 必须是未来触发时交给 agent 执行的完整任务指令，要求自包含、可脱离当前对话单独理解，"
                    + "不能只是对当前对话的复述，也不能保留依赖当前语境的省略表达。"
                    + "创建任务后，当前轮回复只应基于本工具的真实返回结果确认已安排的时间、频率和任务目标，"
                    + "不要把未来真正要执行的结果提前在当前轮发送。"
    )
    public String addAgentJob(
            @Param(description = "任务名称") String name,
            @Param(description = "调度模式：fixed_rate、fixed_delay、once_delay、cron") String mode,
            @Param(description = "调度值：cron 表达式或毫秒值") String scheduleValue,
            @Param(description = "agentTurn 的任务描述。必须自包含、可独立理解，能够直接指导未来那次执行") String message,
            @Param(description = "首次执行前延迟毫秒数，可填 0") long initialDelay,
            @Param(description = "时区，可为空") String zone,
            @Param(description = "投递策略：NONE、BOUND_REPLY_TARGET、LAST_ROUTE") String deliveryMode,
            @Param(description = "可选模型覆盖") String model,
            @Param(description = "可选思考强度") String thinking,
            @Param(description = "可选超时秒数") Integer timeoutSeconds,
            @Param(description = "是否使用轻量上下文，可填 true/false") Boolean lightContext
    ) {
        AgentTurnSpec agentTurnSpec = new AgentTurnSpec();
        agentTurnSpec.setMessage(message);
        agentTurnSpec.setModel(model);
        agentTurnSpec.setThinking(thinking);
        agentTurnSpec.setTimeoutSeconds(timeoutSeconds);
        agentTurnSpec.setLightContext(lightContext != null && lightContext);

        return JSONUtil.toJsonPrettyStr(
                workspaceJobService.addAgentJob(
                        name,
                        mode,
                        scheduleValue,
                        agentTurnSpec,
                        initialDelay,
                        zone,
                        parseDeliveryMode(deliveryMode)
                )
        );
    }

    @ToolMapping(name = "remove_job", description = "删除指定定时任务")
    public String removeJob(@Param(description = "任务名称") String name) {
        return JSONUtil.toJsonPrettyStr(workspaceJobService.removeJob(name));
    }

    @ToolMapping(name = "start_job", description = "启动指定定时任务")
    public String startJob(@Param(description = "任务名称") String name) throws ScheduledException {
        return JSONUtil.toJsonPrettyStr(workspaceJobService.startJob(name));
    }

    @ToolMapping(name = "stop_job", description = "停止指定定时任务")
    public String stopJob(@Param(description = "任务名称") String name) throws ScheduledException {
        return JSONUtil.toJsonPrettyStr(workspaceJobService.stopJob(name));
    }

    private JobWakeMode parseWakeMode(String wakeMode) {
        if (wakeMode == null || wakeMode.trim().isEmpty()) {
            return null;
        }
        return JobWakeMode.valueOf(wakeMode.trim().toUpperCase());
    }

    private JobDeliveryMode parseDeliveryMode(String deliveryMode) {
        if (deliveryMode == null || deliveryMode.trim().isEmpty()) {
            return null;
        }
        return JobDeliveryMode.valueOf(deliveryMode.trim().toUpperCase());
    }
}
