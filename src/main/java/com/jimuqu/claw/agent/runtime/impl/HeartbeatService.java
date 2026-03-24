package com.jimuqu.claw.agent.runtime.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.enums.SystemEventPolicy;
import com.jimuqu.claw.agent.model.route.LatestReplyRoute;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.agent.runtime.support.SystemEventRequest;
import com.jimuqu.claw.config.SolonClawProperties;
import com.jimuqu.claw.config.props.HeartbeatProperties;
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
    /** 系统事件执行器。 */
    private final SystemEventRunner systemEventRunner;
    /** 运行时存储服务。 */
    private final RuntimeStoreService runtimeStoreService;
    /** 项目配置。 */
    private final SolonClawProperties properties;
    /** 定时调度器。 */
    private ScheduledExecutorService scheduler;

    /**
     * 创建心跳服务。
     *
     * @param systemEventRunner 系统事件执行器
     * @param runtimeStoreService 运行时存储服务
     * @param properties 项目配置
     */
    public HeartbeatService(
            SystemEventRunner systemEventRunner,
            RuntimeStoreService runtimeStoreService,
            SolonClawProperties properties
    ) {
        this.systemEventRunner = systemEventRunner;
        this.runtimeStoreService = runtimeStoreService;
        this.properties = properties;
    }

    /**
     * 启动心跳定时任务。
     */
    public void start() {
        HeartbeatProperties heartbeat = properties.getAgent().getHeartbeat();
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
    public void tick() {
        File heartbeatFile = new File(properties.getWorkspace(), "HEARTBEAT.md");
        if (!heartbeatFile.exists()) {
            return;
        }

        String content = normalizeHeartbeatContent(FileUtil.readUtf8String(heartbeatFile));
        if (StrUtil.isBlank(content)) {
            return;
        }

        LatestReplyRoute route = runtimeStoreService.getLatestExternalRoute();
        if (route == null || StrUtil.isBlank(route.getSessionKey())) {
            return;
        }

        SystemEventRequest request = new SystemEventRequest();
        request.setSourceKind(RuntimeSourceKind.HEARTBEAT_EVENT);
        request.setPolicy(SystemEventPolicy.INTERNAL_ONLY);
        request.setSessionKey(route.getSessionKey());
        request.setContent(content);
        request.setAllowNotifyUser(true);
        request.setWakeImmediately(true);
        systemEventRunner.submit(request);
    }

    private String normalizeHeartbeatContent(String rawContent) {
        if (StrUtil.isBlank(rawContent)) {
            return "";
        }

        String[] lines = rawContent.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String trimmed = StrUtil.trim(line);
            if (StrUtil.isBlank(trimmed) || StrUtil.startWith(trimmed, "#")) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line.trim());
        }
        return builder.toString().trim();
    }
}


