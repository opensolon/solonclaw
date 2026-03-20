package com.jimuqu.claw.agent.job;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.enums.SystemEventPolicy;
import com.jimuqu.claw.agent.model.route.LatestReplyRoute;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.runtime.impl.IsolatedAgentRunService;
import com.jimuqu.claw.agent.runtime.impl.SystemEventRunner;
import com.jimuqu.claw.agent.runtime.support.AgentTurnRequest;
import com.jimuqu.claw.agent.runtime.support.SystemEventRequest;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.config.SolonClawProperties;
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
    private final IJobManager jobManager;
    private final JobStoreService jobStoreService;
    private final RuntimeStoreService runtimeStoreService;
    private final SystemEventRunner systemEventRunner;
    private final IsolatedAgentRunService isolatedAgentRunService;
    private final SolonClawProperties properties;

    public WorkspaceJobService(
            IJobManager jobManager,
            JobStoreService jobStoreService,
            RuntimeStoreService runtimeStoreService,
            SystemEventRunner systemEventRunner,
            IsolatedAgentRunService isolatedAgentRunService,
            SolonClawProperties properties
    ) {
        this.jobManager = jobManager;
        this.jobStoreService = jobStoreService;
        this.runtimeStoreService = runtimeStoreService;
        this.systemEventRunner = systemEventRunner;
        this.isolatedAgentRunService = isolatedAgentRunService;
        this.properties = properties;
    }

    public void restorePersistedJobs() {
        for (JobDefinition definition : jobStoreService.loadAll()) {
            normalizeDefinition(definition);
            registerJob(definition);
            jobStoreService.save(definition);
        }
    }

    public List<JobDefinition> listJobs() {
        List<JobDefinition> definitions = jobStoreService.loadAll();
        for (JobDefinition definition : definitions) {
            normalizeDefinition(definition);
        }
        return definitions;
    }

    public JobDefinition getJob(String name) {
        return normalizeDefinition(jobStoreService.get(name));
    }

    public JobDefinition addSystemJob(
            String name,
            String mode,
            String scheduleValue,
            String systemEventText,
            long initialDelay,
            String zone,
            JobWakeMode wakeMode
    ) {
        validateSchedule(name, mode, scheduleValue);
        if (StrUtil.isBlank(systemEventText)) {
            throw new IllegalArgumentException("systemEventText 不能为空");
        }

        LatestReplyRoute route = requireLatestExternalRoute();
        long now = System.currentTimeMillis();

        JobDefinition definition = new JobDefinition();
        definition.setName(name.trim());
        definition.setMode(mode.trim().toLowerCase());
        definition.setScheduleValue(scheduleValue.trim());
        definition.setInitialDelay(Math.max(0L, initialDelay));
        definition.setZone(StrUtil.blankToDefault(zone, "").trim());
        definition.setEnabled(true);
        definition.setPayloadKind(JobPayloadKind.SYSTEM_EVENT);
        definition.setSessionTarget(JobSessionTarget.MAIN);
        definition.setWakeMode(wakeMode == null ? properties.getAgent().getJobs().getDefaultWakeMode() : wakeMode);
        definition.setDeliveryMode(JobDeliveryMode.NONE);
        definition.setBoundSessionKey(route.getSessionKey());
        definition.setBoundReplyTarget(route.getReplyTarget());
        definition.setSystemEventText(systemEventText.trim());
        definition.setAgentTurn(new AgentTurnSpec());
        definition.setCreatedAt(now);
        definition.setUpdatedAt(now);

        validateDefinition(definition);
        registerJob(definition);
        jobStoreService.save(definition);
        return definition;
    }

    public JobDefinition addAgentJob(
            String name,
            String mode,
            String scheduleValue,
            AgentTurnSpec agentTurn,
            long initialDelay,
            String zone,
            JobDeliveryMode deliveryMode
    ) {
        validateSchedule(name, mode, scheduleValue);
        if (agentTurn == null || StrUtil.isBlank(agentTurn.getMessage())) {
            throw new IllegalArgumentException("agentTurn.message 不能为空");
        }

        LatestReplyRoute route = requireLatestExternalRoute();
        long now = System.currentTimeMillis();

        AgentTurnSpec normalizedSpec = new AgentTurnSpec();
        normalizedSpec.setMessage(agentTurn.getMessage().trim());
        normalizedSpec.setModel(StrUtil.blankToDefault(StrUtil.trim(agentTurn.getModel()), null));
        normalizedSpec.setThinking(StrUtil.blankToDefault(StrUtil.trim(agentTurn.getThinking()), null));
        normalizedSpec.setTimeoutSeconds(
                agentTurn.getTimeoutSeconds() == null
                        ? properties.getAgent().getAgentTurn().getDefaultTimeoutSeconds()
                        : agentTurn.getTimeoutSeconds()
        );
        normalizedSpec.setLightContext(agentTurn.isLightContext());

        JobDefinition definition = new JobDefinition();
        definition.setName(name.trim());
        definition.setMode(mode.trim().toLowerCase());
        definition.setScheduleValue(scheduleValue.trim());
        definition.setInitialDelay(Math.max(0L, initialDelay));
        definition.setZone(StrUtil.blankToDefault(zone, "").trim());
        definition.setEnabled(true);
        definition.setPayloadKind(JobPayloadKind.AGENT_TURN);
        definition.setSessionTarget(JobSessionTarget.ISOLATED);
        definition.setWakeMode(JobWakeMode.NOW);
        definition.setDeliveryMode(deliveryMode == null ? properties.getAgent().getJobs().getDefaultDeliveryMode() : deliveryMode);
        definition.setBoundSessionKey(route.getSessionKey());
        definition.setBoundReplyTarget(route.getReplyTarget());
        definition.setSystemEventText(null);
        definition.setAgentTurn(normalizedSpec);
        definition.setCreatedAt(now);
        definition.setUpdatedAt(now);

        validateDefinition(definition);
        registerJob(definition);
        jobStoreService.save(definition);
        return definition;
    }

    public JobDefinition addJob(String name, String mode, String scheduleValue, String prompt, long initialDelay, String zone) {
        if (StrUtil.isBlank(prompt)) {
            throw new IllegalArgumentException("prompt 不能为空");
        }

        AgentTurnSpec agentTurnSpec = new AgentTurnSpec();
        agentTurnSpec.setMessage(prompt.trim());
        return addAgentJob(name, mode, scheduleValue, agentTurnSpec, initialDelay, zone, null);
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
        validateDefinition(definition);
        if (!jobManager.jobExists(definition.getName())) {
            registerJob(definition);
        }
        jobManager.jobStart(definition.getName(), null);
        definition.setEnabled(true);
        definition.setUpdatedAt(System.currentTimeMillis());
        jobStoreService.save(definition);
        return definition;
    }

    private LatestReplyRoute requireLatestExternalRoute() {
        LatestReplyRoute route = runtimeStoreService.getLatestExternalRoute();
        if (route == null || route.getReplyTarget() == null || StrUtil.isBlank(route.getSessionKey())) {
            throw new IllegalStateException("当前没有可绑定的外部会话，无法创建定时任务。");
        }
        return route;
    }

    private JobDefinition requireDefinition(String name) {
        JobDefinition definition = normalizeDefinition(jobStoreService.get(name));
        if (definition == null) {
            throw new IllegalArgumentException("定时任务不存在: " + name);
        }
        return definition;
    }

    private JobDefinition normalizeDefinition(JobDefinition definition) {
        if (definition == null) {
            return null;
        }

        boolean legacyPromptJob = definition.getPayloadKind() == null && StrUtil.isNotBlank(definition.getPrompt());
        if (legacyPromptJob) {
            definition.setPayloadKind(JobPayloadKind.AGENT_TURN);
            definition.setSessionTarget(JobSessionTarget.ISOLATED);
            definition.setWakeMode(JobWakeMode.NOW);
            definition.setDeliveryMode(JobDeliveryMode.BOUND_REPLY_TARGET);
        }

        if (definition.getPayloadKind() == JobPayloadKind.SYSTEM_EVENT) {
            if (definition.getSessionTarget() == null) {
                definition.setSessionTarget(JobSessionTarget.MAIN);
            }
            if (definition.getWakeMode() == null) {
                definition.setWakeMode(properties.getAgent().getJobs().getDefaultWakeMode());
            }
            if (definition.getDeliveryMode() == null) {
                definition.setDeliveryMode(JobDeliveryMode.NONE);
            }
        }

        if (definition.getPayloadKind() == JobPayloadKind.AGENT_TURN) {
            if (definition.getSessionTarget() == null) {
                definition.setSessionTarget(JobSessionTarget.ISOLATED);
            }
            if (definition.getWakeMode() == null) {
                definition.setWakeMode(JobWakeMode.NOW);
            }
            if (definition.getDeliveryMode() == null) {
                definition.setDeliveryMode(properties.getAgent().getJobs().getDefaultDeliveryMode());
            }
            if (definition.getAgentTurn() == null) {
                definition.setAgentTurn(new AgentTurnSpec());
            }
            if (StrUtil.isBlank(definition.getAgentTurn().getMessage()) && StrUtil.isNotBlank(definition.getPrompt())) {
                definition.getAgentTurn().setMessage(definition.getPrompt().trim());
            }
            if (definition.getAgentTurn().getTimeoutSeconds() == null) {
                definition.getAgentTurn().setTimeoutSeconds(properties.getAgent().getAgentTurn().getDefaultTimeoutSeconds());
            }
        }

        if (StrUtil.isBlank(definition.getBoundSessionKey()) && StrUtil.isNotBlank(definition.getSessionKey())) {
            definition.setBoundSessionKey(definition.getSessionKey().trim());
        }
        if (definition.getBoundReplyTarget() == null && definition.getReplyTarget() != null) {
            definition.setBoundReplyTarget(definition.getReplyTarget());
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
            dispatch(definition);
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

    private void dispatch(JobDefinition definition) {
        if (definition.getPayloadKind() == JobPayloadKind.SYSTEM_EVENT) {
            SystemEventRequest request = new SystemEventRequest();
            request.setSourceKind(RuntimeSourceKind.JOB_SYSTEM_EVENT);
            request.setPolicy(SystemEventPolicy.USER_VISIBLE_OPTIONAL);
            request.setSessionKey(definition.getBoundSessionKey());
            request.setReplyTarget(definition.getBoundReplyTarget());
            request.setContent(definition.getSystemEventText());
            request.setAllowNotifyUser(true);
            request.setWakeImmediately(definition.getWakeMode() != JobWakeMode.NEXT_TICK);
            systemEventRunner.submit(request);
            return;
        }

        AgentTurnRequest request = new AgentTurnRequest();
        request.setSourceKind(RuntimeSourceKind.JOB_AGENT_TURN);
        request.setJobName(definition.getName());
        request.setBoundSessionKey(definition.getBoundSessionKey());
        request.setBoundReplyTarget(definition.getBoundReplyTarget());
        request.setDeliveryMode(definition.getDeliveryMode());
        request.setAgentTurn(copyAgentTurn(definition.getAgentTurn()));
        isolatedAgentRunService.submit(request);
    }

    private Scheduled buildScheduled(JobDefinition definition) {
        long initialDelay = Math.max(0L, definition.getInitialDelay());
        ScheduledAnno scheduled = new ScheduledAnno()
                .name(definition.getName())
                .initialDelay(initialDelay)
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
            long delay = initialDelay > 0
                    ? initialDelay
                    : parseLong(definition.getScheduleValue(), "onceDelay");
            scheduled.initialDelay(delay);
            scheduled.fixedDelay(delay);
        } else if ("cron".equals(mode)) {
            scheduled.cron(definition.getScheduleValue());
        } else {
            throw new IllegalArgumentException("不支持的任务模式: " + mode);
        }

        return scheduled;
    }

    private void validateSchedule(String name, String mode, String scheduleValue) {
        if (StrUtil.isBlank(name)) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (StrUtil.isBlank(mode)) {
            throw new IllegalArgumentException("mode 不能为空");
        }
        if (StrUtil.isBlank(scheduleValue)) {
            throw new IllegalArgumentException("scheduleValue 不能为空");
        }

        String normalized = mode.trim().toLowerCase();
        if (!"fixed_rate".equals(normalized)
                && !"fixed_delay".equals(normalized)
                && !"once_delay".equals(normalized)
                && !"cron".equals(normalized)) {
            throw new IllegalArgumentException("mode 仅支持 fixed_rate、fixed_delay、once_delay、cron");
        }
    }

    private void validateDefinition(JobDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition 不能为空");
        }
        if (StrUtil.isBlank(definition.getName())) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (definition.getPayloadKind() == null) {
            throw new IllegalArgumentException("payloadKind 不能为空");
        }
        if (definition.getSessionTarget() == null) {
            throw new IllegalArgumentException("sessionTarget 不能为空");
        }
        if (definition.getPayloadKind() == JobPayloadKind.SYSTEM_EVENT) {
            if (definition.getSessionTarget() != JobSessionTarget.MAIN) {
                throw new IllegalArgumentException("SYSTEM_EVENT 任务必须使用 MAIN sessionTarget");
            }
            if (StrUtil.isBlank(definition.getSystemEventText())) {
                throw new IllegalArgumentException("SYSTEM_EVENT 任务必须提供 systemEventText");
            }
        } else {
            if (definition.getSessionTarget() != JobSessionTarget.ISOLATED) {
                throw new IllegalArgumentException("AGENT_TURN 任务必须使用 ISOLATED sessionTarget");
            }
            if (definition.getAgentTurn() == null || StrUtil.isBlank(definition.getAgentTurn().getMessage())) {
                throw new IllegalArgumentException("AGENT_TURN 任务必须提供 agentTurn.message");
            }
        }
        if (definition.getDeliveryMode() == JobDeliveryMode.BOUND_REPLY_TARGET && definition.getBoundReplyTarget() == null) {
            throw new IllegalArgumentException("BOUND_REPLY_TARGET 模式必须提供 boundReplyTarget");
        }
        if (StrUtil.isBlank(definition.getBoundSessionKey())) {
            throw new IllegalArgumentException("boundSessionKey 不能为空");
        }
    }

    private long parseLong(String value, String fieldName) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " 必须是毫秒数字: " + value, e);
        }
    }

    private boolean isOneShot(JobDefinition definition) {
        return "once_delay".equals(definition.getMode());
    }

    private AgentTurnSpec copyAgentTurn(AgentTurnSpec source) {
        AgentTurnSpec copy = new AgentTurnSpec();
        if (source != null) {
            copy.setMessage(source.getMessage());
            copy.setModel(source.getModel());
            copy.setThinking(source.getThinking());
            copy.setTimeoutSeconds(source.getTimeoutSeconds());
            copy.setLightContext(source.isLightContext());
        }
        return copy;
    }
}
