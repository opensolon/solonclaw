package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 定时任务持久化记录。 */
@Getter
@Setter
@NoArgsConstructor
public class CronJobRecord {
    /** 任务 ID。 */
    private String jobId;

    /** 任务名称。 */
    private String name;

    /** cron 表达式。 */
    private String cronExpr;

    /** 触发时发给 Agent 的提示词。 */
    private String prompt;

    /** 会话来源键。 */
    private String sourceKey;

    /** 投递平台。 */
    private String deliverPlatform;

    /** 投递会话 ID。 */
    private String deliverChatId;

    /** 任务状态。 */
    private String status;

    /** 下次执行时间。 */
    private long nextRunAt;

    /** 最近一次执行时间。 */
    private long lastRunAt;

    /** 创建时间。 */
    private long createdAt;

    /** 更新时间。 */
    private long updatedAt;
}
