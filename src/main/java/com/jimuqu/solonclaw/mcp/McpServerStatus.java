package com.jimuqu.solonclaw.mcp;

/**
 * MCP 服务器状态
 *
 * @author SolonClaw
 */
public enum McpServerStatus {

    /**
     * 已停止
     */
    STOPPED("stopped", "已停止"),

    /**
     * 启动中
     */
    STARTING("starting", "启动中"),

    /**
     * 已初始化
     */
    INITIALIZED("initialized", "已初始化"),

    /**
     * 运行中
     */
    RUNNING("running", "运行中"),

    /**
     * 错误
     */
    ERROR("error", "错误");

    private final String code;
    private final String description;

    McpServerStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}