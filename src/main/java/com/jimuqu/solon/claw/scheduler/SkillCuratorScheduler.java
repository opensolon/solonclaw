package com.jimuqu.solon.claw.scheduler;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.context.SkillCuratorService;
import com.jimuqu.solon.claw.core.service.AgentRunControlService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 后台技能维护调度器，只在 Agent 空闲窗口触发 Curator。 */
@RequiredArgsConstructor
public class SkillCuratorScheduler {
    private static final Logger log = LoggerFactory.getLogger(SkillCuratorScheduler.class);
    private final AppConfig appConfig;
    private final SkillCuratorService curatorService;
    private final AgentRunControlService agentRunControlService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService executorService;

    public void start() {
        if (!appConfig.getCurator().isEnabled()) {
            return;
        }
        executorService = Executors.newSingleThreadScheduledExecutor();
        long tickSeconds =
                Math.max(
                        60L,
                        Math.min(
                                3600L,
                                Math.max(1, appConfig.getCurator().getIntervalHours()) * 3600L));
        executorService.scheduleWithFixedDelay(
                new Runnable() {
                    @Override
                    public void run() {
                        tick();
                    }
                },
                60L,
                tickSeconds,
                TimeUnit.SECONDS);
    }

    public void tick() {
        if (!appConfig.getCurator().isEnabled()) {
            return;
        }
        if (!isIdleEnough()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            curatorService.runOnce(false);
        } catch (Exception e) {
            log.warn("[CURATOR] background run failed: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }

    private boolean isIdleEnough() {
        if (agentRunControlService != null && agentRunControlService.hasRunningRuns()) {
            return false;
        }
        double minIdleHours = Math.max(0.0D, appConfig.getCurator().getMinIdleHours());
        if (minIdleHours <= 0.0D || agentRunControlService == null) {
            return true;
        }
        long lastFinishedAt = agentRunControlService.lastRunFinishedAt();
        if (lastFinishedAt <= 0L) {
            return true;
        }
        long minIdleMillis = (long) (minIdleHours * 60.0D * 60.0D * 1000.0D);
        return System.currentTimeMillis() - lastFinishedAt >= minIdleMillis;
    }

    public void shutdown() {
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }
}
