package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Self-improvement 产生的技能改进报告。 */
@Getter
@Setter
@NoArgsConstructor
public class SkillImprovementRecord {
    private String improvementId;
    private String sessionId;
    private String runId;
    private String skillName;
    private String action;
    private String summary;
    private String changedFilesJson;
    private String evidenceJson;
    private boolean needsReview;
    private long createdAt;
}
