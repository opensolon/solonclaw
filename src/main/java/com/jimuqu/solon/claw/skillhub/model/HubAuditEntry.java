package com.jimuqu.solon.claw.skillhub.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Hub 审计日志条目。 */
@Getter
@Setter
@NoArgsConstructor
public class HubAuditEntry {
    private String timestamp;
    private String action;
    private String skillName;
    private String source;
    private String trustLevel;
    private String verdict;
    private String extra;
}
