package com.jimuqu.claw.agent.runtime;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.model.LatestReplyRoute;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.config.SolonClawProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 定时读取工作区心跳文件并触发一次静默的内部系统检查。
 */
public class HeartbeatService {
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);
    /** Agent 运行时服务。 */
    private final AgentRuntimeService agentRuntimeService;
    /** 运行时存储服务。 */
    private final RuntimeStoreService runtimeStoreService;
    /** 项目配置。 */
    private final SolonClawProperties properties;
    /** 定时调度器。 */
    private ScheduledExecutorService scheduler;

    /**
     * 创建心跳服务。
     *
     * @param agentRuntimeService Agent 运行时服务
     * @param runtimeStoreService 运行时存储服务
     * @param properties 项目配置
     */
    public HeartbeatService(
            AgentRuntimeService agentRuntimeService,
            RuntimeStoreService runtimeStoreService,
            SolonClawProperties properties
    ) {
        this.agentRuntimeService = agentRuntimeService;
        this.runtimeStoreService = runtimeStoreService;
        this.properties = properties;
    }

    /**
     * 启动心跳定时任务。
     */
    public void start() {
        SolonClawProperties.Heartbeat heartbeat = properties.getAgent().getHeartbeat();
        if (!heartbeat.isEnabled()) {
            log.info("Heartbeat service disabled.");
            return;
        }

        if (scheduler != null) {
            return;
        }

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "solonclaw-heartbeat");
            thread.setDaemon(true);
            return thread;
        };

        scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
        long intervalSeconds = Math.max(60, heartbeat.getIntervalSeconds());
        scheduler.scheduleAtFixedRate(this::safeTick, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        log.info("Heartbeat service started with interval {} seconds.", intervalSeconds);
    }

    /**
     * 停止心跳定时任务。
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * 安全执行一次心跳轮询。
     */
    private void safeTick() {
        try {
            tick();
        } catch (Throwable throwable) {
            log.warn("Heartbeat tick failed: {}", throwable.getMessage(), throwable);
        }
    }

    /**
     * 执行一次心跳检查和投递。
     */
    void tick() {
        File heartbeatFile = new File(properties.getWorkspace(), "HEARTBEAT.md");
        if (!heartbeatFile.exists()) {
            return;
        }

        String content = FileUtil.readUtf8String(heartbeatFile).trim();
        if (StrUtil.isBlank(content)) {
            return;
        }

        LatestReplyRoute route = runtimeStoreService.getLatestExternalRoute();
        if (route == null || route.getReplyTarget() == null || StrUtil.isBlank(route.getSessionKey())) {
            return;
        }

        agentRuntimeService.submitSilentSystemMessage(route.getSessionKey(), route.getReplyTarget(), content, "heartbeat");
    }
}
