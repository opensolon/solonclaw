package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Dashboard/Hermes search 过滤条件。 */
@Getter
@Setter
@NoArgsConstructor
public class SessionSearchQuery {
    private String sourceKey;
    private String sessionId;
    private String runId;
    private String toolName;
    private String channel;
    private String query;
    private long timeFrom;
    private long timeTo;
    private boolean summarize;
    private int limit = 10;
}
