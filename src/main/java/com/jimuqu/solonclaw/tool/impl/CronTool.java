package com.jimuqu.solonclaw.tool.impl;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solonclaw.scheduler.SchedulerService;
import com.jimuqu.solonclaw.scheduler.SchedulerService.JobHistory;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Cron 任务管理工具
 * <p>
 * 用于通过 Agent 创建、删除、列出和执行定时任务
 *
 * @author SolonClaw
 */
@Component
public class CronTool {

    private static final Logger log = LoggerFactory.getLogger(CronTool.class);

    @Inject
    private SchedulerService schedulerService;

    /**
     * 管理定时任务
     *
     * @param action 操作类型: add, remove, list, run, history
     * @param name 任务名称（add, remove, run 需要）
     * @param schedule Cron 表达式或固定频率（add 需要）
     * @param command 要执行的命令（add 需要）
     * @param sessionType 会话类型: MAIN 或 ISOLATED（add 需要）
     * @param fixedRateMs 固定频率（毫秒），优先级高于 schedule（add 需要）
     * @return 操作结果
     */
    @ToolMapping(description = """
            管理定时任务（Cron Jobs）

            ## 操作类型（必须指定 action 参数）：

            ### 1. action=add - 创建定时任务
            参数说明：
            - name: 任务名称（必填），如 "my-task"
            - fixedRateMs: 固定间隔（毫秒），如 5000 表示每5秒执行一次
            - schedule: Cron 表达式，如 "0 9 * * *" 表示每天9点执行（与 fixedRateMs 二选一）
            - command: 要执行的命令（必填），如 "echo hello"
            - sessionType: 会话模式，MAIN 或 ISOLATED（默认 MAIN）

            示例：创建每10秒执行的任务
            - action=add, name="test-task", fixedRateMs=10000, command="echo hello", sessionType="ISOLATED"

            ### 2. action=remove - 删除任务
            参数：name=任务名称

            ### 3. action=list - 列出所有任务

            ### 4. action=run - 立即执行任务
            参数：name=任务名称

            ### 5. action=history - 查看执行历史
            参数（可选）：name=任务名称
            """)
    public String cron(
            String action,
            String name,
            String schedule,
            String command,
            String sessionType,
            Long fixedRateMs
    ) {
        log.info("Cron tool: action={}, name={}", action, name);

        try {
            // 默认操作
            if (StrUtil.isBlank(action)) {
                action = "list";
            }
            action = action.toLowerCase().trim();

            switch (action) {
                case "add":
                    return handleAdd(name, schedule, command, sessionType, fixedRateMs);
                case "remove":
                case "delete":
                    return handleRemove(name);
                case "list":
                case "ls":
                    return handleList();
                case "run":
                case "execute":
                    return handleRun(name);
                case "history":
                    return handleHistory(name);
                default:
                    return formatError("未知操作: " + action + "，支持的操作为: add, remove, list, run, history");
            }
        } catch (Exception e) {
            log.error("Cron 工具执行失败", e);
            return formatError("执行失败: " + e.getMessage());
        }
    }

    /**
     * 处理添加任务
     */
    private String handleAdd(String name, String schedule, String command, String sessionType, Long fixedRateMs) {
        if (StrUtil.isBlank(name)) {
            return formatError("任务名称不能为空");
        }

        boolean isFixedRate = fixedRateMs != null && fixedRateMs > 0;
        String actualSchedule = schedule;
        long actualFixedRate = fixedRateMs != null ? fixedRateMs : 0;

        // 如果指定了 fixedRateMs，优先使用
        if (isFixedRate) {
            actualFixedRate = fixedRateMs;
            actualSchedule = null;
        } else if (StrUtil.isBlank(schedule)) {
            return formatError("必须指定 schedule（Cron 表达式）或 fixedRateMs（固定频率毫秒数）");
        }

        // 解析会话类型
        SchedulerService.SessionType actualSessionType = SchedulerService.SessionType.MAIN;
        if (StrUtil.isNotBlank(sessionType)) {
            try {
                actualSessionType = SchedulerService.SessionType.valueOf(sessionType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return formatError("无效的 sessionType: " + sessionType + "，请使用 MAIN 或 ISOLATED");
            }
        }

        // 添加任务
        boolean success;
        if (isFixedRate) {
            success = schedulerService.addFixedRateJob(name, actualFixedRate, command);
        } else {
            success = schedulerService.addCronJob(name, actualSchedule, command, actualSessionType,
                    SchedulerService.MessageMode.STANDARD, null, null);
        }

        if (success) {
            return formatSuccess("任务创建成功: " + name,
                    Map.of(
                            "name", name,
                            "schedule", isFixedRate ? "fixedRate:" + actualFixedRate + "ms" : actualSchedule,
                            "command", command,
                            "sessionType", actualSessionType.name()
                    ));
        } else {
            return formatError("任务已存在: " + name);
        }
    }

    /**
     * 处理删除任务
     */
    private String handleRemove(String name) {
        if (StrUtil.isBlank(name)) {
            return formatError("任务名称不能为空");
        }

        boolean success = schedulerService.removeJob(name);
        if (success) {
            return formatSuccess("任务删除成功: " + name);
        } else {
            return formatError("任务不存在: " + name);
        }
    }

    /**
     * 处理列出任务
     */
    private String handleList() {
        var jobs = schedulerService.getJobs();
        if (jobs.isEmpty()) {
            return "暂无定时任务";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("定时任务列表（共 ").append(jobs.size()).append(" 个）：\n\n");

        for (var job : jobs) {
            sb.append("## ").append(job.name()).append("\n");
            sb.append("- 类型: ").append(job.jobType()).append("\n");
            if (job.jobType() == SchedulerService.JobType.CRON) {
                sb.append("- 调度: ").append(job.cron()).append("\n");
            } else {
                sb.append("- 间隔: ").append(job.fixedRate()).append("ms\n");
            }
            sb.append("- 命令: ").append(job.command()).append("\n");
            sb.append("- 会话: ").append(job.sessionType()).append("\n");
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 处理立即执行任务
     */
    private String handleRun(String name) {
        if (StrUtil.isBlank(name)) {
            return formatError("任务名称不能为空");
        }

        // 检查任务是否存在
        var job = schedulerService.getJob(name);
        if (job == null) {
            return formatError("任务不存在: " + name);
        }

        // 注意：当前实现不支持立即执行，需要通过 Agent 手动触发
        return "任务 " + name + " 已注册，将在下次调度时间执行。\n" +
                "- 类型: " + job.jobType() + "\n" +
                "- 命令: " + job.command();
    }

    /**
     * 处理查看历史
     */
    private String handleHistory(String name) {
        List<JobHistory> history;
        if (StrUtil.isNotBlank(name)) {
            history = schedulerService.getJobHistoryByName(name, 10);
        } else {
            history = schedulerService.getJobHistory(10);
        }

        if (history.isEmpty()) {
            return "暂无执行历史";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("任务执行历史（共 ").append(history.size()).append(" 条）：\n\n");

        for (var h : history) {
            sb.append("## ").append(h.name()).append("\n");
            sb.append("- 时间: ").append(formatTimestamp(h.executionTime())).append("\n");
            sb.append("- 耗时: ").append(h.duration()).append("ms\n");
            sb.append("- 状态: ").append(h.success() ? "成功" : "失败").append("\n");
            if (StrUtil.isNotBlank(h.errorMessage())) {
                sb.append("- 错误: ").append(h.errorMessage()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 格式化时间戳
     */
    private String formatTimestamp(long timestamp) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 格式化成功结果
     */
    private String formatSuccess(String message) {
        return "✅ " + message;
    }

    /**
     * 格式化成功结果（带数据）
     */
    private String formatSuccess(String message, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ ").append(message).append("\n\n");
        sb.append("详细信息：\n");
        for (var entry : data.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 格式化错误结果
     */
    private String formatError(String message) {
        return "❌ " + message;
    }
}
