package com.jimuqu.solonclaw.heartbeat;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;

/**
 * Heartbeat 配置属性
 *
 * @author SolonClaw
 */
@Component
public class HeartbeatProperties {

    /**
     * 心跳间隔（默认 30 分钟）
     * 支持格式：30m, 1h, 30s 等
     */
    private String every = "30m";

    /**
     * 目标会话（默认 last，表示最近会话）
     */
    private String target = "last";

    /**
     * 活跃时间开始（如 "08:00"）
     */
    private String activeHoursStart;

    /**
     * 活跃时间结束（如 "22:00"）
     */
    private String activeHoursEnd;

    /**
     * 最大回复字符数（超过则发送消息通知）
     */
    private int ackMaxChars = 300;

    /**
     * 是否启用 Heartbeat
     */
    private boolean enabled = true;

    /**
     * 是否启用智能跳过（返回 HEARTBEAT_OK 时不发送通知）
     */
    private boolean smartSkip = true;

    @Inject("${solonclaw.heartbeat.enabled:true}")
    public void setEnabledInject(boolean enabled) {
        this.enabled = enabled;
    }

    @Inject("${solonclaw.heartbeat.interval:1800000}")
    public void setIntervalInject(long interval) {
        // interval 已经通过 fixedRate 配置，这里不需要额外处理
    }

    public String getEvery() {
        return every;
    }

    public void setEvery(String every) {
        this.every = every;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getActiveHoursStart() {
        return activeHoursStart;
    }

    public void setActiveHoursStart(String activeHoursStart) {
        this.activeHoursStart = activeHoursStart;
    }

    public String getActiveHoursEnd() {
        return activeHoursEnd;
    }

    public void setActiveHoursEnd(String activeHoursEnd) {
        this.activeHoursEnd = activeHoursEnd;
    }

    public int getAckMaxChars() {
        return ackMaxChars;
    }

    public void setAckMaxChars(int ackMaxChars) {
        this.ackMaxChars = ackMaxChars;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSmartSkip() {
        return smartSkip;
    }

    public void setSmartSkip(boolean smartSkip) {
        this.smartSkip = smartSkip;
    }
}
