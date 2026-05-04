package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** MCP server 配置与发现状态。 */
@Getter
@Setter
@NoArgsConstructor
public class McpServerRecord {
    private String serverId;
    private String name;
    private String transport;
    private String endpoint;
    private String command;
    private String argsJson;
    private String authJson;
    private String status;
    private String toolsJson;
    private String lastError;
    private boolean enabled;
    private long createdAt;
    private long updatedAt;
    private long lastCheckedAt;
}
