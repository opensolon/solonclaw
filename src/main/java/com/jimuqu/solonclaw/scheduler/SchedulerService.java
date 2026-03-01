package com.jimuqu.solonclaw.scheduler;

import com.jimuqu.solonclaw.agent.AgentService;
import com.jimuqu.solonclaw.config.WorkspaceConfig;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.noear.solon.scheduling.scheduled.JobHolder;
import org.noear.solon.scheduling.scheduled.JobInterceptor;
import org.noear.solon.scheduling.scheduled.JobHandler;
import org.noear.solon.scheduling.scheduled.manager.IJobManager;
import org.noear.solon.scheduling.simple.JobManager;
import org.noear.solon.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调度服务
 * <p>
 * 管理动态定时任务，支持任务的增删改查和持久化
 * 集成 Solon 的 IJobManager 实现真正的动态调度
 *
 * @author SolonClaw
 */
@Component
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    @Inject
    private WorkspaceConfig.WorkspaceInfo workspaceInfo;

    @Inject
    private AgentService agentService;

    /**
     * Solon 的任务管理器
     */
    private IJobManager jobManager;

    /**
     * 任务定义：名称 -> 任务信息
     */
    private final Map<String, JobInfo> jobs = new ConcurrentHashMap<>();

    /**
     * 任务执行历史
     */
    private final List<JobHistory> jobHistory = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 初始化时加载任务
     */
    @Init
    public void init() {
        // 获取 Solon 的 JobManager 实例
        jobManager = JobManager.getInstance();
        log.info("SchedulerService 初始化，JobManager: {}", jobManager != null ? "已加载" : "未找到");

        // 加载持久化的任务
        loadJobs();
        loadJobHistory();

        // 恢复之前保存的任务
        restoreJobs();
    }

    /**
     * 添加 Cron 表达式任务
     *
     * @param name     任务名称
     * @param cron     Cron 表达式
     * @param command  要执行的命令（发送给 Agent）
     * @return 是否添加成功
     */
    public boolean addCronJob(String name, String cron, String command) {
        if (jobs.containsKey(name)) {
            log.warn("任务已存在: {}", name);
            return false;
        }

        try {
            // 创建任务信息
            JobInfo jobInfo = new JobInfo(name, cron, 0, false, 0, command, JobType.CRON);
            jobs.put(name, jobInfo);

            // 注册到 Solon 调度器
            registerJob(jobInfo);

            // 持久化
            saveJobs();
            log.info("添加 Cron 任务: name={}, cron={}", name, cron);
            return true;
        } catch (Exception e) {
            log.error("添加 Cron 任务失败: {}", name, e);
            jobs.remove(name);
            return false;
        }
    }

    /**
     * 添加固定频率任务
     *
     * @param name     任务名称
     * @param fixedRate 固定频率（毫秒）
     * @param command  要执行的命令
     * @return 是否添加成功
     */
    public boolean addFixedRateJob(String name, long fixedRate, String command) {
        if (jobs.containsKey(name)) {
            log.warn("任务已存在: {}", name);
            return false;
        }

        try {
            JobInfo jobInfo = new JobInfo(name, null, fixedRate, false, 0, command, JobType.FIXED_RATE);
            jobs.put(name, jobInfo);

            registerJob(jobInfo);
            saveJobs();
            log.info("添加固定频率任务: name={}, fixedRate={}ms", name, fixedRate);
            return true;
        } catch (Exception e) {
            log.error("添加固定频率任务失败: {}", name, e);
            jobs.remove(name);
            return false;
        }
    }

    /**
     * 添加一次性任务
     *
     * @param name          任务名称
     * @param delayMillis   延迟时间（毫秒）
     * @param command       要执行的命令
     * @return 是否添加成功
     */
    public boolean addOneTimeJob(String name, long delayMillis, String command) {
        if (jobs.containsKey(name)) {
            log.warn("任务已存在: {}", name);
            return false;
        }

        try {
            long scheduleTime = System.currentTimeMillis() + delayMillis;
            JobInfo jobInfo = new JobInfo(name, null, delayMillis, true, scheduleTime, command, JobType.ONE_TIME);
            jobs.put(name, jobInfo);

            registerJob(jobInfo);
            saveJobs();
            log.info("添加一次性任务: name={}, delay={}ms", name, delayMillis);
            return true;
        } catch (Exception e) {
            log.error("添加一次性任务失败: {}", name, e);
            jobs.remove(name);
            return false;
        }
    }

    /**
     * 注册任务到 Solon 调度器
     */
    private void registerJob(JobInfo jobInfo) {
        if (jobManager == null) {
            log.warn("JobManager 未初始化，任务将只保存到配置文件");
            return;
        }

        try {
            // 创建 Scheduled 注解实例
            final Scheduled scheduled = new Scheduled() {
                @Override
                public String name() {
                    return jobInfo.name();
                }

                @Override
                public String cron() {
                    return jobInfo.cron() != null ? jobInfo.cron() : "";
                }

                @Override
                public String zone() {
                    return "";
                }

                @Override
                public long initialDelay() {
                    return 0;
                }

                @Override
                public long fixedRate() {
                    return jobInfo.jobType() == JobType.FIXED_RATE ? jobInfo.fixedRate() : 0;
                }

                @Override
                public long fixedDelay() {
                    return jobInfo.jobType() == JobType.ONE_TIME ? jobInfo.fixedRate() : 0;
                }

                @Override
                public boolean enable() {
                    return true;
                }

                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return Scheduled.class;
                }
            };

            // 创建任务处理器
            JobHandler handler = (ctx) -> {
                executeJob(jobInfo);
            };

            jobManager.jobAdd(jobInfo.name(), scheduled, handler);

            log.debug("任务已注册到调度器: {}", jobInfo.name());
        } catch (Exception e) {
            log.error("注册任务到调度器失败: {}", jobInfo.name(), e);
            throw new RuntimeException("注册任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行任务
     */
    private void executeJob(JobInfo jobInfo) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;

        try {
            log.info("执行任务: {}", jobInfo.name());

            // 调用 Agent 执行命令
            String response = agentService.chat(jobInfo.command(), "scheduler-" + jobInfo.name());

            success = true;
            log.info("任务执行成功: {}, 响应长度: {}", jobInfo.name(), response.length());

            // 如果是一次性任务，执行后删除
            if (jobInfo.isOneTime()) {
                removeJob(jobInfo.name());
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("任务执行失败: {}", jobInfo.name(), e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            recordJobExecution(jobInfo.name(), success, duration, errorMessage);
        }
    }

    /**
     * 删除任务
     *
     * @param name 任务名称
     * @return 是否删除成功
     */
    public boolean removeJob(String name) {
        JobInfo jobInfo = jobs.remove(name);
        if (jobInfo == null) {
            log.warn("任务不存在: {}", name);
            return false;
        }

        // 从 Solon 调度器中移除
        if (jobManager != null && jobManager.jobExists(name)) {
            jobManager.jobRemove(name);
        }

        saveJobs();
        log.info("删除任务: {}", name);
        return true;
    }

    /**
     * 暂停任务
     *
     * @param name 任务名称
     * @return 是否暂停成功
     */
    public boolean pauseJob(String name) {
        if (!jobs.containsKey(name)) {
            log.warn("任务不存在: {}", name);
            return false;
        }

        try {
            if (jobManager != null && jobManager.jobExists(name)) {
                jobManager.jobStop(name);
                log.info("暂停任务: {}", name);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("暂停任务失败: {}", name, e);
            return false;
        }
    }

    /**
     * 恢复任务
     *
     * @param name 任务名称
     * @return 是否恢复成功
     */
    public boolean resumeJob(String name) {
        if (!jobs.containsKey(name)) {
            log.warn("任务不存在: {}", name);
            return false;
        }

        try {
            if (jobManager != null && jobManager.jobExists(name)) {
                jobManager.jobStart(name, null);
                log.info("恢复任务: {}", name);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("恢复任务失败: {}", name, e);
            return false;
        }
    }

    /**
     * 获取所有任务
     *
     * @return 任务列表
     */
    public List<JobInfo> getJobs() {
        return new ArrayList<>(jobs.values());
    }

    /**
     * 获取任务信息
     *
     * @param name 任务名称
     * @return 任务信息
     */
    public JobInfo getJob(String name) {
        return jobs.get(name);
    }

    /**
     * 检查任务是否存在
     *
     * @param name 任务名称
     * @return 是否存在
     */
    public boolean hasJob(String name) {
        return jobs.containsKey(name);
    }

    /**
     * 获取任务执行历史
     *
     * @param limit 数量限制
     * @return 执行历史列表
     */
    public List<JobHistory> getJobHistory(int limit) {
        int size = jobHistory.size();
        if (limit <= 0 || limit > size) {
            return new ArrayList<>(jobHistory);
        }
        return new ArrayList<>(jobHistory.subList(size - limit, size));
    }

    /**
     * 获取指定任务的执行历史
     *
     * @param name 任务名称
     * @param limit 数量限制
     * @return 执行历史列表
     */
    public List<JobHistory> getJobHistoryByName(String name, int limit) {
        List<JobHistory> result = new ArrayList<>();
        for (int i = jobHistory.size() - 1; i >= 0 && result.size() < limit; i--) {
            JobHistory history = jobHistory.get(i);
            if (history.name().equals(name)) {
                result.add(history);
            }
        }
        return result;
    }

    /**
     * 记录任务执行历史
     */
    private void recordJobExecution(String name, boolean success, long duration, String errorMessage) {
        JobHistory history = new JobHistory(
            name,
            System.currentTimeMillis(),
            duration,
            success,
            errorMessage
        );
        jobHistory.add(history);
        saveJobHistory();
    }

    /**
     * 清空任务历史
     */
    public void clearJobHistory() {
        jobHistory.clear();
        saveJobHistory();
        log.info("任务历史已清空");
    }

    /**
     * 恢复之前保存的任务
     */
    private void restoreJobs() {
        for (JobInfo jobInfo : jobs.values()) {
            try {
                if (jobManager != null && !jobManager.jobExists(jobInfo.name())) {
                    registerJob(jobInfo);
                    log.debug("恢复任务: {}", jobInfo.name());
                }
            } catch (Exception e) {
                log.error("恢复任务失败: {}", jobInfo.name(), e);
            }
        }
    }

    /**
     * 保存任务配置到文件
     */
    private void saveJobs() {
        try {
            Path jobsFile = workspaceInfo.jobsFile();
            Files.createDirectories(jobsFile.getParent());

            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            int i = 0;
            for (JobInfo job : jobs.values()) {
                if (i > 0) sb.append(",\n");
                sb.append("  {\n");
                sb.append("    \"name\": \"").append(escapeJson(job.name())).append("\",\n");
                sb.append("    \"cron\": ").append(job.cron() != null ? "\"" + escapeJson(job.cron()) + "\"" : "null").append(",\n");
                sb.append("    \"fixedRate\": ").append(job.fixedRate()).append(",\n");
                sb.append("    \"isOneTime\": ").append(job.isOneTime()).append(",\n");
                sb.append("    \"scheduleTime\": ").append(job.scheduleTime()).append(",\n");
                sb.append("    \"command\": \"").append(escapeJson(job.command())).append("\",\n");
                sb.append("    \"jobType\": \"").append(job.jobType().name()).append("\"\n");
                sb.append("  }");
                i++;
            }
            sb.append("\n]");

            Files.writeString(jobsFile, sb.toString());
            log.debug("任务配置已保存: {}", jobsFile);
        } catch (Exception e) {
            log.error("保存任务配置失败", e);
        }
    }

    /**
     * 从文件加载任务配置
     */
    private void loadJobs() {
        try {
            Path jobsFile = workspaceInfo.jobsFile();
            if (!Files.exists(jobsFile)) {
                return;
            }

            String content = Files.readString(jobsFile);
            // 简化的 JSON 解析
            parseJobsJson(content);
            log.info("从文件加载了 {} 个任务: {}", jobs.size(), jobsFile);
        } catch (Exception e) {
            log.error("加载任务配置失败", e);
        }
    }

    /**
     * 简化的 JSON 解析
     */
    private void parseJobsJson(String json) {
        // 移除首尾空白和方括号
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);

        // 分割对象
        int braceCount = 0;
        StringBuilder currentObj = new StringBuilder();
        for (char c : json.toCharArray()) {
            if (c == '{') braceCount++;
            if (c == '}') braceCount--;
            currentObj.append(c);
            if (braceCount == 0 && currentObj.length() > 0) {
                String obj = currentObj.toString().trim();
                if (obj.startsWith("{") && obj.endsWith("}")) {
                    JobInfo jobInfo = parseJobObject(obj);
                    if (jobInfo != null) {
                        jobs.put(jobInfo.name(), jobInfo);
                    }
                }
                currentObj = new StringBuilder();
            }
        }
    }

    /**
     * 解析单个任务对象
     */
    private JobInfo parseJobObject(String obj) {
        try {
            String name = extractStringValue(obj, "name");
            String cron = extractStringValue(obj, "cron");
            long fixedRate = extractLongValue(obj, "fixedRate");
            boolean isOneTime = extractBooleanValue(obj, "isOneTime");
            long scheduleTime = extractLongValue(obj, "scheduleTime");
            String command = extractStringValue(obj, "command");
            String jobTypeStr = extractStringValue(obj, "jobType");

            JobType jobType = JobType.CRON;
            if (jobTypeStr != null) {
                try {
                    jobType = JobType.valueOf(jobTypeStr);
                } catch (IllegalArgumentException ignored) {}
            }

            return new JobInfo(name, cron, fixedRate, isOneTime, scheduleTime, command, jobType);
        } catch (Exception e) {
            log.warn("解析任务对象失败: {}", obj, e);
            return null;
        }
    }

    /**
     * 保存任务执行历史到文件
     */
    private void saveJobHistory() {
        try {
            Path historyFile = workspaceInfo.jobHistoryFile();
            Files.createDirectories(historyFile.getParent());

            // 只保留最近 1000 条历史记录
            List<JobHistory> toSave = new ArrayList<>(jobHistory);
            if (toSave.size() > 1000) {
                toSave = toSave.subList(toSave.size() - 1000, toSave.size());
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            for (int i = 0; i < toSave.size(); i++) {
                if (i > 0) sb.append(",\n");
                JobHistory h = toSave.get(i);
                sb.append("  {\n");
                sb.append("    \"name\": \"").append(escapeJson(h.name())).append("\",\n");
                sb.append("    \"executionTime\": ").append(h.executionTime()).append(",\n");
                sb.append("    \"duration\": ").append(h.duration()).append(",\n");
                sb.append("    \"success\": ").append(h.success()).append(",\n");
                sb.append("    \"errorMessage\": ").append(h.errorMessage() != null ? "\"" + escapeJson(h.errorMessage()) + "\"" : "null").append("\n");
                sb.append("  }");
            }
            sb.append("\n]");

            Files.writeString(historyFile, sb.toString());
            log.debug("任务历史已保存: {}", historyFile);
        } catch (Exception e) {
            log.error("保存任务历史失败", e);
        }
    }

    /**
     * 从文件加载任务执行历史
     */
    private void loadJobHistory() {
        try {
            Path historyFile = workspaceInfo.jobHistoryFile();
            if (!Files.exists(historyFile)) {
                return;
            }

            String content = Files.readString(historyFile);
            parseHistoryJson(content);
            log.info("从文件加载了 {} 条任务历史: {}", jobHistory.size(), historyFile);
        } catch (Exception e) {
            log.error("加载任务历史失败", e);
        }
    }

    /**
     * 解析历史 JSON
     */
    private void parseHistoryJson(String json) {
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);

        int braceCount = 0;
        StringBuilder currentObj = new StringBuilder();
        for (char c : json.toCharArray()) {
            if (c == '{') braceCount++;
            if (c == '}') braceCount--;
            currentObj.append(c);
            if (braceCount == 0 && currentObj.length() > 0) {
                String obj = currentObj.toString().trim();
                if (obj.startsWith("{") && obj.endsWith("}")) {
                    JobHistory history = parseHistoryObject(obj);
                    if (history != null) {
                        jobHistory.add(history);
                    }
                }
                currentObj = new StringBuilder();
            }
        }
    }

    /**
     * 解析单个历史对象
     */
    private JobHistory parseHistoryObject(String obj) {
        try {
            String name = extractStringValue(obj, "name");
            long executionTime = extractLongValue(obj, "executionTime");
            long duration = extractLongValue(obj, "duration");
            boolean success = extractBooleanValue(obj, "success");
            String errorMessage = extractStringValue(obj, "errorMessage");

            return new JobHistory(name, executionTime, duration, success, errorMessage);
        } catch (Exception e) {
            log.warn("解析历史对象失败: {}", obj, e);
            return null;
        }
    }

    // JSON 辅助方法
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String extractStringValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) start++;
        if (start >= json.length() || json.charAt(start) != '"') return null;
        start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != '"') {
            if (json.charAt(end) == '\\') end++;
            end++;
        }
        return json.substring(start, end).replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\\\", "\\");
    }

    private long extractLongValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return 0;
        start += pattern.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean extractBooleanValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return false;
        start += pattern.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) start++;
        return json.substring(start, start + 4).equals("true");
    }

    /**
     * 任务类型
     */
    public enum JobType {
        CRON,       // Cron 表达式任务
        FIXED_RATE, // 固定频率任务
        ONE_TIME    // 一次性任务
    }

    /**
     * 任务信息
     *
     * @param name          任务名称
     * @param cron          Cron 表达式
     * @param fixedRate     固定频率（毫秒）
     * @param isOneTime     是否为一次性任务
     * @param scheduleTime  调度时间（一次性任务）
     * @param command       要执行的命令
     * @param jobType       任务类型
     */
    public record JobInfo(
            String name,
            String cron,
            long fixedRate,
            boolean isOneTime,
            long scheduleTime,
            String command,
            JobType jobType
    ) {
    }

    /**
     * 任务执行历史
     *
     * @param name           任务名称
     * @param executionTime  执行时间
     * @param duration       执行时长（毫秒）
     * @param success        是否成功
     * @param errorMessage   错误消息（如果有）
     */
    public record JobHistory(
            String name,
            long executionTime,
            long duration,
            boolean success,
            String errorMessage
    ) {
    }
}