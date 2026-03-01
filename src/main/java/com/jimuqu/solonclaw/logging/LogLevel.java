package com.jimuqu.solonclaw.logging;

/**
 * 日志级别枚举
 */
public enum LogLevel {
    /**
     * 普通信息
     */
    INFO(0, "INFO", "普通信息"),

    /**
     * 用户对话
     */
    USER_CHAT(10, "USER_CHAT", "用户对话"),

    /**
     * Agent 思考过程
     */
    AGENT_THINK(20, "AGENT_THINK", "Agent 思考"),

    /**
     * 决策日志
     */
    DECISION(30, "DECISION", "决策"),

    /**
     * 行动日志
     */
    ACTION(40, "ACTION", "行动"),

    /**
     * 反省总结
     */
    REFLECTION(50, "REFLECTION", "反省"),

    /**
     * 错误日志
     */
    ERROR(100, "ERROR", "错误");

    private final int priority;
    private final String code;
    private final String description;

    LogLevel(int priority, String code, String description) {
        this.priority = priority;
        this.code = code;
        this.description = description;
    }

    public int getPriority() {
        return priority;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}