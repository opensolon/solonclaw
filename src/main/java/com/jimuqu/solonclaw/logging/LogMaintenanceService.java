package com.jimuqu.solonclaw.logging;

import cn.hutool.core.util.ObjUtil;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.bean.LifecycleBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 日志维护服务
 * 定期执行日志归档和清理任务
 */
@Component
public class LogMaintenanceService implements LifecycleBean {
    private static final Logger log = LoggerFactory.getLogger(LogMaintenanceService.class);

    @Inject
    private LogStore logStore;

    private ScheduledExecutorService scheduler;

    /**
     * 维护任务执行间隔（小时）
     */
    private int maintenanceIntervalHours = 24;

    @Init
    public void init() {
        startMaintenanceScheduler();
    }

    /**
     * 启动维护调度器
     */
    public void startMaintenanceScheduler() {
        if (ObjUtil.isNotNull(scheduler) && !scheduler.isShutdown()) {
            log.warn("Log maintenance scheduler is already running");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-maintenance");
            t.setDaemon(true);
            return t;
        });

        // 初始延迟 1 小时后执行，之后每隔 maintenanceIntervalHours 小时执行一次
        scheduler.scheduleAtFixedRate(
                this::performMaintenance,
                1,
                maintenanceIntervalHours,
                TimeUnit.HOURS
        );

        log.info("Log maintenance scheduler started, interval: {} hours", maintenanceIntervalHours);
    }

    /**
     * 停止维护调度器
     */
    public void stopMaintenanceScheduler() {
        if (ObjUtil.isNotNull(scheduler)) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Log maintenance scheduler stopped");
        }
    }

    /**
     * 执行维护任务
     */
    public void performMaintenance() {
        log.info("Starting scheduled log maintenance...");
        try {
            logStore.performMaintenance();
            log.info("Scheduled log maintenance completed successfully");
        } catch (Exception e) {
            log.error("Scheduled log maintenance failed", e);
        }
    }

    /**
     * 手动触发维护任务
     */
    public void triggerMaintenance() {
        log.info("Manually triggering log maintenance...");
        performMaintenance();
    }

    /**
     * 设置维护间隔（小时）
     */
    public void setMaintenanceIntervalHours(int hours) {
        this.maintenanceIntervalHours = hours;
    }

    /**
     * 获取维护间隔（小时）
     */
    public int getMaintenanceIntervalHours() {
        return maintenanceIntervalHours;
    }

    @Override
    public void start() throws Throwable {
        // LifecycleBean start - scheduler already initialized in @Init
    }

    @Override
    public void stop() throws Throwable {
        stopMaintenanceScheduler();
    }
}