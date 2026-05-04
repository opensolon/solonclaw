package com.jimuqu.solon.claw.tool.runtime;

import com.jimuqu.solon.claw.core.model.CronJobRecord;
import com.jimuqu.solon.claw.core.model.ToolResultEnvelope;
import com.jimuqu.solon.claw.core.repository.CronJobRepository;
import com.jimuqu.solon.claw.support.CronSupport;
import com.jimuqu.solon.claw.support.IdSupport;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Param;

/** CronjobTools 实现。 */
@RequiredArgsConstructor
public class CronjobTools {
    private final CronJobRepository cronJobRepository;
    private final String sourceKey;

    @ToolMapping(
            name = "cronjob",
            description = "Manage cron jobs. action can be create, list, pause, resume, or delete.")
    public String cronjob(
            @Param(name = "action", description = "create、list、pause、resume、delete") String action,
            @Param(name = "name", description = "任务名或任务 ID", required = false) String name,
            @Param(name = "cronExpr", description = "cron 表达式", required = false) String cronExpr,
            @Param(name = "prompt", description = "任务提示词", required = false) String prompt)
            throws Exception {
        if ("list".equalsIgnoreCase(action)) {
            List<CronJobRecord> jobs = cronJobRepository.listBySource(sourceKey);
            StringBuilder buffer = new StringBuilder();
            for (CronJobRecord job : jobs) {
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(job.getJobId())
                        .append(" ")
                        .append(job.getName())
                        .append(" ")
                        .append(job.getStatus());
            }
            String preview = buffer.length() == 0 ? "No cron jobs" : buffer.toString();
            return ToolResultEnvelope.ok("Listed cron jobs")
                    .data("jobs", jobs)
                    .data("count", Integer.valueOf(jobs.size()))
                    .preview(preview)
                    .toJson();
        }

        if ("create".equalsIgnoreCase(action)) {
            long now = System.currentTimeMillis();
            CronJobRecord record = new CronJobRecord();
            record.setJobId(IdSupport.newId());
            record.setName(name);
            record.setCronExpr(cronExpr);
            record.setPrompt(prompt);
            record.setSourceKey(sourceKey);
            record.setStatus("ACTIVE");
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            record.setNextRunAt(CronSupport.nextRunAt(cronExpr, now));
            cronJobRepository.save(record);
            return ToolResultEnvelope.ok("Created cron job: " + record.getJobId())
                    .data("jobId", record.getJobId())
                    .data("name", record.getName())
                    .data("cronExpr", record.getCronExpr())
                    .data("nextRunAt", Long.valueOf(record.getNextRunAt()))
                    .preview(record.getJobId() + " " + record.getName() + " ACTIVE")
                    .toJson();
        }

        if ("pause".equalsIgnoreCase(action)) {
            cronJobRepository.updateStatus(name, "PAUSED");
            return ToolResultEnvelope.ok("Paused cron job: " + name)
                    .data("jobId", name)
                    .data("status", "PAUSED")
                    .toJson();
        }

        if ("resume".equalsIgnoreCase(action)) {
            cronJobRepository.updateStatus(name, "ACTIVE");
            return ToolResultEnvelope.ok("Resumed cron job: " + name)
                    .data("jobId", name)
                    .data("status", "ACTIVE")
                    .toJson();
        }

        if ("delete".equalsIgnoreCase(action)) {
            cronJobRepository.delete(name);
            return ToolResultEnvelope.ok("Deleted cron job: " + name)
                    .data("jobId", name)
                    .data("status", "DELETED")
                    .toJson();
        }

        return ToolResultEnvelope.error("Unsupported cronjob action").toJson();
    }
}
