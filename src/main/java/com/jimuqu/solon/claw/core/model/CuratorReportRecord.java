package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Curator 巡检报告索引。 */
@Getter
@Setter
@NoArgsConstructor
public class CuratorReportRecord {
    private String reportId;
    private String status;
    private String summary;
    private String reportPath;
    private String reportJson;
    private long startedAt;
    private long finishedAt;
}
