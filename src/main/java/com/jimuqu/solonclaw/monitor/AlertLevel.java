package com.jimuqu.solonclaw.monitor;

/**
 * 告警级别
 */
public enum AlertLevel {
    INFO("信息"),
    WARNING("警告"),
    CRITICAL("严重"),
    EMERGENCY("紧急");

    private final String description;

    AlertLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}