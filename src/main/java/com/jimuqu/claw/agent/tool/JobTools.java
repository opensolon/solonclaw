package com.jimuqu.claw.agent.tool;

import cn.hutool.json.JSONUtil;
import com.jimuqu.claw.agent.job.JobDefinition;
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

    @ToolMapping(name = "add_job", description = "新增定时任务。mode 仅支持 fixed_rate、fixed_delay、once_delay、cron；fixed_* 与 once_delay 的 scheduleValue 单位为毫秒")
    public String addJob(
            @Param(description = "任务名称") String name,
            @Param(description = "调度模式：fixed_rate、fixed_delay、once_delay、cron") String mode,
            @Param(description = "调度值：cron 表达式或毫秒值") String scheduleValue,
            @Param(description = "触发时提交给 Agent 的任务提示词") String prompt,
            @Param(description = "首次执行前延迟毫秒数，可填 0") long initialDelay,
            @Param(description = "时区，可为空") String zone
    ) {
        return JSONUtil.toJsonPrettyStr(
                workspaceJobService.addJob(name, mode, scheduleValue, prompt, initialDelay, zone)
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
}
