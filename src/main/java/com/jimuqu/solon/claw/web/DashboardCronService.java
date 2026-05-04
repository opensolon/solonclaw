package com.jimuqu.solon.claw.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.CronJobRecord;
import com.jimuqu.solon.claw.core.repository.CronJobRepository;
import com.jimuqu.solon.claw.scheduler.DefaultCronScheduler;
import com.jimuqu.solon.claw.support.CronSupport;
import com.jimuqu.solon.claw.support.IdSupport;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Dashboard 定时任务管理服务。 */
public class DashboardCronService {
    private static final List<String> ALLOWED_DELIVERY =
            Arrays.asList("local", "feishu", "dingtalk", "wecom", "weixin");

    private final CronJobRepository cronJobRepository;
    private final DefaultCronScheduler cronScheduler;

    public DashboardCronService(
            CronJobRepository cronJobRepository, DefaultCronScheduler cronScheduler) {
        this.cronJobRepository = cronJobRepository;
        this.cronScheduler = cronScheduler;
    }

    public List<Map<String, Object>> listJobs() throws Exception {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (CronJobRecord record : cronJobRepository.listAll()) {
            result.add(toView(record, null));
        }
        return result;
    }

    public Map<String, Object> create(Map<String, Object> body) throws Exception {
        String prompt = StrUtil.nullToEmpty(String.valueOf(body.get("prompt"))).trim();
        String schedule = StrUtil.nullToEmpty(String.valueOf(body.get("schedule"))).trim();
        String name = body.get("name") == null ? null : String.valueOf(body.get("name")).trim();
        String deliver =
                body.get("deliver") == null
                        ? "local"
                        : String.valueOf(body.get("deliver")).trim().toLowerCase(Locale.ROOT);
        if (StrUtil.hasBlank(prompt, schedule)) {
            throw new IllegalStateException("prompt and schedule are required");
        }
        if (!ALLOWED_DELIVERY.contains(deliver)) {
            throw new IllegalStateException("Unsupported delivery target: " + deliver);
        }

        long now = System.currentTimeMillis();
        CronJobRecord record = new CronJobRecord();
        record.setJobId(IdSupport.newId());
        record.setName(StrUtil.blankToDefault(name, null));
        record.setCronExpr(schedule);
        record.setPrompt(prompt);
        record.setSourceKey("MEMORY:dashboard:cron");
        record.setDeliverPlatform(deliver);
        record.setDeliverChatId(null);
        record.setStatus("ACTIVE");
        record.setNextRunAt(CronSupport.nextRunAt(schedule, now));
        record.setLastRunAt(0L);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        cronJobRepository.save(record);
        return toView(record, null);
    }

    public Map<String, Object> pause(String id) throws Exception {
        cronJobRepository.updateStatus(id, "PAUSED");
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> resume(String id) throws Exception {
        cronJobRepository.updateStatus(id, "ACTIVE");
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> trigger(String id) throws Exception {
        cronScheduler.runNow(id);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> delete(String id) throws Exception {
        cronJobRepository.delete(id);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    private Map<String, Object> toView(CronJobRecord record, String lastError) {
        Map<String, Object> schedule = new LinkedHashMap<String, Object>();
        schedule.put("kind", "cron");
        schedule.put("expr", record.getCronExpr());
        schedule.put("display", record.getCronExpr());

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", record.getJobId());
        result.put("name", record.getName());
        result.put("prompt", record.getPrompt());
        result.put("schedule", schedule);
        result.put("schedule_display", record.getCronExpr());
        result.put("enabled", "ACTIVE".equalsIgnoreCase(record.getStatus()));
        result.put("state", "ACTIVE".equalsIgnoreCase(record.getStatus()) ? "scheduled" : "paused");
        result.put("deliver", record.getDeliverPlatform());
        result.put("last_run_at", record.getLastRunAt() <= 0 ? null : iso(record.getLastRunAt()));
        result.put("next_run_at", record.getNextRunAt() <= 0 ? null : iso(record.getNextRunAt()));
        result.put("last_error", lastError);
        return result;
    }

    private String iso(long epochMillis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(epochMillis));
    }
}
