package com.jimuqu.claw.agent.job;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.model.route.LatestReplyRoute;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import org.noear.solon.scheduling.ScheduledAnno;
import org.noear.solon.scheduling.ScheduledException;
import org.noear.solon.scheduling.annotation.Scheduled;
import org.noear.solon.scheduling.scheduled.JobHolder;
import org.noear.solon.scheduling.scheduled.manager.IJobManager;

import java.util.List;

/**
 * 管理工作区中的定时任务定义、恢复和运行。
 */
public class WorkspaceJobService {
    @FunctionalInterface
    public interface JobDispatcher {
        String dispatch(String sessionKey, ReplyTarget replyTarget, String prompt);
    }

    private final IJobManager jobManager;
    private final JobStoreService jobStoreService;
    private final RuntimeStoreService runtimeStoreService;
    private JobDispatcher jobDispatcher;

    public WorkspaceJobService(
            IJobManager jobManager,
            JobStoreService jobStoreService,
            RuntimeStoreService runtimeStoreService
    ) {
        this.jobManager = jobManager;
        this.jobStoreService = jobStoreService;
        this.runtimeStoreService = runtimeStoreService;
    }

    public void setJobDispatcher(JobDispatcher jobDispatcher) {
        this.jobDispatcher = jobDispatcher;
    }

    public void restorePersistedJobs() {
        for (JobDefinition definition : jobStoreService.loadAll()) {
            registerJob(definition);
        }
    }

    public List<JobDefinition> listJobs() {
        return jobStoreService.loadAll();
    }

    public JobDefinition getJob(String name) {
        return jobStoreService.get(name);
    }

    public JobDefinition addJob(String name, String mode, String scheduleValue, String prompt, long initialDelay, String zone) {
        validate(name, mode, scheduleValue, prompt);

        LatestReplyRoute route = runtimeStoreService.getLatestExternalRoute();
        if (route == null || route.getReplyTarget() == null || StrUtil.isBlank(route.getSessionKey())) {
            throw new IllegalStateException("当前没有可绑定的外部会话，无法创建定时任务。");
        }

        JobDefinition definition = new JobDefinition();
        definition.setName(name.trim());
        definition.setMode(mode.trim().toLowerCase());
        definition.setScheduleValue(scheduleValue.trim());
        definition.setPrompt(prompt.trim());
        definition.setInitialDelay(Math.max(0L, initialDelay));
        definition.setZone(StrUtil.blankToDefault(zone, "").trim());
        definition.setEnabled(true);
        definition.setSessionKey(route.getSessionKey());
        definition.setReplyTarget(route.getReplyTarget());
        long now = System.currentTimeMillis();
        definition.setCreatedAt(now);
        definition.setUpdatedAt(now);

        registerJob(definition);
        jobStoreService.save(definition);
        return definition;
    }

    public JobDefinition removeJob(String name) {
        JobDefinition definition = requireDefinition(name);
        if (jobManager.jobExists(definition.getName())) {
            try {
                jobManager.jobRemove(definition.getName());
            } catch (ScheduledException e) {
                throw new IllegalStateException("删除定时任务失败: " + e.getMessage(), e);
            }
        }

        jobStoreService.remove(definition.getName());
        return definition;
    }

    public JobDefinition stopJob(String name) throws ScheduledException {
        JobDefinition definition = requireDefinition(name);
        jobManager.jobStop(definition.getName());
        definition.setEnabled(false);
        definition.setUpdatedAt(System.currentTimeMillis());
        jobStoreService.save(definition);
        return definition;
    }

    public JobDefinition startJob(String name) throws ScheduledException {
        JobDefinition definition = requireDefinition(name);
        if (!jobManager.jobExists(definition.getName())) {
            registerJob(definition);
        }
        jobManager.jobStart(definition.getName(), null);
        definition.setEnabled(true);
        definition.setUpdatedAt(System.currentTimeMillis());
        jobStoreService.save(definition);
        return definition;
    }

    private JobDefinition requireDefinition(String name) {
        JobDefinition definition = jobStoreService.get(name);
        if (definition == null) {
            throw new IllegalArgumentException("定时任务不存在: " + name);
        }
        return definition;
    }

    private void registerJob(JobDefinition definition) {
        if (jobManager.jobExists(definition.getName())) {
            try {
                jobManager.jobRemove(definition.getName());
            } catch (ScheduledException e) {
                throw new IllegalStateException("替换定时任务失败: " + e.getMessage(), e);
            }
        }

        Scheduled scheduled = buildScheduled(definition);
        JobHolder holder = jobManager.jobAdd(definition.getName(), scheduled, ctx -> {
            ReplyTarget replyTarget = definition.getReplyTarget();
            if (replyTarget == null || StrUtil.isBlank(definition.getSessionKey()) || jobDispatcher == null) {
                return;
            }
            jobDispatcher.dispatch(definition.getSessionKey(), replyTarget, buildExecutionPrompt(definition));
            if (isOneShot(definition)) {
                removeJob(definition.getName());
            }
        });
        holder.simpleName(definition.getName());

        if (!definition.isEnabled()) {
            try {
                jobManager.jobStop(definition.getName());
            } catch (ScheduledException e) {
                throw new IllegalStateException("停止定时任务失败: " + e.getMessage(), e);
            }
        }
    }

    private Scheduled buildScheduled(JobDefinition definition) {
        ScheduledAnno scheduled = new ScheduledAnno()
                .name(definition.getName())
                .initialDelay(definition.getInitialDelay())
                .enable(definition.isEnabled());

        if (StrUtil.isNotBlank(definition.getZone())) {
            scheduled.zone(definition.getZone());
        }

        String mode = definition.getMode();
        if ("fixed_rate".equals(mode)) {
            scheduled.fixedRate(parseLong(definition.getScheduleValue(), "fixedRate"));
        } else if ("fixed_delay".equals(mode)) {
            scheduled.fixedDelay(parseLong(definition.getScheduleValue(), "fixedDelay"));
        } else if ("once_delay".equals(mode)) {
            long delay = definition.getInitialDelay() > 0
                    ? definition.getInitialDelay()
                    : parseLong(definition.getScheduleValue(), "onceDelay");
            scheduled.fixedDelay(delay);
        } else if ("cron".equals(mode)) {
            scheduled.cron(definition.getScheduleValue());
        } else {
            throw new IllegalArgumentException("不支持的任务模式: " + mode);
        }

        return scheduled;
    }

    private long parseLong(String value, String fieldName) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " 必须是毫秒数字: " + value, e);
        }
    }

    private void validate(String name, String mode, String scheduleValue, String prompt) {
        if (StrUtil.isBlank(name)) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (StrUtil.isBlank(mode)) {
            throw new IllegalArgumentException("mode 不能为空");
        }
        if (StrUtil.isBlank(scheduleValue)) {
            throw new IllegalArgumentException("scheduleValue 不能为空");
        }
        if (StrUtil.isBlank(prompt)) {
            throw new IllegalArgumentException("prompt 不能为空");
        }

        String normalized = mode.trim().toLowerCase();
        if (!"fixed_rate".equals(normalized)
                && !"fixed_delay".equals(normalized)
                && !"once_delay".equals(normalized)
                && !"cron".equals(normalized)) {
            throw new IllegalArgumentException("mode 仅支持 fixed_rate、fixed_delay、once_delay、cron");
        }
    }

    private boolean isOneShot(JobDefinition definition) {
        return "once_delay".equals(definition.getMode());
    }

    /**
     * 将定时任务执行包装成明确的内部事件，避免模型把触发内容误解为新的建任务请求。
     *
     * @param definition 定时任务定义
     * @return 运行时提交给 Agent 的内部提示
     */
    private String buildExecutionPrompt(JobDefinition definition) {
        StringBuilder builder = new StringBuilder();
        builder.append("[内部定时任务触发]").append('\n');
        builder.append("任务名称: ").append(StrUtil.blankToDefault(definition.getName(), "(未命名)")).append('\n');
        builder.append("调度模式: ").append(StrUtil.blankToDefault(definition.getMode(), "(未知)")).append('\n');
        if (StrUtil.isNotBlank(definition.getScheduleValue())) {
            builder.append("调度值: ").append(definition.getScheduleValue()).append('\n');
        }
        builder.append("提醒内容:").append('\n');
        builder.append(StrUtil.blankToDefault(definition.getPrompt(), "(空)")).append('\n');
        builder.append('\n');
        builder.append("处理规则:").append('\n');
        builder.append("- 这是一个已经存在的定时提醒正在触发，不是用户要求新建、修改、删除或查询定时任务。").append('\n');
        builder.append("- 请把这条提醒自然、友好地告知用户。优先调用 notify_user 发送提醒；发送后返回 NO_REPLY。").append('\n');
        builder.append("- 如果你直接产出了面向用户的提醒文案，运行时会代为发送一次；不要再重复解释内部触发过程。").append('\n');
        builder.append("- 不要调用 add_job、remove_job、start_job、stop_job、list_jobs、get_job。").append('\n');
        builder.append("- 如果本次无需对外提醒，也直接返回 NO_REPLY。");
        return builder.toString();
    }
}

