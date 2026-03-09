package com.jimuqu.solonclaw.heartbeat;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Init;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Heartbeat 配置类
 * <p>
 * 用于配置 Heartbeat 定时任务
 *
 * @author SolonClaw
 */
@Component
public class HeartbeatConfig {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatConfig.class);

    @Inject
    private HeartbeatProperties properties;

    @Inject
    private HeartbeatService heartbeatService;

    @Init
    public void init() {
        log.info("Heartbeat 配置初始化:");
        log.info("  - enabled: {}", properties.isEnabled());
        log.info("  - interval: {}", properties.getEvery());
        log.info("  - target: {}", properties.getTarget());
        log.info("  - activeHours: {} - {}", properties.getActiveHoursStart(), properties.getActiveHoursEnd());
        log.info("  - ackMaxChars: {}", properties.getAckMaxChars());
        log.info("  - smartSkip: {}", properties.isSmartSkip());

        if (properties.isEnabled()) {
            log.info("Heartbeat 已启用，将定时执行");
        } else {
            log.info("Heartbeat 已禁用");
        }
    }

    /**
     * 获取 Heartbeat 配置
     */
    public HeartbeatProperties getProperties() {
        return properties;
    }
}
